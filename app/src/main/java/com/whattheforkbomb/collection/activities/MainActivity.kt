package com.whattheforkbomb.collection.activities

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.View.INVISIBLE
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

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val model: DataCollectionViewModel by viewModels()

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
        Log.i("MA", "Permission request result received")
        model.dataCollectionService.permissionsService.onPermsResult(permissions, grantResults)
    }
}
