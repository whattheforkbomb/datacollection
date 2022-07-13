package com.whattheforkbomb.collection.services

import android.app.Activity
import io.esense.esenselib.*

class EarableProcessor {
    // TODO: Create something that connects to earable, and handles binding the data collection listeners/callbacks
    @Volatile private var range = 0.0

    private fun createConnectionListener() = EarableConnectionListener { range = it }
}

class EarableConnectionListener(private val callback: (range: Double) -> Unit) : ESenseConnectionListener {
    private var deviceStatus = ""
    override fun onDeviceFound(manager: ESenseManager) {
        deviceStatus = "device found"
        println(deviceStatus)
    }

    override fun onDeviceNotFound(manager: ESenseManager) {
        deviceStatus = "device not found"
        println(deviceStatus)
    }

    override fun onConnected(manager: ESenseManager) {
        deviceStatus = "device connected"
        println(deviceStatus)
        // you can only listen to sensor data after a device has been connected
        val eSenseSensorListener = EarableSensorListener()
        // start listening to earable sensor data
//        earableManager.registerSensorListener(eSenseSensorListener, 100)
//        mSocket.emit("esense side connect", sideString)

        // set current eSense configuration so that acceleration scale factor (called 'range') should be 8192 LSB/g (default value)
//        val eSenseEventListener = EarableEventListener()
//        earableManager.registerEventListener(eSenseEventListener)
        val config = ESenseConfig()
//        earableManager.setSensorConfig(config)
//        println("range: $range")
        callback(config.accSensitivityFactor)
    }

    override fun onDisconnected(manager: ESenseManager) {
        deviceStatus = "device not connected"
        println(deviceStatus)
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

// collect eSense sensor data
internal class EarableSensorListener : ESenseSensorListener {
    //private float timestamp;
    //Called when there is a new eSense sensor event (e.g. every time when eSense accelerometer data has changed)
    override fun onSensorChanged(evt: ESenseEvent) {
        val accelValues = evt.accel
        val gyroValues = evt.gyro
        val timestamp = evt.timestamp

        //getting new eSense data is a background task, move updating the UI onto main thread
//        Activity.runOnUiThread(Runnable { //updates the UI:
//            setEarableDataView(accelValues)
//        })
//        if (recording) {
//            //timestamp,acc_x,acc_y,acc_z,gyro_x,_gyro_y,gyro_z
//            attemptSendESense(timestamp.toString() + "," + accelValues[0] + "," + accelValues[1] + "," + accelValues[2] + "," + gyroValues[0] + "," + gyroValues[1] + "," + gyroValues[2])
//            attemptSendESenseConverted(
//                "$timestamp," + accelerometerDataConversion(
//                    accelValues[0]
//                ) + "," + accelerometerDataConversion(accelValues[1]) + "," + accelerometerDataConversion(
//                    accelValues[2]
//                )
//            )
//        }
    }
}
