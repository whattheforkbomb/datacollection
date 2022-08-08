package com.whattheforkbomb.collection.fragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.data.Instructions
import com.whattheforkbomb.collection.databinding.FragmentInstructionsViewBinding

class InstructionsView : Fragment() {

    private val TAG = "IV"
    private var _binding: FragmentInstructionsViewBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var instruction: Instructions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instruction = arguments!![resources.getString(R.string.motion_arg_name)] as Instructions
//        Log.i(TAG, "Loading instructions for $instruction")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateInstructions(instruction)

        binding.openGuide.setOnClickListener {
            binding.instructions.visibility = INVISIBLE
            binding.alignmentGuide.visibility = VISIBLE
            Log.i(TAG, "Displaying the alignment guide.")
        }
        binding.closeGuide.setOnClickListener {
            binding.instructions.visibility = VISIBLE
            binding.alignmentGuide.visibility = INVISIBLE
            Log.i(TAG, "Displaying the instructions.")
        }
    }

    fun updateInstructions(instruction: Instructions) {
        binding.direction.setImageResource(instruction.directionId)
        binding.video.setVideoPath("android.resource://${activity!!.packageName}/${instruction.videoId}")
        binding.video.start()
        binding.video.setOnPreparedListener { it.isLooping = true }
        if (instruction.alignmentGuideTextId != DataCollectionFragment.NO_ALIGNMENT_INSTRCUTIONS) {
            binding.instructionsText.text = instruction.instructionText
            binding.guideText.setText(instruction.alignmentGuideTextId)
            binding.imageGuide.setImageResource(instruction.alignmentGuideImageId)
        }
    }

    fun stopVideo() {
        binding.video.stopPlayback()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        Log.i(TAG, "InstructionsView View Create")
        _binding = FragmentInstructionsViewBinding.inflate(inflater, container, false)
        return binding.root
    }

}
