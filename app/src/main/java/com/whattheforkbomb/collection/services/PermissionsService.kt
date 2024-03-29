package com.whattheforkbomb.collection.services

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Service to ensure required permissions are obtained prior to attempting to interface with
 *  permission restricted hardware (camera and sensors)
 */
class PermissionsService {

    private val requiredPermissions: MutableMap<String, Boolean> = mutableMapOf()
    private val latch = CountDownLatch(1)

    fun checkOrGetPerms(activity: Activity): Boolean {
        permissions.filter {
            ActivityCompat.checkSelfPermission(activity.applicationContext, it) != PackageManager.PERMISSION_GRANTED
        }.associateWith {
            false
        }.toMap(requiredPermissions)

        return if (requiredPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, requiredPermissions.keys.toTypedArray(), 1)
            Log.i(TAG, "Permissions requested: ${requiredPermissions.keys.joinToString()}")
            Log.i(DataCollectionService.TAG, "Awaiting count down, count: ${latch.count}")
            latch.await()
            Log.i(DataCollectionService.TAG, "Verifying permissions granted")
            requiredPermissions.values.all { it }
        } else true
    }

    fun onPermsResult(permissions: Array<out String>, grantResults: IntArray) {
        val results = permissions.toList().zip(grantResults.toList())
        Log.i(TAG, results.joinToString())
        results.forEach {
            requiredPermissions[it.first] = (it.second == PackageManager.PERMISSION_GRANTED)
        }
        Log.i(TAG, "Counting down: ${requiredPermissions.entries.joinToString()}")
        latch.countDown()
        Log.i(TAG, "Count down complete, count: ${latch.count}")
    }

    companion object {
        private const val TAG = "PS"

        private val permissions = listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

}
