package com.whattheforkbomb.collection.services

import android.media.Image

/**
 * Denoting a type of data to be collected
 */
abstract class DataCollector<T> {

    fun start(startupCallback: (success: Boolean, reason: String?) -> Unit, dataReceivedCallback: (T) -> Unit) = onStart(dataReceivedCallback, startupCallback)

    protected abstract fun onStart(dataReceivedCallback: (T) -> Unit, startupCallback: (success: Boolean, reason: String?) -> Unit)

    abstract fun registerDataReceivedCallback(dataReceivedCallback: (T) -> Unit): Boolean

    // onStop

}
