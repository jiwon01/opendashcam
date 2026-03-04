package com.example.openblackbox

import androidx.camera.core.CameraSelector
import androidx.camera.video.Quality

enum class StorageMode(val raw: String, val labelRes: Int) {
    INTERNAL("internal", R.string.storage_internal),
    EXTERNAL_TREE("external_tree", R.string.storage_external);

    companion object {
        fun fromRaw(raw: String?): StorageMode {
            return entries.firstOrNull { it.raw == raw } ?: INTERNAL
        }
    }
}

enum class LensMode(val raw: String, val lensFacing: Int, val labelRes: Int) {
    BACK("back", CameraSelector.LENS_FACING_BACK, R.string.lens_back),
    FRONT("front", CameraSelector.LENS_FACING_FRONT, R.string.lens_front);

    companion object {
        fun fromRaw(raw: String?): LensMode {
            return entries.firstOrNull { it.raw == raw } ?: BACK
        }
    }
}

enum class ResolutionMode(val raw: String, val quality: Quality, val labelRes: Int) {
    UHD("uhd", Quality.UHD, R.string.quality_uhd),
    FHD("fhd", Quality.FHD, R.string.quality_fhd),
    HD("hd", Quality.HD, R.string.quality_hd),
    SD("sd", Quality.SD, R.string.quality_sd);

    companion object {
        fun fromRaw(raw: String?): ResolutionMode {
            return entries.firstOrNull { it.raw == raw } ?: FHD
        }
    }
}

enum class SegmentMode(val raw: String, val seconds: Int, val labelRes: Int) {
    MIN_1("1m", 60, R.string.segment_1m),
    MIN_3("3m", 180, R.string.segment_3m),
    MIN_5("5m", 300, R.string.segment_5m),
    MIN_10("10m", 600, R.string.segment_10m);

    companion object {
        fun fromRaw(raw: String?): SegmentMode {
            return entries.firstOrNull { it.raw == raw } ?: MIN_3
        }
    }
}

enum class StoragePressurePolicy(val raw: String, val labelRes: Int) {
    STOP_RECORDING("stop_recording", R.string.storage_pressure_stop),
    DELETE_OLDEST_AND_RETRY("delete_oldest_and_retry", R.string.storage_pressure_delete_oldest);

    companion object {
        fun fromRaw(raw: String?): StoragePressurePolicy {
            return entries.firstOrNull { it.raw == raw } ?: STOP_RECORDING
        }
    }
}

enum class SpeedUnitMode(val raw: String, val labelRes: Int) {
    KMH("kmh", R.string.speed_unit_kmh),
    MPH("mph", R.string.speed_unit_mph);

    companion object {
        fun fromRaw(raw: String?): SpeedUnitMode {
            return entries.firstOrNull { it.raw == raw } ?: KMH
        }
    }
}

enum class AppLanguageMode(val raw: String, val localeTag: String?, val labelRes: Int) {
    SYSTEM("system", null, R.string.language_system),
    KOREAN("ko", "ko", R.string.language_korean),
    ENGLISH_US("en_us", "en-US", R.string.language_english_us),
    JAPANESE("ja", "ja", R.string.language_japanese),
    FRENCH("fr", "fr", R.string.language_french),
    GERMAN("de", "de", R.string.language_german),
    SPANISH("es", "es", R.string.language_spanish);

    companion object {
        fun fromRaw(raw: String?): AppLanguageMode {
            return entries.firstOrNull { it.raw == raw } ?: SYSTEM
        }
    }
}
