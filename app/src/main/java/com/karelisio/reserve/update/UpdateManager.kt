package com.karelisio.reserve.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import com.karelisio.reserve.BuildConfig
import com.karelisio.reserve.container
import com.karelisio.reserve.data.prefs.SettingsRepository
import com.karelisio.reserve.data.remote.arr
import com.karelisio.reserve.data.remote.await
import com.karelisio.reserve.data.remote.getJson
import com.karelisio.reserve.data.remote.lng
import com.karelisio.reserve.data.remote.obj
import com.karelisio.reserve.data.remote.str
import com.karelisio.reserve.diagnostics.Diagnostics
import com.karelisio.reserve.domain.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** A release published on GitHub by the CI. */
data class AppUpdate(
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class Installing(val update: AppUpdate) : UpdateState
    data class Failed(val message: String, val update: AppUpdate?) : UpdateState
}

/**
 * In-app updates from the GitHub releases of the project (the app is not on the Play Store):
 * check the latest release, download the APK for this device and install it with
 * [PackageInstaller]. Android asks the user to confirm; the signature must match.
 */
class UpdateManager(
    private val context: Context,
    http: OkHttpClient,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val api = http
    private val downloads = http.newBuilder().cache(null).readTimeout(2, TimeUnit.MINUTES).build()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    val enabled: Boolean get() = BuildConfig.UPDATES_ENABLED

    /** At launch: at most every 6 hours, silent when there is nothing new. */
    fun checkOnLaunch() {
        if (!enabled) return
        scope.launch {
            if (System.currentTimeMillis() - settings.lastUpdateCheck() < CHECK_INTERVAL_MS) return@launch
            check(userInitiated = false)
        }
    }

    fun checkNow() {
        if (!enabled) {
            _state.value = UpdateState.Failed(t("Mises à jour désactivées dans la version de développement.", "Updates are disabled in the development build."), null)
            return
        }
        scope.launch { check(userInitiated = true) }
    }

    private suspend fun check(userInitiated: Boolean) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        _state.value = UpdateState.Checking
        _state.value = try {
            val update = latestRelease()
            settings.setLastUpdateCheck(System.currentTimeMillis())
            when {
                update != null && update.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Available(update)
                userInitiated -> UpdateState.UpToDate
                else -> UpdateState.Idle
            }
        } catch (e: Exception) {
            Diagnostics.log("maj", "Vérification des mises à jour impossible", e)
            if (userInitiated) {
                UpdateState.Failed(t("Impossible de vérifier les mises à jour. Vérifiez la connexion.", "Could not check for updates. Check the connection."), null)
            } else {
                UpdateState.Idle
            }
        }
    }

    private suspend fun latestRelease(): AppUpdate? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val release = api.getJson(request).obj() ?: return null
        val assets = release["assets"].arr().orEmpty().mapNotNull { asset ->
            val obj = asset.obj() ?: return@mapNotNull null
            val name = obj.str("name") ?: return@mapNotNull null
            val match = ASSET_NAME.matchEntire(name) ?: return@mapNotNull null
            Triple(match.groupValues[2], match.groupValues[1].toLong(), obj)
        }
        // The APK for this device's ABI (smaller), else the universal one.
        val preferred = Build.SUPPORTED_ABIS.toList() + "universal"
        val (_, code, asset) = preferred.firstNotNullOfOrNull { abi -> assets.firstOrNull { it.first == abi } } ?: return null
        return AppUpdate(
            versionName = (release.str("tag_name") ?: release.str("name")).orEmpty().removePrefix("v"),
            versionCode = code,
            notes = release.str("body").orEmpty().trim(),
            apkUrl = asset.str("browser_download_url") ?: return null,
            sizeBytes = asset.lng("size") ?: 0L,
        )
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.Installing) _state.value = UpdateState.Idle
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun downloadAndInstall(update: AppUpdate) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                val apk = download(update)
                _state.value = UpdateState.Installing(update)
                install(apk)
            } catch (e: Exception) {
                Diagnostics.log("maj", "Téléchargement ou installation impossible", e)
                _state.value = UpdateState.Failed(e.message ?: t("Échec du téléchargement.", "Download failed."), update)
            }
        }
    }

    private suspend fun download(update: AppUpdate): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "reserve-${update.versionCode}.apk")
        _state.value = UpdateState.Downloading(update, 0f)
        val response = downloads.newCall(Request.Builder().url(update.apkUrl).build()).await()
        withContext(Dispatchers.IO) {
            response.use {
                if (!it.isSuccessful) throw IllegalStateException(t("Téléchargement refusé (HTTP ${it.code}).", "Download refused (HTTP ${it.code})."))
                val body = it.body ?: throw IllegalStateException(t("Téléchargement vide.", "Empty download."))
                val total = body.contentLength().takeIf { len -> len > 0 } ?: update.sizeBytes
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        var lastEmit = 0f
                        while (input.read(buffer).also { n -> read = n } >= 0) {
                            output.write(buffer, 0, read)
                            done += read
                            val progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                            if (progress - lastEmit >= 0.01f) {
                                lastEmit = progress
                                _state.value = UpdateState.Downloading(update, progress)
                            }
                        }
                    }
                }
            }
        }
        // Sanity check: same app, newer version.
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: throw IllegalStateException(t("Fichier de mise à jour invalide.", "Invalid update file."))
        if (info.packageName != context.packageName || PackageInfoCompat.getLongVersionCode(info) <= BuildConfig.VERSION_CODE) {
            throw IllegalStateException(
                t("Cette mise à jour ne correspond pas à l'application installée.", "This update does not match the installed app."),
            )
        }
        return file
    }

    private suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("reserve.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS),
                // Mutable: the system adds the install status to this (explicit) intent.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    internal fun onInstallStatus(status: Int, message: String?) {
        val update = (_state.value as? UpdateState.Installing)?.update
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Available(update ?: return)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed(
                    t(
                        "Installation impossible : la signature diffère de la version installée.",
                        "Installation failed: the signature differs from the installed version.",
                    ),
                    update,
                )
            else -> UpdateState.Failed(
                t(
                    "Installation impossible${message?.let { " : $it" }.orEmpty()}.",
                    "Installation failed${message?.let { ": $it" }.orEmpty()}.",
                ),
                update,
            )
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.karelisio.reserve.INSTALL_STATUS"
        private const val CHECK_INTERVAL_MS = 6 * 3600 * 1000L
        /** reserve-<versionName>-<versionCode>-<abi>.apk, as named by the CI. */
        private val ASSET_NAME = Regex("""^reserve-.+-(\d+)-(arm64-v8a|armeabi-v7a|x86_64|x86|universal)\.apk$""")
    }
}

/** Receives the result of the install session; asks the user to confirm when needed. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateManager.ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { confirm ->
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        context.container.updates.onInstallStatus(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
