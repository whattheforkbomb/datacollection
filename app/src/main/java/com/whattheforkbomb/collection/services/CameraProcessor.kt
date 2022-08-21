package com.whattheforkbomb.collection.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.camera.core.CameraSelector
//import org.opencv.android.Utils
//import org.opencv.core.CvType
//import org.opencv.core.Mat
//import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.System.currentTimeMillis
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.fixedRateTimer
import kotlin.io.path.pathString

// Sampling code from: https://github.com/android/camera-samples/blob/main/Camera2Basic/app/src/main/java/com/example/android/camera2/basic/fragments/CameraFragment.kt
class CameraProcessor(private val appContext: Context): DataCollector {

    private lateinit var executor: ExecutorService
    private lateinit var fileSavingExecutor: ExecutorService
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
        // TODO: Check if atomic int has value
        val photoPath = Paths.get(rootDir, IMAGES_DIR)
        photoPath?.toFile()?.mkdirs()
        return camera2ImageCapture(photoPath.pathString)
    }

    // TODO: add method to 'wait' for job to finish, probs add to interface also

    override fun stop(onStoppedCallback: (stopSuccessful: Boolean) -> Unit): Boolean {
        captureSession?.stopRepeating()
        stopping = true
        Log.i(TAG, "Stop called with ${jobCount.get()} photos still being processed")
        this.onStoppedCallback = onStoppedCallback
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
                imageReader = ImageReader.newInstance(1280, 720, format, IMAGE_BUFFER_SIZE)
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
    private fun camera2ImageCapture(photoPath: String): Boolean = if (camera2 != null) {
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
        val extension = if (RAW_MODE) RAW_EXT else PNG_EXT
        var lastPhotoms = currentTimeMillis()
        captureSession!!.setSingleRepeatingRequest(request.build(), executor, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                if (camera2ShouldCapture || stopping) {
                    val image = imageReader?.acquireNextImage()
                    if (image != null) {
                        jobCount.incrementAndGet()
                        camera2ShouldCapture = false
                        val timeToProcessImage = currentTimeMillis()
                        val size = WIDTH*HEIGHT
                        var yuvBytes: ByteArray? = ByteArray(size)
                        image.planes[0].buffer.apply {
                            rewind()
//                            Log.i(TAG, "y buffer size: ${remaining()}")
                            get(yuvBytes!!)
                        }
//                        image.planes[2].apply {
//                            buffer.rewind()
//                            buffer.get(yuvBytes!!, size, uvSize-1)
////                            Log.i(TAG, "V Row Stride: $rowStride, Pixel Stride: $pixelStride, ${buffer.get(0)}, ${buffer.get(1)} ${buffer.get(2)} ${buffer.get(3)}")
//                        }
//                        image.planes[1].apply {
//                            buffer.rewind()
//                            yuvBytes!![size + uvSize-1] = buffer.get(uvSize - 2)
////                            Log.i(TAG, "U Row Stride: $rowStride, Pixel Stride: $pixelStride, ${buffer.get(0)}, ${buffer.get(1)} ${buffer.get(2)} ${buffer.get(3)}")
//                        }
                        image.close()
                        val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS")
                        timestampFormat.timeZone = TimeZone.getTimeZone("UTC")
                        val timestamp = timestampFormat.format(Date())
                        fileSavingExecutor.submit {
                            var rgb: IntArray? = IntArray(size)
                            var index = 0
                            try {
//                                var uvI = 0
//                                var uvICount = 0
//                                for (i in 0 until HEIGHT) {
//                                    uvI = i shr 1
//                                    var uvJ = 0
//                                    var uvJCount = 2
//                                    for (j in 0 until WIDTH) {
//                                        val y = yuvBytes!![index].toInt() and 0xFF
//
//                                        val uvIdx = size + ((uvI * WIDTH) + uvJ) //((i shr 1) * WIDTH) + (j - ((0xFF and j) and 0x01))
//                                        val v = (yuvBytes!![uvIdx].toInt()/* and 0xFF*/) - 128
//                                        val u = (yuvBytes!![uvIdx + 1].toInt()/* and 0xFF*/) - 128
//
//                                        // ABGR
//                                        // if top left is 0,0: (WIDTH-1-j)*HEIGHT) + i
//                                        // if bottom left is 0,0: (j*HEIGHT) + (HEIGHT-1-i)
//                                        rgb!![index++] = getRGB(y, u, v)
////                                        if (--uvJCount < 1) {
////                                            uvJ+=2
////                                            uvJCount = 2
////                                        }
//                                    }
////                                    if (--uvICount < 1) {
////                                        uvI++
////                                        uvICount = 2
////                                    }
//                                }
//                                var uvOffset = 0
//                                for (uvOffset in 0 until (size shr 1) step 2) {
//                                    val y1 = yuvBytes!![index].toInt() and 0xFF
//                                    val y2 = yuvBytes!![index+1].toInt() and 0xFF
//                                    val y3 = yuvBytes!![WIDTH+index].toInt() and 0xFF
//                                    val y4 = yuvBytes!![WIDTH+index+1].toInt() and 0xFF
//
//                                    val uvIdx = size + uvOffset//((uvI * WIDTH) + uvJ) //((i shr 1) * WIDTH) + (j - ((0xFF and j) and 0x01))
//                                    val v = (yuvBytes!![uvIdx].toInt() and 0xFF) - 128
//                                    val u = (yuvBytes!![uvIdx + 1].toInt() and 0xFF) - 128
//
//                                    // ABGR
//                                    // if top left is 0,0: (WIDTH-1-j)*HEIGHT) + i
//                                    // if bottom left is 0,0: (j*HEIGHT) + (HEIGHT-1-i)
//                                    rgb!![index] = getRGB(y1, u, v)
//                                    rgb[index+1] = getRGB(y2, u, v)
//                                    rgb[WIDTH+index] = getRGB(y3, u, v)
//                                    rgb[WIDTH+index+1] = getRGB(y4, u, v)
//                                    if (index != 0 && (index+2)%WIDTH == 0)
//                                        index+=WIDTH
//                                    index += 2
//                                }
//                                val chunkSize = 259200
//                                (0 until 8).toList().parallelStream().forEach { chunkNum ->
//                                    val start = chunkSize*chunkNum
//                                }
                                for (i in 0 until size) {
                                    val y = yuvBytes!![i].toInt() and 0xFF.let {
                                        if (it > 255) 255 else if (it < 0) 0 else it
                                    }
                                    rgb!![i]=(0xFF shl 24) +
                                            (y shl 16) +
                                            (y shl 8) +
                                            (y)
                                }
                            } catch (pkm: Exception) {
                                Log.e(TAG, "Something Went Wrong, Index: ${index}, Size: $size", pkm)
                                return@submit
                            }
                            yuvBytes = null
                            val bmp = Bitmap.createBitmap(rgb!!, HEIGHT, WIDTH, Bitmap.Config.ARGB_8888)
                            rgb = null
//                            val mat = Matrix()
//                            mat.postRotate(-90F)
//                            val rotatedBmp = Bitmap.createBitmap(bmp, 0,0, HEIGHT, WIDTH, mat, true)
//                            bmp.recycle()
                            val photoFile = File(Paths.get(photoPath, "$timestamp$extension").toUri())
//                            val timeToSaveImage = currentTimeMillis()
                            try {
//                                Log.i(TAG, "Saving file")
                                FileOutputStream(photoFile).use {
//                                        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, it)
                                    bmp/*rotatedBmp*/.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    bmp.recycle()
                                }
                                // TODO: decrement atomic int
//                                Log.d(TAG, "Image Captured: ${photoFile.absolutePath}, time to save: ${currentTimeMillis() - timeToSaveImage}ms")
                            } catch (ioex: IOException) {
                                Log.e(TAG, "Unable to write image to file", ioex)
                            } catch (iaex: IllegalArgumentException) {
                                Log.w(TAG, "Unable to write image to file as no pixels available.", iaex)
                            }
                            val count = jobCount.decrementAndGet()
                            if (stopping && count == 0) {
                                stopping = false
                                onStoppedCallback(true)
                            }
                        }
                        val now = currentTimeMillis()
//                        Log.d(TAG, "Time Taken To Process Image: ${now - timeToProcessImage}ms, time since last image: ${now - lastPhotoms}")
                        lastPhotoms = now
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
        private const val SAMPLE_RATE = 45L//(1000 / 30).toLong()
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val RAW_MODE = false

        private const val WIDTH = 720
        private const val HEIGHT = 1280
        private const val SENSOR_ROTATION_COMPENSATION = -90F

        @Suppress("NAME_SHADOWING")
        fun getRGB(y: Int, v: Int, u: Int): Int {
            val r = (y + (1.402/*1.370705*/ * v)).toInt().let {
                if (it > 255) 255 else if (it < 0) 0 else it
            }// ((Y + 1.370705 * (Cr-128)).toInt())
            val g = (y - (0.714/*0.698001f*/ * v) - (0.344/*0.337633f*/ * u)).toInt().let {
                if (it > 255) 255 else if (it < 0) 0 else it
            }// ((Y - (0.698001 * (Cr-128)) - (0.337633 * (Cb-128))).toInt())
            val b = (y + (1.772/*1.732446f*/ * u)).toInt().let {
                if (it > 255) 255 else if (it < 0) 0 else it
            }// ((Y + (1.732446 * (Cb-128))).toInt())
            return (0xFF shl 24) +
                   (/*y*/b shl 16) +
                   (/*y*/g shl 8) +
                   (/*y*/r)
        }

        // Taken from https://stackoverflow.com/a/56812799
        fun imageToBitmap(image: Image): Bitmap {
            val height = image.height
            val width = image.width
            val yBuffer = image.planes[0].buffer
            val vuBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val vuSize = vuBuffer.remaining()
            val nv21 = ByteArray(ySize + vuSize)

            yBuffer.get(nv21, 0, ySize)
            vuBuffer.get(nv21, ySize, vuSize)
            image.close()

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

//        fun getImageBytes(image: Image, yByteArray: ByteArray, vuByteArray: ByteArray): Unit = image.use {
//            yByteArray.
//            val height = image.height
//            val width = image.width
//            val yBuffer = image.planes[0].buffer
//            val vuBuffer = image.planes[2].buffer
//
//            val ySize = yBuffer.remaining()
//            val vuSize = vuBuffer.remaining()
//            val nv21 = ByteArray(ySize + vuSize)
//
//            yBuffer.get(nv21, 0, ySize)
//            vuBuffer.get(nv21, ySize, vuSize)
//            image.close()
//
//            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
//            val out = ByteArrayOutputStream()
//            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
//            val imageBytes = out.toByteArray()
//            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
//        }

//        fun imageToMat(image: Image): Mat? {
//            var buffer: ByteBuffer
//            var rowStride: Int
//            var pixelStride: Int
//            val width = image.width
//            val height = image.height
//            var offset = 0
//            val planes = image.planes
//            val data =
//                ByteArray(image.width * image.height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8)
//            val rowData = ByteArray(planes[0].rowStride)
//            for (i in planes.indices) {
//                buffer = planes[i].buffer
//                rowStride = planes[i].rowStride
//                pixelStride = planes[i].pixelStride
//                val w = if (i == 0) width else width / 2
//                val h = if (i == 0) height else height / 2
//                for (row in 0 until h) {
//                    val bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8
//                    if (pixelStride == bytesPerPixel) {
//                        val length = w * bytesPerPixel
//                        buffer[data, offset, length]
//                        if (h - row != 1) {
//                            buffer.position(buffer.position() + rowStride - length)
//                        }
//                        offset += length
//                    } else {
//                        if (h - row == 1) {
//                            buffer[rowData, 0, width - pixelStride + 1]
//                        } else {
//                            buffer[rowData, 0, rowStride]
//                        }
//                        for (col in 0 until w) {
//                            data[offset++] = rowData[col * pixelStride]
//                        }
//                    }
//                }
//            }
//            val mat = Mat(height + height / 2, width, CvType.CV_8UC1)
//            mat.put(0, 0, data)
//            return mat
//        }

        private fun cloneByteBuffer(buffer: ByteBuffer): ByteArray {
            val clone = ByteArray(buffer.capacity())
            buffer.get(clone)
            return clone
        }
    }

}
