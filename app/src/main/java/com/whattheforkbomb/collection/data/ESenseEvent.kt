package com.whattheforkbomb.collection.data

import io.esense.esenselib.ESenseConfig
import java.text.SimpleDateFormat
import java.util.*

data class ESenseEvent (
    var accel: ShortArray = ShortArray(3), //3-elements array with X, Y and Z axis for accelerometer
    var gyro: ShortArray = ShortArray(3) //3-elements array with X, Y and Z axis for gyroscope
) {
    var timestamp : Long = 0 //phone's timestamp
    var packetIndex = 0

    /**
     * Converts current ADC accelerometer values to acceleration in g
     * @param config device configuration
     * @return acceleration in g on X, Y and Z axis
     */
    fun convertAccToG(config: ESenseConfig): DoubleArray {
        val data = DoubleArray(3)
        for (i in 0..2) {
            data[i] = accel[i] / config.accSensitivityFactor
        }
        return data
    }

    /**
     * Converts current ADC gyroscope values to rotational speed in degrees/second
     * @param config device configuration
     * @return rotational speed in deg/s on X, Y and Z axis
     */
    fun convertGyroToDegPerSecond(config: ESenseConfig): DoubleArray {
        val data = DoubleArray(3)
        for (i in 0..2) {
            data[i] = gyro[i] / config.gyroSensitivityFactor
        }
        return data
    }

    fun toCSV(config: ESenseConfig): String {
        val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS")
        timestampFormat.timeZone = TimeZone.getTimeZone("UTC")
        timestampFormat.format(Date(timestamp))
        val accelG = convertAccToG(config)
        val gyroDegrees = convertGyroToDegPerSecond(config)
        return "${timestampFormat.format(Date(timestamp))},${accel[0]},${accel[1]},${accel[2]},${gyro[0]},${gyro[1]},${gyro[2]},${accelG[0]},${accelG[1]},${accelG[2]},${gyroDegrees[0]},${gyroDegrees[1]},${gyroDegrees[2]}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ESenseEvent

        if (!accel.contentEquals(other.accel)) return false
        if (!gyro.contentEquals(other.gyro)) return false
        if (timestamp != other.timestamp) return false
        if (packetIndex != other.packetIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accel.contentHashCode()
        result = 31 * result + gyro.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + packetIndex
        return result
    }

    companion object {
        const val HEADER = "TIME_STAMP,RAW_ACCELERATION_X,RAW_ACCELERATION_Y,RAW_ACCELERATION_Z,RAW_GYROSCOPE_X,RAW_GYROSCOPE_Y,RAW_GYROSCOPE_Z,ACCELERATION_X,ACCELERATION_Y,ACCELERATION_Z,GYROSCOPE_X,GYROSCOPE_Y,GYROSCOPE_Z"
    }
}
