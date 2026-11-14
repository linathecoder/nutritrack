package com.nutritrack.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.nutritrack.android.databinding.ActivityScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraExecutor: ExecutorService

    private var camera: Camera? = null
    private var torchOn = false

    
    private val handled = AtomicBoolean(false)

    private val scanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)
                .build()
        )
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showPermissionNeeded()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topArea.setPadding(0, bars.top, 0, 0)
            binding.bottomArea.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnManual.setOnClickListener { showManualEntryDialog() }
        binding.btnFlash.setOnClickListener { toggleTorch() }
        binding.btnGrantPermission.setOnClickListener { onGrantPermissionClicked() }

        ensureCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        handled.set(false)
        if (camera == null && hasCameraPermission()) startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.close()
        cameraExecutor.shutdown()
    }


    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun ensureCameraPermission() {
        if (hasCameraPermission()) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showPermissionNeeded() {
        binding.permissionPanel.visibility = View.VISIBLE
        binding.btnFlash.visibility = View.GONE
        binding.tvFlashLabel.visibility = View.GONE
    }

    private fun onGrantPermissionClicked() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
    }

    private fun startCamera() {
        binding.permissionPanel.visibility = View.GONE
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::analyze) }

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)

                val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                binding.btnFlash.visibility = if (hasFlash) View.VISIBLE else View.GONE
                binding.tvFlashLabel.visibility = if (hasFlash) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                binding.tvPermissionMessage.text = "Couldn't start the camera. You can still enter the code manually."
                binding.btnGrantPermission.visibility = View.GONE
                binding.permissionPanel.visibility = View.VISIBLE
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val mediaImage = proxy.image
        if (mediaImage == null || handled.get()) {
            proxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                if (value != null) onBarcodeDetected(value)
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun onBarcodeDetected(value: String) {
        if (!handled.compareAndSet(false, true)) return
        binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        openProduct(value)
    }

    private fun openProduct(barcode: String) {
        startActivity(ProductActivity.intent(this, barcode))
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        binding.btnFlash.setBackgroundResource(
            if (torchOn) R.drawable.bg_scan_control_on else R.drawable.bg_scan_control
        )
        val tint = ContextCompat.getColor(this, if (torchOn) R.color.text_slate_900 else R.color.white)
        ImageViewCompat.setImageTintList(binding.ivFlash, ColorStateList.valueOf(tint))
    }


    private fun showManualEntryDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_manual_barcode, null)
        val til = content.findViewById<TextInputLayout>(R.id.tilBarcode)
        val input = content.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBarcode)
        input.inputType = InputType.TYPE_CLASS_NUMBER

        val dialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Enter barcode")
            .setView(content)
            .setPositiveButton("Look up", null)
            .setNegativeButton("Cancel", null)
            .create()

       
        handled.set(true)
        dialog.setOnDismissListener { handled.set(false) }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = input.text?.toString()?.trim().orEmpty()
                if (code.length in 8..14 && code.all { it.isDigit() }) {
                    til.error = null
                    dialog.dismiss()
                    openProduct(code)
                } else {
                    til.error = "Enter the 8–14 digits printed under the barcode"
                }
            }
        }
        dialog.show()
    }
}
