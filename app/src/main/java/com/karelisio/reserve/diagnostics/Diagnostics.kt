package com.karelisio.reserve.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.karelisio.reserve.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Local diagnostics, without any server: crashes and recent errors are written to the app
 * storage and can be sent by the user as a pre-filled GitHub issue or any sharing app.
 * Nothing leaves the phone without an explicit action, and no position is logged.
 */
object Diagnostics {
    private const val MAX_LOG_BYTES = 48 * 1024
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Volatile
    private var dir: File? = null

    fun install(context: Context) {
        dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(
                    "Thread : ${thread.name}\n${stackTrace(error)}",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Records a handled error (API failure, fallback…) to help diagnose problems later. */
    fun log(tag: String, message: String, error: Throwable? = null) {
        val file = dir?.let { File(it, "errors.log") } ?: return
        runCatching {
            if (file.length() > MAX_LOG_BYTES) {
                file.writeText(file.readText().takeLast(MAX_LOG_BYTES / 2))
            }
            val cause = error?.let { " — ${it::class.java.simpleName}: ${it.message}" }.orEmpty()
            file.appendText("${LocalDateTime.now().format(timeFormat)} [$tag] $message$cause\n")
        }
    }

    /** Crash of a previous session that the user has not seen yet. */
    fun pendingCrash(): String? = crashFiles().firstOrNull()?.let { runCatching { it.readText() }.getOrNull() }

    fun acknowledgeCrashes() {
        crashFiles().forEach { it.renameTo(File(it.parentFile, it.name.replace("crash-", "seen-"))) }
        // Keep only the last few reports.
        dir?.listFiles { f -> f.name.startsWith("seen-") }?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
    }

    private fun crashFiles(): List<File> =
        dir?.listFiles { f -> f.name.startsWith("crash-") }?.sortedByDescending { it.name }.orEmpty()

    fun report(crash: String?): String = buildString {
        appendLine("**Version** : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("**Android** : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("**Appareil** : ${Build.MANUFACTURER} ${Build.MODEL} · ${Build.SUPPORTED_ABIS.firstOrNull()}")
        appendLine()
        appendLine("**Description** : (décrivez ce qui s'est passé)")
        appendLine()
        if (crash != null) {
            appendLine("**Plantage**")
            appendLine("```")
            appendLine(crash.take(2500))
            appendLine("```")
        }
        val recent = dir?.let { File(it, "errors.log") }?.takeIf { it.exists() }?.readLines()?.takeLast(25).orEmpty()
        if (recent.isNotEmpty()) {
            appendLine("**Dernières erreurs**")
            appendLine("```")
            recent.forEach { appendLine(it.take(200)) }
            appendLine("```")
        }
    }

    /** Pre-filled issue on the project repository (the body is shortened to fit in a URL). */
    fun githubIssueIntent(crash: String?): Intent {
        val title = if (crash != null) "Plantage : ${crash.lineSequence().drop(1).firstOrNull()?.take(80).orEmpty()}" else "Problème : "
        val body = report(crash).take(5500)
        val uri = Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}/issues/new").buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body)
            .build()
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Same report through any app (mail, messaging…). */
    fun shareIntent(crash: String?): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Reserve ${BuildConfig.VERSION_NAME} : rapport de problème")
                .putExtra(Intent.EXTRA_TEXT, report(crash)),
            "Envoyer le rapport",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun stackTrace(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
}
