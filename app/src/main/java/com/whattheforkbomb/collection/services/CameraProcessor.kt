package com.whattheforkbomb.collection.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.Surface.ROTATION_270
import androidx.camera.core.CameraSelector
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
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import kotlin.io.path.pathString

// Sampling code from: https://github.com/android/camera-samples/blob/main/Camera2Basic/app/src/main/java/com/example/android/camera2/basic/fragments/CameraFragment.kt
class CameraProcessor(private val appContext: Context): DataCollector {

    private lateinit var executor: ExecutorService
    private lateinit var fileSavingExecutor: ExecutorService

    // Camera 2
    private lateinit var onCamera2ReadyCallback: (success: Boolean) -> Unit
    private val cameraManager: CameraManager by lazy {
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private var imageReader: ImageReader? = null
    private var camera2: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    @Volatile private var camera2ShouldCapture = true
    private var camera2ShouldCaptureTimer: Timer? = null

    @SuppressLint("MissingPermission")
    override fun setup(onReadyCallback: (setupSuccessful: Boolean) -> Unit) {
        executor = Executors.newSingleThreadExecutor()
        fileSavingExecutor = Executors.newFixedThreadPool(4)
        setupCamera2Images()
        onCamera2ReadyCallback = onReadyCallback
    }

    // false means unable to start
    override fun start(rootDir: String): Boolean {
        val photoPath = Paths.get(rootDir, IMAGES_DIR)
        photoPath.toFile().mkdirs()
        return camera2ImageCapture(photoPath.pathString)
    }

    override fun stop(): Boolean {
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
                val characteristics = cameraManager.getCameraCharacteristics(camera2!!.id)

                // Check camera abilities
                val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) as Range<Int>?
                if(null != isoRange) {
                    Log.i(TAG, "iso range => lower : ${isoRange.lower}, higher : ${isoRange.upper}")
                } else {
                    Log.w(TAG, "iso range => NULL NOT SUPPORTED")
                }
                val exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) as Range<Long>?
                if(null != exposureTimeRange) {
                    Log.i(TAG, "exposure range => lower : ${exposureTimeRange.lower}, higher : ${exposureTimeRange.upper}")
                } else {
                    Log.w(TAG, "exposure range => NULL NOT SUPPORTED")
                }
                val apertureRange = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                if(null != apertureRange) {
                    Log.i(TAG, "aperture range => ${apertureRange.joinToString()}")
                } else {
                    Log.w(TAG, "aperture range => NULL NOT SUPPORTED")
                }

                val format = if (RAW_MODE) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
                val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                    .getOutputSizes(format)
                Log.i(TAG, "Possible sizes: ${sizes.joinToString()}")
                val size = sizes.maxByOrNull { it.height * it.width }!!
                imageReader = ImageReader.newInstance(1920, 1080, format, IMAGE_BUFFER_SIZE)
                createCaptureSession(camera2!!, imageReader!!.surface)
                Log.i(TAG, "Camera: $frontCameraId Opened")
            }

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $frontCameraId has been disconnected")
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Camera Disabled (Have you changed/closed the app?)"
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
        val request = camera2!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        request.addTarget(imageReader!!.surface)
