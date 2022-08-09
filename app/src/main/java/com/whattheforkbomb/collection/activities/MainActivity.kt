package com.whattheforkbomb.collection.activities

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.databinding.ActivityMainBinding
import com.whattheforkbomb.collection.services.*
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel
//import org.opencv.android.BaseLoaderCallback
//import org.opencv.android.LoaderCallbackInterface
//import org.opencv.android.OpenCVLoader
import java.nio.file.Paths
import kotlin.io.path.pathString

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val model: DataCollectionViewModel by viewModels()
    private val longRunningSensorRecorder by lazy {
        SensorProcessor(getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val filePath = applicationContext.getExternalFilesDir(null)!!.toPath()
        model.rootDir = filePath
        model.dataCollectionService = DataCollectionService.Builder(filePath, this)
            .registerDataCollector(CameraProcessor(applicationContext))
            .registerDataCollector(SensorProcessor(getSystemService(Context.SENSOR_SERVICE) as SensorManager))
            .registerDataCollector(EarableProcessor(applicationContext))
            .build()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        longRunningSensorRecorder.start(Paths.get(filePath.pathString, model.dataCollectionService.getParticipantId().toString()).pathString)
//        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, applicationContext, object : BaseLoaderCallback(this) {
//            override fun onManagerConnected(status: Int) {
//                when (status) {
//                    LoaderCallbackInterface.SUCCESS -> {
//                        Log.i("OpenCV", "OpenCV loaded successfully")
//                    }
//                    else -> {
//                        super.onManagerConnected(status)
//                    }
//                }
//            }
//        })
        setSupportActionBar(binding.toolbar)
        actionBar?.setDisplayHomeAsUpEnabled(false)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
//        appBarConfiguration = AppBarConfiguration(navController.graph)
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.Researcher, R.id.Welcome, R.id.Collection, R.id.Finish
        ))
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "Permission request result received")
        model.dataCollectionService.permissionsService.onPermsResult(permissions, grantResults)
    }

    companion object {
//        init {
//            System.loadLibrary("opencv_java")
//        }
        private const val TAG = "MA"
    }
}
