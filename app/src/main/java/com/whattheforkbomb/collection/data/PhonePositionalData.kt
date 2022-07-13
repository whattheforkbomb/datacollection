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
)
