package com.whattheforkbomb.collection.services

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.whattheforkbomb.collection.data.ESenseEvent
import io.esense.esenselib.ESenseConfig
import io.esense.esenselib.ESenseConnectionListener
import io.esense.esenselib.ESenseEventListener
import io.esense.esenselib.ESenseManager
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class EarableProcessor(appContext: Context) : DataCollector {

    @Volatile private var range = 0.0
    private var eSenseManager: ESenseManager? = null
    private var fileWriter: FileWriter? = null
    private var esenseConfig: ESenseConfig? = null
    @Volatile private var ready: Boolean = false
    private val latch = CountDownLatch(1)

    init {
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = mBluetoothAdapter.bondedDevices

        val s: MutableList<String> = ArrayList()
        for (bt in pairedDevices) s.add(bt.name)
        Log.i(TAG, "Bluetooth devices:\n${s.joinToString(",")}")
        val listener = object : ESenseConnectionListener {
            //
            override fun onDeviceFound(manager: ESenseManager) {
                Log.i(TAG, "Device: $EARABLE_DEVICE_NAME found")
            }

            override fun onDeviceNotFound(manager: ESenseManager) {
                Log.e(TAG, "Unable to find device: $EARABLE_DEVICE_NAME")
                ready = false
                latch.countDown()
            }

            override fun onConnected(manager: ESenseManager) {
                Log.i(TAG, "Connected to device: $EARABLE_DEVICE_NAME")
                val config = ESenseConfig()
                manager.setSensorConfig(config)
                range = config.accSensitivityFactor
                esenseConfig = config
                ready = true
                latch.countDown()
            }

            override fun onDisconnected(manager: ESenseManager) {
                //
            }
        }
        val manager = ESenseManager(EARABLE_DEVICE_NAME, appContext, listener)
        manager.connect(CONNECTION_TIMEOUT)
    }

    override fun setup(onReadyCallback: (setupSuccessful: Boolean) -> Unit) {
        latch.await(9, TimeUnit.SECONDS)
        onReadyCallback(ready)
    }

    override fun start(rootDir: String): Boolean {
        Log.i(TAG, "Starting ESense earable processor")
        val csvFile = File(Paths.get(rootDir, FILE_NAME).toUri())
        csvFile.createNewFile()
        val fw = FileWriter(csvFile)
        fw.appendLine(ESenseEvent.HEADER)
        fileWriter = fw
        eSenseManager?.registerSensorListener({ eSenseEvent: ESenseEvent ->
            Log.i(TAG, "ESense Event received.")
            fw.appendLine(eSenseEvent.toCSV(esenseConfig!!))
        }, SAMPLING_RATE)

        return true
    }

    override fun stop(): Boolean {
        eSenseManager?.unregisterSensorListener()
        fileWriter?.close()
        return true
    }

    companion object {
        const val TAG = "EP"
        private const val EARABLE_DEVICE_NAME = "eSense-1635"
        private const val FILE_NAME = "ESENSE_IMU_GYRO_DATA.csv"
        private const val CONNECTION_TIMEOUT = 60 * 1000 // 1min
        private const val SAMPLING_RATE = 60 // 60 times a second / Hz
    }
}

class EarableEventListener : ESenseEventListener {
    override fun onBatteryRead(voltage: Double) {}
    override fun onButtonEventChanged(pressed: Boolean) {}
    override fun onAdvertisementAndConnectionIntervalRead(
        minAdvertisementInterval: Int,
        maxAdvertisementInterval: Int,
        minConnectionInterval: Int,
        maxConnectionInterval: Int
    ) {
    }

    override fun onDeviceNameRead(deviceName: String) {}
    override fun onSensorConfigRead(config: ESenseConfig) {
        // accelerometer scale factor, needed for conversion of acceleration data in raw ADC format to m/s^2
        val range = config.accSensitivityFactor
//        println("range: $range")
    }

    override fun onAccelerometerOffsetRead(offsetX: Int, offsetY: Int, offsetZ: Int) {}
}
