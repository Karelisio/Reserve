package com.karelisio.reserve

import com.karelisio.reserve.data.charging.ChargingRepository
import com.karelisio.reserve.data.charging.Plug
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingParserTest {
    private val repo = ChargingRepository(OkHttpClient())

    @Test
    fun irveRowsAreGroupedByStation() {
        val rows = listOf(
            """{"id_station_itinerance":"FRXXXP1","nom_station":"Parking Mairie","nom_operateur":"Izivia","adresse_station":"1 place de la Mairie",
               "consolidated_latitude":48.85,"consolidated_longitude":2.35,"consolidated_code_postal":"75004","consolidated_commune":"Paris",
               "nbre_pdc":2,"puissance_nominale":22,"prise_type_2":"true","prise_type_combo_ccs":"false","gratuit":"false","horaires":"24/7"}""",
            """{"id_station_itinerance":"FRXXXP1","nom_station":"Parking Mairie","consolidated_latitude":48.85,"consolidated_longitude":2.35,
               "puissance_nominale":50000,"prise_type_2":"false","prise_type_combo_ccs":"true"}""",
        ).map { Json.parseToJsonElement(it).jsonObject }
        val station = repo.irveStation("FRXXXP1", rows)!!
        assertEquals(50.0, station.maxPowerKw!!, 0.01) // 50000 W published in watts
        assertEquals(setOf(Plug.TYPE2, Plug.CCS), station.plugs)
        assertEquals(2, station.points)
        assertEquals(false, station.free)
        assertTrue(station.open24)
        assertEquals("1 place de la Mairie, 75004 Paris", station.address)
    }

    @Test
    fun osmElement() {
        val element = Json.parseToJsonElement(
            """{"type":"node","id":42,"lat":45.1,"lon":5.7,"tags":{"amenity":"charging_station","operator":"Ionity",
               "socket:type2_combo":"4","socket:type2_combo:output":"350 kW","capacity":"4","fee":"yes","opening_hours":"24/7"}}""",
        ).jsonObject
        val station = repo.osmStation(element)!!
        assertEquals(350.0, station.maxPowerKw!!, 0.01)
        assertEquals(setOf(Plug.CCS), station.plugs)
        assertEquals("Ionity", station.operator)
        assertEquals(false, station.free)
    }
}
