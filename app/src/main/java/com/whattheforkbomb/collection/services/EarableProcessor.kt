package com.whattheforkbomb.collection.services

import android.content.Context
import android.util.Log
import com.whattheforkbomb.collection.data.ESenseEvent
import io.esense.esenselib.ESenseConfig
import io.esense.esenselib.ESenseConnectionListener
import io.esense.esenselib.ESenseManager
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths

class EarableProcessor(private val appContext: Context) : DataCollector {

    @Volatile private var range = 0.0
    private var eSenseManager: ESenseManager? = null
    private var fileWriter: FileWriter? = null
    private var esenseConfig: ESenseConfig? = null
    @Volatile private var ready: Boolean = false
    @Volatile private var writing = false
    @Volatile private var closing = false

    override fun setup(onReadyCallback: (setupSuccessful: Boolean) -> Unit) {
        val listener = object : ESenseConnectionListener {
            override fun onDeviceFound(manager: ESenseManager) {}

            override fun onDeviceNotFound(manager: ESenseManager) {
                Log.e(TAG, "Unable to find device: $EARABLE_DEVICE_NAME")
                ready = false
                onReadyCallback(ready)
            }

            override fun onConnected(manager: ESenseManager) {
                Log.i(TAG, "Connected to device: $EARABLE_DEVICE_NAME")
                val config = ESenseConfig()
                manager.setSensorConfig(config)
                range = config.accSensitivityFactor
                esenseConfig = config
                eSenseManager = manager
                ready = true
                onReadyCallback(ready)
            }

            override fun onDisconnected(manager: ESenseManager) {
                Log.i(TAG, "For some reason being disconnected")
                onReadyCallback(ready)
            }
        }

        val manager = ESenseManager(EARABLE_DEVICE_NAME, appContext, listener)
        val earableDevice = manager.bluetoothManager.adapter.bondedDevices.firstOrNull { EARABLE_DEVICE_NAME == it.name }

        if (earableDevice != null ) {
            // already connected to phone, try opening communication
            manager.connect(earableDevice)
        } else {
            // not already connected,
            manager.connect(CONNECTION_TIMEOUT)
        }

        eSenseManager?.registerSensorListener({ eSenseEvent: ESenseEvent ->
//            Log.d(TAG, "ESense Event received.")
            if (!closing) {
                if (writing) {
                    fileWriter!!.appendLine(eSenseEvent.toCSV(esenseConfig!!))
                    fileWriter!!.flush()
                }
            } else {
                fileWriter?.close()
                writing = false
            }
        }, SAMPLING_RATE)
    }

    override fun start(rootDir: String): Boolean {
        closing = false
        Log.i(TAG, "Starting ESense earable processor")
        val csvFile = File(Paths.get(rootDir, FILE_NAME).toUri())
        if (csvFile.exists()) {
            csvFile.delete()
        }
        csvFile.createNewFile()
        fileWriter = FileWriter(csvFile)
        fileWriter!!.appendLine(ESenseEvent.HEADER)
        writing = true

        return true
    }

    override fun stop(onStoppedCallback: (stopSuccessful: Boolean) -> Unit): Boolean {
        eSenseManager?.unregisterSensorListener()
        closing = true

        return true
    }

    companion object {
        const val TAG = "EP"
        private const val EARABLE_DEVICE_NAME = "eSense-0467" //"eSense-1635"
        private const val FILE_NAME = "ESENSE_IMU_GYRO_DATA.csv"
        private const val CONNECTION_TIMEOUT = 60 * 1000 // 1min
        private const val SAMPLING_RATE = 50 // 60 times a second / Hz
    }
}
