package com.whattheforkbomb.collection.fragments

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    private var faceDetectionEnabled: Boolean = false
    private lateinit var timer: CountDownTimer
    private var recording = false
    private var readyToRecord: Boolean // Replace with variable binding from fragment
        get() = false
        set(value) {
            if (value && !binding.buttonNext.isEnabled) {
                binding.buttonPlayPause.isEnabled = true
            }
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

        parentFragmentManager.commit {
            val bundle = bundleOf(getString(R.string.motion_arg_name) to selectedMotion)
            replace<InstructionsView>(R.id.instructions_placeholder, args = bundle)
        }

        if (faceDetectionEnabled) parentFragmentManager.commit {
            replace<FaceIndicator>(R.id.face_indicator_placeholder)
        }

        // Nav
        binding.buttonNext.setOnClickListener {
            val nextMotion = selectedMotion.getNext()
            if (nextMotion != null) {
                reset(nextMotion)
            } else {
                findNavController().navigate(R.id.nav_to_stage_2_instructions)
            }
        }

        // Timer
        binding.timeRemaining = TimeRemaining(0, 30)
        timer = getTimer(binding.timeRemaining!!.minutes, binding.timeRemaining!!.seconds)

        binding.buttonPlayPause.isEnabled = readyToRecord
        binding.buttonPlayPause.text = getString(R.string.record)
        binding.buttonNext.isEnabled = false
        recording = false

        binding.buttonPlayPause.setOnClickListener {
            if (recording) {
                recording = false
                timer.cancel()

                model.dataCollectionService.stop()

                binding.buttonPlayPause.text = getString(R.string.record)
                timer = getTimer(binding.timeRemaining!!.minutes, binding.timeRemaining!!.seconds)
            } else {
                timer.start()
                recording = true

                model.dataCollectionService.start(selectedMotion)

                binding.buttonPlayPause.text = getString(R.string.pause)
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

    private fun getTimer(min: Long, sec: Long) = object : CountDownTimer((min*60*1000) + (sec*1000), 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
            Log.i(TAG, "Time Remaining (ms): $millisUntilFinished, $minutes")
            binding.timeRemaining = TimeRemaining(minutes, TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) - (minutes * 60))
        }

        override fun onFinish() {
            binding.buttonNext.isEnabled = true
            binding.buttonPlayPause.isEnabled = false

            model.dataCollectionService.stop()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
//        /**
//         * Use this factory method to create a new instance of
//         * this fragment using the provided parameters.
//         *
//         * @param param1 Parameter 1.
//         * @param param2 Parameter 2.
//         * @return A new instance of fragment DataCollectionFragment.
//         */
//        // TODO: Rename and change types and number of parameters
//        @JvmStatic
//        fun newInstance(param1: String, param2: String) =
//            DataCollectionFragment().apply {
//                arguments = Bundle().apply {
//                    putString(ARG_PARAM1, param1)
//                    putString(ARG_PARAM2, param2)
//                }
//            }
        enum class Motions(val instructionGraphicPath: Int, val instructionsTextId: Int) {
            POINTING_PHONE(R.drawable.phonepointing, R.string.phone_pointing_instructions) {
                override fun getNext() = POINTING_HEAD
            },
            POINTING_HEAD(R.drawable.phonepointing, R.string.initial_instructions) {
                override fun getNext() = CIRCULAR_PHONE
            },
            CIRCULAR_PHONE(R.drawable.phonepointing, R.string.initial_instructions) {
                override fun getNext() = CIRCULAR_HEAD
            },
            CIRCULAR_HEAD(R.drawable.phonepointing, R.string.initial_instructions) {
                override fun getNext() = LATERAL_HEAD
            },
            LATERAL_HEAD(R.drawable.phonepointing, R.string.initial_instructions) {
                override fun getNext() = LATERAL_PHONE
            },
            LATERAL_PHONE(R.drawable.phonepointing, R.string.initial_instructions) {
                override fun getNext() = ZOOM_PHONE
            },
            ZOOM_PHONE(R.drawable.phonepointing, R.string.initial_instructions) {
                override fun getNext() = ZOOM_HEAD
            },
            ZOOM_HEAD(R.drawable.phonepointing, R.string.initial_instructions) {
                override fun getNext() = ROTATE_HEAD
            },
            ROTATE_HEAD(R.drawable.phonepointing, R.string.initial_instructions) {
                override fun getNext() = ROTATE_PHONE
            },
            ROTATE_PHONE(R.drawable.phonepointing, R.string.initial_instructions) {
                override fun getNext() = null
            };

            abstract fun getNext(): Motions?

        }

        val DEFAULT_MOTION = Motions.POINTING_PHONE
    }
}
