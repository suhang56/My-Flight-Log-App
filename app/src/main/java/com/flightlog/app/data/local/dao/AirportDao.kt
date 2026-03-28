package com.flightlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.flightlog.app.data.local.entity.Airport

@Dao
interface AirportDao {

    @Query("SELECT * FROM airports WHERE iata = :iata LIMIT 1")
    suspend fun getByIata(iata: String): Airport?

    @Query("SELECT * FROM airports WHERE icao = :icao LIMIT 1")
    suspend fun getByIcao(icao: String): Airport?

    @Query("""
        SELECT * FROM airports
        WHERE (name LIKE '%' || :query || '%' COLLATE NOCASE
            OR city LIKE '%' || :query || '%' COLLATE NOCASE
            OR iata LIKE :query || '%' COLLATE NOCASE)
        AND iata != ''
        ORDER BY iata ASC
        LIMIT 20
    """)
    suspend fun search(query: String): List<Airport>
}
