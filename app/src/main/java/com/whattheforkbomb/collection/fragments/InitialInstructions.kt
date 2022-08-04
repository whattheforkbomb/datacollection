package com.whattheforkbomb.collection.fragments

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.databinding.FragmentInitialInstructionsBinding
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel

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

    private val model: DataCollectionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInitialInstructionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity!!.actionBar?.setDisplayHomeAsUpEnabled(false)
        activity!!.actionBar?.setHomeButtonEnabled(false)

        binding.buttonNext.setOnClickListener {
            findNavController().navigate(R.id.nav_to_stage_1_data_collection)
        }
        val styledText = SpannableString(getString(R.string.disclaimer))
        styledText.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 91, 104, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        styledText.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 120, 129, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.disclaimer.text = styledText
        activity!!.title = "Welcome"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
