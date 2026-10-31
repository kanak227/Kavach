package com.kavach.app.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
    val provider: String = "GPS",
    val mapsUrl: String = "https://maps.google.com/?q=$latitude,$longitude",
    val timestamp: Long = System.currentTimeMillis()
)

object LocationHelper {

    /**
     * Checks if location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Checks if device GPS / Location services are actively switched on.
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Returns true only if location permission is granted AND device GPS is actively switched on.
     */
    fun isLocationAvailable(context: Context): Boolean {
        return hasLocationPermission(context) && isLocationEnabled(context)
    }

    /**
     * Gets battery percentage and charging state for the emergency dispatch report.
     */
    fun getBatteryStatus(context: Context): String {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            if (batteryPct >= 0) {
                "$batteryPct%${if (isCharging) " (Charging ⚡)" else ""}"
            } else {
                "Unknown"
            }
        } catch (_: Exception) {
            "Unknown"
        }
    }

    /**
     * Fetch best location available.
     * First checks best recent cached fix. If absent or older than 2 minutes,
     * attempts an active GPS/Network location fix with a 5-second timeout.
     */
    suspend fun getBestLocation(context: Context): LocationInfo? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        // 1. Check last known location from available providers
        var bestLocation: Location? = null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.accuracy < bestLocation.accuracy || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {}
        }

        // If last known location is very fresh (less than 60 seconds old), return it immediately
        val now = System.currentTimeMillis()
        if (bestLocation != null && (now - bestLocation.time) < 60_000) {
            return@withContext LocationInfo(
                latitude = bestLocation.latitude,
                longitude = bestLocation.longitude,
                accuracyMeters = bestLocation.accuracy,
                provider = bestLocation.provider ?: "Cache"
            )
        }

        // 2. Otherwise attempt a fast fresh GPS/Network fix with a 5-second timeout
        val freshLocation = withTimeoutOrNull(5000L) {
            requestFreshFix(locationManager)
        }

        val resultLoc = freshLocation ?: bestLocation
        if (resultLoc != null) {
            LocationInfo(
                latitude = resultLoc.latitude,
                longitude = resultLoc.longitude,
                accuracyMeters = resultLoc.accuracy,
                provider = resultLoc.provider ?: "Sensor"
            )
        } else {
            null
        }
    }

    private suspend fun requestFreshFix(locationManager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try {
                        locationManager.removeUpdates(this)
                    } catch (_: Exception) {}
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            continuation.invokeOnCancellation {
                try {
                    locationManager.removeUpdates(listener)
                } catch (_: Exception) {}
            }

            try {
                val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val hasNet = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                if (hasGps) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                }
                if (hasNet) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                }

                if (!hasGps && !hasNet) {
                    if (continuation.isActive) continuation.resume(null)
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
}
