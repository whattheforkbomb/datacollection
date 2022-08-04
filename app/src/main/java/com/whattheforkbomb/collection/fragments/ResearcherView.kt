package com.whattheforkbomb.collection.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.databinding.FragmentInitialInstructionsBinding
import com.whattheforkbomb.collection.databinding.FragmentResearcherViewBinding
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel


/**
 * A simple [Fragment] subclass.
 * Use the [ResearcherView.newInstance] factory method to
 * create an instance of this fragment.
 */
class ResearcherView : Fragment() {

    private var _binding: FragmentResearcherViewBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val model: DataCollectionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentResearcherViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity!!.title = model.dataCollectionService.getParticipantId().toString()
        binding.text.text = "PID: ${model.dataCollectionService.getParticipantId().toString()}"
        binding.buttonNext.setOnClickListener {
            findNavController().navigate(R.id.nav_to_welcome)
        }
    }

}
