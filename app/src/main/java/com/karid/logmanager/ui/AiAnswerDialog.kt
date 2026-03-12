package com.karid.logmanager.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.karid.logmanager.R
import com.karid.logmanager.databinding.DialogAiAnswerBinding

class AiAnswerDialog : DialogFragment() {

    private var _binding: DialogAiAnswerBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_ANSWER = "answer"
        private const val ARG_PROBLEM = "problem"

        fun newInstance(answer: String, problem: String): AiAnswerDialog {
            return AiAnswerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_ANSWER, answer)
                    putString(ARG_PROBLEM, problem)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAiAnswerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val answer = arguments?.getString(ARG_ANSWER) ?: ""
        val problem = arguments?.getString(ARG_PROBLEM) ?: ""

        binding.tvProblem.text = problem
        binding.tvAnswer.text = answer

        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
