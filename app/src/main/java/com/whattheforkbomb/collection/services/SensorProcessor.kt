package com.whattheforkbomb.collection.services

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.util.Log
import com.whattheforkbomb.collection.data.*
import java.util.concurrent.Executor
import kotlin.concurrent.fixedRateTimer

class SensorProcessor(private val sensorManager: SensorManager, private val executor: Executor) : DataCollector<PhonePositionalData>() {

    val samplingRate = (1e6/60).toInt() // 60 times a second (1/60) scaled to microseconds (1e6)

    @Volatile private var started = false

    private val TAG = "SP"

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

    private val handler: Handler = Handler()

    private fun registerListener(assignValue: (values: FloatArray) -> Unit, sensor: Sensor): Boolean {
        return sensorManager.registerListener(object: SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null)
                    assignValue(event.values)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(TAG, "accuracy changed")
            }

        }, sensor, samplingRate)
    }

    //
    override fun onStart(
        dataReceivedCallback: (PhonePositionalData) -> Unit,
        startupCallback: (success: Boolean, reason: String?) -> Unit
    ) {
        linearAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        rotationalVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rawAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)
        rawGyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)

        started = registerDataReceivedCallback(dataReceivedCallback)
        fixedRateTimer("PushLatestPosData", true, 0L, (samplingRate/1000).toLong()) {
            val data = PhonePositionalData(
                LinearAccelerationVector(linearAccelerationVector[0], linearAccelerationVector[1], linearAccelerationVector[2]),
                RotationVector(rotationVector[0], rotationVector[1], rotationVector[2], rotationVector[3]),
                AccelerometerVector(accelerationVector[0], accelerationVector[1], accelerationVector[2]),
                GyroscopeVector(gyroVector[0], gyroVector[1], gyroVector[2]),
                RawAccelerometerVectors(Vector3D(rawAccelerationVector[0], rawAccelerationVector[1], rawAccelerationVector[2]), Vector3D(rawAccelerationVector[3], rawAccelerationVector[4], rawAccelerationVector[5])),
                RawGyroscopeVectors(Vector3D(rawGyroVector[0], rawGyroVector[1], rawGyroVector[2]), Vector3D(rawGyroVector[3], rawGyroVector[4], rawGyroVector[5])),
            )
            dataReceivedCallback(data)
        }
        startupCallback(started, null)
    }



    override fun registerDataReceivedCallback(dataReceivedCallback: (PhonePositionalData) -> Unit): Boolean {
        return registerListener({linearAccelerationVector = it}, linearAccSensor) &&
            registerListener({rotationVector = it}, rotationalVectorSensor) &&
            registerListener({accelerationVector = it}, accelerationSensor) &&
            registerListener({gyroVector = it}, gyroSensor) &&
            registerListener({rawAccelerationVector = it}, rawAccelerationSensor) &&
            registerListener({rawGyroVector = it}, rawGyroSensor)
    }

}
