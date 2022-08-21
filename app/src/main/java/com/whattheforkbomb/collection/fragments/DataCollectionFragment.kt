package com.whattheforkbomb.collection.fragments

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.withCreated
import androidx.navigation.fragment.findNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.data.Instructions
import com.whattheforkbomb.collection.data.TimeRemaining
import com.whattheforkbomb.collection.databinding.FragmentDataCollectionBinding
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.concurrent.fixedRateTimer
import kotlin.io.path.pathString
import kotlin.math.ceil
import kotlin.math.sqrt


/**
 * A simple [Fragment] subclass.
 * Use the [DataCollectionFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class DataCollectionFragment : Fragment() {
    private lateinit var instructionsView: InstructionsView
    private var _binding: FragmentDataCollectionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var repetitions = 0

    private val model: DataCollectionViewModel by activityViewModels()
    private val executor: Executor = Executors.newSingleThreadExecutor()

    private var previousGridPoints: List<Pair<GridPoints, Instructions>> = listOf()
    private var selectedMotion: Motions = DEFAULT_MOTION
    private var remainingGridPoints: List<Pair<GridPoints, Instructions>> = listOf()
    private var faceDetectionEnabled: Boolean = false
    private var recording = false
    private lateinit var timer: CountDownTimer
    @Volatile private var animationTimer: Timer? = null
    @Volatile private var pendingShake = false
    @Volatile private var pendingSecondShake = false
    private var readyToRecord: Boolean = false // Replace with variable binding from fragment
        set(value) {
            if (value && !binding.buttonNext.isEnabled) {
                // Need some indicator that ready to record?
            }
            field = value
        }

    private lateinit var shakeDetectorFilePath: File
    // TODO: check that direction changed twice with threshold exceeded within 1-2sec(?), not just once.
    private val shakeDetector = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (pendingShake && shakeDetected(event!!.values)) {
                val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS")
                timestampFormat.timeZone = TimeZone.getTimeZone("UTC")
                val timestamp = timestampFormat.format(Date())
                FileWriter(shakeDetectorFilePath, true).use {
                    it.appendLine("$timestamp,${event.values[0]},${event.values[1]},${event.values[2]}")//,${event.values[3]},${event.values[4]},${event.values[0]}")
                    it.flush()
                }
                pendingShake = false
                binding.roundStart.visibility = INVISIBLE
                reset(DEFAULT_MOTION)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}// N/A
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometerSensor: Sensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        faceDetectionEnabled = arguments?.getBoolean(resources.getString(R.string.face_detection)) ?: false
        val onBackCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!recording) {
                    Log.i(TAG, "Remaining: ${remainingGridPoints.size}, Previous: ${previousGridPoints.size}")
                    if (remainingGridPoints.size < previousGridPoints.size) {
                        binding.progress.progress = binding.progress.progress - 1
                        remainingGridPoints = previousGridPoints.subList(previousGridPoints.size - remainingGridPoints.size - 1 , previousGridPoints.size)
                        binding.buttonNext.isEnabled = false
                        binding.buttonNext.text = "Record"
                        val layoutParams = FrameLayout.LayoutParams(binding.target.layoutParams)
                        layoutParams.gravity = remainingGridPoints[0].first.layoutGravity
                        binding.target.layoutParams = layoutParams
                        binding.target.visibility = VISIBLE

                        if (remainingGridPoints[0].first.animated) setAnimation(remainingGridPoints[0].first)

                        Log.i(TAG, "Current Target Position: ${remainingGridPoints[0]} - ${remainingGridPoints[0].first.layoutGravity}")
//                        parentFragmentManager.commit {
//                            val bundle = bundleOf(getString(R.string.motion_arg_name) to remainingGridPoints[0].second)
//                            replace<InstructionsView>(R.id.instructions_placeholder, args = bundle)
//                        }
                        instructionsView.updateInstructions(remainingGridPoints[0].second)
                    } else {
                        val previousMotion = Motions.values().singleOrNull {
                            it.getNext() == selectedMotion
                        }
                        if (previousMotion != null) {
                            animationTimer?.cancel()
                            animationTimer = null
                            binding.animatedTarget.visibility = INVISIBLE
                            reset(previousMotion)
                        }
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackCallback);
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        _binding = FragmentDataCollectionBinding.inflate(inflater, container, false)
        activity!!.actionBar?.title = "Data Collection"
        return binding.root
    }

    private fun setupDataCollection() {
        binding.timer.visibility = INVISIBLE
        binding.recordingState.setTextColor(Color.WHITE)
        binding.recordingState.text = "Press Record Button To Start"
        binding.recordingState.visibility = VISIBLE
        binding.target.scaleX = 1.0F
        binding.target.scaleY = 1.0F
        binding.target.visibility = VISIBLE

        if (::instructionsView.isInitialized) {
            instructionsView.updateInstructions(remainingGridPoints[0].second)
        } else {
            parentFragmentManager.commit {
                val bundle = bundleOf(getString(R.string.motion_arg_name) to remainingGridPoints[0].second)
                replace<InstructionsView>(R.id.instructions_placeholder, tag = "INSTRUCTIONS_FRAGMENT_TAG", args = bundle)
            }
            parentFragmentManager.addFragmentOnAttachListener { _, fragment ->
                if (fragment is InstructionsView) {
                    instructionsView = fragment
                }
            }
        }

        val layoutParams = FrameLayout.LayoutParams(binding.target.layoutParams)
        layoutParams.gravity = remainingGridPoints[0].first.layoutGravity
        Log.i(TAG, "Current Target Position: ${remainingGridPoints[0].first.name} - ${remainingGridPoints[0].first.layoutGravity}")
        binding.target.layoutParams = layoutParams

        if (remainingGridPoints[0].first.animated) setAnimation(remainingGridPoints[0].first)

        // Nav
        binding.buttonNext.isEnabled = true
        binding.buttonNext.setOnClickListener {
            startRecording()
            binding.buttonNext.setOnClickListener {
                timer.cancel()
                nextMotion()
            }
        }
    }

    private fun reset(motion: Motions, reverse: Boolean = false) {
        recording = false
        selectedMotion = motion
        remainingGridPoints = selectedMotion.getTargets()
        previousGridPoints = remainingGridPoints.toList()
        binding.progress.min = 0
        binding.progress.max = remainingGridPoints.size
        binding.progress.progress = 0
        if (reverse) {
            binding.progress.progress = remainingGridPoints.size - 1
            remainingGridPoints = listOf(remainingGridPoints.last())
        }

        activity!!.title = "Data Collection Stage ${selectedMotion.ordinal+1}/${Motions.values().size} - Attempt ${repetitions+1}/5"
        Log.i(TAG, "Remaining GridPoints: ${remainingGridPoints.joinToString()}")

        val currentInstructions = remainingGridPoints[0].second
        if (currentInstructions.alignmentGuideTextId != NO_ALIGNMENT_INSTRCUTIONS) {
            binding.alignmentImage.visibility = INVISIBLE
            binding.alignmentText.visibility = VISIBLE
            binding.dataCollection.visibility = INVISIBLE
            binding.alignmentGuide.visibility = VISIBLE
            binding.alignmentImage.setImageResource(currentInstructions.alignmentGuideImageId)
            binding.alignmentText.setText(currentInstructions.alignmentGuideTextId)

            binding.alignmentNext.setOnClickListener {
                if (binding.alignmentImage.visibility == INVISIBLE) {
                    binding.alignmentText.visibility = INVISIBLE
                    binding.alignmentImage.visibility = VISIBLE
                } else {
                    binding.alignmentGuide.visibility = INVISIBLE
                    binding.dataCollection.visibility = VISIBLE
                    setupDataCollection()
                }
            }
        } else {
            setupDataCollection()
        }
    }

    private fun nextMotion() {
        binding.buttonNext.isEnabled = false
        binding.buttonNext.text = "Record"
        recording=false
        model.dataCollectionService.stop({stopped: Boolean -> Log.i(TAG, "Collectors stopped $stopped")})
        binding.progress.progress = binding.progress.progress + 1
        binding.target.scaleX = 1.0F
        binding.target.scaleY = 1.0F
        binding.recordingState.setTextColor(Color.WHITE)
        if (remainingGridPoints.size == 1) {
            val nextMotion = selectedMotion.getNext()
            animationTimer?.cancel()
            animationTimer = null
            binding.animatedTarget.visibility = INVISIBLE
            if (nextMotion != null) {
                reset(nextMotion)
            } else if(repetitions < 5) {
                repetitions++
                pendingShake = true
                binding.roundStartText.text = "Round ${repetitions+1}/5\n\nPlease Shake The Phone To Begin"
                binding.roundStart.visibility = VISIBLE
                binding.dataCollection.visibility = INVISIBLE
            } else {
                findNavController().navigate(R.id.nav_to_finish)
            }
        } else {
            val layoutParams = FrameLayout.LayoutParams(binding.target.layoutParams)
            binding.timer.visibility = INVISIBLE
            binding.recordingState.text = "NEXT MOTION"
            binding.recordingState.visibility = VISIBLE
            binding.buttonNext.isEnabled = false
            binding.buttonNext.setOnClickListener {
                startRecording()
                binding.buttonNext.setOnClickListener {
                    timer.cancel()
                    nextMotion()
                }
            }
            getTimer(2) {
                binding.recordingState.text = "Press Record Button To Start"
                binding.buttonNext.isEnabled = true
            }.start()
            remainingGridPoints = remainingGridPoints.subList(1, remainingGridPoints.size)

            layoutParams.gravity = remainingGridPoints[0].first.layoutGravity
            binding.target.layoutParams = layoutParams
            binding.target.visibility = VISIBLE
            instructionsView.updateInstructions(remainingGridPoints[0].second)

            if (remainingGridPoints[0].first.animated) setAnimation(remainingGridPoints[0].first)

            Log.i(TAG, "Current Target Position: ${remainingGridPoints[0].first} - ${remainingGridPoints[0].first.layoutGravity}")
        }
    }

    private fun startRecording() {
        instructionsView.stopVideo()
        binding.recordingState.text = "RECORDING"
        binding.recordingState.setTextColor(Color.RED)

        recording = true
        model.dataCollectionService.start(repetitions, selectedMotion, remainingGridPoints[0].first)
        binding.buttonNext.isEnabled = false
        binding.buttonNext.text = "Stop Recording"
        timer = getTimer(1) {
            binding.buttonNext.isEnabled = true
            timer = getTimer(6) {
                Toast.makeText(
                    activity!!.applicationContext,
                    "Recording Stopped Automatically As Over 7 Seconds Had Elapsed.",
                    Toast.LENGTH_LONG
                ).show()
                nextMotion()
            }
            timer.start()
        }
        timer.start()
    }

    private fun getTimer(sec: Long, onFinishCallback: () -> Unit): CountDownTimer {
        binding.timeRemaining = TimeRemaining(0, sec)
        return object : CountDownTimer((sec * 1000), 200) {
            override fun onTick(millisUntilFinished: Long) {
//                Log.i(TAG, "Time Remaining (ms): $millisUntilFinished")
                binding.timeRemaining = TimeRemaining(0, ceil(millisUntilFinished / 1000.0).toLong())
            }

            override fun onFinish() = onFinishCallback()
        }
    }

    private fun shakeDetected(acceleration: FloatArray): Boolean {
        // https://jasonmcreynolds.com/?p=388
        val x = acceleration[0]
        val y = acceleration[1]
        val z = acceleration[2]

        val  gX = x / SensorManager.GRAVITY_EARTH
        val  gY = y / SensorManager.GRAVITY_EARTH
        val  gZ = z / SensorManager.GRAVITY_EARTH

        // gForce will be close to 1 when there is no movement.
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        return gForce > SHAKE_THRESHOLD
    }

    private fun setAnimation(gridPoints: GridPoints) {
//        animationTimer?.cancel()
//        when(gridPoints) {
//            GridPoints.ZOOM_IN -> {
//                binding.target.visibility = INVISIBLE
//                val maxScale = 3.0
//                animationTimer = fixedRateTimer("AnimationTimer", true, 0L, (400).toLong()) {
//                    val newScale = binding.target.scaleX + 0.5
//                    if (newScale <= maxScale) {
//                        activity!!.runOnUiThread {
//                            binding.target.scaleX = newScale.toFloat()
//                            binding.target.scaleY = newScale.toFloat()
//                        }
//                    } else {
//                        activity!!.runOnUiThread {
//                            binding.target.scaleX = 1.0.toFloat()
//                            binding.target.scaleY = 1.0.toFloat()
//                        }
//                    }
//                }
//            }
//
//            GridPoints.ZOOM_OUT -> {
//                binding.target.visibility = INVISIBLE
//                val maxScale = 3.0
//                animationTimer = fixedRateTimer("AnimationTimer", true, 0L, (400).toLong()) {
//                    val newScale = binding.target.scaleX - 0.5
//                    if (newScale >= 1.0) {
//                        activity!!.runOnUiThread {
//                            binding.target.scaleX = newScale.toFloat()
//                            binding.target.scaleY = newScale.toFloat()
//                        }
//                    } else {
//                        activity!!.runOnUiThread {
//                            binding.target.scaleX = maxScale.toFloat()
//                            binding.target.scaleY = maxScale.toFloat()
//                        }
//                    }
//                }
//            }
//
//            GridPoints.CLOCKWISE -> {
//                binding.animatedTarget.visibility = VISIBLE
//                val positions = POINTING_GRID_POINTS
//                var currentIdx = 0
//                val layoutParams =  FrameLayout.LayoutParams(binding.animatedTarget.layoutParams)
//                animationTimer = fixedRateTimer("AnimationTimer", true, 0L, (400).toLong()) {
//                    currentIdx = if (currentIdx < positions.size-1)
//                        currentIdx + 1
//                    else
//                        0
//                    layoutParams.gravity = positions[currentIdx].layoutGravity
//                    activity!!.runOnUiThread {
//                        binding.animatedTarget.layoutParams = layoutParams
//                    }
//                }
//            }
//
//            GridPoints.ANTI_CLOCKWISE -> {
//                binding.animatedTarget.visibility = VISIBLE
//                val positions = POINTING_GRID_POINTS.reversed()
//                var currentIdx = 0
//                val layoutParams =  FrameLayout.LayoutParams(binding.animatedTarget.layoutParams)
//                animationTimer = fixedRateTimer("AnimationTimer", true, 0L, (400).toLong()) {
//                    currentIdx = if (currentIdx < positions.size-1)
//                        currentIdx + 1
//                    else
//                        0
//                    layoutParams.gravity = positions[currentIdx].layoutGravity
//                    activity!!.runOnUiThread {
//                        binding.animatedTarget.layoutParams = layoutParams
//                    }
//                }
//            }
//
//            else -> {
//                Log.e(TAG, "There should be no animation for this")
//            }
//        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity!!.actionBar?.setDisplayHomeAsUpEnabled(false)
        shakeDetectorFilePath = Paths.get(model.rootDir.pathString, model.dataCollectionService.getParticipantId().toString(), "ShakeDetected.csv").toFile()
        FileWriter(shakeDetectorFilePath, true).use {
            it.appendLine("TIMESTAMP,X_RAW,Y_RAW,Z_RAW,X,Y,Z")
            it.flush()

        }
        Log.i(TAG, "Motion: $selectedMotion")
        readyToRecord = model.dataCollectionService.ready
        binding.timeRemaining = TimeRemaining(0, 0)
        sensorManager = activity!!.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager.registerListener(shakeDetector, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
        if (!readyToRecord) {
            binding.roundStartText.text = "Preparing For Data Collection\n\nPlease Wait..."
            executor.execute {
                model.dataCollectionService.setup {
                    activity!!.runOnUiThread {
                        binding.roundStartText.text = "Round $repetitions/5\n\nPlease Shake The Phone To Begin"
                        readyToRecord = it
                        pendingShake = true
//                    val toast = if (!it) {
//                        Log.e(TAG, "Failed to start data collectors")
//                        Toast.makeText(
//                            activity!!.applicationContext,
//                            "Unable to successfully start data collectors... Please check logs and restart.",
//                            Toast.LENGTH_LONG
//                        )
//                    } else {
//                        Toast.makeText(
//                            activity!!.applicationContext,
//                            "Ready to begin recording",
//                            Toast.LENGTH_LONG
//                        )
//                    }
//                    toast.show()
                    }
                }
            }
        }
        binding.buttonNext.isEnabled = false
        binding.buttonNext.text = "Record"

//        instructionsView = parentFragmentManager.findFragmentByTag(INSTRUCTIONS_FRAGMENT_TAG) as InstructionsView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sensorManager.unregisterListener(shakeDetector)
        _binding = null
    }

    companion object {
        const val NO_ALIGNMENT_INSTRCUTIONS = -1
        private const val SHAKE_THRESHOLD = 2.0
        private const val TAG = "DC"

        enum class Motions {
            POINTING_TRANSLATE_PHONE {
                override fun getNext() = POINTING_ROTATE_PHONE
                override fun getTargets() = POINTING_GRID_POINTS.zip(listOf(
                    Instructions(R.raw.phone_translate_pointing_topcentre, R.drawable.direction_down, "${move("Down", "Move", true)}\n\n$TARGET_NOSE", R.string.pointing_translate_phone_instructions, R.drawable.phone_translate_pointing_alignment),
                    Instructions(R.raw.phone_translate_pointing_topright, R.drawable.direction_down_left, "${move("Down And To The Left", "Move", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_translate_pointing_midright, R.drawable.direction_left, "${move("Left", "Move", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_translate_pointing_bottomright, R.drawable.direction_up_left, "${move("Down And To The Left", "Move", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_translate_pointing_bottomcentre, R.drawable.direction_up, "${move("Up", "Move", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_translate_pointing_bottomleft, R.drawable.direction_up_right, "${move("Up And To The Right", "Move", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_translate_pointing_midleft, R.drawable.direction_right, "${move("Right", "Move", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_translate_pointing_topleft, R.drawable.direction_down_right, "${move("Down And To The Right", "Move", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            },
            POINTING_ROTATE_PHONE {
                override fun getNext() = POINTING_ROTATE_HEAD
                override fun getTargets() = POINTING_GRID_POINTS.zip(listOf(
                    Instructions(R.raw.phone_rotate_pointing_topcentre, R.drawable.direction_down, "${move("Towards Your Nose", "Turn The Top Edge Of", true)}\n\n$TARGET_NOSE", R.string.pointing_translate_phone_instructions, R.drawable.phone_rotate_pointing_alignment),
                    Instructions(R.raw.phone_rotate_pointing_topright, R.drawable.direction_down_left, "${move("Towards Your Nose", "Turn The Top Right Corner Of", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_rotate_pointing_midright, R.drawable.direction_left, "${move("Towards Your Nose", "Turn The Right Edge Of", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_rotate_pointing_bottomright, R.drawable.direction_up_left, "${move("Towards Your Nose", "Turn The Bottom Right Corner Of", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_rotate_pointing_bottomcentre, R.drawable.direction_up, "${move("Towards Your Nose", "Turn The Bottom Edge Of", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_rotate_pointing_bottomleft, R.drawable.direction_up_right, "${move("Towards Your Nose", "Turn The Bottom Left Corner Of", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_rotate_pointing_midleft, R.drawable.direction_right, "${move("Towards Your Nose", "Turn The Left Edge Of", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.phone_rotate_pointing_topleft, R.drawable.direction_down_right, "${move("Towards Your Nose", "Turn The Top Left Corner Of", true)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            },
            POINTING_ROTATE_HEAD {
                override fun getNext() = TRANSLATE_PHONE
                override fun getTargets() = POINTING_GRID_POINTS.zip(listOf(
                    Instructions(R.raw.head_rotate_pointing_topcentre, R.drawable.direction_up, "${move("Towards The Top Of The Phone", "Turn", false)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.head_rotate_pointing_topright, R.drawable.direction_up_right, "${move("Towards The Top Right Of The Phone", "Turn", false)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.head_rotate_pointing_midright, R.drawable.direction_right, "${move("Towards The Right Of The Phone", "Turn", false)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.head_rotate_pointing_bottomright, R.drawable.direction_down_right, "${move("Towards The Bottom Right Of The Phone", "Turn", false)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.head_rotate_pointing_bottomcentre, R.drawable.direction_down, "${move("Towards The Bottom Of The Phone", "Turn", false)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.head_rotate_pointing_bottomleft, R.drawable.direction_down_left, "${move("Towards The Bottom Left Of The Phone", "Turn", false)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.head_rotate_pointing_midleft, R.drawable.direction_left, "${move("Towards The Left Of The Phone", "Turn", false)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.head_rotate_pointing_topleft, R.drawable.direction_up_left, "${move("Towards The Top Left Of The Phone", "Turn", false)}\n\n$TARGET_NOSE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            },
            TRANSLATE_PHONE {
                override fun getNext() = CIRCULAR_PHONE
                override fun getTargets() = EDGE_GRID_POINTS.zip(listOf(
                    Instructions(R.raw.phone_translate_topcentre, R.drawable.direction_down, "${move("Down", "Move", true)}\n\n$TARGET_CHIN", R.string.pointing_translate_phone_instructions, R.drawable.phone_translate_chin_alignment),
                    Instructions(R.raw.phone_translate_midright, R.drawable.direction_left, "${move("Left", "Move", true)}\n\n$TARGET_LEFT_EAR", R.string.pointing_translate_phone_instructions, R.drawable.phone_translate_ear_alignment_flip),
                    Instructions(R.raw.phone_translate_bottomcentre, R.drawable.direction_up, "${move("Up", "Move", true)}\n\n$TARGET_FOREHEAD", R.string.pointing_translate_phone_instructions, R.drawable.phone_translate_forehead_alignment),
                    Instructions(R.raw.phone_translate_midleft, R.drawable.direction_right, "${move("Right", "Move", true)}\n\n$TARGET_RIGHT_EAR", R.string.pointing_translate_phone_instructions, R.drawable.phone_translate_ear_alignment),
                ))
            },
            CIRCULAR_PHONE {
                // little and large circles?
                override fun getNext() = CIRCULAR_HEAD
                override fun getTargets() = ROLL_GRID_POINTS.zip(listOf(
                    Instructions(R.raw.phone_circular_clockwise, R.drawable.direction_clockwise, "${move("Clockwise In A Small Circle", "Move", true, false)}\n\nA Line Originating From The Tip Of Your Nose Should Trace A Circle On The Phone Screen.", R.string.circular_phone_instructions, R.drawable.phone_circular_alignment),
                    Instructions(R.raw.phone_circular_anticlockwise, R.drawable.direction_anticlockwise, "${move("Anti-Clockwise In A Small Circle", "Move", true, false)}\n\nA Line Originating From The Tip Of Your Nose Should Trace A Circle On The Phone Screen.", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            },
            CIRCULAR_HEAD {
                override fun getNext() = ZOOM_PHONE
                override fun getTargets() = ROLL_GRID_POINTS.zip(listOf(
                    Instructions(R.raw.head_circular_clockwise, R.drawable.direction_clockwise, "${move("Clockwise In A Small Circle", "While Keeping Your Body/Shoulders Still, Move", false, false)}\n\nA Line Originating From The Tip Of Your Nose Should Trace A Circle On The Phone Screen.", R.string.circular_head_instructions, R.drawable.head_circular_alignment),
                    Instructions(R.raw.head_circular_anticlockwise, R.drawable.direction_anticlockwise, "${move("Anti-Clockwise In A Small Circle", "While Keeping Your Body/Shoulders Still, Move", false, false)}\n\nA Line Originating From The Tip Of Your Nose Should Trace A Circle On The Phone Screen.", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            },
            ZOOM_PHONE {
                override fun getNext() = ZOOM_HEAD
                override fun getTargets() = listOf(GridPoints.ZOOM_IN, GridPoints.ZOOM_OUT).zip(listOf(
                    Instructions(R.raw.phone_zoom_in, R.drawable.direction_zoom_in, "${move("Towards Your Face", "Move", true)}\n\n$TARGET_CENTRE", R.string.zoom_phone_instructions, R.drawable.phone_zoom_alignment),
                    Instructions(R.raw.phone_zoom_out, R.drawable.direction_zoom_out, "${move("Away From Your Face", "Move", true)}\n\n$TARGET_CENTRE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            },
            ZOOM_HEAD {
                override fun getNext() = ROTATE_HEAD
                override fun getTargets() = listOf(GridPoints.ZOOM_IN, GridPoints.ZOOM_OUT).zip(listOf(
                    Instructions(R.raw.head_zoom_in, R.drawable.direction_zoom_in, "${move("Towards The Phone", "Move", false)}\n\n$TARGET_CENTRE", R.string.zoom_head_instructions, R.drawable.head_zoom_alignment),
                    Instructions(R.raw.head_zoom_out, R.drawable.direction_zoom_out, "${move("Away From The Phone", "Move", false)}\n\n$TARGET_CENTRE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            },
            ROTATE_HEAD {
                override fun getNext() = ROTATE_PHONE_ROLL
                override fun getTargets() = EDGE_GRID_POINTS.zip(listOf(
                    Instructions(R.raw.head_rotate_topcentre, R.drawable.direction_up, "Look Up\n\nYour Nose Should Be Pointing Above The Top Of The Phone.", R.string.rotate_head_instructions, R.drawable.head_rotate_alignment),
                    Instructions(R.raw.head_rotate_midright, R.drawable.direction_right, "Look Right\n\nYour Nose Should Be Pointing Beyond The Right Of The Phone.", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.head_rotate_bottomcentre, R.drawable.direction_down, "Look Down\n\nYour Nose Should Be Pointing Below The Bottom Of The Phone.", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                    Instructions(R.raw.head_rotate_midleft, R.drawable.direction_left, "Look Left\n\nYour Nose Should Be Pointing Beyond The Left Of The Phone.", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            },
            ROTATE_PHONE_ROLL {
                override fun getNext() = ROTATE_HEAD_ROLL
                override fun getTargets() = ROLL_GRID_POINTS.zip(listOf(
                    Instructions(R.raw.phone_roll_clockwise, R.drawable.direction_clockwise, "${move("Clockwise", "Tilt", true)}\n\n$TARGET_CENTRE", R.string.rotate_phone_roll_instructions, R.drawable.phone_roll_alignment),
                    Instructions(R.raw.phone_roll_anticlockwise, R.drawable.direction_anticlockwise, "${move("Anti-Clockwise", "Tilt", true)}\n\n$TARGET_CENTRE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            },
            ROTATE_HEAD_ROLL {
                override fun getNext(): Motions? = null
                override fun getTargets() = ROLL_GRID_POINTS.zip(listOf(
                    Instructions(R.raw.head_roll_clockwise, R.drawable.direction_clockwise, "${move("Clockwise", "Tilt", false)}\n\n$TARGET_CENTRE", R.string.rotate_head_roll_instructions, R.drawable.head_roll_alignment),
                    Instructions(R.raw.head_roll_anticlockwise, R.drawable.direction_anticlockwise, "${move("Anti-Clockwise", "false", true)}\n\n$TARGET_CENTRE", NO_ALIGNMENT_INSTRCUTIONS, NO_ALIGNMENT_INSTRCUTIONS),
                ))
            };

            abstract fun getNext(): Motions?
            abstract fun getTargets(): List<Pair<GridPoints, Instructions>>
        }

        val DEFAULT_MOTION = Motions.POINTING_TRANSLATE_PHONE

        enum class GridPoints(val layoutGravity: Int, val animated: Boolean) {
            TOP_CENTRE(Gravity.TOP or Gravity.CENTER, false),
            TOP_RIGHT(Gravity.TOP or Gravity.RIGHT, false),
            MID_RIGHT(Gravity.CENTER or Gravity.RIGHT, false),
            BOTTOM_RIGHT(Gravity.BOTTOM or Gravity.RIGHT, false),
            BOTTOM_CENTRE(Gravity.BOTTOM or Gravity.CENTER, false),
            BOTTOM_LEFT(Gravity.BOTTOM or Gravity.LEFT, false),
            MID_LEFT(Gravity.CENTER or Gravity.LEFT, false),
            TOP_LEFT(Gravity.TOP or Gravity.LEFT, false),
            CLOCKWISE(Gravity.CENTER, true),
            ANTI_CLOCKWISE(Gravity.CENTER, true),
            ZOOM_IN(Gravity.CENTER, true),
            ZOOM_OUT(Gravity.CENTER, true),
        }

        val POINTING_GRID_POINTS = listOf(
            GridPoints.TOP_CENTRE,
            GridPoints.TOP_RIGHT,
            GridPoints.MID_RIGHT,
            GridPoints.BOTTOM_RIGHT,
            GridPoints.BOTTOM_CENTRE,
            GridPoints.BOTTOM_LEFT,
            GridPoints.MID_LEFT,
            GridPoints.TOP_LEFT
        )
        val EDGE_GRID_POINTS = listOf(
            GridPoints.TOP_CENTRE,
            GridPoints.MID_RIGHT,
            GridPoints.BOTTOM_CENTRE,
            GridPoints.MID_LEFT
        )
        val ROLL_GRID_POINTS = listOf(GridPoints.CLOCKWISE, GridPoints.ANTI_CLOCKWISE)

        private fun align(target: String) = "Align Target On Screen With Your $target"
        val TARGET_NOSE = align("Nose")
        val TARGET_LEFT_EAR =  align("Left Ear")
        val TARGET_RIGHT_EAR =  align("Right Ear")
        val TARGET_CHIN =  align("Chin")
        val TARGET_FOREHEAD =  align("Forehead")
        const val TARGET_CENTRE = "Keep Target On Screen Aligned With You Nose"
        private fun move(direction: String, modifier: String, phone: Boolean, reverse: Boolean = true): String {
            val (moving, stationary) = if (phone)
                Pair("The Phone", "Your Head")
            else
                Pair("Your Head", "The Phone")
            return "$modifier $moving $direction While Keeping $stationary Still.${if (reverse) "\n\nOnce Done, Return $moving To The Starting Position By Doing The Opposite Motion" else ""}"
        }

    }
}
