package com.whattheforkbomb.collection.fragments

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.navigation.fragment.findNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.data.TimeRemaining
import com.whattheforkbomb.collection.databinding.FragmentDataCollectionBinding
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

    private var selectedMotion: String? = null
    private lateinit var timer: CountDownTimer
    private var recording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selectedMotion = it.getString(resources.getString(R.string.motion_arg_name))
        }
        selectedMotion?.let {
            selectedMotion = resources.getString(R.string.pointing_phone)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentDataCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun setMotion() {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Timer
        binding.timeRemaining = TimeRemaining(1, 0)
        timer = getTimer(binding.timeRemaining!!.minutes, binding.timeRemaining!!.seconds)

        binding.buttonPlayPause.setOnClickListener {
            if (recording) {
                recording = false
                timer.cancel()

                // TODO: Stop recording, ensure save what currently have

                binding.buttonPlayPause.text = getString(R.string.record)
                timer = getTimer(binding.timeRemaining!!.minutes, binding.timeRemaining!!.seconds)
            } else {
                timer.start()
                recording = true
                // TODO: Start recording
                binding.buttonPlayPause.text = getString(R.string.pause)
            }
        }

        var destination: Int = R.id.nav_to_circular_phone

        selectedMotion?.let {
            when (it) {
                resources.getString(R.string.circular_phone) -> {
                    destination = R.id.nav_to_circular_head
                }
                resources.getString(R.string.circular_head) -> {
                    destination = R.id.nav_to_pointing_phone
                }
                resources.getString(R.string.pointing_phone) -> {
                    destination = R.id.nav_to_pointing_head
                }
                else -> {
                    destination = R.id.nav_to_circular_phone
                }
            }
        }
        parentFragmentManager.commit {
            val bundle = bundleOf(getString(R.string.motion_arg_name) to selectedMotion)
            replace<InstructionsView>(R.id.instructions_placeholder, args = bundle)
        }
        // Nav
        binding.buttonNext.setOnClickListener {
            findNavController().navigate(destination)
        }
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
            // TODO: Stop recording
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

//    companion object {
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
//    }
}
