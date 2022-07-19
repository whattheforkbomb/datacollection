package com.whattheforkbomb.collection.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Sampling code from: https://github.com/android/camera-samples/blob/main/CameraXBasic/app/src/main/java/com/android/example/cameraxbasic/fragments/CameraFragment.kt
class CameraProcessor(participantId: String, private val appContext: Context) {

    private lateinit var executor: ExecutorService
    private var outputDirectory = Paths.get(appContext.applicationContext.filesDir.path, participantId)
//    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    @SuppressLint("MissingPermission")
    fun setupCamera(lifecycleOwner: LifecycleOwner) {
        setupCameraXImages(lifecycleOwner)
    }

    @SuppressLint("MissingPermission")
    fun setupCameraXImages(lifecycleOwner: LifecycleOwner) {
        executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)

        cameraProviderFuture.addListener({

            // CameraProvider
            val cameraProvider = cameraProviderFuture.get()

            val cameraSelector = CameraSelector.Builder().build()

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setBufferFormat(ImageFormat.YUV_420_888)
                .build()

            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

            scheduleRepeatingCapture { timestamp: String -> cameraXCapture(imageCapture, timestamp) }
        }, ContextCompat.getMainExecutor(appContext))
    }

    // TODO pass ref to capture method
    private fun scheduleRepeatingCapture(capture: (timestamp: String) -> Unit) {
        // TODO: measure time taken to capture picture
        // TODO: setup repeating task to take photos
        val timestamp = SimpleDateFormat("yyyy-MMM-ddTHH:mm:ss.SSS");
        timestamp.timeZone = TimeZone.getTimeZone("UTC");
        timestamp.format(Date())
    }

    private fun cameraXCapture(imageCapture: ImageCapture, timestamp: String) {
//        val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .build()

        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }

            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                super.onCaptureSuccess(imageProxy)
                imageProxy.use {
                    val image = imageToBitmap(it.image!!)
                    val out = FileOutputStream(file)
                    image.compress(Bitmap.CompressFormat.PNG)
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                }
            }
        })

    }

    companion object {
        val IMAGES_DIR = "images"
        val TAG = "CP"

        // Taken from https://stackoverflow.com/a/56812799
        fun imageToBitmap(image: Image): Bitmap {
            val yBuffer = image.planes[0].buffer // Y
            val vuBuffer = image.planes[2].buffer // VU

            val ySize = yBuffer.remaining()
            val vuSize = vuBuffer.remaining()

            val nv21 = ByteArray(ySize + vuSize)

            yBuffer.get(nv21, 0, ySize)
            vuBuffer.get(nv21, ySize, vuSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
    }

}
