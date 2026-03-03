package com.example.openblackbox

import android.content.Context
import android.net.Uri

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getStorageMode(): StorageMode = StorageMode.fromRaw(prefs.getString(KEY_STORAGE_MODE, null))

    fun setStorageMode(mode: StorageMode) {
        prefs.edit().putString(KEY_STORAGE_MODE, mode.raw).apply()
    }

    fun getLensMode(): LensMode = LensMode.fromRaw(prefs.getString(KEY_LENS_MODE, null))

    fun setLensMode(mode: LensMode) {
        prefs.edit().putString(KEY_LENS_MODE, mode.raw).apply()
    }

    fun getResolutionMode(): ResolutionMode =
        ResolutionMode.fromRaw(prefs.getString(KEY_RESOLUTION_MODE, null))

    fun setResolutionMode(mode: ResolutionMode) {
        prefs.edit().putString(KEY_RESOLUTION_MODE, mode.raw).apply()
    }

    fun getSegmentMode(): SegmentMode = SegmentMode.fromRaw(prefs.getString(KEY_SEGMENT_MODE, null))

    fun setSegmentMode(mode: SegmentMode) {
        prefs.edit().putString(KEY_SEGMENT_MODE, mode.raw).apply()
    }

    fun getSpeedUnitMode(): SpeedUnitMode = SpeedUnitMode.fromRaw(prefs.getString(KEY_SPEED_UNIT_MODE, null))

    fun setSpeedUnitMode(mode: SpeedUnitMode) {
        prefs.edit().putString(KEY_SPEED_UNIT_MODE, mode.raw).apply()
    }

    fun getStoragePressurePolicy(): StoragePressurePolicy =
        StoragePressurePolicy.fromRaw(prefs.getString(KEY_STORAGE_PRESSURE_POLICY, null))

    fun setStoragePressurePolicy(policy: StoragePressurePolicy) {
        prefs.edit().putString(KEY_STORAGE_PRESSURE_POLICY, policy.raw).apply()
    }

    fun isTimeWatermarkEnabled(): Boolean = prefs.getBoolean(KEY_WATERMARK_TIME, true)

    fun setTimeWatermarkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WATERMARK_TIME, enabled).apply()
    }

    fun isLocationWatermarkEnabled(): Boolean = prefs.getBoolean(KEY_WATERMARK_LOCATION, false)

    fun setLocationWatermarkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WATERMARK_LOCATION, enabled).apply()
    }

    fun isSpeedWatermarkEnabled(): Boolean = prefs.getBoolean(KEY_WATERMARK_SPEED, true)

    fun setSpeedWatermarkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WATERMARK_SPEED, enabled).apply()
    }

    fun isAudioRecordingEnabled(): Boolean = prefs.getBoolean(KEY_RECORD_AUDIO, true)

    fun setAudioRecordingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECORD_AUDIO, enabled).apply()
    }

    fun isRecordingFooterOverlayEnabled(): Boolean = prefs.getBoolean(KEY_RECORDING_FOOTER_OVERLAY, true)

    fun setRecordingFooterOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECORDING_FOOTER_OVERLAY, enabled).apply()
    }

    fun getExternalTreeUri(): Uri? {
        return prefs.getString(KEY_EXTERNAL_TREE_URI, null)?.let(Uri::parse)
    }

    fun setExternalTreeUri(uri: Uri?) {
        prefs.edit().putString(KEY_EXTERNAL_TREE_URI, uri?.toString()).apply()
    }

    companion object {
        private const val PREF_NAME = "blackbox_settings"
        private const val KEY_STORAGE_MODE = "storage_mode"
        private const val KEY_LENS_MODE = "lens_mode"
        private const val KEY_RESOLUTION_MODE = "resolution_mode"
        private const val KEY_SEGMENT_MODE = "segment_mode"
        private const val KEY_SPEED_UNIT_MODE = "speed_unit_mode"
        private const val KEY_STORAGE_PRESSURE_POLICY = "storage_pressure_policy"
        private const val KEY_RECORD_AUDIO = "record_audio"
        private const val KEY_WATERMARK_TIME = "watermark_time"
        private const val KEY_WATERMARK_LOCATION = "watermark_location"
        private const val KEY_WATERMARK_SPEED = "watermark_speed"
        private const val KEY_RECORDING_FOOTER_OVERLAY = "recording_footer_overlay"
        private const val KEY_EXTERNAL_TREE_URI = "external_tree_uri"
    }
}
