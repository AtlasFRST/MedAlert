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

    // Hold the user-confirmed normalized drug from RxNorm
    private var confirmedDrugName: String? = null
    private var confirmedRxcui: String? = null

    // Permissions based on API level
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

                        // Show confirmation dialog before saving
                        /*
                        showParsedConfirmationDialog(parsed) {
                            saveParsedToFirebase(parsed)
                        }
                         */
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




    /*
    private fun saveParsedToFirebase(parsed: MedicationEntry) {
        if (user == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = user.uid

        val data = hashMapOf(
            "Directions" to parsed.directions,
            "Drug name" to parsed.drugName,
            "Form" to parsed.form,
            "Rxnum" to parsed.rxNumber,
            "Strength" to parsed.strength,
            "timestamp" to System.currentTimeMillis(),
            "timesPerDay" to parsed.timesPerDay


        )


        db.collection("userMedications")
            .document("users")
           .collection(userId)
            .add(data)
            .addOnSuccessListener { doc ->
                Log.d("ScannerActivity", "Saved under user $userId with ID: ${doc.id}")
                Toast.makeText(this, "Saved medication", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("ScannerActivity", "Failed to save medication", e)
                Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
            }
    }

     */

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
                // --- LAUNCH ALARM ACTIVITY WITH DATA
                val intent = Intent(this, AlarmActivity::class.java).apply {
                    putExtra("SCANNED_DRUG_NAME", parsed.drugName)
                    // Use parsed value, or default to 1
                    putExtra("SCANNED_TIMES_PER_DAY", parsed.timesPerDay ?: 1)
                    // We can also pass pills remaining if the parser finds it
                    // putExtra("SCANNED_PILLS_REMAINING", parsed.quantity ?: 0)
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
    /*
    private fun showParsedConfirmationDialog(
        parsed:MedicationEntry,
        onConfirm: () -> Unit
    ) {
        val previewText = buildString {
            appendLine("Patient: ${parsed.patientName ?: "(unknown)"}")
            appendLine("Drug: ${parsed.drugName ?: "(unknown)"}")
            appendLine("Strength: ${parsed.strength ?: "(unknown)"}")
            appendLine("Form: ${parsed.form ?: "(unknown)"}")
            appendLine("Directions: ${parsed.directions ?: "(unknown)"}")
            appendLine("Times per day: ${parsed.timesPerDay ?: "(unknown)"}")

        }

        AlertDialog.Builder(this@ScannerActivity)
            .setTitle("Confirm Prescription Details")
            .setMessage(previewText)
            .setCancelable(false)
            .setPositiveButton("Confirm") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("Retake") { dialog, _ ->
                Toast.makeText(this, "Retake the photo", Toast.LENGTH_SHORT).show()
                startCamera()
                dialog.dismiss()
            }
            .show()
    }

     */
}


/**
 * Look up each word in the scanned drugName using RxNorm Approximate Term.
 * If multiple good matches exist, prompt the user to pick.

private suspend fun verifyDrugNameWithRxNorm(
drugName: String,
onDone: (standardName: String?, rxcui: String?) -> Unit
) {
if (drugName.isBlank()) {
onDone(null, null); return
}

val tokens = drugName
.split("\\s+".toRegex())
.map { it.trim().replace("[^A-Za-z0-9-]".toRegex(), "") }
.filter { it.length >= 3 }
.take(6)

if (tokens.isEmpty()) {
onDone(null, null); return
}

val candidates = mutableListOf<Candidate>()

withContext(Dispatchers.IO) {
for (t in tokens) {
try {
val resp = RxNormClient.api.approximateTerm(t)
candidates += resp.approximateGroup?.candidate.orEmpty()
} catch (e: Exception) {
Log.w(TAG, "RxNorm lookup failed for '$t': ${e.message}")
}
}
}

val merged = candidates
.groupBy { (it.rxcui ?: "") + "|" + (it.name ?: "") }
.map { (_, group) -> group.maxByOrNull { it.score?.toIntOrNull() ?: -1 }!! }
.sortedByDescending { it.score?.toIntOrNull() ?: -1 }
.take(20)

when {
merged.isEmpty() -> onDone(null, null)
merged.size == 1 -> {
val c = merged.first()
onDone(c.name, c.rxcui)
}
else -> {
runOnUiThread {
val items = merged.map {
"${it.name ?: "(unknown)"}  [RxCUI: ${it.rxcui ?: "—"} | score ${it.score ?: "?"}]"
}.toTypedArray()

AlertDialog.Builder(this@ScannerActivity)
.setTitle("Select the correct medication")
.setItems(items) { dialog, which ->
val chosen = merged[which]
onDone(chosen.name, chosen.rxcui)
dialog.dismiss()
}
.setNegativeButton("Cancel") { dialog, _ ->
onDone(null, null)
dialog.dismiss()
}
.show()
}
}
}
} */

