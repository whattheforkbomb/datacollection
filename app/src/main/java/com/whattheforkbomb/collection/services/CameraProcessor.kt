package com.whattheforkbomb.collection.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.CountDownTimer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.camera.core.CameraSelector
import com.whattheforkbomb.collection.data.TimeRemaining
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.fixedRateTimer
import kotlin.io.path.pathString
import kotlin.math.ceil


// Sampling code from: https://github.com/android/camera-samples/blob/main/Camera2Basic/app/src/main/java/com/example/android/camera2/basic/fragments/CameraFragment.kt
class CameraProcessor(private val appContext: Context): DataCollector {

    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var fileSavingExecutor: ExecutorService = Executors.newFixedThreadPool(8)
    private val jobCount = AtomicInteger(0)
    @Volatile private var stopping = false
    @Volatile private lateinit var onStoppedCallback: (stopSuccessful: Boolean) -> Unit

    // Camera 2
    private lateinit var onCamera2ReadyCallback: (success: Boolean) -> Unit
    private val cameraManager: CameraManager by lazy {
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private var imageReader: ImageReader? = null
    private var camera2: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    @Volatile private var camera2ShouldCapture = true
    @Volatile private var photoPath: String? = null
    private var camera2ShouldCaptureTimer: Timer? = null
    val extension = if (RAW_MODE) RAW_EXT else PNG_EXT

    @SuppressLint("MissingPermission")
    override fun setup(onReadyCallback: (setupSuccessful: Boolean) -> Unit) {
        setupCamera2Images()
        onCamera2ReadyCallback = onReadyCallback
    }

    // false means unable to start
    override fun start(rootDir: String): Boolean {
        val photoPath = Paths.get(rootDir, IMAGES_DIR)
        photoPath?.toFile()?.mkdirs()
        return camera2ImageCapture(photoPath.pathString)
    }

    override fun stop(onStoppedCallback: (stopSuccessful: Boolean) -> Unit): Boolean {
        this.onStoppedCallback = onStoppedCallback
        Log.i(TAG, "Stop called with ${jobCount.get()} photos still being processed")
        stopping = true
        if (jobCount.get() == 0) {
            Log.i(TAG, "Stopping via stop: ${jobCount.get()}")
            onStoppedCallback(true)
        }
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

                val format = if (RAW_MODE) ImageFormat.RAW_SENSOR else ImageFormat.YUV_420_888
                val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                    .getOutputSizes(format)
                Log.i(TAG, "Possible sizes: ${sizes.joinToString()}")
                imageReader = ImageReader.newInstance(WIDTH, HEIGHT, format, IMAGE_BUFFER_SIZE)
//                imageReader?.setOnImageAvailableListener({ ir ->
//                    if (camera2ShouldCapture && !stopping) {
//                        val image = ir?.acquireNextImage()
//                        if (image != null) {
//                            jobCount.incrementAndGet()
//                            camera2ShouldCapture = false
//                            val size = HEIGHT*WIDTH
//                            val uvSize = size/2
//                            var yBytes: ByteArray? = ByteArray(size)
//                            var uBytes: ByteArray? = ByteArray(uvSize-1)
//                            var vBytes: ByteArray? = ByteArray(uvSize-1)
//                            val pixelStride = image.planes[1].pixelStride
//                            val rowStride = image.planes[1].rowStride
//                            image.planes[0].buffer.get(yBytes!!)
//                            image.planes[2].buffer.get(vBytes!!)
//                            image.planes[1].buffer.get(uBytes!!)
//                            image.close()
//                            val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS")
//                            timestampFormat.timeZone = TimeZone.getTimeZone("UTC")
//                            val timestamp = timestampFormat.format(Date())
//                            fileSavingExecutor.submit {
//                                var rgb: IntArray? = IntArray(size)
//                                var index = 0
//                                try {
////                                 greyscale
////                                for (i in 0 until size) {
////                                    val y = yuvBytes!![i].toInt() and 0xFF.let {
////                                        if (it > 255) 255 else if (it < 0) 0 else it
////                                    }
////                                    rgb!![i]=(0xFF shl 24) +
////                                            (y shl 16) +
////                                            (y shl 8) +
////                                            (y)
////                                }
//                                var uvX = 0
//                                var uvY = 0
//                                for (xIdx in 0 until HEIGHT) {
//                                    for (yIdx in 0 until WIDTH) {
//                                        val y = ((yBytes!![index].toInt()) and 0xFF).let {
//                                            if (it > 255) 255 else if (it < 0) 0 else it
//                                        }
//                                        val uvIdx = uvX + uvY
//                                        val u = ((uBytes!![uvIdx].toInt() and 0xFF) - 128)
//                                        val v = ((vBytes!![uvIdx].toInt() and 0xFF) - 128)
//                                        rgb!![index] = getRGB(y, u, v)
//                                        if (yIdx % 2 == 1) uvY += pixelStride
//                                        index++
//                                    }
//                                    if (xIdx % 2 == 1) uvX+=rowStride
//                                    uvY = 0
//                                }
//                                } catch (pkm: Exception) {
//                                    Log.e(TAG, "Something Went Wrong, Index: ${index}, Size: $size", pkm)
//                                    return@submit
//                                }
////                                val latch  = CountDownLatch(4)
////                                val yuv2rgb: (start: Int, end: Int) -> Unit = { start: Int, end: Int ->
////                                    var uvX = start / 2
////                                    var uvY = 0
////                                    try {
////                                        for (xIdx in start until end) {
////                                            for (yIdx in 0 until WIDTH) {
////                                                val y = ((yBytes!![index].toInt()) and 0xFF).let {
////                                                    if (it > 255) 255 else if (it < 0) 0 else it
////                                                }
////                                                val uvIdx = uvX + uvY
////                                                val u = ((uBytes!![uvIdx].toInt() and 0xFF) - 128)
////                                                val v = ((vBytes!![uvIdx].toInt() and 0xFF) - 128)
////                                                rgb!![index] = getRGB(y, u, v)
////                                                if (yIdx % 2 == 1) uvY += pixelStride
////                                                index++
////                                            }
////                                            if (xIdx % 2 == 1) uvX += rowStride
////                                            uvY = 0
////                                        }
////                                    } catch (pkmn: Exception) {
////                                        Log.e(TAG, "WTF???", pkmn)
////                                    }
////                                    latch.countDown()
////                                }
////                                val chunkSize = HEIGHT/4
////                                listOf((0 to chunkSize)).parallelStream()
////                                imageProcessingExecutor.submit { yuv2rgb(0, chunkSize) }
////                                imageProcessingExecutor.submit { yuv2rgb(chunkSize, 2*chunkSize) }
////                                imageProcessingExecutor.submit { yuv2rgb(2*chunkSize, 3*chunkSize) }
////                                imageProcessingExecutor.submit { yuv2rgb(3*chunkSize, HEIGHT) }
////                                latch.await()
//                                yBytes = null
//                                uBytes = null
//                                vBytes = null
//                                val bmp = Bitmap.createBitmap(rgb!!, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
//                                rgb = null
//                                val photoFile = File(Paths.get(photoPath, "$timestamp$extension").toUri())
//                                try {
//                                    FileOutputStream(photoFile).use { fos ->
//                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
//                                        bmp.recycle()
//                                    }
//                                    val count = jobCount.decrementAndGet()
//                                    if (stopping && count == 0) {
//                                        stopping = false
//                                        Log.i(TAG, "Stopping via image processing callback: $count")
//                                        onStoppedCallback(true)
//                                    }
//                                } catch (ioex: IOException) {
//                                    Log.e(TAG, "Unable to write image to file", ioex)
//                                } catch (iaex: IllegalArgumentException) {
//                                    Log.w(TAG, "Unable to write image to file as no pixels available.", iaex)
//                                }
//                            }
//                        } else {
//                            Log.w(TAG, "Unable to save image as none present.")
//                        }
//                    }
//                }, handler)
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
        device.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, listOf(OutputConfiguration(target)), executor, object :
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
    private fun camera2ImageCapture(path: String): Boolean = if (camera2 != null) {
        photoPath = path
        Log.i(TAG, "Setting-up repeating capture request for camera2")
        val request = camera2!!.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG)
        request.addTarget(imageReader!!.surface)
        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        request.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        request.set(CaptureRequest.SENSOR_SENSITIVITY, 200)
        request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30,30))
        request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, TimeUnit.MILLISECONDS.toNanos(5))

        camera2ShouldCaptureTimer = fixedRateTimer("Camera2ShouldCaptureTimer", true, period = SAMPLE_RATE) {
            camera2ShouldCapture = true
        }
        captureSession!!.setSingleRepeatingRequest(request.build(), executor, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                if (camera2ShouldCapture && !stopping) {
                    val image = imageReader?.acquireNextImage()
                    if (image != null) {
                        jobCount.incrementAndGet()
                        camera2ShouldCapture = false
                        val size = HEIGHT*WIDTH
                        val uvSize = size/2
                        val yBytes = ByteArray(size)
                        image.planes[0].buffer.get(yBytes)
//                        val uBytes: ByteArray = ByteArray(uvSize-1)
//                        image.planes[1].buffer.get(uBytes!!)
//                        val vBytes: ByteArray = ByteArray(uvSize-1)
//                        image.planes[2].buffer.get(vBytes!!)
//                        val uvBytes = ByteArray(uvSize)
//                        image.planes[1].buffer.get(uvBytes, 0, uvSize - 1)
//                        uvBytes[uvSize-1] = image.planes[2].buffer[uvSize-2]
//                        val pixelStride = image.planes[1].pixelStride
//                        val rowStride = image.planes[1].rowStride
                        image.close()
                        val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS")
                        timestampFormat.timeZone = TimeZone.getTimeZone("UTC")
                        val timestamp = timestampFormat.format(Date())
                        fileSavingExecutor.submit {
                            var rgb: IntArray? = IntArray(size)
                                try {
                                    // greyscale
                                    for (i in 0 until size) {
                                        val y = yBytes[i].toInt() and 0xFF.let {
                                            if (it > 255) 255 else if (it < 0) 0 else it
                                        }
                                        rgb!![i]=(0xFF shl 24) +
                                                (y shl 16) +
                                                (y shl 8) +
                                                (y)
                                    }
//                                    // rgb
//                                    var index = 0
//                                    var uvX = 0
//                                    var uvY = 0
//                                    for (xIdx in 0 until HEIGHT) {
//                                        for (yIdx in 0 until WIDTH) {
//                                            val y = ((yBytes!![index].toInt()) and 0xFF).let {
//                                                if (it > 255) 255 else if (it < 0) 0 else it
//                                            }
//                                            val uvIdx = uvX + uvY
//                                            val u = ((uvBytes[uvIdx].toInt() and 0xFF) - 128)
//                                            val v = ((uvBytes[uvIdx+1].toInt() and 0xFF) - 128)
//                                            rgb!![index] = getRGB(y, u, v)
//                                            if (yIdx % 2 == 1) uvY += pixelStride
//                                            index++
//                                        }
//                                        if (xIdx % 2 == 1) uvX+=rowStride
//                                        uvY = 0
//                                    }
                                } catch (pkm: Exception) {
                                    Log.e(TAG, "Something Went Wrong, Size: $size", pkm)
                                    return@submit
                                }
//                            uBytes = null
//                            vBytes = null
                            val bmp = Bitmap.createBitmap(rgb!!, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
                            rgb = null
                            val photoFile = File(Paths.get(photoPath, "$timestamp$extension").toUri())
                            try {
                                FileOutputStream(photoFile).use { fos ->
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                    bmp.recycle()
                                }
                                val count = jobCount.decrementAndGet()
                                if (stopping && count == 0) {
                                    stopping = false
                                    imageReader?.acquireLatestImage()?.close()
                                    onStoppedCallback(true)
                                }
                            } catch (ioex: IOException) {
                                Log.e(TAG, "Unable to write image to file", ioex)
                            } catch (iaex: IllegalArgumentException) {
                                Log.w(TAG, "Unable to write image to file as no pixels available.", iaex)
                            }
                        }
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
        private const val PNG_EXT = ".png"
        private const val JPEG_EXT = ".jpeg"
        private const val RAW_EXT = ".dng"
        private const val TAG = "CP"
        private const val SAMPLE_RATE = 50L//(1000 / 30).toLong()
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val RAW_MODE = false

        private const val WIDTH = 1280//720
        private const val HEIGHT = 720//1280
        private const val SENSOR_ROTATION_COMPENSATION = -90F

        @Suppress("NAME_SHADOWING")
        fun getRGB(y: Int, v: Int, u: Int): Int {
            val r = (y + (1.370705 * v)).toInt().let {
                if (it > 255) 255 else if (it < 0) 0 else it
            }
            val g = (y - (0.698001 * v) - (0.337633 * u)).toInt().let {
                if (it > 255) 255 else if (it < 0) 0 else it
            }
            val b = (y + (1.732446f * u)).toInt().let {
                if (it > 255) 255 else if (it < 0) 0 else it
            }
            return (0xFF shl 24) +
                   (b shl 16) +
                   (g shl 8) +
                   (r)
        }

//        @Suppress("NAME_SHADOWING")
//        fun getRGB3(y: Int, v: Int, u: Int): Int {
//            val r = (y + (1.402 * v)).toInt().let {
//                if (it > 255) 255 else if (it < 0) 0 else it
//            }// ((Y + 1.370705 * (Cr-128)).toInt())
//            val g = (y - (0.714 * v) - (0.344 * u)).toInt().let {
//                if (it > 255) 255 else if (it < 0) 0 else it
//            }// ((Y - (0.698001 * (Cr-128)) - (0.337633 * (Cb-128))).toInt())
//            val b = (y + (1.772 * u)).toInt().let {
//                if (it > 255) 255 else if (it < 0) 0 else it
//            }// ((Y + (1.732446 * (Cb-128))).toInt())
//            return (0xFF shl 24) +
//                    (b shl 16) +
//                    (g shl 8) +
//                    (r)
//        }
//
//        @Suppress("NAME_SHADOWING")
//        fun getRGB2(y: Int, v: Int, u: Int): Int {
//            val r = ((1.164 * (y/*-16*/)) + (1.596 * (v))).toInt().let {
//                if (it > 255) 255 else if (it < 0) 0 else it
//            }
//            val g = ((1.164 * (y/*-16*/)) - (0.813 * (v)) - (0.391 * (u))).toInt().let {
//                if (it > 255) 255 else if (it < 0) 0 else it
//            }
//            val b = ((1.164 * (y/*-16*/)) + (2.018 * (u))).toInt().let {
//                if (it > 255) 255 else if (it < 0) 0 else it
//            }
//            return (0xFF shl 24) +
//                    (b shl 16) +
//                    (g shl 8) +
//                    (r)
//        }

    }

}
