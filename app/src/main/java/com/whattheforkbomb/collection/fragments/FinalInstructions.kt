package com.whattheforkbomb.collection.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.data.ESenseEvent
import com.whattheforkbomb.collection.databinding.FragmentFinalInstructionsBinding
import com.whattheforkbomb.collection.services.EarableProcessor
import com.whattheforkbomb.collection.viewmodels.DataCollectionViewModel
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import kotlin.io.path.pathString

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

    private val model: DataCollectionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFinalInstructionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity!!.actionBar?.setDisplayHomeAsUpEnabled(false)

        val csvFile = File(Paths.get(model.rootDir.pathString, CONSENT_CSV).toUri())
        if (!csvFile.exists()) {
            csvFile.createNewFile()
            FileWriter(csvFile).use {
                it.appendLine(HEADER)
            }
        }

        binding.buttonNext.setOnClickListener {
            FileWriter(csvFile, true).use {
                it.appendLine("${model.dataCollectionService.getParticipantId()},${binding.checkbox.isChecked}")
            }
            binding.buttonNext.isEnabled = false
            binding.buttonNext.visibility = INVISIBLE
            binding.textviewFirst.text = getString(R.string.exit_instructions)
            binding.checkbox.isEnabled = false
            binding.checkbox.visibility = INVISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val CONSENT_CSV = "consent.csv"
        const val HEADER = "PARTICIPANT_ID,CONSENT_PROVIDED"
    }
}
