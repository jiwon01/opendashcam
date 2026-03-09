package com.example.openblackbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraEffect
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import com.example.openblackbox.databinding.ActivityMainBinding
import com.example.openblackbox.databinding.DialogSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : LocalizedAppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings

    private val cameraExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private val fileTimeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private val overlayBackgroundPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(224, 0, 0, 0)
        }
    }
    private val overlayBadgeFillPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(48, 255, 255, 255)
        }
    }
    private val overlayBadgeStrokePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(170, 255, 255, 255)
        }
    }
    private val overlayTextPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
        }
    }
    private val overlayBadgeTextPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            isFakeBoldText = true
        }
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recordingOverlayEffect: OverlayEffect? = null
    private var currentRecording: Recording? = null
    private var currentOutputDescriptor: ParcelFileDescriptor? = null
    private var currentOutputName: String = ""

    private var shouldContinueSegments: Boolean = false
    private var pendingAutoRestart: Boolean = false
    private var segmentCounter: Int = 0

    private var segmentJob: Job? = null
    private var watermarkJob: Job? = null
    private var recBlinkJob: Job? = null
    private var recBlinkOn: Boolean = false
    private var settingsDialog: AlertDialog? = null
    private var settingsDialogBinding: DialogSettingsBinding? = null
    private var activeLocationListener: LocationListener? = null
    private var lastSpeedSample: LocationSample? = null

    @Volatile
    private var overlayFooterText: String = ""

    @Volatile
    private var overlayGpsFix: Boolean = false

    @Volatile
    private var overlayRecBlinkOn: Boolean = false

    @Volatile
    private var overlayIsRecording: Boolean = false

    @Volatile
    private var overlayRecordingFooterEnabled: Boolean = true

    @Volatile
    private var latestLocationSnapshot: LocationSnapshot? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasCameraPermission()) {
            startCamera()
        } else {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
        }

        if (settings.isAudioRecordingEnabled() && !hasAudioPermission()) {
            Toast.makeText(this, getString(R.string.permission_audio_needed), Toast.LENGTH_SHORT).show()
        }
        if (settings.isLocationWatermarkEnabled() && !hasLocationPermission()) {
            Toast.makeText(this, getString(R.string.permission_location_needed), Toast.LENGTH_SHORT).show()
        }
        syncLocationUpdates()
        renderWatermark()
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        saveExternalTreeUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = AppSettings(this)
        applyKeepScreenOnSetting(settings.isKeepScreenOnEnabled())
        overlayRecordingFooterEnabled = settings.isRecordingFooterOverlayEnabled()
        setupUi()
        setupOverlayInsets()
        updateRecordUi(isRecording = false)
        renderWatermark()
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCorePermissions()
        }
        syncLocationUpdates()
        startWatermarkLoop()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        stopWatermarkLoop()
    }

    override fun onStop() {
        super.onStop()
        if (currentRecording != null) {
            stopContinuousRecording(showToast = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        segmentJob?.cancel()
        stopLocationUpdates()
        stopWatermarkLoop()
        stopRecBlink()
        settingsDialog?.dismiss()
        recordingOverlayEffect?.close()
        recordingOverlayEffect = null
        closeOutputDescriptor()
        cameraProvider?.unbindAll()
    }

    private fun setupUi() {
        binding.buttonSettings.setOnClickListener { showSettingsDialog() }
        binding.buttonRecord.setOnClickListener {
            if (currentRecording == null) {
                startContinuousRecording()
            } else {
                stopContinuousRecording(showToast = true)
            }
        }
        binding.buttonRecordings.setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
    }

    private fun setupOverlayInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val baseTop = dpToPx(24)

            binding.leftOverlayPanel.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                marginStart = 0
                marginEnd = insets.right
                topMargin = baseTop + insets.top
            }
            binding.footerBar.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                marginStart = 0
                marginEnd = 0
                bottomMargin = 0
            }
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.rootContainer)
    }

    private fun showSettingsDialog() {
        if (settingsDialog?.isShowing == true) return

        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        settingsDialogBinding = dialogBinding
        bindSettingsDialog(dialogBinding)

        settingsDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_close, null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener {
                    settingsDialogBinding = null
                    settingsDialog = null
                }
                dialog.show()
                val width = (resources.displayMetrics.widthPixels * 0.72f).toInt()
                dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
    }

    private fun bindSettingsDialog(dialogBinding: DialogSettingsBinding) {
        val canChangeCaptureConfig = currentRecording == null
        val languageLabels = AppLanguageMode.entries.map { getString(it.labelRes) }
        val storageLabels = StorageMode.entries.map { getString(it.labelRes) }
        val storagePressureLabels = StoragePressurePolicy.entries.map { getString(it.labelRes) }
        val lensLabels = LensMode.entries.map { getString(it.labelRes) }
        val resolutionLabels = ResolutionMode.entries.map { getString(it.labelRes) }
        val segmentLabels = SegmentMode.entries.map { getString(it.labelRes) }
        val speedUnitLabels = SpeedUnitMode.entries.map { getString(it.labelRes) }

        dialogBinding.spinnerLanguage.adapter = spinnerAdapter(languageLabels)
        dialogBinding.spinnerStorage.adapter = spinnerAdapter(storageLabels)
        dialogBinding.spinnerStoragePressure.adapter = spinnerAdapter(storagePressureLabels)
        dialogBinding.spinnerLens.adapter = spinnerAdapter(lensLabels)
        dialogBinding.spinnerResolution.adapter = spinnerAdapter(resolutionLabels)
        dialogBinding.spinnerSegment.adapter = spinnerAdapter(segmentLabels)
        dialogBinding.spinnerSpeedUnit.adapter = spinnerAdapter(speedUnitLabels)

        dialogBinding.spinnerLanguage.setSelection(AppLanguageMode.entries.indexOf(settings.getAppLanguageMode()))
        dialogBinding.spinnerStorage.setSelection(StorageMode.entries.indexOf(settings.getStorageMode()))
        dialogBinding.spinnerStoragePressure.setSelection(
            StoragePressurePolicy.entries.indexOf(settings.getStoragePressurePolicy())
        )
        dialogBinding.spinnerLens.setSelection(LensMode.entries.indexOf(settings.getLensMode()))
        dialogBinding.spinnerResolution.setSelection(ResolutionMode.entries.indexOf(settings.getResolutionMode()))
        dialogBinding.spinnerSegment.setSelection(SegmentMode.entries.indexOf(settings.getSegmentMode()))
        dialogBinding.spinnerSpeedUnit.setSelection(SpeedUnitMode.entries.indexOf(settings.getSpeedUnitMode()))

        dialogBinding.spinnerStorage.isEnabled = canChangeCaptureConfig
        dialogBinding.spinnerLens.isEnabled = canChangeCaptureConfig
        dialogBinding.spinnerResolution.isEnabled = canChangeCaptureConfig

        dialogBinding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = AppLanguageMode.entries[position]
                if (selected != settings.getAppLanguageMode()) {
                    settings.setAppLanguageMode(selected)
                    settingsDialog?.dismiss()
                    recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dialogBinding.spinnerStorage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = StorageMode.entries[position]
                if (selected != settings.getStorageMode()) {
                    settings.setStorageMode(selected)
                    refreshSettingsDialogStorageSummary()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dialogBinding.spinnerStoragePressure.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = StoragePressurePolicy.entries[position]
                if (selected != settings.getStoragePressurePolicy()) {
                    settings.setStoragePressurePolicy(selected)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dialogBinding.spinnerLens.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = LensMode.entries[position]
                if (selected != settings.getLensMode()) {
                    settings.setLensMode(selected)
                    if (currentRecording == null && hasCameraPermission()) {
                        bindCameraUseCases()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dialogBinding.spinnerResolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = ResolutionMode.entries[position]
                if (selected != settings.getResolutionMode()) {
                    settings.setResolutionMode(selected)
                    if (currentRecording == null && hasCameraPermission()) {
                        bindCameraUseCases()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dialogBinding.spinnerSegment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = SegmentMode.entries[position]
                if (selected != settings.getSegmentMode()) {
                    settings.setSegmentMode(selected)
                    if (currentRecording != null) {
                        scheduleSegmentRotation()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dialogBinding.spinnerSpeedUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = SpeedUnitMode.entries[position]
                if (selected != settings.getSpeedUnitMode()) {
                    settings.setSpeedUnitMode(selected)
                    renderWatermark()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dialogBinding.switchWatermarkTime.isChecked = settings.isTimeWatermarkEnabled()
        dialogBinding.switchWatermarkLocation.isChecked = settings.isLocationWatermarkEnabled()
        dialogBinding.switchWatermarkSpeed.isChecked = settings.isSpeedWatermarkEnabled()
        dialogBinding.switchRecordingFooterOverlay.isChecked = settings.isRecordingFooterOverlayEnabled()
        dialogBinding.switchRecordAudio.isChecked = settings.isAudioRecordingEnabled()
        dialogBinding.switchKeepScreenOn.isChecked = settings.isKeepScreenOnEnabled()

        dialogBinding.switchWatermarkTime.setOnCheckedChangeListener { _, checked ->
            settings.setTimeWatermarkEnabled(checked)
            renderWatermark()
        }
        dialogBinding.switchWatermarkLocation.setOnCheckedChangeListener { _, checked ->
            settings.setLocationWatermarkEnabled(checked)
            dialogBinding.switchWatermarkSpeed.isEnabled = checked
            dialogBinding.spinnerSpeedUnit.isEnabled = checked && dialogBinding.switchWatermarkSpeed.isChecked
            if (checked && !hasLocationPermission()) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            }
            syncLocationUpdates()
            renderWatermark()
        }
        dialogBinding.switchWatermarkSpeed.setOnCheckedChangeListener { _, checked ->
            settings.setSpeedWatermarkEnabled(checked)
            dialogBinding.spinnerSpeedUnit.isEnabled =
                dialogBinding.switchWatermarkLocation.isChecked && checked
            renderWatermark()
        }
        dialogBinding.switchRecordingFooterOverlay.setOnCheckedChangeListener { _, checked ->
            settings.setRecordingFooterOverlayEnabled(checked)
            overlayRecordingFooterEnabled = checked
        }
        dialogBinding.switchRecordAudio.setOnCheckedChangeListener { _, checked ->
            settings.setAudioRecordingEnabled(checked)
            if (checked && !hasAudioPermission()) {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }
        dialogBinding.switchKeepScreenOn.setOnCheckedChangeListener { _, checked ->
            settings.setKeepScreenOnEnabled(checked)
            applyKeepScreenOnSetting(checked)
        }

        dialogBinding.buttonSelectFolder.isEnabled = canChangeCaptureConfig
        dialogBinding.switchWatermarkSpeed.isEnabled = dialogBinding.switchWatermarkLocation.isChecked
        dialogBinding.spinnerSpeedUnit.isEnabled =
            dialogBinding.switchWatermarkLocation.isChecked && dialogBinding.switchWatermarkSpeed.isChecked
        dialogBinding.buttonSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(settings.getExternalTreeUri())
        }
        refreshSettingsDialogStorageSummary()
    }

    private fun applyKeepScreenOnSetting(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun spinnerAdapter(values: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            values
        )
    }

    private fun refreshSettingsDialogStorageSummary() {
        val dialogBinding = settingsDialogBinding ?: return
        val mode = settings.getStorageMode()
        if (mode == StorageMode.INTERNAL) {
            dialogBinding.buttonSelectFolder.visibility = View.GONE
            val path = StorageRepository.getInternalRecordingDir(this).absolutePath
            dialogBinding.textStoragePath.text = getString(R.string.storage_folder_selected, path)
            return
        }

        dialogBinding.buttonSelectFolder.visibility = View.VISIBLE
        val selectedUri = settings.getExternalTreeUri()
        dialogBinding.textStoragePath.text = if (selectedUri == null) {
            getString(R.string.storage_folder_not_selected)
        } else {
            getString(R.string.storage_folder_selected, selectedUri.toString())
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasCameraPermission(): Boolean = hasPermission(Manifest.permission.CAMERA)

    private fun hasAudioPermission(): Boolean = hasPermission(Manifest.permission.RECORD_AUDIO)

    private fun requestCorePermissions() {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun shouldTrackLocation(): Boolean {
        return settings.isLocationWatermarkEnabled() && hasLocationPermission()
    }

    private fun syncLocationUpdates() {
        if (shouldTrackLocation()) {
            startLocationUpdates()
        } else {
            stopLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (activeLocationListener != null) return
        val locationManager = getSystemService(LocationManager::class.java) ?: return
        val enabledProviders = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        if (enabledProviders.isEmpty()) return

        lastSpeedSample = null
        latestLocationSnapshot = getBestLastKnownLocationSnapshot(locationManager)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                updateLatestLocationSnapshot(location)
            }
        }

        enabledProviders.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    LOCATION_UPDATE_INTERVAL_MS,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }
        activeLocationListener = listener
    }

    private fun stopLocationUpdates() {
        val listener = activeLocationListener ?: return
        val locationManager = getSystemService(LocationManager::class.java)
        if (locationManager != null) {
            runCatching { locationManager.removeUpdates(listener) }
        }
        activeLocationListener = null
        lastSpeedSample = null
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                cameraProvider = future.get()
                bindCameraUseCases()
            },
            cameraExecutor
        )
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = binding.previewView.surfaceProvider
        }
        val selectedResolution = settings.getResolutionMode()
        val selectedLens = settings.getLensMode()
        val qualitySelector = QualitySelector.from(
            selectedResolution.quality,
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        val videoCaptureUseCase = VideoCapture.withOutput(recorder)
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(selectedLens.lensFacing)
            .build()

        try {
            bindCameraUseCaseGroup(provider, cameraSelector, preview, videoCaptureUseCase)
            videoCapture = videoCaptureUseCase
        } catch (exception: Exception) {
            Toast.makeText(this, getString(R.string.resolution_apply_failed), Toast.LENGTH_SHORT).show()
            runCatching {
                val fallbackRecorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
                val fallbackVideoCapture = VideoCapture.withOutput(fallbackRecorder)
                bindCameraUseCaseGroup(
                    provider,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    fallbackVideoCapture
                )
                videoCapture = fallbackVideoCapture
            }
        }
    }

    private fun bindCameraUseCaseGroup(
        provider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
        preview: Preview,
        capture: VideoCapture<Recorder>
    ) {
        provider.unbindAll()
        recordingOverlayEffect?.close()
        val overlayEffect = createVideoOverlayEffect()
        recordingOverlayEffect = overlayEffect
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .addEffect(overlayEffect)
            .build()
        provider.bindToLifecycle(this, cameraSelector, group)
    }

    private fun startContinuousRecording() {
        if (!hasCameraPermission()) {
            requestCorePermissions()
            return
        }
        if (settings.isAudioRecordingEnabled() && !hasAudioPermission()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        if (settings.getStorageMode() == StorageMode.EXTERNAL_TREE && settings.getExternalTreeUri() == null) {
            Toast.makeText(this, getString(R.string.toast_select_external_folder_first), Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (videoCapture == null) {
            startCamera()
        }

        shouldContinueSegments = true
        pendingAutoRestart = false
        segmentCounter = 0
        startSegmentRecording()
        Toast.makeText(this, getString(R.string.toast_recording_started), Toast.LENGTH_SHORT).show()
    }

    private fun stopContinuousRecording(showToast: Boolean) {
        shouldContinueSegments = false
        pendingAutoRestart = false
        segmentJob?.cancel()
        if (showToast) {
            Toast.makeText(this, getString(R.string.toast_recording_stopped), Toast.LENGTH_SHORT).show()
        }
        currentRecording?.stop()
        if (currentRecording == null) {
            updateRecordUi(isRecording = false)
        }
    }

    private fun startSegmentRecording(allowStorageRecovery: Boolean = true) {
        val capture = videoCapture ?: run {
            updateRecordUi(isRecording = false)
            return
        }
        val output = createOutput() ?: run {
            val recovered = if (allowStorageRecovery) {
                tryRecoverStorageAndRetry()
            } else {
                false
            }
            if (recovered) {
                startSegmentRecording(allowStorageRecovery = false)
                return
            }
            shouldContinueSegments = false
            updateRecordUi(isRecording = false)
            Toast.makeText(
                this,
                getString(R.string.toast_recording_failed, getString(R.string.record_error_prepare_output)),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        currentOutputName = output.displayName
        closeOutputDescriptor()
        currentOutputDescriptor = output.parcelFileDescriptor

        val pendingRecordingBase = when {
            output.fileOutputOptions != null -> {
                capture.output.prepareRecording(this, output.fileOutputOptions)
            }

            output.fileDescriptorOutputOptions != null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    capture.output.prepareRecording(this, output.fileDescriptorOutputOptions)
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_recording_failed, getString(R.string.record_error_prepare_output)),
                        Toast.LENGTH_SHORT
                    ).show()
                    updateRecordUi(isRecording = false)
                    return
                }
            }

            else -> {
                updateRecordUi(isRecording = false)
                return
            }
        }
        var pendingRecording: PendingRecording = pendingRecordingBase
        if (settings.isAudioRecordingEnabled()) {
            val audioGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            if (audioGranted) {
                try {
                    pendingRecording = pendingRecording.withAudioEnabled()
                } catch (_: SecurityException) {
                    Toast.makeText(this, getString(R.string.permission_audio_needed), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        currentRecording = pendingRecording.start(cameraExecutor) { event ->
            handleRecordEvent(event)
        }
    }

    private fun handleRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                updateRecordUi(isRecording = true)
                scheduleSegmentRotation()
            }

            is VideoRecordEvent.Finalize -> {
                segmentJob?.cancel()
                closeOutputDescriptor()
                currentRecording = null
                val success = event.error == VideoRecordEvent.Finalize.ERROR_NONE
                val shouldRestart = success && shouldContinueSegments && pendingAutoRestart
                val insufficientStorage = isInsufficientStorageError(event)
                pendingAutoRestart = false
                var restartedAfterCleanup = false

                if (success && !shouldRestart) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_recording_saved, currentOutputName),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (!success) {
                    if (insufficientStorage && shouldContinueSegments && tryRecoverStorageAndRetry()) {
                        restartedAfterCleanup = true
                        startSegmentRecording(allowStorageRecovery = false)
                    } else {
                        if (insufficientStorage) {
                            shouldContinueSegments = false
                            Toast.makeText(
                                this,
                                getString(R.string.toast_storage_full_stop),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                getString(
                                    R.string.toast_recording_failed,
                                    event.cause?.message ?: event.error.toString()
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                if (shouldRestart) {
                    startSegmentRecording()
                } else if (restartedAfterCleanup) {
                    Unit
                } else {
                    updateRecordUi(isRecording = false)
                }
            }
        }
    }

    private fun scheduleSegmentRotation() {
        segmentJob?.cancel()
        val durationMillis = settings.getSegmentMode().seconds * 1000L
        segmentJob = lifecycleScope.launch {
            delay(durationMillis)
            if (isActive && shouldContinueSegments && currentRecording != null) {
                pendingAutoRestart = true
                currentRecording?.stop()
            }
        }
    }

    private fun createOutput(): RecordingOutput? {
        val baseName = buildNextOutputName()
        return when (settings.getStorageMode()) {
            StorageMode.INTERNAL -> {
                val file = StorageRepository.createInternalOutputFile(this, "$baseName.mp4")
                RecordingOutput(
                    fileOutputOptions = FileOutputOptions.Builder(file).build(),
                    displayName = file.name,
                    parcelFileDescriptor = null
                )
            }

            StorageMode.EXTERNAL_TREE -> {
                val treeUri = settings.getExternalTreeUri() ?: return null
                val tree = DocumentFile.fromTreeUri(this, treeUri) ?: return null
                val document = tree.createFile("video/mp4", "$baseName.mp4") ?: return null
                val descriptor = contentResolver.openFileDescriptor(document.uri, "w") ?: return null
                RecordingOutput(
                    fileDescriptorOutputOptions = FileDescriptorOutputOptions.Builder(descriptor).build(),
                    displayName = document.name ?: "$baseName.mp4",
                    parcelFileDescriptor = descriptor
                )
            }
        }
    }

    private fun tryRecoverStorageAndRetry(): Boolean {
        if (settings.getStoragePressurePolicy() != StoragePressurePolicy.DELETE_OLDEST_AND_RETRY) {
            return false
        }
        val deletedName = StorageRepository.deleteOldestRecording(
            context = this,
            storageMode = settings.getStorageMode(),
            externalTreeUri = settings.getExternalTreeUri()
        ) ?: return false
        Toast.makeText(
            this,
            getString(R.string.toast_storage_auto_deleted, deletedName),
            Toast.LENGTH_SHORT
        ).show()
        return true
    }

    private fun isInsufficientStorageError(event: VideoRecordEvent.Finalize): Boolean {
        if (event.error == VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE) {
            return true
        }
        val message = event.cause?.message ?: return false
        return message.contains("ENOSPC", ignoreCase = true) ||
            message.contains("No space", ignoreCase = true) ||
            message.contains("space left", ignoreCase = true)
    }

    private fun buildNextOutputName(): String {
        val stamp = fileTimeFormat.format(Date())
        segmentCounter += 1
        return "blackbox_${stamp}_${segmentCounter.toString().padStart(3, '0')}"
    }

    private fun updateRecordUi(isRecording: Boolean) {
        overlayIsRecording = isRecording
        binding.buttonRecord.text = if (isRecording) {
            getString(R.string.btn_stop_recording)
        } else {
            getString(R.string.btn_start_recording)
        }
        val recordButtonColor = if (isRecording) {
            Color.parseColor("#D32F2F")
        } else {
            Color.parseColor("#1E88E5")
        }
        binding.buttonRecord.backgroundTintList = ColorStateList.valueOf(recordButtonColor)
        binding.textRecordingStatus.text = if (isRecording) {
            getString(R.string.label_recording_status_active)
        } else {
            getString(R.string.label_recording_status_idle)
        }
        binding.buttonRecordings.isEnabled = !isRecording
        if (isRecording) {
            startRecBlink()
        } else {
            stopRecBlink()
        }
    }

    private fun saveExternalTreeUri(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
            settings.setExternalTreeUri(uri)
            refreshSettingsDialogStorageSummary()
        } catch (exception: SecurityException) {
            Toast.makeText(
                this,
                exception.message ?: getString(R.string.toast_permission_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startWatermarkLoop() {
        if (watermarkJob != null) return
        watermarkJob = lifecycleScope.launch {
            while (isActive) {
                renderWatermark()
                delay(1000)
            }
        }
    }

    private fun stopWatermarkLoop() {
        watermarkJob?.cancel()
        watermarkJob = null
    }

    private fun renderWatermark() {
        val showTime = settings.isTimeWatermarkEnabled()
        val showLocation = settings.isLocationWatermarkEnabled()
        val showSpeed = showLocation && settings.isSpeedWatermarkEnabled()
        val now = if (showTime) formatTimeWithZone(Date()) else ""
        val locationSnapshot = getLastLocationSnapshot()
        val location = if (showLocation) locationSnapshot.text else ""
        val speed = if (showSpeed) formatSpeed(locationSnapshot.speedMps) else ""
        updateGpsIndicator(locationSnapshot.hasFix)

        val text = when {
            showTime && showLocation && showSpeed -> {
                getString(R.string.watermark_format_with_speed, now, location, speed)
            }
            showTime && showLocation -> getString(R.string.watermark_format, now, location)
            showTime -> getString(R.string.watermark_time_only, now)
            showLocation && showSpeed -> getString(R.string.watermark_location_with_speed, location, speed)
            showLocation -> getString(R.string.watermark_location_only, location)
            else -> ""
        }
        overlayFooterText = text
        binding.textWatermark.text = text
        adjustPreviewFooterTextSize(text)
        binding.footerBar.visibility = View.VISIBLE
    }

    private fun adjustPreviewFooterTextSize(text: String) {
        val textView = binding.textWatermark
        val availableWidth = textView.width - textView.paddingStart - textView.paddingEnd
        if (availableWidth <= 0) {
            textView.post {
                if (text == textView.text.toString()) {
                    adjustPreviewFooterTextSize(text)
                }
            }
            return
        }

        val maxTextSizePx = spToPx(17f)
        val minTextSizePx = spToPx(10f)
        if (text.isBlank()) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, maxTextSizePx)
            return
        }

        val measurePaint = Paint(textView.paint).apply { textSize = maxTextSizePx }
        val measuredWidth = measurePaint.measureText(text)
        val fittedSizePx = if (measuredWidth <= availableWidth) {
            maxTextSizePx
        } else {
            (maxTextSizePx * (availableWidth.toFloat() / measuredWidth) * 0.98f).coerceAtLeast(minTextSizePx)
        }
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fittedSizePx)
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocationSnapshot(): LocationSnapshot {
        if (!hasLocationPermission()) {
            return LocationSnapshot(getString(R.string.watermark_no_location), false, null)
        }

        latestLocationSnapshot?.let { return it }

        val locationManager = getSystemService(LocationManager::class.java)
            ?: return LocationSnapshot(getString(R.string.watermark_no_location), false, null)

        return getBestLastKnownLocationSnapshot(locationManager)
            ?: LocationSnapshot(getString(R.string.watermark_no_location), false, null)
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocationSnapshot(locationManager: LocationManager): LocationSnapshot? {
        val providers = locationManager.getProviders(true)
        val latestLocation = providers.asSequence()
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        return latestLocation?.let(::createLastKnownLocationSnapshot)
    }

    private fun createLastKnownLocationSnapshot(location: Location): LocationSnapshot {
        val isRecent = location.time > 0L &&
            (System.currentTimeMillis() - location.time) in 0..LAST_KNOWN_SPEED_FRESHNESS_MS
        return LocationSnapshot(
            text = String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude),
            hasFix = true,
            speedMps = if (isRecent && location.hasSpeed()) location.speed.coerceAtLeast(0f) else null
        )
    }

    private fun updateLatestLocationSnapshot(location: Location) {
        val estimatedSpeedMps = estimateSpeedFromLocation(location)
        val directSpeedMps = location.speed.takeIf { location.hasSpeed() }?.coerceAtLeast(0f)
        latestLocationSnapshot = LocationSnapshot(
            text = String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude),
            hasFix = true,
            speedMps = when {
                directSpeedMps == null -> estimatedSpeedMps
                directSpeedMps > 0f -> directSpeedMps
                estimatedSpeedMps != null -> estimatedSpeedMps
                else -> directSpeedMps
            }
        )
    }

    private fun estimateSpeedFromLocation(location: Location): Float? {
        val sample = location.toLocationSample() ?: return null
        val estimatedSpeedMps = LocationSpeedEstimator.estimateSpeedMps(lastSpeedSample, sample)
        lastSpeedSample = sample
        return estimatedSpeedMps
    }

    private fun Location.toLocationSample(): LocationSample? {
        val timestampMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
            elapsedRealtimeNanos > 0L
        ) {
            elapsedRealtimeNanos / 1_000_000L
        } else {
            time.takeIf { it > 0L }
        } ?: return null

        return LocationSample(
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis,
            accuracyMeters = accuracy.takeIf { hasAccuracy() }
        )
    }

    private fun formatSpeed(speedMps: Float?): String {
        val speedUnit = settings.getSpeedUnitMode()
        val unitLabel = if (speedUnit == SpeedUnitMode.KMH) {
            getString(R.string.speed_unit_kmh)
        } else {
            getString(R.string.speed_unit_mph)
        }
        if (speedMps == null) {
            return "-- $unitLabel"
        }
        val converted = if (speedUnit == SpeedUnitMode.KMH) {
            speedMps * 3.6f
        } else {
            speedMps * 2.2369363f
        }
        val locale = resources.configuration.locales[0]
        return String.format(locale, "%.0f %s", converted.coerceAtLeast(0f), unitLabel)
    }

    private fun updateGpsIndicator(hasFix: Boolean) {
        overlayGpsFix = hasFix
        val color = if (hasFix) {
            Color.parseColor("#4CAF50")
        } else {
            ContextCompat.getColor(this, android.R.color.white)
        }
        binding.textGpsIndicator.setTextColor(color)
    }

    private fun startRecBlink() {
        if (recBlinkJob != null) return
        recBlinkJob = lifecycleScope.launch {
            while (isActive) {
                recBlinkOn = !recBlinkOn
                overlayRecBlinkOn = recBlinkOn
                val color = if (recBlinkOn) {
                    Color.parseColor("#FF3B30")
                } else {
                    ContextCompat.getColor(this@MainActivity, android.R.color.white)
                }
                binding.textRecIndicator.setTextColor(color)
                delay(500)
            }
        }
    }

    private fun stopRecBlink() {
        recBlinkJob?.cancel()
        recBlinkJob = null
        recBlinkOn = false
        overlayRecBlinkOn = false
        binding.textRecIndicator.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun formatTimeWithZone(now: Date): String {
        val locale = resources.configuration.locales[0]
        val timeText = SimpleDateFormat("yyyy.MM.dd  hh:mm:ssa", locale).format(now)
        val timeZone = TimeZone.getDefault()
        val zoneText = if (timeZone.id == "Asia/Seoul") {
            "KST"
        } else {
            timeZone.getDisplayName(false, TimeZone.SHORT, locale).uppercase(locale)
        }
        val safeZone = if (zoneText.isBlank()) {
            timeZone.id
        } else {
            zoneText
        }
        return "$timeText $safeZone"
    }

    private fun createVideoOverlayEffect(): OverlayEffect {
        val errorListener = Consumer<Throwable> { throwable ->
            runOnUiThread {
                Toast.makeText(
                    this,
                    throwable.message ?: getString(R.string.toast_overlay_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        return OverlayEffect(
            CameraEffect.VIDEO_CAPTURE,
            0,
            Handler(Looper.getMainLooper()),
            errorListener
        ).apply {
            setOnDrawListener { frame ->
                drawRecordingFooter(frame)
            }
        }
    }

    private fun drawRecordingFooter(frame: Frame): Boolean {
        val canvas = frame.overlayCanvas
        val frameWidth = frame.size.width.toFloat()
        val frameHeight = frame.size.height.toFloat()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (!overlayRecordingFooterEnabled) {
            return true
        }
        val uiScale = (frameHeight / 1080f).coerceIn(0.65f, 1.35f)
        updateOverlayPaintScale(uiScale)

        val barHeight = (84f * uiScale).coerceIn(frameHeight * 0.075f, frameHeight * 0.14f)
        val barTop = frameHeight - barHeight

        canvas.drawRect(0f, barTop, frameWidth, frameHeight, overlayBackgroundPaint)

        var badgeRight = frameWidth - (20f * uiScale)
        badgeRight = drawFooterBadge(
            canvas = canvas,
            label = getString(R.string.label_footer_rec),
            textColor = if (overlayIsRecording && overlayRecBlinkOn) {
                Color.parseColor("#FF3B30")
            } else {
                Color.WHITE
            },
            right = badgeRight,
            barTop = barTop,
            barHeight = barHeight
        )
        badgeRight = drawFooterBadge(
            canvas = canvas,
            label = getString(R.string.label_footer_gps),
            textColor = if (overlayGpsFix) {
                Color.parseColor("#4CAF50")
            } else {
                Color.WHITE
            },
            right = badgeRight,
            barTop = barTop,
            barHeight = barHeight
        )

        val leftPadding = 20f * uiScale
        val textRight = (badgeRight - (10f * uiScale)).coerceAtLeast(leftPadding + (60f * uiScale))
        val text = overlayFooterText
        fitOverlayTextSize(text, (textRight - leftPadding).coerceAtLeast(1f), uiScale)
        val baseline = barTop + (barHeight / 2f) - ((overlayTextPaint.descent() + overlayTextPaint.ascent()) / 2f)
        canvas.save()
        canvas.clipRect(leftPadding, barTop, textRight, frameHeight)
        canvas.drawText(text, leftPadding, baseline, overlayTextPaint)
        canvas.restore()
        return true
    }

    private fun drawFooterBadge(
        canvas: Canvas,
        label: String,
        textColor: Int,
        right: Float,
        barTop: Float,
        barHeight: Float
    ): Float {
        val uiScale = (barHeight / 84f).coerceIn(0.65f, 1.35f)
        val textWidth = overlayBadgeTextPaint.measureText(label)
        val horizontalPadding = 10f * uiScale
        val badgeWidth = textWidth + (horizontalPadding * 2f)
        val badgeHeight = 34f * uiScale
        val badgeTop = barTop + ((barHeight - badgeHeight) / 2f)
        val badgeRect = RectF(
            right - badgeWidth,
            badgeTop,
            right,
            badgeTop + badgeHeight
        )
        val radius = badgeHeight / 2f
        canvas.drawRoundRect(badgeRect, radius, radius, overlayBadgeFillPaint)
        canvas.drawRoundRect(badgeRect, radius, radius, overlayBadgeStrokePaint)
        overlayBadgeTextPaint.color = textColor
        val textBaseline =
            badgeRect.centerY() - ((overlayBadgeTextPaint.descent() + overlayBadgeTextPaint.ascent()) / 2f)
        canvas.drawText(label, badgeRect.left + horizontalPadding, textBaseline, overlayBadgeTextPaint)
        return badgeRect.left - (8f * uiScale)
    }

    private fun updateOverlayPaintScale(uiScale: Float) {
        overlayTextPaint.textSize = 38f * uiScale
        overlayBadgeTextPaint.textSize = 24f * uiScale
        overlayBadgeStrokePaint.strokeWidth = 1.5f * uiScale
    }

    private fun fitOverlayTextSize(text: String, availableWidth: Float, uiScale: Float) {
        val maxSize = 38f * uiScale
        val minSize = 16f * uiScale
        overlayTextPaint.textSize = maxSize
        if (text.isBlank() || availableWidth <= 0f) return

        val measuredWidth = overlayTextPaint.measureText(text)
        if (measuredWidth <= availableWidth) return

        var targetSize = (maxSize * (availableWidth / measuredWidth) * 0.98f).coerceAtLeast(minSize)
        overlayTextPaint.textSize = targetSize
        while (targetSize > minSize && overlayTextPaint.measureText(text) > availableWidth) {
            targetSize = (targetSize - (0.5f * uiScale)).coerceAtLeast(minSize)
            overlayTextPaint.textSize = targetSize
        }
    }

    private fun closeOutputDescriptor() {
        runCatching {
            currentOutputDescriptor?.close()
        }
        currentOutputDescriptor = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    }

    private fun dpToPxF(dp: Int): Float {
        return dp * resources.displayMetrics.density
    }

    private data class RecordingOutput(
        val fileOutputOptions: FileOutputOptions? = null,
        val fileDescriptorOutputOptions: FileDescriptorOutputOptions? = null,
        val displayName: String,
        val parcelFileDescriptor: ParcelFileDescriptor?
    )

    private data class LocationSnapshot(
        val text: String,
        val hasFix: Boolean,
        val speedMps: Float?
    )

    private companion object {
        const val LOCATION_UPDATE_INTERVAL_MS = 1_000L
        const val LAST_KNOWN_SPEED_FRESHNESS_MS = 5_000L
    }
}
