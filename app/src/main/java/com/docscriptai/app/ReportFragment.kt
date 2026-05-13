package com.docscriptai.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.docscriptai.app.databinding.FragmentReportBinding
import kotlinx.coroutines.*

/**
 * Page 2 — Processes transcription via LLM and displays the medical report
 * with staggered card-entrance animations.
 */
class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!
    private val vm: SharedViewModel by activityViewModels()
    private val services get() = requireActivity() as ServiceProvider

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNewRecording.setOnClickListener {
            vm.transcriptionBuilder.clear()
            vm.transcriptionText.value = ""
            findNavController().popBackStack()
        }

        startProcessing()
    }

    private fun startProcessing() {
        val text = vm.transcriptionBuilder.toString().trim()
        if (text.isEmpty()) {
            binding.reportStatusText.text = "No transcription to process"
            return
        }

        if (services.extractReportUseCase.wouldTruncate(text)) {
            Toast.makeText(
                requireContext(),
                "बातचीत बहुत लंबी है — मध्य भाग हटाकर प्रोसेस होगा",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.processingIndicator.visibility = View.VISIBLE

        scope.launch {
            val result = services.extractReportUseCase.execute(text) { field, value ->
                withContext(Dispatchers.Main) {
                    when (field) {
                        "diagnosis"  -> revealCard(binding.diagnosisCard, binding.root.findViewById(R.id.diagnosisText), value, 0)
                        "medication" -> revealCard(binding.medicationCard, binding.root.findViewById(R.id.medicationText), value, 100)
                        "tests"      -> revealCard(binding.testsCard, binding.root.findViewById(R.id.testsText), value, 200)
                        "followUp"   -> revealCard(binding.followupCard, binding.root.findViewById(R.id.followupText), value, 300)
                    }
                }
            }

            result.onSuccess {
                binding.processingIndicator.visibility = View.GONE
                binding.reportStatusText.text = "प्रोसेसिंग पूरी हुई ✓"
                binding.btnNewRecording.visibility = View.VISIBLE
                binding.btnNewRecording.alpha = 0f
                binding.btnNewRecording.animate().alpha(1f).setDuration(400).start()
            }.onFailure { e ->
                binding.reportProgress.visibility = View.GONE
                binding.reportStatusText.text = "AI प्रोसेसिंग में विफल: ${e.message}"
                binding.btnNewRecording.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Reveals a result card with a slide-up + fade-in animation.
     */
    private fun revealCard(card: View, textView: android.widget.TextView, value: String, delayMs: Long) {
        textView.text = value
        card.visibility = View.VISIBLE
        card.alpha = 0f
        card.translationY = 40f
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setStartDelay(delayMs)
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
