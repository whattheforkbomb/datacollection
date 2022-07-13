package com.whattheforkbomb.collection.fragments

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.activities.DataCollectionActivity
import com.whattheforkbomb.collection.databinding.FragmentInitialInstructionsBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 * TODO:
 *  - Display Instructions for motions to perform with the phone
 *  - Ideally include images
 */
class InitialInstructions : Fragment() {

    private var _binding: FragmentInitialInstructionsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentInitialInstructionsBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonNext.setOnClickListener {
            val intent = Intent(activity, DataCollectionActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
