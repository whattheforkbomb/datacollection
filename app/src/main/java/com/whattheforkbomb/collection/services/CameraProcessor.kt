package com.whattheforkbomb.collection.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.System.currentTimeMillis
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.fixedRateTimer
import kotlin.io.path.pathString

// Sampling code from: https://github.com/android/camera-samples/blob/main/CameraXBasic/app/src/main/java/com/android/example/cameraxbasic/fragments/CameraFragment.kt
// Sampling code from: https://github.com/android/camera-samples/blob/main/Camera2Basic/app/src/main/java/com/example/android/camera2/basic/fragments/CameraFragment.kt
class CameraProcessor(private val appContext: Context): DataCollector {

    private lateinit var executor: ExecutorService
    private lateinit var fileSavingExecutor: ExecutorService
    private lateinit var onCamera2ReadyCallback: (success: Boolean) -> Unit

    // Camera 1
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var repeatingImageCaptureTask: Timer? = null

    // Camera 2
    private val cameraManager: CameraManager by lazy {
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private var imageReader: ImageReader? = null
    private var camera2: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    @Volatile private var camera2ShouldCapture = true
    private var camera2ShouldCaptureTimer: Timer? = null

    @SuppressLint("MissingPermission")
    override fun setup(/*lifecycleOwner: LifecycleOwner, */onReadyCallback: (setupSuccessful: Boolean) -> Unit) {
        executor = Executors.newSingleThreadExecutor()
        fileSavingExecutor = Executors.newFixedThreadPool(4)
//        if (CAMERAX_MODE) {
//            setupCameraXImages(lifecycleOwner)
//        } else {
            setupCamera2Images()
//        }
        onCamera2ReadyCallback = onReadyCallback
    }

    // false means unable to start
    override fun start(rootDir: String): Boolean {
        val photoPath = Paths.get(rootDir, IMAGES_DIR)
        return if (CAMERAX_MODE) {
            if (repeatingImageCaptureTask == null) {
                if (imageCapture != null) {
                    scheduleRepeatingCapture { timestamp: String ->
                        cameraXCapture(
                            imageCapture!!,
                            timestamp,
                            photoPath.pathString
                        )
                    }
                    true
                } else {
                    Log.e(TAG, "Unable to begin captures as imageCapture is null")
                    false
                }
            } else {
                Log.e(TAG, "Unable to begin captures as recording already running")
                false
            }
        } else {
            photoPath.toFile().mkdirs()
            camera2ImageCapture(photoPath.pathString)
        }
    }

    override fun stop(): Boolean {
        repeatingImageCaptureTask?.cancel()
        repeatingImageCaptureTask = null
        captureSession?.stopRepeating()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun setupCamera2Images() {
        val frontCameraId = cameraManager.cameraIdList.filter {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraSelector.LENS_FACING_FRONT
        }[0]
        Log.i(TAG, "Opening Camera: $frontCameraId")
        cameraManager.openCamera(frontCameraId, executor, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                camera2 = device
                val size = cameraManager.getCameraCharacteristics(camera2!!.id).get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                    .getOutputSizes(ImageFormat.RAW_SENSOR).maxByOrNull { it.height * it.width }!!
                imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, IMAGE_BUFFER_SIZE) // Possibly use PNG to increase frame rate
                createCaptureSession(camera2!!, imageReader!!.surface)
                Log.i(TAG, "Camera: $frontCameraId Opened")
            }

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $frontCameraId has been disconnected")
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $frontCameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
            }
        })
    }

    private fun createCaptureSession(device: CameraDevice, target: Surface) {
        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, List(1) { OutputConfiguration(target) }, executor, object :
            CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                onCamera2ReadyCallback(true)
                Log.i(TAG, "Capture Session created for Camera: ${camera2!!.id}")
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, "Failed To Configure Camera Capture Session", exc)
                onCamera2ReadyCallback(false)
            }
        }))
    }

    // GC is issue as will block until complete...
    // OOM Error also likely if saving RAW...
    private fun camera2ImageCapture(photoPath: String): Boolean = if (camera2 != null) {
        Log.i(TAG, "Setting-up repeating capture request for camera2")
        val request = camera2!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        request.addTarget(imageReader!!.surface)
        request.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        request.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        camera2ShouldCaptureTimer = fixedRateTimer("Camera2ShouldCaptureTimer", true, period = SAMPLE_RATE) {
            camera2ShouldCapture = true
        }
        captureSession!!.setSingleRepeatingRequest(request.build(), executor, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                if (camera2ShouldCapture) {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        camera2ShouldCapture = false
                        var timeToSaveImage: Long = 0L
                        val timeToProcessImage = currentTimeMillis()
                        val (bytes, size) = image.use {
                            Pair(cloneByteBuffer(it.planes[0].buffer), Size(it.width, it.height))
                        }
                        Log.d(TAG, "Time to copy Bytes: ${currentTimeMillis() - timeToProcessImage}ms")
                        fileSavingExecutor.submit {
                            val dngCreator =
                                DngCreator(cameraManager.getCameraCharacteristics(camera2!!.id), result)
                            val timestamp = SimpleDateFormat("yyyy-MMM-dd'T'HH:mm:ss.SSS")
                            timestamp.timeZone = TimeZone.getTimeZone("UTC")
                            val photoFile = File(
                                Paths.get(photoPath, "${timestamp.format(Date())}${RAW_EXT}")
                                    .toUri()
                            )
                            timeToSaveImage = currentTimeMillis()
                            try {
                                FileOutputStream(photoFile).use {
                                    dngCreator.writeByteBuffer(it, size, bytes, 0)
                                }
                                Log.d(TAG, "Image Captured: ${photoFile.absolutePath}, time to save: ${currentTimeMillis() - timeToSaveImage}ms")
                            } catch (ioex: IOException) {
                                Log.e(TAG, "Unable to write DNG image to file", ioex)
                            } catch (iaex: IllegalArgumentException) {
                                Log.w(TAG, "Unable to write DNG image to file as no pixels available.", iaex)
                            }
                        }
                        Log.d(TAG, "Time Taken To Process Image: ${currentTimeMillis() - timeToProcessImage}ms, time to prep Image: ${timeToSaveImage - timeToProcessImage}ms")
                    } else {
                        Log.w(TAG, "Unable to save image as none present.")
                    }
                }
            }
        })
        true
    } else {
        Log.e(TAG, "Camera2 not setup")
        false
    }

    @SuppressLint("MissingPermission")
    private fun setupCameraXImages(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)

        cameraProviderFuture.addListener({
            // CameraProvider
            val cameraProvider = cameraProviderFuture.get()

            val cameraSelector = CameraSelector.Builder().build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setBufferFormat(ImageFormat.YUV_420_888)
                .build()

            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
            QualitySelector.from(Quality.HIGHEST)
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun scheduleRepeatingCapture(capture: (timestamp: String) -> Unit) {
        var timeLastPhotoTaken = currentTimeMillis()
        repeatingImageCaptureTask = fixedRateTimer("ImageCapture", true, period = SAMPLE_RATE) {
            val timestamp = SimpleDateFormat("yyyy-MMM-dd'T'HH:mm:ss.SSS");
            timestamp.timeZone = TimeZone.getTimeZone("UTC");

            capture(timestamp.format(Date()))
            val timeCurrentImageTaken = currentTimeMillis()
            Log.i(TAG, "time since last photo: ${timeCurrentImageTaken - timeLastPhotoTaken}ms")
            timeLastPhotoTaken = timeCurrentImageTaken
        }
    }

    private fun cameraXCapture(imageCapture: ImageCapture, timestamp: String, photoPath: String) {
        val photoFile = File(Paths.get(photoPath, "${timestamp}${PNG_EXT}").toUri())

        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }

            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                super.onCaptureSuccess(imageProxy)
                val imageToBitmapTimestamp = currentTimeMillis()
                val bitmap = imageToBitmap(imageProxy)
                Log.i(TAG, "Time taken to convert image to bitmap: ${currentTimeMillis() - imageToBitmapTimestamp}ms")
                FileOutputStream(photoFile).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                Log.i(TAG, "Image Saved at: ${photoFile.absolutePath}")
            }
        })
    }

    companion object {
        private const val IMAGES_DIR = "images"
        private const val PNG_EXT = ".png"
        private const val RAW_EXT = ".dng"
        private const val TAG = "CP"
        private const val SAMPLE_RATE = (1000 / 30).toLong()
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val CAMERAX_MODE = false

        // Taken from https://stackoverflow.com/a/56812799
        fun imageToBitmap(image: ImageProxy): Bitmap {
            val height = image.height
            val width = image.width
            val yBuffer = cloneByteBuffer(image.planes[0].buffer)
            val vuBuffer = cloneByteBuffer(image.planes[2].buffer)
            image.close()

            val ySize = yBuffer.remaining()
            val vuSize = vuBuffer.remaining()

            val nv21 = ByteArray(ySize + vuSize)

            yBuffer.get(nv21, 0, ySize)
            vuBuffer.get(nv21, ySize, vuSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        // https://stackoverflow.com/a/4074089
        private fun cloneByteBuffer(buffer: ByteBuffer): ByteBuffer {
            val clone = ByteBuffer.allocate(buffer.capacity())
            buffer.rewind() //copy from the beginning
            clone.put(buffer)
            buffer.rewind()
            clone.flip()
            return clone
        }
    }

}
