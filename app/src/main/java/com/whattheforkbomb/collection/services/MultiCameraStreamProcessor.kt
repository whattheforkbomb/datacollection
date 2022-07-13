package com.whattheforkbomb.collection.services

import android.Manifest
import android.annotation.SuppressLint
import android.hardware.camera2.*
import android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
import android.hardware.camera2.CameraDevice.TEMPLATE_RECORD
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR
import android.media.Image
import android.nfc.Tag
import android.os.Handler
import android.provider.MediaStore.MediaColumns.ORIENTATION
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
//import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService


class MultiCameraStreamProcessor(
    private val cameraManager: CameraManager,
    private val permissionsService: PermissionsService,
    private val executor: ExecutorService,
    private val frontSurfaceView: SurfaceView,
    rearSurfaceView: SurfaceView
) : DataCollector<Pair<MultiCameraStreamProcessor.Facing, Image>>() {
    private val TAG: String = "MCSP"

    // Attempt to access both rear and front facing cameras
    // sample frames from each stream upon request
//    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    @Volatile var started: Boolean = false
    @Volatile var failureOpeningCamera = false
    @Volatile var frontFacing = true
    val cameras: ConcurrentHashMap<Facing, CameraDevice> = ConcurrentHashMap()
    val dataReceivedCallbacks: Set<(data: Pair<Facing, Image>) -> Unit> = HashSet()
    @Volatile var elapsed: Long = 0L

    val handler = Handler()
    private val frontSurface: Surface = frontSurfaceView.holder.surface
    private val rearSurface: Surface = rearSurfaceView.holder.surface

    // returns Ids for cameras to use: (Front, Rear)
    private fun retrieveCameraIds(manager: CameraManager): Pair<String, String>? {
        val cameraFilter = { cameraDirection: Int, id: String ->
            val characteristics = manager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == cameraDirection
        }

        Log.i(TAG, "Cameras Found: ${manager.cameraIdList.joinToString()}")

        val frontCameras = manager.cameraIdList.filter {
            cameraFilter(CameraSelector.LENS_FACING_FRONT, it)
        }

        val rearCameras = manager.cameraIdList.filter {
            cameraFilter(CameraSelector.LENS_FACING_BACK, it)
        }

        // verify both front and rear cameras exist
        val missingCameras: MutableSet<String> = HashSet()
        if (frontCameras.isEmpty()) {
            missingCameras.add("LENS_FACING_FRONT")
        }
        if (rearCameras.isEmpty()) {
            missingCameras.add("LENS_FACING_BACK")
        }
        if (missingCameras.isNotEmpty()) {
            Log.e(TAG, "Unable to find cameras that meet the following qualifiers: ${missingCameras.joinToString()}")
            return null
        }

        // Take first of each camera (later we'll try filtering on additional criteria, like FoV or dimensions
        Log.i(TAG, "Found ${frontCameras.size} Front-Facing Cameras and ${rearCameras.size} Rear-Facing Cameras.")
        return Pair<String, String>(frontCameras[0], rearCameras[0])
    }

    override fun registerDataReceivedCallback(dataReceivedCallback: (data: Pair<Facing, Image>) -> Unit): Boolean {
        return if (started) {
            dataReceivedCallbacks.plusElement(dataReceivedCallback)
            dataReceivedCallbacks.contains(dataReceivedCallback)
        } else {
            false
        }
    }

    private fun cameraOpeningFailure(device: CameraDevice, dataReceivedCallback: (data: Pair<Facing, Image>) -> Unit, startupCallback: (success: Boolean, reason: String?) -> Unit, reason: String?) {
        Log.e(TAG, "Failed to open camera ${device.id}")
        failureOpeningCamera = true
        device.close()
        if (reason == "2")
            // Unable to open rear camera
            camerasOpened(dataReceivedCallback, startupCallback)
        else
            startupCallback(false, "Failed to open camera: ${device.id} due to: $reason")
    }

    @Synchronized private fun cameraOpened(facing: Facing, device: CameraDevice, dataReceivedCallback: (data: Pair<Facing, Image>) -> Unit, startupCallback: (success: Boolean, reason: String?) -> Unit) {
        Log.i(TAG, "Opened camera: ${device.id}")
        cameras.put(facing, device)
        Log.i(TAG, "cam count: ${cameras.size}")
        if ((failureOpeningCamera && cameras.size == 1) || cameras.size == 2) {
            camerasOpened(dataReceivedCallback, startupCallback)
        }
    }

    @SuppressLint("MissingPermission") // checked within the permissionsService
    override fun onStart(dataReceivedCallback: (data: Pair<Facing, Image>) -> Unit, startupCallback: (success: Boolean, reason: String?) -> Unit) {
        if (!started) {
            val start = System.currentTimeMillis()
//            val imageCapture = ImageCapture.Builder().build()
//
//            imageCapture.takePicture(executor, object: ImageCapture.OnImageCapturedCallback() {
//                override fun onCaptureSuccess(image: ImageProxy) {
//                    // Use the image, then make sure to close it.
//                    frontSurfaceView.
//                    image.close()
//                }
//
//                override fun onError(exception: ImageCaptureException) {
//                }
//            })
            val cameraIds = retrieveCameraIds(cameraManager) ?: return startupCallback(false, "Unable to find cameras")
            val (frontCamera, rearCamera) = cameraIds

            if (!permissionsService.checkOrGetPerms(Manifest.permission.CAMERA)) {
                // Display error saying perms not provided.
                return startupCallback(false, "Permissions not granted")
            }

            // Probs want to check permitted to open more than one camera...

            Log.i(TAG, "Opening Camera: $frontCamera")
            cameraManager.openCamera(frontCamera, executor, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) = cameraOpened(Facing.FRONT, device, dataReceivedCallback, startupCallback)
                // Omitting for brevity...
                override fun onError(device: CameraDevice, error: Int) = cameraOpeningFailure(device, dataReceivedCallback, startupCallback, error.toString())
                override fun onDisconnected(device: CameraDevice) = cameraOpeningFailure(device, dataReceivedCallback, startupCallback, "Disconnected")
            })

            Log.i(TAG, "Opening Camera: $rearCamera")
            cameraManager.openCamera(rearCamera, executor, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) = cameraOpened(Facing.REAR, device, dataReceivedCallback, startupCallback)
                // Omitting for brevity...
                override fun onError(device: CameraDevice, error: Int) = cameraOpeningFailure(device, dataReceivedCallback, startupCallback, error.toString())
                override fun onDisconnected(device: CameraDevice) = cameraOpeningFailure(device, dataReceivedCallback, startupCallback, "Disconnected")
            })
        }
    }

    private fun camerasOpened(dataReceivedCallback: (data: Pair<Facing, Image>) -> Unit, startupCallback: (success: Boolean, reason: String?) -> Unit) {
        Log.i(TAG, "Both cameras open")
        registerDataReceivedCallback(dataReceivedCallback)

        val frontReq = cameras[Facing.FRONT]?.createCaptureRequest(TEMPLATE_RECORD)
        frontReq?.addTarget(frontSurface)
        frontReq?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        val rearReq = cameras[Facing.REAR]?.createCaptureRequest(TEMPLATE_RECORD)
        rearReq?.addTarget(rearSurface)
        rearReq?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        capture(frontReq, rearReq)

        started = true
        startupCallback(true, null)
    }

    private fun capture(frontReq: CaptureRequest.Builder?, rearReq: CaptureRequest.Builder?) {
        val start = System.currentTimeMillis()
//        cameras[if (frontFacing) Facing.FRONT else Facing.REAR]?.createCaptureSession(SessionConfiguration(SESSION_REGULAR, List(1) { OutputConfiguration(if (frontFacing) frontSurface else rearSurface) }, executor, object :
//            CameraCaptureSession.StateCallback() {
//            override fun onConfigured(session: CameraCaptureSession) {
//                val req = if (frontFacing) frontReq else rearReq
//                if (req != null) {
//                    session.capture(req.build(), object :
//                        CameraCaptureSession.CaptureCallback() {
//                        override fun onCaptureCompleted(
//                            session: CameraCaptureSession,
//                            request: CaptureRequest,
//                            result: TotalCaptureResult
//                        ) {
//                            super.onCaptureCompleted(session, request, result)
//                            Log.i(TAG, "Image Captured - elapsed: ${(System.currentTimeMillis() - start)}ms")
//                            frontFacing = !frontFacing
//                            handler.postDelayed({
//                                capture(frontReq, rearReq)
//                            }, 1000/120)
//                        }
//                    }, handler)
//                }
//            }
//            override fun onConfigureFailed(session: CameraCaptureSession) {
//                Log.e(TAG, "Failed To Configure Camera Capture Session")
//            }
//        }))
        cameras[Facing.FRONT]?.createCaptureSession(SessionConfiguration(SESSION_REGULAR, List(1) { OutputConfiguration(frontSurface) }, executor, object :
            CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (frontReq != null) {
                    session.setRepeatingRequest(frontReq.build(), object :
                        CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
//                            Log.i(TAG, "Image Captured - elapsed: ${(System.currentTimeMillis() - start)}ms")
//                            frontFacing = !frontFacing
//                            handler.postDelayed({
//                                capture(frontReq, rearReq)
//                            }, 1000/120)
                        }
                    }, handler)
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Failed To Configure Camera Capture Session")
            }
        }))
        cameras[Facing.REAR]?.createCaptureSession(SessionConfiguration(SESSION_REGULAR, List(1) { OutputConfiguration(rearSurface) }, executor, object :
            CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (rearReq != null) {
                    session.setRepeatingRequest(rearReq.build(), object :
                        CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
//                            Log.i(TAG, "Image Captured - elapsed: ${(System.currentTimeMillis() - start)}ms")
//                            frontFacing = !frontFacing
//                            handler.postDelayed({
//                                capture(frontReq, rearReq)
//                            }, 1000/120)
                        }
                    }, handler)
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Failed To Configure Camera Capture Session")
            }
        }))
    }

    enum class Facing {
        FRONT,
        REAR
    }

//    fun openCameras(executor: Executor, frontCallback: CameraDevice.StateCallback, rearCallback: CameraDevice.StateCallback, cameraManager: CameraManager): Boolean {
//        val cameraIds = retrieveCameraIds(cameraManager) ?: return false
//        val (frontCamera, rearCamera) = cameraIds
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.CAMERA
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            return
//        }
//        cameraManager.openCamera(frontCamera, executor, frontCallback)
//    }

//    fun openCameras(executor: Executor, frontCallback: CameraDevice.StateCallback, rearCallback: CameraDevice.StateCallback, activity: Activity) =
//        openCameras(executor, frontCallback, rearCallback, activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager)

}
