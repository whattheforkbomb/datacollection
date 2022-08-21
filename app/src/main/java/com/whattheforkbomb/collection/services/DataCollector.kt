package com.whattheforkbomb.collection.services

/**
 * Denoting a type of data to be collected
 */
interface DataCollector { // interface?

     fun setup(onReadyCallback: (setupSuccessful: Boolean) -> Unit)
     fun start(rootDir: String): Boolean // return whether able to start collection, or have as callback? pass rootDir as Path?
     fun stop(onStoppedCallback: (stopSuccessful: Boolean) -> Unit): Boolean // return whether able to stop collection, or have as callback?

}
