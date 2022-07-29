package com.whattheforkbomb.collection.fragments

import android.os.Bundle
import android.os.CountDownTimer
import android.transition.Visibility
import android.util.Log
import android.util.Size
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.navigation.fragment.findNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.data.TimeRemaining
import com.whattheforkbomb.collection.databinding.FragmentDataCollectionBinding
import com.whattheforkbomb.collection.services.CameraProcessor
import com.whattheforkbomb.collection.services.SensorProcessor
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors.toList
import kotlin.concurrent.fixedRateTimer

/**
 * A simple [Fragment] subclass.
 * Use the [DataCollectionFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class DataCollectionFragment : Fragment() {
    private val TAG = "DC"
    private var _binding: FragmentDataCollectionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val model: DataCollectionViewModel by activityViewModels()
    private val executor: Executor = Executors.newSingleThreadExecutor()

    private var selectedMotion: Motions = DEFAULT_MOTION
    private var remainingGridPoints: List<GridPoints> = listOf()
    private var faceDetectionEnabled: Boolean = false
    private var recording = false
    @Volatile private var animationTimer: Timer? = null
    private var readyToRecord: Boolean = false // Replace with variable binding from fragment
        set(value) {
            if (value && !binding.buttonNext.isEnabled) {
                binding.buttonPlayPause.isEnabled = true
            }
            field = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        faceDetectionEnabled = arguments?.getBoolean(resources.getString(R.string.face_detection)) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentDataCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun reset(motion: Motions) {
        selectedMotion = motion
        remainingGridPoints = selectedMotion.getTargets().shuffled()

        Log.i(TAG, "Remaining GridPoints: ${remainingGridPoints.joinToString()}")

        parentFragmentManager.commit {
            val bundle = bundleOf(getString(R.string.motion_arg_name) to selectedMotion)
            replace<InstructionsView>(R.id.instructions_placeholder, args = bundle)
        }

//        if (faceDetectionEnabled) parentFragmentManager.commit {
//            replace<FaceIndicator>(R.id.face_indicator_placeholder)
//        }

        val layoutParams = FrameLayout.LayoutParams(binding.target.layoutParams)
        layoutParams.gravity = remainingGridPoints[0].layoutGravity
        Log.i(TAG, "Current Target Position: ${remainingGridPoints[0].name} - ${remainingGridPoints[0].layoutGravity}")
        binding.target.layoutParams = layoutParams

        if (remainingGridPoints[0].animated) setAnimation(remainingGridPoints[0])

        // Nav
        binding.buttonNext.setOnClickListener {
            if (remainingGridPoints.size == 1) {
                val nextMotion = selectedMotion.getNext()
                if (nextMotion != null) {
                    reset(nextMotion)
                } else {
                    findNavController().navigate(R.id.nav_to_stage_2_instructions)
                }
                animationTimer?.cancel()
                animationTimer = null
                binding.animatedTarget.visibility = View.INVISIBLE
            } else {
                remainingGridPoints = remainingGridPoints.subList(1, remainingGridPoints.size)
                binding.buttonNext.isEnabled = false
                layoutParams.gravity = remainingGridPoints[0].layoutGravity
                binding.target.layoutParams = layoutParams
                binding.target.visibility = View.VISIBLE

                if (remainingGridPoints[0].animated) setAnimation(remainingGridPoints[0])

                Log.i(TAG, "Current Target Position: ${remainingGridPoints[0]} - ${remainingGridPoints[0].layoutGravity}")
            }
        }

        binding.buttonPlayPause.isEnabled = readyToRecord
        binding.buttonPlayPause.text = getString(R.string.record)
        binding.buttonNext.isEnabled = false
        recording = false

        binding.buttonPlayPause.setOnClickListener {
            if (recording) {
                recording = false

                model.dataCollectionService.stop()
                binding.buttonNext.isEnabled = true

                binding.buttonPlayPause.text = getString(R.string.record)
                binding.target.visibility = View.INVISIBLE
            } else {
                recording = true

                model.dataCollectionService.start(selectedMotion, remainingGridPoints[0])

                binding.buttonPlayPause.text = getString(R.string.pause)
                binding.target.visibility = View.VISIBLE
            }
        }
    }

    private fun setAnimation(gridPoints: GridPoints) {
        binding.animatedTarget.visibility = View.VISIBLE
        animationTimer?.cancel()
        when(gridPoints) {
            GridPoints.ZOOM_IN -> {
                val maxScale = 3.0
                animationTimer = fixedRateTimer("AnimationTimer", true, 0L, (400).toLong()) {
                    val newScale = binding.target.scaleX + 0.5
                    if (newScale <= maxScale) {
                        activity!!.runOnUiThread {
                            binding.target.scaleX = newScale.toFloat()
                            binding.target.scaleY = newScale.toFloat()
                        }
                    } else {
                        activity!!.runOnUiThread {
                            binding.target.scaleX = 1.0.toFloat()
                            binding.target.scaleY = 1.0.toFloat()
                        }
                    }
                }
            }

            GridPoints.ZOOM_OUT -> {
                val maxScale = 3.0
                animationTimer = fixedRateTimer("AnimationTimer", true, 0L, (400).toLong()) {
                    val newScale = binding.target.scaleX - 0.5
                    if (newScale >= 1.0) {
                        activity!!.runOnUiThread {
                            binding.target.scaleX = newScale.toFloat()
                            binding.target.scaleY = newScale.toFloat()
                        }
                    } else {
                        activity!!.runOnUiThread {
                            binding.target.scaleX = maxScale.toFloat()
                            binding.target.scaleY = maxScale.toFloat()
                        }
                    }
                }
            }

            GridPoints.CLOCKWISE -> {
                val positions = POINTING_GRID_POINTS
                var currentIdx = 0
                val layoutParams =  FrameLayout.LayoutParams(binding.animatedTarget.layoutParams)
                animationTimer = fixedRateTimer("AnimationTimer", true, 0L, (400).toLong()) {
                    currentIdx = if (currentIdx < positions.size-1)
                        currentIdx + 1
                    else
                        0
                    layoutParams.gravity = positions[currentIdx].layoutGravity
                    activity!!.runOnUiThread {
                        binding.animatedTarget.layoutParams = layoutParams
                    }
                }
            }

            GridPoints.ANTI_CLOCKWISE -> {
                val positions = POINTING_GRID_POINTS.reversed()
                var currentIdx = 0
                val layoutParams =  FrameLayout.LayoutParams(binding.animatedTarget.layoutParams)
                animationTimer = fixedRateTimer("AnimationTimer", true, 0L, (400).toLong()) {
                    currentIdx = if (currentIdx < positions.size-1)
                        currentIdx + 1
                    else
                        0
                    layoutParams.gravity = positions[currentIdx].layoutGravity
                    activity!!.runOnUiThread {
                        binding.animatedTarget.layoutParams = layoutParams
                    }
                }
            }

            else -> {
                Log.e(TAG, "There should be no animation for this")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "Motion: $selectedMotion")
        readyToRecord = model.dataCollectionService.ready
        if (!readyToRecord) executor.execute {

            model.dataCollectionService.setup {
                activity!!.runOnUiThread {
                    readyToRecord = it
                    if (!it) {
                        val toast = Toast.makeText(
                            activity!!.applicationContext,
                            "Unable to successfully start data collectors... Please check logs and restart.",
                            Toast.LENGTH_LONG
                        )
                        Log.e(TAG, "Failed to start data collectors")
                        toast.show()
                    }
                }
            }
        }
        reset(selectedMotion)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        enum class Motions(val instructionGraphicPath: Int, val instructionsTextId: Int) {
            POINTING_TRANSLATE_PHONE(R.drawable.phonepointing, R.string.pointing_translate_phone_instructions) {
                override fun getNext() = POINTING_ROTATE_PHONE
                override fun getTargets() = POINTING_GRID_POINTS
            },
            POINTING_ROTATE_PHONE(R.drawable.phonepointing, R.string.pointing_rotate_phone_instructions) {
                override fun getNext() = POINTING_ROTATE_HEAD
                override fun getTargets() = POINTING_GRID_POINTS
            },
            POINTING_ROTATE_HEAD(R.drawable.phonepointing, R.string.pointing_rotate_head_instructions) {
                override fun getNext() = TRANSLATE_PHONE
                override fun getTargets() = POINTING_GRID_POINTS
            },
            TRANSLATE_PHONE(R.drawable.phonepointing, R.string.pointing_translate_phone_instructions) {
                override fun getNext() = CIRCULAR_PHONE
                override fun getTargets() = EDGE_GRID_POINTS.toList()
            },
            CIRCULAR_PHONE(R.drawable.phonepointing, R.string.circular_phone_instructions) {
                // little and large circles?
                override fun getNext() = CIRCULAR_HEAD
                override fun getTargets() = ROLL_GRID_POINTS
            },
            CIRCULAR_HEAD(R.drawable.phonepointing, R.string.circular_head_instructions) {
                override fun getNext() = ZOOM_PHONE
                override fun getTargets() = ROLL_GRID_POINTS
            },
            ZOOM_PHONE(R.drawable.phonepointing, R.string.zoom_phone_instructions) {
                // Need additional instruction to indicate direction to move phone
                override fun getNext() = ZOOM_HEAD
                override fun getTargets() = listOf(GridPoints.ZOOM_IN, GridPoints.ZOOM_OUT)
            },
            ZOOM_HEAD(R.drawable.phonepointing, R.string.zoom_head_instructions) {
                override fun getNext() = ROTATE_HEAD
                override fun getTargets() = listOf(GridPoints.ZOOM_IN)
            },
            ROTATE_HEAD(R.drawable.phonepointing, R.string.rotate_head_instructions) {
                override fun getNext() = ROTATE_PHONE_ROLL
                override fun getTargets() = EDGE_GRID_POINTS.toList()
            },
            ROTATE_PHONE_ROLL(R.drawable.phonepointing, R.string.rotate_phone_roll_instructions) {
                // Need additional instruction to indicate direction to move phone, maybe second target that circles the screen in direction?
                override fun getNext() = ROTATE_HEAD_ROLL
                override fun getTargets() = ROLL_GRID_POINTS
            },
            ROTATE_HEAD_ROLL(R.drawable.phonepointing, R.string.rotate_head_roll_instructions) {
                // Need additional instruction to indicate direction to move head, maybe second target that circles the screen in direction?
                override fun getNext() = ROTATE_PHONE_ORBIT
                override fun getTargets() = ROLL_GRID_POINTS
            },
            ROTATE_PHONE_ORBIT(R.drawable.phonepointing, R.string.rotation_orbit_phone) {
                override fun getNext(): Motions? = null
                override fun getTargets() = listOf(GridPoints.MID_LEFT, GridPoints.MID_RIGHT)
            };

            abstract fun getNext(): Motions?
            abstract fun getTargets(): List<GridPoints>

        }
        val DEFAULT_MOTION = Motions.CIRCULAR_PHONE //POINTING_TRANSLATE_PHONE

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

    }
}
