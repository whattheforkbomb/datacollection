package com.whattheforkbomb.collection.services

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.util.Log
import com.whattheforkbomb.collection.data.*
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.concurrent.timerTask

class SensorProcessor(private val sensorManager: SensorManager) : DataCollector {

    private var fileWriter: FileWriter? = null
    @Volatile private var started = false

    private lateinit var linearAccSensor: Sensor
    private lateinit var rotationalVectorSensor: Sensor
    private lateinit var accelerationSensor: Sensor
    private lateinit var gyroSensor: Sensor
    private lateinit var rawAccelerationSensor: Sensor
    private lateinit var rawGyroSensor: Sensor

    @Volatile private var linearAccelerationVector: FloatArray = FloatArray(3) { 0f }
    @Volatile private var rotationVector: FloatArray = FloatArray(4) { 0f }
    @Volatile private var accelerationVector: FloatArray = FloatArray(3) { 0f }
    @Volatile private var gyroVector: FloatArray = FloatArray(3) { 0f }
    @Volatile private var rawAccelerationVector: FloatArray = FloatArray(6) { 0f }
    @Volatile private var rawGyroVector: FloatArray = FloatArray(6) { 0f }

    @Volatile private var closing = false

    private var timer: Timer? = null

    private fun registerListener(assignValue: (values: FloatArray) -> Unit, sensor: Sensor): Boolean {
        return sensorManager.registerListener(object: SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null)
                    assignValue(event.values)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(TAG, "accuracy changed")
            }

        }, sensor, SAMPLING_RATE)
    }

    private fun registerListeners(): Boolean {
        return registerListener({linearAccelerationVector = it}, linearAccSensor) &&
            registerListener({rotationVector = it}, rotationalVectorSensor) &&
            registerListener({accelerationVector = it}, accelerationSensor) &&
            registerListener({gyroVector = it}, gyroSensor) &&
            registerListener({rawAccelerationVector = it}, rawAccelerationSensor) &&
            registerListener({rawGyroVector = it}, rawGyroSensor)
    }

    override fun setup(onReadyCallback: (setupSuccessful: Boolean) -> Unit) {
        linearAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        rotationalVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rawAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)
        rawGyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        val success = registerListeners()
        onReadyCallback(success)
    }

    override fun start(rootDir: String): Boolean {
        closing = false
        val csvFile = File(Paths.get(rootDir, FILE_NAME).toUri())
        if (csvFile.exists()) {
            csvFile.delete()
        }
        csvFile.createNewFile()
        val fw = FileWriter(csvFile)
        fw.appendLine(PhonePositionalData.HEADER)
        fileWriter = fw
        val timerTask: TimerTask.() -> Unit = {
            if (!closing) {
                val data = PhonePositionalData(
                    LinearAccelerationVector(
                        linearAccelerationVector[0],
                        linearAccelerationVector[1],
                        linearAccelerationVector[2]
                    ),
                    RotationVector(
                        rotationVector[0],
                        rotationVector[1],
                        rotationVector[2],
                        rotationVector[3]
                    ),
                    AccelerometerVector(
                        accelerationVector[0],
                        accelerationVector[1],
                        accelerationVector[2]
                    ),
                    GyroscopeVector(gyroVector[0], gyroVector[1], gyroVector[2]),
                    RawAccelerometerVectors(
                        Vector3D(
                            rawAccelerationVector[0],
                            rawAccelerationVector[1],
                            rawAccelerationVector[2]
                        ),
                        Vector3D(
                            rawAccelerationVector[3],
                            rawAccelerationVector[4],
                            rawAccelerationVector[5]
                        )
                    ),
                    RawGyroscopeVectors(
                        Vector3D(
                            rawGyroVector[0],
                            rawGyroVector[1],
                            rawGyroVector[2]
                        ), Vector3D(rawGyroVector[3], rawGyroVector[4], rawGyroVector[5])
                    ),
                )
                val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS")
                timestamp.timeZone = TimeZone.getTimeZone("UTC")
                fw.appendLine(data.toCSV(timestamp.format(Date())))
                fw.flush()
            } else {
                fileWriter?.close()
            }
        }
        timer = fixedRateTimer("PushLatestPosData", true, 0L, (SAMPLING_RATE/1000).toLong(), timerTask)
        return true
    }

    override fun stop(): Boolean {
        closing = true
        timer?.cancel()
        return true
    }

    companion object {
        const val TAG = "SP"
        const val FILE_NAME = "IMU_GYRO_DATA.csv"
        private const val SAMPLING_RATE = (1e6/50).toInt() // 60 times a second (1/60) scaled to microseconds (1e6)
    }
}
