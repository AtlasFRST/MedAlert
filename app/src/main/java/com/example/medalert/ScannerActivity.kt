package com.example.medalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.medalert.databinding.ActivityScannerBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.io.File
import java.io.FileWriter

@androidx.annotation.OptIn(ExperimentalGetImage::class)
class ScannerActivity : AppCompatActivity() {

    //create variables to bind to the execution time
    private lateinit var viewBinding: ActivityScannerBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Permissions based on API level
    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.CAMERA
    ).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }.toTypedArray()


    // Permission launcher
    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true

        permissions.entries.forEach { (permission, granted) ->
            if (!granted) {
                allGranted = false
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    Toast.makeText(
                        this,
                        "Permission $permission permanently denied. Enable it from app settings.",
                        Toast.LENGTH_LONG
                    ).show()
                    openAppSettings()
                } else {
                    Toast.makeText(this, "Permission $permission denied.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (allGranted) startCamera()
    }

    //bind everything when created 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() } //button that calls the text recognition

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    recognizeText(imageProxy)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun recognizeText(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d(TAG, "Recognized text: ${visionText.text}")

                    // Parse the recognized text
                    val parsed = LabelParser.parse(visionText.text)

                    runOnUiThread {
                        if (parsed != null) {
                            val summary = buildString {
                                appendLine("Patient: ${parsed.patientName ?: "(unknown)"}")
                                appendLine("Drug: ${parsed.drugName}")
                                appendLine("Directions: ${parsed.directions}")
                                parsed.strength?.let { appendLine("Strength: $it") }
                                parsed.form?.let { appendLine("Form: $it") }
                                parsed.rxNumber?.let { appendLine("Rx#: $it") }
                            }

                            viewBinding.textResult.text = summary
                            Toast.makeText(this, "Parsed label ✔", Toast.LENGTH_SHORT).show()

                            // JSON & Map (for Firestore later)
                            Log.d(TAG, "Entry JSON:\n${parsed.toJsonString()}")
                            val map = parsed.toMap()
                            saveParsedToCsv(parsed)

                            // TODO: when Firebase added
                            // Firebase.firestore.collection("users")
                            //     .document(currentUserId)
                            //     .collection("medications")
                            //     .add(map)

                            // TODO: DDI check integration
                            // DdiChecker.check(parsed.drugName)
                        } else {
                            viewBinding.textResult.text = visionText.text
                            Toast.makeText(this, "Couldn’t confidently parse—showing raw text.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed", e)
                    Toast.makeText(this, "Recognition failed", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
        /*
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d(TAG, "Recognized text: ${visionText.text}")
                    runOnUiThread {
                        viewBinding.textResult.text = visionText.text
                        Toast.makeText(this, "Text recognized", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed", e)
                    Toast.makeText(this, "Recognition failed", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }*/
    }

     private fun saveParsedToCsv(parsed: MedicationEntry) {
        try {
            // Get or create app-specific CSV file in external storage
            val file = File(getExternalFilesDir(null), "parsed_labels.csv")

            val isNewFile = !file.exists()
            FileWriter(file, true).use { writer ->
                if (isNewFile) {
                    writer.appendLine("Timestamp,Patient,Drug,Directions,Strength,Form,RxNumber")
                }

                val timestamp = System.currentTimeMillis()
                val row = listOf(
                    timestamp.toString(),
                    parsed.patientName ?: "",
                    parsed.drugName ?: "",
                    parsed.directions ?: "",
                    parsed.strength ?: "",
                    parsed.form ?: "",
                    parsed.rxNumber ?: ""
                ).joinToString(",")

                writer.appendLine(row)
            }

            Log.d(TAG, "CSV saved: ${file.absolutePath}")
            Toast.makeText(this, "Saved to CSV ✔", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving CSV", e)
            Toast.makeText(this, "Failed to save CSV", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "ScannerActivity"
    }
}
