package com.whattheforkbomb.collection.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.camera.core.CameraSelector
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
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
        photoPath?.toFile()?.mkdirs()
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

                val format = if (RAW_MODE) ImageFormat.RAW_SENSOR else ImageFormat.YUV_420_888
//                characteristics.get(CameraCharacteristics.format
                val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                    .getOutputSizes(format)
                Log.i(TAG, "Possible sizes: ${sizes.joinToString()}")
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
                if (camera2ShouldCapture) {
                    val image = imageReader?.acquireNextImage()
                    if (image != null) {
//                        camera2ShouldCapture = false
                        var timeToSaveImage = 0L
                        val timeToProcessImage = currentTimeMillis()
                        if (RAW_MODE) {
//                            val (bytes, size) = image.use {
//                                Pair(cloneByteBuffer(it.planes[0].buffer), Size(it.width, it.height))
//                            }
//                            Log.d(TAG, "Time to copy Bytes: ${currentTimeMillis() - timeToProcessImage}ms")
//                            fileSavingExecutor.submit {
//                                val dngCreator = DngCreator(cameraManager.getCameraCharacteristics(camera2!!.id), result)
//                                    .setOrientation(ExifInterface.ORIENTATION_NORMAL)
//                                val timestamp = SimpleDateFormat("yyyy-MMM-dd'T'HH:mm:ss.SSS")
//                                timestamp.timeZone = TimeZone.getTimeZone("UTC")
//                                val photoFile = File(
//                                    Paths.get(photoPath!!.pathString, "${timestamp.format(Date())}${extension}")
//                                        .toUri()
//                                )
//                                timeToSaveImage = currentTimeMillis()
//                                try {
//                                    FileOutputStream(photoFile).use {
//                                        dngCreator.writeByteBuffer(it, size, bytes, 0)
//                                    }
//                                    Log.d(TAG, "Image Captured: ${photoFile.absolutePath}, time to save: ${currentTimeMillis() - timeToSaveImage}ms")
//                                } catch (ioex: IOException) {
//                                    Log.e(TAG, "Unable to write DNG image to file", ioex)
//                                } catch (iaex: IllegalArgumentException) {
//                                    Log.w(TAG, "Unable to write DNG image to file as no pixels available.", iaex)
//                                }
//                            }
                        } else {
                            val ySize = image.planes[0].buffer.remaining()
                            val vuSize = image.planes[2].buffer.remaining()
                            var nv21: ByteArray? = ByteArray(ySize + vuSize)
                            image.planes[0].buffer.get(nv21!!, 0, ySize)
                            image.planes[2].buffer.get(nv21, ySize, vuSize)
                            image.close()
                            val size = HEIGHT*WIDTH

//                            val r = ByteArray(size)
//                            image.planes[0].buffer.get(r)
//                            val g = ByteArray(size)
//                            image.planes[1].buffer.get(g)
//                            val b = ByteArray(size)
//                            image.planes[2].buffer.get(b)
                            val timestampFormat = SimpleDateFormat("yyyy-MMM-dd'T'HH:mm:ss.SSS")
                            timestampFormat.timeZone = TimeZone.getTimeZone("UTC")
                            val timestamp = timestampFormat.format(Date())
                            fileSavingExecutor.submit {
//                                val imageBytes = out.toByteArray()
//                                val colours = IntArray(1920*1080)
//                                for (i in 0 until (size-1)) {
//                                    colours[i] = ((0x0 and 0xFF) shl 24) or
//                                        ((r[i].toInt() and 0xFF) shl 16) or
//                                        ((g[i].toInt() and 0xFF) shl 8 ) or
//                                        ((b[i].toInt() and 0xFF) shl 0 )
//                                }
//                                var rgb: IntArray? = IntArray(size)
                                Log.i(TAG, "CONVERTING YUV to RGBA")
//                                val yuvMat = Mat(HEIGHT + (HEIGHT/2), WIDTH, CvType.CV_8UC1)
//                                yuvMat.put(0,0, nv21!!)
//                                val rgbMat = Mat()
//                                Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGBA_NV21, 4)
//                                yuvMat.release()
//                                val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888, false)
//                                Utils.matToBitmap(rgbMat, bmp)
//                                rgbMat.release()
                                var rgb: IntArray? = IntArray(size)
//                                var Y: Int
//                                var u = 0
//                                var v = 0

                                var index = 0
                                try {
//                                    for (i in 0 until WIDTH) {
//                                        for (j in 0 until HEIGHT) {
//                                            val y = nv21!![(i * HEIGHT) + j].toInt().let {
//                                                if (it < 16) 16 else if (it > 255) 255 else it
//        //                                        if (Y < 0) Y += 255
//        //                                        if (Y > 255) Y = 255
//                                            }
//                                            val vXIdx = j - (j and 0x01) * WIDTH
//                                            val vYIdx = (i shr 1)
//                                            val v = nv21!![size + vXIdx + vYIdx].toInt()
//                                            val u = nv21!![size + vXIdx + vYIdx + 1].toInt()
//
////                                            if (x and 1 == 0) {
////                                                v = nv21!![size + (((y shr 1) * HEIGHT) + x)].toInt()
////                                                u = nv21!![size + (((y shr 1) * HEIGHT) + x + 1)].toInt()
////                                            }
//    //                                        if (Cb < 0) Cb += 255 else if (Cb > 255) Cb = 255
//    //                                        if (Cr < 0) Cr += 255 else if (Cr > 255) Cr  = 255
//                                            var r = (1.164 * (y-16) + 1.596 * (v-128)).toInt()// ((Y + 1.370705 * (Cr-128)).toInt())
//                                            var g = (1.164 * (y-16) - 0.813 * (v-128) - 0.391 * (u-128)).toInt()// ((Y - (0.698001 * (Cr-128)) - (0.337633 * (Cb-128))).toInt())
//                                            var b = (1.164 * (y-16) + 2.018 * (u-128)).toInt()// ((Y + (1.732446 * (Cb-128))).toInt())
//
//                                            r = if (r > 255) 255 else if (r < 0) 0 else r
//                                            g = if (g > 255) 255 else if (g < 0) 0 else g
//                                            b = if (b > 255) 255 else if (b < 0) 0 else b
//
//                                            // ABGR
//                                            rgb!![(i * HEIGHT) + j] = (0xFF shl 24) +
//                                                        (b shl 16) +
//                                                        (g shl 8) +
//                                                        (r)
//                                            index++
//                                        }
//                                    }
                                    for (i in 0 until HEIGHT step 2) {
                                        for (j in 0 until WIDTH / 2) {
                                            val uvIdx = size + (i * WIDTH) + j
                                            val v = nv21!![uvIdx]
                                            val u = nv21!![uvIdx + 1]



                                        }
                                    }
                                } catch (pkm: Exception) {
                                    Log.e(TAG, "Something Went Wrong, Index: ${++index}, Size: $size", pkm)
                                    return@submit
                                }
                                nv21 = null
                                Log.i(TAG, "Creating Bitmap")
                                val bmp = Bitmap.createBitmap(HEIGHT, WIDTH, Bitmap.Config.ARGB_8888, false)
                                bmp.copyPixelsFromBuffer(IntBuffer.wrap(rgb!!))
                                rgb = null
//                                rgba = null
////                                val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
////                                val mat = Matrix()
////                                mat.postRotate(SENSOR_ROTATION_COMPENSATION)
////                                val rotatedBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true)
                                val photoFile = File(Paths.get(photoPath, "$timestamp$extension").toUri())
                                timeToSaveImage = currentTimeMillis()
                                try {
                                    Log.i(TAG, "Saving file")
                                    FileOutputStream(photoFile).use {
//                                        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, it)
                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    }
                                    Log.d(TAG, "Image Captured: ${photoFile.absolutePath}, time to save: ${currentTimeMillis() - timeToSaveImage}ms")
                                } catch (ioex: IOException) {
                                    Log.e(TAG, "Unable to write DNG image to file", ioex)
                                } catch (iaex: IllegalArgumentException) {
                                    Log.w(TAG, "Unable to write DNG image to file as no pixels available.", iaex)
                                }
                            }
                        }
                        val now = currentTimeMillis()
                        Log.d(TAG, "Time Taken To Process Image: ${now - timeToProcessImage}ms, time since last image: ${now - lastPhotoms}")
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
        private const val SAMPLE_RATE = 20L//(1000 / 30).toLong()
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val RAW_MODE = false

        private const val WIDTH = 1080
        private const val HEIGHT = 1920
        private const val SENSOR_ROTATION_COMPENSATION = -90F

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
