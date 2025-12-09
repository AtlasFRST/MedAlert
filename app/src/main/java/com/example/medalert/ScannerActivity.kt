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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.medalert.databinding.ActivityScannerBinding
import com.example.medalert.data.rxnorm.Candidate
import com.example.medalert.data.rxnorm.RxNormClient
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@androidx.annotation.OptIn(ExperimentalGetImage::class)
class ScannerActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityScannerBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    //private val db = FirebaseFirestore.getInstance()
    //val user = FirebaseAuth.getInstance().currentUser


    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    //hold the user-confirmed normalized drug from RxNorm
    private var confirmedDrugName: String? = null
    private var confirmedRxcui: String? = null

    //permissions based on API level
    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA
    ).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }.toTypedArray()

    //permission launcher
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }

        if (allPermissionsGranted()) startCamera() else requestPermissions()
    }

    private fun allPermissionsGranted() =
        requiredPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun requestPermissions() {
        activityResultLauncher.launch(requiredPermissions)
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
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    //takephoto to be recognized
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


    //take photo and return text from it
    //also show text on screen
    private fun recognizeText(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                Log.d(TAG, "Recognized text: ${visionText.text}")

                val parsed = LabelParser.parse(visionText.text)

                imageProxy.close()

                // show text as it hass been parsed in bvbackgrouns
                if (parsed != null) {
                    val summary = buildString {
                        appendLine("Patient: ${parsed.patientName ?: "(unknown)"}")
                        appendLine("Drug: ${parsed.drugName ?: "(unknown)"}")
                        appendLine("Directions: ${parsed.directions ?: "(none)"}")
                        parsed.strength?.let { appendLine("Strength: $it") }
                        parsed.form?.let { appendLine("Form: $it") }
                        parsed.rxNumber?.let { appendLine("Rx#: $it") }
                        parsed.timesPerDay?.let { appendLine("Times per day: $it") }
                    }

                    runOnUiThread {
                        viewBinding.textResult.text = summary
                        Toast.makeText(
                            this@ScannerActivity,
                            "Text recognized and parsed",
                            Toast.LENGTH_SHORT
                        ).show()
                        showParsedConfirmationDialog(parsed)
                    }

                } else {
                    runOnUiThread {
                        viewBinding.textResult.text = visionText.text
                        Toast.makeText(
                            this@ScannerActivity,
                            "Couldn’t confidently parse—showing raw text.",
                            Toast.LENGTH_SHORT
                        ).show()
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
    }




    

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "ScannerActivity"
    }

    private fun showParsedConfirmationDialog(parsed: MedicationEntry) {
        val previewText = buildString {
            appendLine("Drug: ${parsed.drugName ?: "(unknown)"}")
            appendLine("Times per day: ${parsed.timesPerDay ?: 1}") // Default to 1 if not parsed
            //appendLine("\nDirections: ${parsed.directions ?: "(unknown)"}") revisit
        }

        AlertDialog.Builder(this@ScannerActivity)
            .setTitle("Confirm Scanned Details")
            .setMessage(previewText)
            .setCancelable(false)
            .setPositiveButton("Confirm and Set Alarms") { dialog, _ ->
                //launch alrm activity with data
                val intent = Intent(this, AlarmActivity::class.java).apply {
                    putExtra("SCANNED_DRUG_NAME", parsed.drugName)
                    // Use parsed value, or default to 1
                    putExtra("SCANNED_TIMES_PER_DAY", parsed.timesPerDay ?: 1)
                    // We can also pass pills remaining if the parser finds it
                   
                }
                startActivity(intent)
                finish() // Close the scanner after confirming
                dialog.dismiss()
            }
            .setNegativeButton("Retake Photo") { dialog, _ ->
                dialog.dismiss() // Just dismiss, camera is already running
            }
            .show()
    }
  
}



