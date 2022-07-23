package com.whattheforkbomb.collection.data

// helper
data class Vector3D(val x: Float, val y: Float, val z: Float)

// aliases for above
typealias LinearAccelerationVector = Vector3D
typealias AccelerometerVector = Vector3D
typealias GyroscopeVector = Vector3D

// composed data
data class RotationVector(val x: Float, val y: Float, val z: Float, val scalar: Float) // Quaternion Rotational Components
data class RawAccelerometerVectors(val rawOutput: Vector3D, val estimatedDrift: Vector3D)
data class RawGyroscopeVectors(val rawOutput: Vector3D, val estimatedDrift: Vector3D)

// data to store regarding phone
data class PhonePositionalData(
    // Lead with most pre-processed data (linear acceleration and device rotation)
    val linearAcceleration: LinearAccelerationVector,
    val rotation: RotationVector,
    // Follow with filtered / bias-adjusted rotation and acceleration
    val acceleration: AccelerometerVector,
    val gyroscope: GyroscopeVector,
    // Finally the raw data with estimated drift
    val rawAccelerometer: RawAccelerometerVectors,
    val rawGyroscope: RawGyroscopeVectors
) {
    fun toCSV(timeStamp: String): String =
        "$timeStamp,${linearAcceleration.x},${linearAcceleration.y},${linearAcceleration.z},${rotation.x},${rotation.y},${rotation.z},${rotation.scalar}," +
                "${acceleration.x},${acceleration.y},${acceleration.z},${gyroscope.x},${gyroscope.y},${gyroscope.z}" +
                "${rawAccelerometer.rawOutput.x},${rawAccelerometer.rawOutput.y},${rawAccelerometer.rawOutput.z},${rawAccelerometer.estimatedDrift.x},${rawAccelerometer.estimatedDrift.y},${rawAccelerometer.estimatedDrift.z}" +
                "${rawGyroscope.rawOutput.x},${rawGyroscope.rawOutput.y},${rawGyroscope.rawOutput.z},${rawGyroscope.estimatedDrift.x},${rawGyroscope.estimatedDrift.y},${rawGyroscope.estimatedDrift.z}"

    companion object {
        val HEADER = "TIME_STAMP,LINEAR_X,LINEAR_Y,LINEAR_Z,ROTATION_X,ROTATION_Y,ROTATION_Z,ROTATION_SCALAR," +
                "ACCELERATION_X,ACCELERATION_Y,ACCELERATION_Z,GYROSCOPE_X,GYROSCOPE_Y,GYROSCOPE_Z," +
                "RAW_ACCELERATION_X,RAW_ACCELERATION_Y,RAW_ACCELERATION_Z,RAW_ACCELERATION_X_DRIFT,RAW_ACCELERATION_Y_DRIFT,RAW_ACCELERATION_Z_DRIFT," +
                "RAW_GYROSCOPE_X,RAW_GYROSCOPE_Y,RAW_GYROSCOPE_Z,RAW_GYROSCOPE_X_DRIFT,RAW_GYROSCOPE_Y_DRIFT,RAW_GYROSCOPE_Z_DRIFT"
    }
}
