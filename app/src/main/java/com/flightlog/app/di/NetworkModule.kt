package com.flightlog.app.di

import com.flightlog.app.BuildConfig
import com.flightlog.app.data.network.AviationStackApi
import com.flightlog.app.data.network.FlightAwareApi
import com.flightlog.app.data.network.FlightRouteService
import com.flightlog.app.data.network.FlightRouteServiceImpl
import com.flightlog.app.data.network.PlanespottersApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://aeroapi.flightaware.com/aeroapi/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val apiKey = BuildConfig.FLIGHTAWARE_API_KEY
                val request = if (apiKey.isNotBlank()) {
                    chain.request().newBuilder()
                        .addHeader("x-apikey", apiKey)
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideFlightAwareApi(retrofit: Retrofit): FlightAwareApi {
        return retrofit.create(FlightAwareApi::class.java)
    }

    @Provides
    @Singleton
    @Named("planespotters")
    fun providePlanespottersOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @PlanespottersRetrofit
    fun providePlanespottersRetrofit(
        @Named("planespotters") client: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.planespotters.net/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun providePlanespottersApi(@PlanespottersRetrofit retrofit: Retrofit): PlanespottersApi {
        return retrofit.create(PlanespottersApi::class.java)
    }

    // -- AviationStack (HTTP-only free tier) --
    // AviationStack free tier does not support HTTPS. The access_key is a
    // low-risk free-tier key passed as a query parameter per-request, so no
    // auth interceptor is needed. Cleartext is allowed via network_security_config.xml.

    @Provides
    @Singleton
    @Named("aviationStack")
    fun provideAviationStackOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @AviationStackRetrofit
    fun provideAviationStackRetrofit(
        @Named("aviationStack") client: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://api.aviationstack.com/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideAviationStackApi(@AviationStackRetrofit retrofit: Retrofit): AviationStackApi {
        return retrofit.create(AviationStackApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {

    @Binds
    @Singleton
    abstract fun bindFlightRouteService(impl: FlightRouteServiceImpl): FlightRouteService
}
