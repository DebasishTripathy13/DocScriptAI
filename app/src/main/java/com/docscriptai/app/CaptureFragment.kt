package com.docscriptai.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.docscriptai.app.databinding.FragmentCaptureBinding
import com.docscriptai.domain.repository.TranscriptionListener
import kotlinx.coroutines.*
import java.io.File

/**
 * Page 1 — Audio capture, transcription display, and model management.
 */
class CaptureFragment : Fragment() {

    companion object { private const val TAG = "CaptureFragment" }

    private var _binding: FragmentCaptureBinding? = null
    private val binding get() = _binding!!
    private val vm: SharedViewModel by activityViewModels()
    private val services get() = requireActivity() as ServiceProvider

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordingJob: Job? = null
    private var isRecording = false
    private var pulseAnimator: ObjectAnimator? = null

    private val audioUploadLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { processUploadedAudio(it) } }

    private val modelPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { (requireActivity() as MainActivity).loadLlmModelFromUri(it) } }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCaptureBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        observeViewModel()
        restoreTranscription()
    }

    private fun setupButtons() {
        binding.btnRecord.setOnClickListener { toggleRecording() }
        binding.btnProcess.setOnClickListener { navigateToReport() }
        binding.btnUploadAudio.setOnClickListener { audioUploadLauncher.launch("audio/*") }
        binding.btnLoadModel.setOnClickListener { modelPickerLauncher.launch("*/*") }
        binding.btnDownloadModel.setOnClickListener {
            (requireActivity() as MainActivity).downloadModelFromGDrive()
        }
        binding.btnClear.setOnClickListener { clearAll() }
    }

    private fun observeViewModel() {
        vm.isVoskReady.observe(viewLifecycleOwner) { ready ->
            binding.btnRecord.isEnabled = ready
            if (ready) {
                setBadgeReady(binding.voskStatusBadge, getString(R.string.badge_vosk_ready))
                binding.statusText.text = getString(R.string.ready)
                binding.progressBar.visibility = View.GONE
            }
        }
        vm.isLlmReady.observe(viewLifecycleOwner) { ready ->
            if (ready) {
                setBadgeReady(binding.llmStatusBadge, getString(R.string.badge_llm_ready))
                binding.btnLoadModel.text = getString(R.string.model_loaded)
                binding.btnLoadModel.isEnabled = false
                binding.btnDownloadModel.isEnabled = false
                updateProcessButton()
            }
        }
    }

    private fun restoreTranscription() {
        val text = vm.transcriptionBuilder.toString()
        if (text.isNotEmpty()) {
            binding.transcriptionText.text = text
            binding.btnClear.visibility = View.VISIBLE
            updateProcessButton()
        }
    }

    // ── Recording ────────────────────────────────────────────────────────────

    private fun toggleRecording() { if (isRecording) stopRecording() else startRecording() }

    private fun startRecording() {
        isRecording = true
        binding.btnRecord.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.recording_red))
        binding.recordHintText.text = getString(R.string.stop)
        binding.btnProcess.isEnabled = false
        binding.statusText.text = getString(R.string.recording)
        binding.progressBar.visibility = View.VISIBLE
        startPulseAnimation()

        recordingJob = scope.launch {
            try {
                val wavFile = services.wavRecorder.startRecording()
                withContext(Dispatchers.Main) {
                    binding.statusText.text = getString(R.string.transcribing)
                    transcribeFile(wavFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "रिकॉर्डिंग में विफल: ${e.message}"
                    binding.progressBar.visibility = View.GONE
                    resetRecordButton()
                }
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        services.wavRecorder.stopRecording()
        resetRecordButton()
        stopPulseAnimation()
    }

    private fun resetRecordButton() {
        binding.btnRecord.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
        binding.recordHintText.text = getString(R.string.record_hint)
    }

    // ── Pulse animation ──────────────────────────────────────────────────────

    private fun startPulseAnimation() {
        binding.pulseRing.alpha = 1f
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.pulseRing,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.3f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.3f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0f)
        ).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        binding.pulseRing.alpha = 0f
    }

    // ── Transcription ────────────────────────────────────────────────────────

    private fun transcribeFile(file: File) {
        var lastPartialText = ""
        services.transcribeUseCase.transcribeFile(file, object : TranscriptionListener {
            override fun onModelReady() {}
            override fun onModelError(error: String) {}
            override fun onPartialResult(text: String) {
                lastPartialText = text
                activity?.runOnUiThread {
                    binding.transcriptionText.text = vm.transcriptionBuilder.toString() + text
                }
            }
            override fun onFinalResult(text: String) {
                activity?.runOnUiThread {
                    if (text.isNotEmpty()) vm.transcriptionBuilder.append(text).append(" ")
                    else if (lastPartialText.isNotEmpty()) vm.transcriptionBuilder.append(lastPartialText).append(" ")
                    lastPartialText = ""
                    val full = vm.transcriptionBuilder.toString()
                    vm.transcriptionText.value = full
                    binding.transcriptionText.text = full
                    binding.statusText.text = getString(R.string.ready)
                    binding.progressBar.visibility = View.GONE
                    binding.btnClear.visibility = View.VISIBLE
                    updateProcessButton()
                }
            }
            override fun onError(error: String) {
                activity?.runOnUiThread {
                    binding.statusText.text = "ट्रांसक्रिप्शन में विफल: $error"
                    binding.progressBar.visibility = View.GONE
                }
            }
        })
    }

    // ── Audio Upload ─────────────────────────────────────────────────────────

    private fun processUploadedAudio(uri: Uri) {
        binding.statusText.text = getString(R.string.converting)
        binding.progressBar.visibility = View.VISIBLE

        scope.launch {
            try {
                val outputFile = File(requireContext().cacheDir, "upload_converted.wav")
                val result = services.audioConverter.convertToWav(uri, outputFile)
                withContext(Dispatchers.Main) {
                    result.onSuccess { wavFile ->
                        binding.statusText.text = getString(R.string.transcribing)
                        transcribeFile(wavFile)
                    }.onFailure { e ->
                        binding.statusText.text = "${getString(R.string.conversion_failed)}: ${e.message}"
                        binding.progressBar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "Error: ${e.message}"
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private fun navigateToReport() {
        if (vm.isLlmReady.value != true) {
            Toast.makeText(requireContext(), getString(R.string.llm_not_available), Toast.LENGTH_LONG).show()
            return
        }
        val text = vm.transcriptionBuilder.toString().trim()
        if (text.isEmpty()) return
        findNavController().navigate(R.id.action_capture_to_report)
    }

    private fun updateProcessButton() {
        binding.btnProcess.isEnabled =
            vm.transcriptionBuilder.isNotEmpty() && vm.isLlmReady.value == true
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun clearAll() {
        vm.transcriptionBuilder.clear()
        vm.transcriptionText.value = ""
        binding.transcriptionText.text = ""
        binding.btnProcess.isEnabled = false
        binding.btnClear.visibility = View.GONE
    }

    private fun setBadgeReady(badge: android.widget.TextView, label: String) {
        badge.text = label
        badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.badge_ready_text))
        badge.setBackgroundResource(R.drawable.badge_bg_ready)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pulseAnimator?.cancel()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
