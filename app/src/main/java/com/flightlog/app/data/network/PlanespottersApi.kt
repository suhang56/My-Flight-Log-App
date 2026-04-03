package com.flightlog.app.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface PlanespottersApi {

    @GET("pub/photos/reg/{registration}")
    suspend fun getPhotosByRegistration(
        @Path("registration") registration: String
    ): Response<PlanespottersResponse>
}

@JsonClass(generateAdapter = true)
data class PlanespottersResponse(
    @Json(name = "photos") val photos: List<PlanespottersPhoto>?
)

@JsonClass(generateAdapter = true)
data class PlanespottersPhoto(
    @Json(name = "id") val id: String?,
    @Json(name = "thumbnail_large") val thumbnailLarge: PlanespottersThumbnail?,
    @Json(name = "photographer") val photographer: String?
)

@JsonClass(generateAdapter = true)
data class PlanespottersThumbnail(
    @Json(name = "src") val src: String?,
    @Json(name = "size") val size: PlanespottersSize?
)

@JsonClass(generateAdapter = true)
data class PlanespottersSize(
    @Json(name = "width") val width: Int?,
    @Json(name = "height") val height: Int?
)
