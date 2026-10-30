package com.marvindi.mediaoptimizer.viewmodel

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marvindi.mediaoptimizer.R
import com.marvindi.mediaoptimizer.model.OutputFormat
import com.marvindi.mediaoptimizer.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the selected photos and conversion options, and runs the
 * batch conversion job. Progress and status are exposed as Compose
 * state so the UI updates automatically.
 */
class CompressViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ImageRepository(application)
    private val TAG = "CompressViewModel"

    var uris by mutableStateOf<List<Uri>>(emptyList())
    var format by mutableStateOf(OutputFormat.HEIF)
    var quality by mutableStateOf(85f)
    var preserveExif by mutableStateOf(true)
    var isProcessing by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var statusMessage by mutableStateOf<String?>(null)

    val outputFolder: String
        get() = ImageRepository.OUTPUT_FOLDER

    fun addUris(newUris: List<Uri>) {
        uris = uris + newUris.filterNot { it in uris }
    }

    fun clear() {
        if (!isProcessing) {
            uris = emptyList()
            statusMessage = null
            progress = 0f
        }
    }

    fun start() {
        if (isProcessing || uris.isEmpty()) return

        isProcessing = true
        progress = 0f
        statusMessage = null

        viewModelScope.launch {
            val list = uris
            var success = 0
            var failed = 0
            var saved = 0L

            for (uri in list) {
                try {
                    val result = withContext(Dispatchers.Default) {
                        repository.compress(uri, format, quality.toInt(), preserveExif)
                    }
                    saved += result.savedBytes
                    success++
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to compress $uri", e)
                    failed++
                }
                progress = (success + failed).toFloat() / list.size
            }

            val app = getApplication<Application>()
            val savedText = Formatter.formatShortFileSize(app, saved)
            statusMessage = app.getString(R.string.result_message, success, failed, savedText, outputFolder)
            isProcessing = false
        }
    }
}
