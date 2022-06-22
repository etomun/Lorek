package com.etomun.lorek

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.etomun.lorek.databinding.FragmentScannerBinding
import com.etomun.lorek.mlkit.CameraAnalyzer
import com.etomun.lorek.mlkit.FileAnalyzer
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

private const val ARG_MODE = "mode"
private const val ARG_SUB_TEXT = "subText"
private const val ARG_FLASH_ENABLED = "flashEnabled"
private const val ARG_GALLERY_ENABLED = "galleryEnabled"
private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.READ_EXTERNAL_STORAGE
)

class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding: FragmentScannerBinding get() = _binding!!
    private var callback: ScannerCallback? = null

    private lateinit var mode: ScanMode
    private lateinit var subText: String
    private var flashEnabled = true
    private var galleryEnabled = true

    private var lastResult: String? = null

    private lateinit var preview: Preview
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var analizer: CameraAnalyzer
    private lateinit var camProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var camControl: CameraControl
    private lateinit var camInfo: CameraInfo
    private val camExecutor by lazy { Executors.newSingleThreadExecutor() }
    private val cameraSelector by lazy {
        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
    }


    private val reqPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.entries.all { p -> p.value == true }) {
                provideCamera()
            }
        }

    private val pickPhotoFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { parsePhoto(it) }
            }
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            callback = context as ScannerCallback
        } catch (e: ClassCastException) {
            throw ClassCastException("${context.javaClass.name} must implement ScannerCallback")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mode = it.getParcelable(ARG_MODE)!!
            subText = it.getString(ARG_SUB_TEXT).orEmpty()
            flashEnabled = it.getBoolean(ARG_FLASH_ENABLED)
            flashEnabled = it.getBoolean(ARG_FLASH_ENABLED)
            galleryEnabled = it.getBoolean(ARG_GALLERY_ENABLED)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.vScanner.setMode(mode)
        binding.tvSubtext.text = subText
        binding.tvSubtext.toggleGone(subText.isNotEmpty())
        binding.cbGallery.toggleGone(galleryEnabled)
        binding.cbFlash.toggleGone(flashEnabled)
        binding.cbGallery.setOnCheckedChangeListener { _, b -> if (b) pickPhoto() }
        binding.cbFlash.setOnCheckedChangeListener { _, b -> switchFlash(b) }

        context?.let {
            camProviderFuture = ProcessCameraProvider.getInstance(it)
            provideCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.cbGallery.isChecked = false
        binding.vScanner.resumeAnim()
    }

    override fun onPause() {
        super.onPause()
        binding.vScanner.pauseAnim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.vScanner.stopAnim()
        _binding = null
        callback = null
    }

    private fun parsePhoto(uri: Uri) {
        activity?.let { ctx ->
            FileAnalyzer.Builder(mode)
                .load(ctx, uri)
                .onDetected { onScanResult(it) }
                .onError { onScanError(it) }
                .build()
                .analyze()
        }
    }

    private fun pickPhoto() {
        Intent(Intent.ACTION_PICK, null).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        }.let { pickPhotoFile.launch(it) }
    }

    private fun switchFlash(on: Boolean) {
        camControl.enableTorch(on)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { p ->
        context?.let { ContextCompat.checkSelfPermission(it, p) == PERMISSION_GRANTED } == true
    }

    private fun provideCamera() {
        if (!allPermissionsGranted()) {
            reqPermissions.launch(REQUIRED_PERMISSIONS)
            return
        }

        activity?.let { ContextCompat.getMainExecutor(it) }?.also { executor ->
            camProviderFuture.addListener({
                binding.vCamera.post {
                    val rotation = binding.vCamera.rotation.toInt()
                    preview = Preview.Builder()
                        .setTargetResolution(getResolution())
                        .setTargetRotation(rotation)
                        .build()
                    imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(getResolution())
                        .setTargetRotation(rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analizer =
                        CameraAnalyzer(binding.vScanner.frameRect, mode) { onScanResult(it) }

                    val cameraProvider = camProviderFuture.get()
                    startCamera(cameraProvider)
                }
            }, executor)
        }
    }

    private fun startCamera(cameraProvider: ProcessCameraProvider) {
        imageAnalysis.setAnalyzer(camExecutor, analizer)
        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        camControl = camera.cameraControl
        camInfo = camera.cameraInfo
        preview.setSurfaceProvider(binding.vCamera.surfaceProvider)
    }

    private fun onScanResult(result: String) {
        if (lastResult != result && _binding != null) {
            lastResult = result
            vibrate()
            binding.vCamera.post { callback?.onScanResult(result) }
            binding.vScanner.pauseAnim()
        }
    }

    private fun onScanError(message: String) {
        callback?.onFailed(message)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        context?.let {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = it.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                it.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(200)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getResolution(): Size {
        val metrics = DisplayMetrics().also { binding.vCamera.display.getRealMetrics(it) }
        val aspectRatio = aspectRatio(metrics.widthPixels / 2, metrics.heightPixels / 2)
        val width = binding.vCamera.measuredWidth
        val height = if (aspectRatio == AspectRatio.RATIO_16_9) {
            (width * RATIO_16_9_VALUE).toInt()
        } else {
            (width * RATIO_4_3_VALUE).toInt()
        }
        return Size(width, height)
    }

    companion object {
        @JvmStatic
        fun qrcode(subtext: String = "", withGallery: Boolean = true, withFlash: Boolean = true) =
            ScannerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MODE, ScanMode.QRCode())
                    putString(ARG_SUB_TEXT, subtext)
                    putBoolean(ARG_FLASH_ENABLED, withFlash)
                    putBoolean(ARG_GALLERY_ENABLED, withGallery)
                }
            }

        @JvmStatic
        fun barcode(subtext: String = "", withGallery: Boolean = true, withFlash: Boolean = true) =
            ScannerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MODE, ScanMode.BarCode())
                    putString(ARG_SUB_TEXT, subtext)
                    putBoolean(ARG_FLASH_ENABLED, withFlash)
                    putBoolean(ARG_GALLERY_ENABLED, withGallery)
                }
            }
    }
}