package com.example.openblackbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationSpeedEstimatorTest {

    @Test
    fun returnsNullWithoutPreviousSample() {
        val current = LocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            timestampMillis = 1_000L,
            accuracyMeters = 5f
        )

        assertNull(LocationSpeedEstimator.estimateSpeedMps(null, current))
    }

    @Test
    fun estimatesSpeedFromDistanceAndTime() {
        val previous = LocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            timestampMillis = 0L,
            accuracyMeters = 3f
        )
        val current = LocationSample(
            latitude = 37.56659,
            longitude = 126.9780,
            timestampMillis = 1_000L,
            accuracyMeters = 3f
        )

        val speedMps = LocationSpeedEstimator.estimateSpeedMps(previous, current)

        assertEquals(10f, speedMps ?: error("speedMps should not be null"), 1.5f)
    }

    @Test
    fun treatsMovementInsideAccuracyAsStationary() {
        val previous = LocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            timestampMillis = 0L,
            accuracyMeters = 8f
        )
        val current = LocationSample(
            latitude = 37.56654,
            longitude = 126.9780,
            timestampMillis = 1_000L,
            accuracyMeters = 8f
        )

        assertEquals(0f, LocationSpeedEstimator.estimateSpeedMps(previous, current) ?: -1f, 0.01f)
    }

    @Test
    fun ignoresStaleSamples() {
        val previous = LocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            timestampMillis = 0L,
            accuracyMeters = 3f
        )
        val current = LocationSample(
            latitude = 37.5675,
            longitude = 126.9780,
            timestampMillis = 20_000L,
            accuracyMeters = 3f
        )

        assertNull(LocationSpeedEstimator.estimateSpeedMps(previous, current))
    }
}
