package com.neubofy.watch.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Locale

/**
 * Fetches real weather data using:
 * 1. Device GPS/Network location (if permission granted)
 * 2. IP-based geolocation fallback (always works, no permission needed)
 * 3. Open-Meteo API (100% free, no API key needed)
 * Maps WMO weather codes to Moyoung watch icon format.
 */
class WeatherService(private val context: Context) {

    companion object {
        private const val TAG = "WeatherService"
        private const val OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"
        private const val IP_GEO_URL = "http://ip-api.com/json/?fields=lat,lon,city,status"
        private const val MIN_FETCH_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }

    data class WeatherData(
        val city: String,
        val currentTemp: Int,
        val minTemp: Int,
        val maxTemp: Int,
        val moyoungIcon: Int,
        val description: String,
        val tomorrowIcon: Int = 0,
        val tomorrowMin: Int = 0,
        val tomorrowMax: Int = 0,
        val fetchTimeMs: Long = System.currentTimeMillis()
    )

    private data class GeoLocation(val lat: Double, val lon: Double, val city: String)

    private var lastFetch: WeatherData? = null

    /**
     * Fetch real weather. Returns cached data if too recent. Returns null only on total failure.
     */
    suspend fun fetchWeather(forceRefresh: Boolean = false): WeatherData? {
        val cached = lastFetch
        if (!forceRefresh && cached != null &&
            (System.currentTimeMillis() - cached.fetchTimeMs) < MIN_FETCH_INTERVAL_MS) {
            Log.d(TAG, "Returning cached weather (${cached.city}: ${cached.currentTemp}°C)")
            return cached
        }

        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Get location (GPS → Network → IP fallback)
                val geo = getLocation()
                if (geo == null) {
                    Log.e(TAG, "Could not determine location at all")
                    return@withContext cached
                }

                Log.d(TAG, "Using location: ${geo.city} (${geo.lat}, ${geo.lon})")

                // Step 2: Fetch weather from Open-Meteo
                val url = "$OPEN_METEO_URL?latitude=${geo.lat}&longitude=${geo.lon}" +
                    "&current_weather=true" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                    "&forecast_days=7" +
                    "&timezone=auto"

                Log.d(TAG, "Fetching weather: $url")
                val response = URL(url).readText()
                val json = JSONObject(response)

                val current = json.getJSONObject("current_weather")
                val currentTemp = current.getDouble("temperature").toInt()
                val wmoCode = current.getInt("weathercode")

                val daily = json.getJSONObject("daily")
                val todayMax = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()
                val todayMin = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()
                
                val tomorrowMax = if (daily.getJSONArray("temperature_2m_max").length() > 1) daily.getJSONArray("temperature_2m_max").getDouble(1).toInt() else todayMax
                val tomorrowMin = if (daily.getJSONArray("temperature_2m_min").length() > 1) daily.getJSONArray("temperature_2m_min").getDouble(1).toInt() else todayMin
                val tomorrowWmo = if (daily.getJSONArray("weather_code").length() > 1) daily.getJSONArray("weather_code").getInt(1) else wmoCode
                val tomorrowIcon = wmoCodeToMoyoungIcon(tomorrowWmo)

                val moyoungIcon = wmoCodeToMoyoungIcon(wmoCode)
                val description = wmoCodeToDescription(wmoCode)

                val result = WeatherData(
                    city = geo.city,
                    currentTemp = currentTemp,
                    minTemp = todayMin,
                    maxTemp = todayMax,
                    moyoungIcon = moyoungIcon,
                    description = description,
                    tomorrowIcon = tomorrowIcon,
                    tomorrowMin = tomorrowMin,
                    tomorrowMax = tomorrowMax
                )

                lastFetch = result
                Log.i(TAG, "Weather OK: ${geo.city} ${currentTemp}°C ($description), range $todayMin-$todayMax")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Weather fetch failed: ${e.message}", e)
                cached
            }
        }
    }

    /**
     * Get location using best available method:
     * 1. GPS/Network provider (if permission granted)
     * 2. IP-based geolocation (always works as long as internet is available)
     */
    private fun getLocation(): GeoLocation? {
        // Try device GPS/network first
        val deviceLoc = getDeviceLocation()
        if (deviceLoc != null) return deviceLoc

        // Fallback: IP-based geolocation (no permission needed, just internet)
        Log.d(TAG, "No device location, trying IP geolocation...")
        return getIpLocation()
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation(): GeoLocation? {
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasCoarse && !hasFine) {
            Log.d(TAG, "No location permission, skipping GPS")
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                        bestLocation = loc
                    }
                }
            } catch (_: Exception) {}
        }

        if (bestLocation == null) {
            Log.d(TAG, "GPS/Network location returned null")
            return null
        }

        val city = getCityName(bestLocation.latitude, bestLocation.longitude)
        Log.d(TAG, "Device location: $city (${bestLocation.latitude}, ${bestLocation.longitude})")
        return GeoLocation(bestLocation.latitude, bestLocation.longitude, city)
    }

    /**
     * IP-based geolocation fallback. Works without any permissions.
     * Uses geojs.io free tier, fully HTTPS.
     */
    private fun getIpLocation(): GeoLocation? {
        // Try GeoJS first
        try {
            val response = URL("https://get.geojs.io/v1/ip/geo.json").readText()
            val json = JSONObject(response)
            val lat = json.optDouble("latitude", 0.0)
            val lon = json.optDouble("longitude", 0.0)
            val city = json.optString("city", "City")
            if (lat != 0.0) {
                Log.d(TAG, "IP geolocation (GeoJS): $city ($lat, $lon)")
                return GeoLocation(lat, lon, city)
            }
        } catch (e: Exception) {
            Log.e(TAG, "GeoJS failed: ${e.message}")
        }

        // Secondary fallback: ipapi.co
        try {
            val response = URL("https://ipapi.co/json/").readText()
            val json = JSONObject(response)
            val lat = json.optDouble("latitude", 0.0)
            val lon = json.optDouble("longitude", 0.0)
            val city = json.optString("city", "City")
            if (lat != 0.0) {
                Log.d(TAG, "IP geolocation (ipapi.co): $city ($lat, $lon)")
                return GeoLocation(lat, lon, city)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ipapi.co failed: ${e.message}")
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun getCityName(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                addresses[0].locality ?: addresses[0].subAdminArea ?: addresses[0].adminArea ?: "City"
            } else "City"
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder failed: ${e.message}")
            "City"
        }
    }

    /**
     * WMO Weather Codes → Moyoung watch icon IDs
     * CLOUDY=0, FOGGY=1, OVERCAST=2, RAINY=3, SNOWY=4, SUNNY=5, SANDSTORM=6, HAZE=7
     */
    private fun wmoCodeToMoyoungIcon(wmoCode: Int): Int = when (wmoCode) {
        0, 1 -> 5       // Clear / Mainly clear → SUNNY
        2 -> 0           // Partly cloudy → CLOUDY
        3 -> 2           // Overcast → OVERCAST
        45, 48 -> 1      // Fog → FOGGY
        51, 53, 55, 56, 57 -> 3 // Drizzle → RAINY
        61, 63, 65, 66, 67 -> 3 // Rain → RAINY
        71, 73, 75, 77 -> 4     // Snow → SNOWY
        80, 81, 82 -> 3  // Rain showers → RAINY
        85, 86 -> 4      // Snow showers → SNOWY
        95, 96, 99 -> 3  // Thunderstorm → RAINY
        else -> 0        // Unknown → CLOUDY
    }

    private fun wmoCodeToDescription(wmoCode: Int): String = when (wmoCode) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45 -> "Fog"
        48 -> "Rime fog"
        51 -> "Light drizzle"
        53 -> "Moderate drizzle"
        55 -> "Dense drizzle"
        61 -> "Slight rain"
        63 -> "Moderate rain"
        65 -> "Heavy rain"
        71 -> "Slight snow"
        73 -> "Moderate snow"
        75 -> "Heavy snow"
        80 -> "Light showers"
        81 -> "Moderate showers"
        82 -> "Violent showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }
}
