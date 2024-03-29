package com.whattheforkbomb.collection.services

import android.app.Activity
import android.util.Log
import com.whattheforkbomb.collection.fragments.DataCollectionFragment
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString

class DataCollectionService private constructor(private val rootDir: Path, private val dataCollectors: Set<DataCollector>, private val activity: Activity) {

    private var participantId: Int? = null
    private lateinit var filePath: Path
    var ready: Boolean = false
        private set
    @Volatile private var running = false
    val permissionsService = PermissionsService()

//    init {
//        generateNewParticipantId()
//    }

    fun getParticipantId(): Int? {
        return participantId
    }

    fun setParticipantId(id: Int): Int { // call on app reset?
        participantId = id
        filePath = Paths.get(rootDir.pathString, participantId.toString())
        filePath.toFile().mkdirs()
        return participantId!!
    }

    /* TODO:
     *   - Contain all the data processing classes
     *   - Expose an 'onReady' callback
     *   - On creation attempt to initialise all data collectors
     *   - Expose builder to select the data collectors?
     *   - trigger start and stop of data collectors
     *   - manage where data is saved (e.g. parent DIR for the data)
     *   - Need Executor to setup (and run?) collectors
     */

    // TODO: Save down to session wide file when start capturing, both time since shake, and time since app startup?
    fun start(repetitions: Int, motion: DataCollectionFragment.Companion.Motions, gridPoint: DataCollectionFragment.Companion.GridPoints) {
        if (!running) {
            running = true
            val path = Paths.get(filePath.pathString, "attempt_$repetitions", motion.name, gridPoint.name)
            path.toFile().mkdirs()
            dataCollectors.parallelStream().forEach { it.start(path.pathString) } // need parallel?
        } else {
            Log.e(TAG, "Already running")
        }
    }

    fun stop(onStoppedCallback: (Boolean) -> Unit) = runBlocking {
        val latch = CountDownLatch(dataCollectors.size)
        val success = ConcurrentHashMap<DataCollector, Boolean>()

        dataCollectors.parallelStream().forEach { collector ->
            Log.i(TAG, "Stopping ${collector.javaClass.name}...")
            collector.stop {
                success[collector] = true
                Log.i(TAG, "Countdown ${collector.javaClass.name}...")
                latch.countDown()
            }
        }
        try {
            latch.await(15, TimeUnit.SECONDS)
        } catch (iex: InterruptedException) {
            // Failure, who would interrupt here?
        }
        Log.i(TAG, success.entries.joinToString(" | "))
        val stopped = dataCollectors.stream().map { success.getOrDefault(it, false) }.allMatch { it }
        running = false
        onStoppedCallback(stopped)
    }

    fun setup(onReady: (setupSuccess: Boolean) -> Unit) = runBlocking {
        val latch = CountDownLatch(dataCollectors.size)
        val success = ConcurrentHashMap<DataCollector, Boolean>()

        if (permissionsService.checkOrGetPerms(activity)) {
            dataCollectors.parallelStream().forEach { collector ->
                Log.i(TAG, "Starting ${collector.javaClass.name}...")
                collector.setup {
                    success[collector] = it
                    Log.i(TAG, "Countdown ${collector.javaClass.name}...")
                    latch.countDown()
                }
            }
            try {
                latch.await(10, TimeUnit.SECONDS)
            } catch (iex: InterruptedException) {
                // Failure, who would interrupt here?
            }
            Log.i(TAG, success.entries.joinToString(" | "))
            ready = dataCollectors.stream().map { success.getOrDefault(it, false) }.allMatch { it }
        } else {
            Log.e(TAG, "Not all permissions were provided.")

            ready = false
        }
        Log.i(TAG, "ready: $ready")
        onReady(ready)
    }

    class Builder(private val rootDir: Path, private val dataCollectors: Set<DataCollector>, private val activity: Activity) {
        constructor(rootDir: Path, activity: Activity): this(rootDir, HashSet(), activity)

        fun registerDataCollector(dataCollector: DataCollector) = Builder(rootDir, dataCollectors + dataCollector, activity)

        fun build(): DataCollectionService = DataCollectionService(rootDir, dataCollectors, activity)

    }

    companion object {
        const val TAG = "DCS"
    }

}
