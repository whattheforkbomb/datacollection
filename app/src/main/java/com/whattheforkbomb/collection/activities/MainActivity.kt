package com.whattheforkbomb.collection.activities

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.databinding.ActivityMainBinding
import com.whattheforkbomb.collection.services.CameraProcessor
import com.whattheforkbomb.collection.services.DataCollectionService
import com.whattheforkbomb.collection.services.EarableProcessor
import com.whattheforkbomb.collection.services.SensorProcessor
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

//        val permissionRequestLauncher: (CountDownLatch) -> ActivityResultLauncher<String> = { latch: CountDownLatch ->
//            registerForActivityResult(
//                ActivityResultContracts.RequestPermission()
//            ) { isGranted: Boolean ->
//                if (isGranted) {
//                    // Permission is granted. Continue the action or workflow in your
//                    // app.
//                    Log.i("MA", "BLUETOOTH CONNECT perm given")
//                    latch.countDown()
//                } else {
//                    // Explain to the user that the feature is unavailable because the
//                    // features requires a permission that the user has denied. At the
//                    // same time, respect the user's decision. Don't link to system
//                    // settings in an effort to convince the user to change their
//                    // decision.
//                    Log.i("MA", "BLUETOOTH CONNECT perm NOT given")
//                    latch.countDown()
//                }
//            }
//        }
//
//        Thread {
//            listOf(
////                android.Manifest.permission.CAMERA,
////                android.Manifest.permission.BLUETOOTH_CONNECT,
//                android.Manifest.permission.BLUETOOTH_SCAN,
//                android.Manifest.permission.BLUETOOTH_ADVERTISE,
////                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
//            ).forEach {
//                val latch = CountDownLatch(1)
//                Thread {
//                    permissionRequestLauncher(latch).launch(it)
//                }.run()
//                latch.await(5, TimeUnit.SECONDS)
//            }
//            runOnUiThread {
//               onPermissionsReady()
//            }
//        }.run()
        onPermissionsReady()

    }

    fun onPermissionsReady() {
        val model: DataCollectionViewModel by viewModels()
        val filePath = applicationContext.getExternalFilesDir(null)!!.toPath()
        model.dataCollectionService = DataCollectionService.Builder(filePath)
            .registerDataCollector(CameraProcessor(applicationContext))
            .registerDataCollector(SensorProcessor(getSystemService(Context.SENSOR_SERVICE) as SensorManager))
            .registerDataCollector(EarableProcessor(applicationContext))
            .build()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
