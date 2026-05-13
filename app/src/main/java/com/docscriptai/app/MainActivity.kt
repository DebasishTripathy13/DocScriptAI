package com.docscriptai.app

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.docscriptai.app.databinding.ActivityMainBinding
import com.docscriptai.data.audio.AudioConverter
import com.docscriptai.data.audio.VoskTranscriptionService
import com.docscriptai.data.audio.WavRecorder
import com.docscriptai.data.llm.LlmProcessor
import com.docscriptai.domain.repository.LlmRepository
import com.docscriptai.domain.repository.TranscriptionListener
import com.docscriptai.domain.repository.TranscriptionRepository
import com.docscriptai.domain.usecase.ExtractReportUseCase
import com.docscriptai.domain.usecase.TranscribeAudioUseCase
import kotlinx.coroutines.*
import java.io.File

/**
 * Thin Activity host — owns the NavHostFragment, initializes models,
 * manages GDrive downloads, and exposes services to fragments via [ServiceProvider].
 */
class MainActivity : AppCompatActivity(), ServiceProvider {

    companion object { private const val TAG = "MainActivity" }

    private lateinit var binding: ActivityMainBinding
    private val vm: SharedViewModel by viewModels()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── ServiceProvider implementation ────────────────────────────────────────
    override lateinit var transcriptionRepo: TranscriptionRepository
    override lateinit var llmRepo: LlmRepository
    override lateinit var wavRecorder: WavRecorder
    override lateinit var audioConverter: AudioConverter
    override lateinit var transcribeUseCase: TranscribeAudioUseCase
    override lateinit var extractReportUseCase: ExtractReportUseCase

    private var gdriveDownloadId = -1L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) initVoskModel() }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != gdriveDownloadId) return
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = dm.query(DownloadManager.Query().setFilterById(id))
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                    Uri.parse(localUri).path?.let { loadLlmModelFromFile(File(it)) }
                }
            }
            cursor.close()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Wire dependencies
        val voskService = VoskTranscriptionService()
        val llmProcessor = LlmProcessor()
        transcriptionRepo = voskService
        llmRepo = llmProcessor
        wavRecorder = WavRecorder(cacheDir)
        audioConverter = AudioConverter(this)
        transcribeUseCase = TranscribeAudioUseCase(transcriptionRepo)
        extractReportUseCase = ExtractReportUseCase(llmRepo)

        // Setup navigation with toolbar
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        binding.toolbar.setupWithNavController(navController)

        // Register download receiver
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, filter)
        }

        // Init models
        checkPermissionAndInit()
    }

    private fun checkPermissionAndInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) initVoskModel()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun initVoskModel() {
        transcriptionRepo.initModel(this, object : TranscriptionListener {
            override fun onModelReady() {
                runOnUiThread {
                    vm.isVoskReady.value = true
                    scanAndAutoLoadTaskModel()
                }
            }
            override fun onModelError(error: String) { Log.e(TAG, "Vosk: $error") }
            override fun onPartialResult(text: String) {}
            override fun onFinalResult(text: String) {}
            override fun onError(error: String) {}
        })
    }

    // ── LLM model loading (called from CaptureFragment) ──────────────────────

    fun loadLlmModelFromUri(uri: Uri) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val modelFile = File(cacheDir, "llm_model.task")
                    if (modelFile.exists()) modelFile.delete()
                    contentResolver.openInputStream(uri)?.use { input ->
                        modelFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw Exception("Failed to open stream")
                    val result = llmRepo.loadModel(this@MainActivity, modelFile.absolutePath)
                    withContext(Dispatchers.Main) {
                        result.onSuccess { vm.isLlmReady.value = true }
                            .onFailure { e -> Log.e(TAG, "LLM load failed", e) }
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Model copy failed", e) }
        }
    }

    private fun loadLlmModelFromFile(file: File) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val result = llmRepo.loadModel(this@MainActivity, file.absolutePath)
                    withContext(Dispatchers.Main) {
                        result.onSuccess { vm.isLlmReady.value = true }
                            .onFailure { e -> Log.e(TAG, "LLM load failed", e) }
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Model load failed", e) }
        }
    }

    fun downloadModelFromGDrive() {
        val fileId = getString(R.string.gdrive_model_file_id)
        if (fileId == "YOUR_GDRIVE_FILE_ID_HERE") {
            Toast.makeText(this, getString(R.string.gdrive_not_configured), Toast.LENGTH_LONG).show()
            return
        }
        val url = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        val destFile = File(getExternalFilesDir(null), "llm_model.task")
        if (destFile.exists()) destFile.delete()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(getString(R.string.download_model))
            .setDescription(getString(R.string.downloading_model))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destFile))
            .setAllowedOverMetered(true)
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        gdriveDownloadId = dm.enqueue(request)
        Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
    }

    private fun scanAndAutoLoadTaskModel() {
        scope.launch(Dispatchers.IO) {
            val found = scanForTaskModel()
            if (found != null) {
                Log.d(TAG, "Auto-found model: ${found.absolutePath}")
                withContext(Dispatchers.Main) { loadLlmModelFromFile(found) }
            }
        }
    }

    private fun scanForTaskModel(): File? {
        val searchDirs = listOfNotNull(
            getExternalFilesDir(null), filesDir, cacheDir,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        val allMatches = mutableListOf<File>()
        for (dir in searchDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in listOf("litertlm", "task") }
                ?.let { allMatches.addAll(it) }
        }
        return allMatches.maxByOrNull { if (it.extension.lowercase() == "litertlm") 1 else 0 }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        transcriptionRepo.destroy()
        llmRepo.destroy()
        try { unregisterReceiver(downloadReceiver) } catch (_: Exception) {}
    }
}
