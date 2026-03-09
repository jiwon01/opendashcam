package com.example.openblackbox

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float? = null
)

internal object LocationSpeedEstimator {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_ESTIMATE_INTERVAL_MILLIS = 10_000L
    private const val MIN_DISTANCE_METERS = 3.0
    private const val MAX_USABLE_ACCURACY_METERS = 20f
    private const val MAX_REASONABLE_SPEED_MPS = 90.0

    fun estimateSpeedMps(previous: LocationSample?, current: LocationSample): Float? {
        previous ?: return null

        val deltaMillis = current.timestampMillis - previous.timestampMillis
        if (deltaMillis <= 0L || deltaMillis > MAX_ESTIMATE_INTERVAL_MILLIS) {
            return null
        }

        if (!isAccuracyUsable(previous.accuracyMeters) || !isAccuracyUsable(current.accuracyMeters)) {
            return null
        }

        val distanceMeters = haversineMeters(
            previous.latitude,
            previous.longitude,
            current.latitude,
            current.longitude
        )
        val accuracyFloorMeters = max(
            MIN_DISTANCE_METERS,
            max(previous.accuracyMeters ?: 0f, current.accuracyMeters ?: 0f).toDouble()
        )
        if (distanceMeters <= accuracyFloorMeters) {
            return 0f
        }

        val speedMps = distanceMeters / (deltaMillis / 1_000.0)
        return speedMps
            .takeIf { it.isFinite() && it in 0.0..MAX_REASONABLE_SPEED_MPS }
            ?.toFloat()
    }

    private fun isAccuracyUsable(accuracyMeters: Float?): Boolean {
        return accuracyMeters == null || (accuracyMeters.isFinite() && accuracyMeters <= MAX_USABLE_ACCURACY_METERS)
    }

    private fun haversineMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {
        val latitudeDelta = Math.toRadians(latitude2 - latitude1)
        val longitudeDelta = Math.toRadians(longitude2 - longitude1)
        val startLatitude = Math.toRadians(latitude1)
        val endLatitude = Math.toRadians(latitude2)

        val a = sin(latitudeDelta / 2).pow(2.0) +
            cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2).pow(2.0)
        val clamped = min(1.0, max(0.0, a))
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(clamped))
    }
}
