package com.whattheforkbomb.collection.fragments

import android.os.Bundle
import android.text.Editable
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.text.isDigitsOnly
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.databinding.FragmentInitialInstructionsBinding
import com.whattheforkbomb.collection.databinding.FragmentResearcherViewBinding
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel
import kotlin.io.path.name
import kotlin.io.path.pathString


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
        Log.i("RF", "rootDir: ${model.rootDir}\nlist: ${model.rootDir.toFile().listFiles()}")
        val priorMaxId = model.rootDir.toFile().listFiles()
            ?.filter { it.name.isDigitsOnly() }
            ?.map { it.name.toInt() }
            ?.maxOrNull()
        if (priorMaxId != null) {
            binding.text.text.append(priorMaxId.inc().toString())
        }
        binding.buttonNext.setOnClickListener {
            if (model.rootDir.toFile().listFiles()?.map { it.name }?.contains(binding.text.text.toString()) == true) {
                Toast.makeText(
                    activity!!.applicationContext,
                    "This participant Id has previously been used.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                model.dataCollectionService.setParticipantId((binding.text.text.toString()).toInt())
                findNavController().navigate(R.id.nav_to_welcome)
            }
        }
    }

}