//        request.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
//        request.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
//        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        request.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        request.set(CaptureRequest.SENSOR_SENSITIVITY, 200)
        request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, TimeUnit.MILLISECONDS.toNanos(5))
        request.set(CaptureRequest.JPEG_ORIENTATION, ROTATION_270)

        camera2ShouldCaptureTimer = fixedRateTimer("Camera2ShouldCaptureTimer", true, period = SAMPLE_RATE) {
            camera2ShouldCapture = true
        }
        val extension = if (RAW_MODE) RAW_EXT else PNG_EXT
        captureSession!!.setSingleRepeatingRequest(request.build(), executor, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                if (camera2ShouldCapture) {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        camera2ShouldCapture = false
                        var timeToSaveImage = 0L
                        val timeToProcessImage = currentTimeMillis()
                        if (RAW_MODE) {
                            val (bytes, size) = image.use {
                                Pair(cloneByteBuffer(it.planes[0].buffer), Size(it.width, it.height))
                            }
                            Log.d(TAG, "Time to copy Bytes: ${currentTimeMillis() - timeToProcessImage}ms")
                            fileSavingExecutor.submit {
                                val dngCreator = DngCreator(cameraManager.getCameraCharacteristics(camera2!!.id), result)
                                    .setOrientation(ExifInterface.ORIENTATION_NORMAL)
                                val timestamp = SimpleDateFormat("yyyy-MMM-dd'T'HH:mm:ss.SSS")
                                timestamp.timeZone = TimeZone.getTimeZone("UTC")
                                val photoFile = File(
                                    Paths.get(photoPath, "${timestamp.format(Date())}${extension}")
                                        .toUri()
                                )
                                timeToSaveImage = currentTimeMillis()
                                try {
                                    FileOutputStream(photoFile).use {
                                        dngCreator.writeByteBuffer(it, size, ByteBuffer.wrap(bytes), 0)
                                    }
                                    Log.d(TAG, "Image Captured: ${photoFile.absolutePath}, time to save: ${currentTimeMillis() - timeToSaveImage}ms")
                                } catch (ioex: IOException) {
                                    Log.e(TAG, "Unable to write DNG image to file", ioex)
                                } catch (iaex: IllegalArgumentException) {
                                    Log.w(TAG, "Unable to write DNG image to file as no pixels available.", iaex)
                                }
                            }
                        } else {
                            val bitmap = imageToBitmap(image)
                            fileSavingExecutor.submit {
                                val timestamp = SimpleDateFormat("yyyy-MMM-dd'T'HH:mm:ss.SSS")
                                timestamp.timeZone = TimeZone.getTimeZone("UTC")
                                val photoFile = File(Paths.get(photoPath, "${timestamp.format(Date())}${extension}").toUri())
                                timeToSaveImage = currentTimeMillis()
                                try {
                                    FileOutputStream(photoFile).use {
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                                    }
                                    Log.d(TAG, "Image Captured: ${photoFile.absolutePath}, time to save: ${currentTimeMillis() - timeToSaveImage}ms")
                                } catch (ioex: IOException) {
                                    Log.e(TAG, "Unable to write DNG image to file", ioex)
                                } catch (iaex: IllegalArgumentException) {
                                    Log.w(TAG, "Unable to write DNG image to file as no pixels available.", iaex)
                                }
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

    companion object {
        private const val IMAGES_DIR = "images"
        private const val PNG_EXT = ".JPEG"
        private const val RAW_EXT = ".dng"
        private const val TAG = "CP"
        private const val SAMPLE_RATE = (1000 / 30).toLong()
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val RAW_MODE = false
        private const val SENSOR_ROTATION_COMPENSATION = -270

        // Taken from https://stackoverflow.com/a/56812799
        fun imageToBitmap(image: Image): Bitmap {
            val height = image.height
            val width = image.width
//            val yBuffer = ByteBuffer.wrap(cloneByteBuffer(image.planes[0].buffer))
//            val vuBuffer = ByteBuffer.wrap(cloneByteBuffer(image.planes[2].buffer))
////            val imageBytes = cloneByteBuffer(image.planes[0].buffer)
            val clone = cloneByteBuffer(image.planes[0].buffer)
            image.close()
////            Log.i(TAG, "bytes: ${imageBytes.size}")
//
//            val ySize = yBuffer.remaining()
//            val vuSize = vuBuffer.remaining()
//
////            val imageBytes = ByteArray(ySize + vuSize)
////            yBuffer.get(imageBytes, 0, ySize)
////            vuBuffer.get(imageBytes, ySize, vuSize)
//
//            val nv21 = ByteArray(ySize + vuSize)
//
//            yBuffer.get(nv21, 0, ySize)
//            vuBuffer.get(nv21, ySize, vuSize)
//
//            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
//            val out = ByteArrayOutputStream()
//            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
//            val imageBytes = out.toByteArray()
//            val decodeImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
//            Log.i(TAG, "is null? ${decodeImage}")
//            Bitmap.createBitmap()
            return BitmapFactory.decodeByteArray(clone, 0, clone.size)
//            return decodeImage
        }

        private fun cloneByteBuffer(buffer: ByteBuffer): ByteArray {
            val clone = ByteArray(buffer.capacity())
            buffer.get(clone)
            return clone
        }
    }

}
