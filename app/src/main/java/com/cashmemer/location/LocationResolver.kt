package com.cashmemer.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.cashmemer.core.BuildConfig
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Turns "where am I" into a street address for the receipt's location field.
 *
 * Tries the on-device geocoder first because it is free and works offline in
 * many regions, then falls back to the Maps Geocoding API — that fallback is
 * what MAPS_API_KEY is for.
 */
object LocationResolver {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    /** Fixes the device position and returns a human-readable address for it. */
    suspend fun currentAddress(context: Context): Result<String> = runCatching {
        check(hasPermission(context)) { "Location permission not granted" }

        val location = currentLocation(context) ?: error("Could not get a location fix")

        deviceGeocode(context, location)
            ?: remoteGeocode(location.latitude, location.longitude)
            ?: "${location.latitude}, ${location.longitude}"
    }

    /** Just the coordinates, for centring the map picker. */
    suspend fun currentLatLng(context: Context): Result<Pair<Double, Double>> = runCatching {
        check(hasPermission(context)) { "Location permission not granted" }
        val location = currentLocation(context) ?: error("Could not get a location fix")
        location.latitude to location.longitude
    }

    /** Reverse-geocodes an arbitrary point — used as the map camera settles. */
    suspend fun addressFor(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): Result<String> = runCatching {
        val point = Location("map").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        deviceGeocode(context, point)
            ?: remoteGeocode(latitude, longitude)
            ?: "$latitude, $longitude"
    }

    /** Forward geocode for the picker's search box. */
    suspend fun searchPlace(
        context: Context,
        query: String,
    ): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        runCatching {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocationName(query, 1)
                    ?.firstOrNull()
                    ?.let { return@runCatching it.latitude to it.longitude }
            }

            val key = BuildConfig.MAPS_API_KEY
            require(key.isNotBlank()) { "No result, and MAPS_API_KEY is not set" }

            val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                "?address=${java.net.URLEncoder.encode(query, "UTF-8")}&key=$key"

            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                check(json.optString("status") == "OK") { "No match for \"$query\"" }

                val location = json.getJSONArray("results")
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONObject("location")

                location.getDouble("lat") to location.getDouble("lng")
            }
        }
    }

    @SuppressLint("MissingPermission") // guarded by the hasPermission check above
    private suspend fun currentLocation(context: Context): Location? =
        suspendCancellableCoroutine { continuation ->
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(10_000)
                .build()

            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(request, null)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

    /** Platform geocoder. Async on API 33+, blocking (deprecated) below that. */
    private suspend fun deviceGeocode(context: Context, location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(location.latitude, location.longitude, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses.firstOrNull()?.format())
                        }

                        override fun onError(message: String?) {
                            continuation.resume(null)
                        }
                    })
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()
                        ?.format()
                }.getOrNull()
            }
        }
    }

    /** Maps Geocoding API fallback for when the device geocoder has no backend. */
    private suspend fun remoteGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            val key = BuildConfig.MAPS_API_KEY
            if (key.isBlank()) return@withContext null

            runCatching {
                val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                    "?latlng=$latitude,$longitude&key=$key"

                val request = Request.Builder().url(url).get().build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

                    val json = JSONObject(response.body?.string().orEmpty())
                    if (json.optString("status") != "OK") return@use null

                    json.optJSONArray("results")
                        ?.optJSONObject(0)
                        ?.optString("formatted_address")
                        ?.takeIf { it.isNotBlank() }
                }
            }.getOrNull()
        }

    /** Joins the useful address lines, skipping the blanks the geocoder leaves. */
    private fun Address.format(): String? {
        val street = (0..maxAddressLineIndex.coerceAtLeast(0))
            .mapNotNull { runCatching { getAddressLine(it) }.getOrNull() }
            .firstOrNull()

        if (!street.isNullOrBlank()) return street

        return listOfNotNull(featureName, thoroughfare, subLocality, locality, countryName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
    }
}
