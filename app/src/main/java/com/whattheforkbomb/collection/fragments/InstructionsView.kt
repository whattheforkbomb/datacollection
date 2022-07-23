package com.whattheforkbomb.collection.fragments

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.databinding.FragmentInstructionsViewBinding
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel

class InstructionsView : Fragment() {

    private val TAG = "IV"
    private var _binding: FragmentInstructionsViewBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var selectedMotion: DataCollectionFragment.Companion.Motions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedMotion = arguments?.let {
            it.get(resources.getString(R.string.motion_arg_name)) as DataCollectionFragment.Companion.Motions
        } ?: DataCollectionFragment.DEFAULT_MOTION
        Log.i(TAG, "Loading instructions for $selectedMotion")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, selectedMotion.instructionGraphicPath)) as AnimatedImageDrawable
        drawable.start()
        binding.image.setImageDrawable(drawable)
        binding.text.text = getString(selectedMotion.instructionsTextId)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentInstructionsViewBinding.inflate(inflater, container, false)
        return binding.root
    }

}
