package com.whattheforkbomb.collection.fragments

import android.graphics.ImageDecoder
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.mlkit.vision.face.FaceDetection
import com.whattheforkbomb.collection.R
import com.whattheforkbomb.collection.databinding.FragmentFaceIndicatorBinding
import com.google.mlkit.vision.face.FaceDetectorOptions




/**
 * A simple [Fragment] subclass.
 * Use the [FaceIndicator.newInstance] factory method to
 * create an instance of this fragment.
 */
class FaceIndicator : Fragment() {

    private val TAG = "FI"
    private var _binding: FragmentFaceIndicatorBinding? = null
    // Need access to camera on capture processed to then run the MLFaceKit
    private val faceDetector = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .build()
    )

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentFaceIndicatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, FACE_NOT_DETECTED_ID))
        binding.image.setImageDrawable(drawable)
    }

//    fun checkFace(byteArray: ByteArray, ) {
//        InputImage.fromByteArray(byteArray, )
//    }

    companion object {
        const val FACE_DETECTED_ID = R.drawable.facedetected
        const val FACE_NOT_DETECTED_ID = R.drawable.facenotdetected
    }
}
