package com.whattheforkbomb.collection.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.databinding.FragmentFinalInstructionsBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 * TODO:
 *  - Display Instructions for motions to perform with the phone
 *  - Ideally include images
 */
class FinalInstructions : Fragment() {

    private var _binding: FragmentFinalInstructionsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFinalInstructionsBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonNext.setOnClickListener {
            // TODO: Navigate to DataCollectionActivity (second stage)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