//OLD SCANNER ACTIVITY
/*package com.example.medalert

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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.medalert.data.rxnorm.RxNormClient
import com.example.medalert.data.rxnorm.Candidate

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

                        if (parsed != null) {
                            // Kick off RxNorm verification for the scanned drug name
                            val rawDrug = parsed.drugName.orEmpty()
                            lifecycleScope.launchWhenStarted {
                                verifyDrugNameWithRxNorm(rawDrug) { chosenName, chosenRxcui ->
                                    confirmedDrugName = chosenName
                                    confirmedRxcui = chosenRxcui

                                    val summary = buildString {
                                        appendLine("Patient: ${parsed.patientName ?: "(unknown)"}")
                                        appendLine("Drug (raw): ${parsed.drugName}")
                                        appendLine("Drug (RxNorm): ${confirmedDrugName ?: "(no match)"}")
                                        appendLine("RxCUI: ${confirmedRxcui ?: "(—)"}")
                                        appendLine("Directions: ${parsed.directions}")
                                        parsed.strength?.let { appendLine("Strength: $it") }
                                        parsed.form?.let { appendLine("Form: $it") }
                                        parsed.rxNumber?.let { appendLine("Rx#: $it") }
                                    }

                                    runOnUiThread {
                                        viewBinding.textResult.text = summary
                                        Toast.makeText(
                                            this@ScannerActivity,
                                            if (confirmedDrugName != null) "Matched via RxNorm ✔" else "No RxNorm match",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    // Persist as needed
                                    saveParsedToCsv(parsed) // keeps original fields; update if you want RxNorm too
                                    Log.d(TAG, "Entry JSON:\n${parsed.toJsonString()}")
                                }
                            }
                        }
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
                        }

                        else {
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
    private suspend fun verifyDrugNameWithRxNorm(
        drugName: String,
        onDone: (standardName: String?, rxcui: String?) -> Unit
    ) {
        if (drugName.isBlank()) {
            onDone(null, null); return
        }

        // Tokenize and filter short/noisy fragments
        val tokens = drugName
            .split("\\s+".toRegex())
            .map { it.trim().replace("[^A-Za-z0-9-]".toRegex(), "") }
            .filter { it.length >= 3 }
            .take(6) // safety bound

        if (tokens.isEmpty()) {
            onDone(null, null); return
        }

        val candidates = mutableListOf<Candidate>()

        // Fetch candidates for each word on IO
        withContext(Dispatchers.IO) {
            for (t in tokens) {
                try {
                    val resp = RxNormClient.api.approximateTerm(t)
                    val list = resp.approximateGroup?.candidate.orEmpty()
                    candidates += list
                } catch (e: Exception) {
                    Log.w(TAG, "RxNorm lookup failed for '$t': ${e.message}")
                }
            }
        }

        // Merge: de-dup by RXCUI or name; prefer higher score
        val merged = candidates
            .groupBy { (it.rxcui ?: "") + "|" + (it.name ?: "") }
            .map { (_, group) -> group.maxByOrNull { it.score?.toIntOrNull() ?: -1 }!! }
            .sortedByDescending { it.score?.toIntOrNull() ?: -1 }
            .take(20)

        when {
            merged.isEmpty() -> onDone(null, null)

            merged.size == 1 -> {
                val c = merged.first()
                onDone(c.name, c.rxcui)
            }

            else -> {
                // Let user pick the correct normalized name
                runOnUiThread {
                    val items = merged.map { "${it.name ?: "(unknown)"}  [RxCUI: ${it.rxcui ?: "—"} | score ${it.score ?: "?"}]" }
                        .toTypedArray()

                    AlertDialog.Builder(this)
                        .setTitle("Select the correct medication")
                        .setItems(items) { dialog, which ->
                            val chosen = merged[which]
                            onDone(chosen.name, chosen.rxcui)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            onDone(null, null)
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
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
*/
