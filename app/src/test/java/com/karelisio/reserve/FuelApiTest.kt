package com.karelisio.reserve

import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.remote.FuelApi
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Requests answered locally: which endpoint is called, and how many times. */
class FuelApiTest {

    private fun record(id: Long) =
        """{"id": $id, "geom": {"lon": 5.0, "lat": 45.0}, "adresse": "1 RUE", "ville": "LYON", "gazole_prix": 1.799, "gazole_maj": "2026-10-26T07:00:00+00:00"}"""

    private fun api(answer: (path: String, offset: Int) -> Pair<Int, String>): Pair<FuelApi, MutableList<String>> {
        val calls = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val url = chain.request().url
            calls += url.encodedPath.substringAfterLast('/')
            val (code, body) = answer(url.encodedPath, url.queryParameter("offset")?.toInt() ?: 0)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }).build()
        return FuelApi(client) to calls
    }

    @Test
    fun allStationsInOneExportRequest() = runBlocking {
        val (api, calls) = api { _, _ -> 200 to (1L..250L).joinToString(",", "[", "]") { record(it) } }
        val stations = api.stationsAround(LatLon(45.0, 5.0), 20.0)
        assertEquals(250, stations.size)
        assertEquals(listOf("json"), calls)
    }

    @Test
    fun pagesWhenTheExportIsRefused() = runBlocking {
        val (api, calls) = api { path, offset ->
            if (path.endsWith("/json")) 404 to "{}"
            else 200 to """{"total_count": 150, "results": [${(offset + 1L..minOf(offset + 100L, 150L)).joinToString(",") { record(it) }}]}"""
        }
        assertEquals(150, api.stationsAround(LatLon(45.0, 5.0), 20.0).size)
        assertEquals(listOf("json", "records", "records"), calls)
        // The export is not retried afterwards.
        api.stationsAround(LatLon(45.0, 5.0), 20.0)
        assertTrue(calls.drop(3).all { it == "records" })
    }

    @Test
    fun pagesWhenTheExportFormatIsUnexpected() = runBlocking {
        val (api, _) = api { path, _ ->
            if (path.endsWith("/json")) 200 to """[{"unexpected": true}]"""
            else 200 to """{"total_count": 1, "results": [${record(7)}]}"""
        }
        assertEquals(listOf(7L), api.stationsAround(LatLon(45.0, 5.0), 20.0).map { it.id })
    }
}
