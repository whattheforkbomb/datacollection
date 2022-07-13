package com.whattheforkbomb.collection.fragments

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.databinding.FragmentDataCollectionBinding
import com.whattheforkbomb.collection.databinding.FragmentInstructionsViewBinding
import com.whattheforkbomb.collection.viewmodels.InstructionsViewViewModel

class InstructionsView : Fragment() {

    private val TAG = "DC"
    private var _binding: FragmentInstructionsViewBinding? = null
    private lateinit var viewModel: InstructionsViewViewModel

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var selectedMotion: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
      arguments?.let {
            selectedMotion = it.getString(resources.getString(R.string.motion_arg_name))
        }
        selectedMotion?.let {
            selectedMotion = resources.getString(R.string.pointing_phone)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var instructionsId: Int = R.string.phone_pointing_instructions
        var imageResource: Int = R.drawable.phonepointing

        selectedMotion?.let {
            when (it) {
                resources.getString(R.string.circular_phone) -> {
                    instructionsId = R.string.initial_instructions // TODO: Replace
                    imageResource = R.drawable.phonepointing // TODO: Replace
                }
                resources.getString(R.string.circular_head) -> {
                    instructionsId = R.string.initial_instructions // TODO: Replace
                    imageResource = R.drawable.phonepointing // TODO: Replace
                }
                resources.getString(R.string.pointing_phone) -> {
                    instructionsId = R.string.phone_pointing_instructions // TODO: Replace
                    imageResource = R.drawable.phonepointing // TODO: Replace
                }
                else -> {
                    instructionsId = R.string.phone_pointing_instructions // TODO: Replace
                    imageResource = R.drawable.phonepointing // TODO: Replace
                }
            }
        }

        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, imageResource)) as AnimatedImageDrawable
        drawable.start()
        binding.image.setImageDrawable(drawable)
        binding.text.text = getString(instructionsId)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentInstructionsViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(InstructionsViewViewModel::class.java)
        // TODO: Use the ViewModel
    }


//    companion object {
//        fun newInstance() = InstructionsView()
//    }
}
