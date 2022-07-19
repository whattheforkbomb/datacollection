package com.whattheforkbomb.collection.services

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.file.Paths
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
                .build()

            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

            scheduleRepeatingCapture { timestamp: String -> cameraXCapture(imageCapture, timestamp) }
        }, ContextCompat.getMainExecutor(appContext))
    }

    // TODO pass ref to capture method
    private fun scheduleRepeatingCapture(capture: (timestamp: String) -> Unit) {

    }

    private fun cameraXCapture(imageCapture: ImageCapture, timestamp: String) {
        val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                    Log.d(TAG, "Photo capture succeeded: $savedUri")

                    // We can only change the foreground Drawable using API level 23+ API
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // Update the gallery thumbnail with latest picture taken
                        setGalleryThumbnail(savedUri)
                    }

                    // Implicit broadcasts will be ignored for devices running API level >= 24
                    // so if you only target API level 24+ you can remove this statement
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        requireActivity().sendBroadcast(
                            Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
                        )
                    }

                    // If the folder selected is an external media directory, this is
                    // unnecessary but otherwise other apps will not be able to access our
                    // images unless we scan them using [MediaScannerConnection]
                    val mimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(savedUri.toFile().extension)
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(savedUri.toFile().absolutePath),
                        arrayOf(mimeType)
                    ) { _, uri ->
                        Log.d(TAG, "Image capture scanned into media store: $uri")
                    }
                }
            })

    }

    companion object {
        val IMAGES_DIR = "images"
    }

}
