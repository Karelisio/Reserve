package com.karelisio.reserve

import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.remote.Http
import com.karelisio.reserve.data.remote.StationParser
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationParserTest {

    private val record = """
    {
      "id": 1000001,
      "latitude": "4620114",
      "longitude": "519791",
      "cp": "01000",
      "pop": "R",
      "adresse": "596 AVENUE DE TREVOUX",
      "ville": "SAINT-DENIS-LèS-BOURG",
      "horaires": "{\"@automate-24-24\": \"1\", \"jour\": [{\"@id\": \"1\", \"@nom\": \"Lundi\", \"@ferme\": \"\", \"horaire\": {\"@ouverture\": \"06.00\", \"@fermeture\": \"20.00\"}}, {\"@id\": \"7\", \"@nom\": \"Dimanche\", \"@ferme\": \"1\"}]}",
      "services": "{\"service\": [\"Lavage automatique\", \"Boutique alimentaire\"]}",
      "geom": {"lon": 5.19791, "lat": 46.20114},
      "gazole_maj": "2024-01-02T07:52:00+00:00",
      "gazole_prix": 1.799,
      "sp95_maj": null,
      "sp95_prix": null,
      "e10_maj": "2024-01-02 07:52:00",
      "e10_prix": 1.869,
      "carburants_disponibles": ["Gazole", "E10"],
      "carburants_rupture_temporaire": "E85",
      "carburants_rupture_definitive": ["SP95", "GPLc"],
      "horaires_automate_24_24": "Oui",
      "services_service": ["Lavage automatique", "Boutique alimentaire", "Station de gonflage"]
    }
    """.trimIndent()

    @Test
    fun parsesOfficialRecord() {
        val station = StationParser.parse(Http.json.parseToJsonElement(record).jsonObject)
        assertNotNull(station)
        station!!
        assertEquals(1000001L, station.id)
        assertEquals(46.20114, station.lat, 1e-6)
        assertEquals(5.19791, station.lon, 1e-6)
        assertEquals(1.799, station.priceOf(Fuel.GAZOLE)!!.price, 1e-6)
        assertEquals(1.869, station.priceOf(Fuel.E10)!!.price, 1e-6)
        assertEquals(null, station.priceOf(Fuel.SP95))
        assertTrue(station.automate24)
        assertFalse(station.isHighway)
        assertTrue(station.isOutOf(Fuel.E85))
        assertFalse(station.isOutOf(Fuel.SP95)) // definitive = not sold, not a temporary shortage
        assertEquals(3, station.services.size)
        assertEquals(2, station.hours.size)
        assertEquals(listOf("06:00–20:00"), station.hours[0].ranges)
        assertTrue(station.hours[1].closed)
    }

    @Test
    fun fallsBackToLegacyCoordinatesAndPriceDocument() {
        val legacy = """
        {"id": 42, "latitude": "4885660", "longitude": "235220", "ville": "PARIS", "cp": "75001", "pop": "A",
         "prix": "[{\"@nom\": \"Gazole\", \"@id\": \"1\", \"@maj\": \"2024-03-01 10:00:00\", \"@valeur\": \"1.759\"}, {\"@nom\": \"SP98\", \"@id\": \"6\", \"@maj\": \"2024-03-01 10:00:00\", \"@valeur\": \"1959\"}]"}
        """.trimIndent()
        val station = StationParser.parse(Http.json.parseToJsonElement(legacy).jsonObject)!!
        assertEquals(48.8566, station.lat, 1e-6)
        assertEquals(2.3522, station.lon, 1e-6)
        assertEquals(1.759, station.priceOf(Fuel.GAZOLE)!!.price, 1e-6)
        assertEquals(1.959, station.priceOf(Fuel.SP98)!!.price, 1e-6)
        assertTrue(station.isHighway)
    }

    @Test
    fun parsesDates() {
        assertEquals(1704181920000L, StationParser.parseDate("2024-01-02T07:52:00+00:00"))
        // Naive dates are French local time (UTC+1 in winter).
        assertEquals(1704178320000L, StationParser.parseDate("2024-01-02 07:52:00"))
    }
}
