package com.whattheforkbomb.collection.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

/**
 * Service to ensure required permissions are obtained prior to attempting to interface with
 *  permission restricted hardware (camera and sensors)
 */
class PermissionsService(private val context: Context) {

    // check if permissions already retrieved
    // obtain missing permissions

    /* Permissions
     *  - Camera
     *  - Location (For earables bluetooth connection)
     */

    // Convert to callbacks, can't return directly
    fun checkOrGetPerms(permission: String): Boolean {
        return if (ActivityCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
//            ActivityCompat.requestPermissions()
            return true
        } else true
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher. You can use either a val, as shown in this snippet,
    // or a lateinit var in your onAttach() or onCreate() method.
//    val requestPermissionLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.RequestPermission()
//        ) { isGranted: Boolean ->
//            if (isGranted) {
//                // Permission is granted. Continue the action or workflow in your
//                // app.
//            } else {
//                // Explain to the user that the feature is unavailable because the
//                // features requires a permission that the user has denied. At the
//                // same time, respect the user's decision. Don't link to system
//                // settings in an effort to convince the user to change their
//                // decision.
//            }
//        }


}
