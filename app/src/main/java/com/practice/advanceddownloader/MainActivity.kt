package com.practice.advanceddownloader

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.practice.advanceddownloader.databinding.ActivityMainBinding
import com.practice.advanceddownloader.dispatcher.PausingDispatchQueue
import com.practice.advanceddownloader.dispatcher.PausingDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val queue = PausingDispatchQueue()
    private val pausingDispatcher = PausingDispatcher(queue, Dispatchers.IO)
    private val cancelToken = AtomicBoolean(false)
    private val downloadFlow = MutableSharedFlow<String>(extraBufferCapacity = 1) // To emit URLs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initComponent()
        initListener()

        // Collect download flow with flatMapLatest
        lifecycleScope.launch {
            downloadFlow
                .flatMapLatest { url ->
                    startDownload(url)
                }
                .collect { result ->
                    Log.d(TAG, result)
                }
        }
    }

    private fun initComponent() {
        // Initialize any components here if needed
    }

    private fun initListener() {
        binding.btnDownload.setOnClickListener {
            val outputFile = File(this.filesDir, "downloaded_large_file.iso")
            val url = binding.etUrl.text.toString()

            // Reset the cancel token before starting a new download
            cancelToken.set(false)

            // Emit the URL to start a new download
            lifecycleScope.launch {
                downloadFlow.emit(url)
            }
        }

        binding.btnPause.setOnClickListener {
            queue.pause()
            Log.d(TAG, "Download paused")
        }

        binding.btnResume.setOnClickListener {
            queue.resume()
            Log.d(TAG, "Download resumed")
        }

        binding.btnCancel.setOnClickListener {
            cancelToken.set(true)
            Log.d(TAG, "Download canceled")
        }
    }

    private fun startDownload(url: String = "https://archive.org/download/BigBuckBunny_328/BigBuckBunny_512kb.mp4"): Flow<String> = flow {
        val outputFile = File(this@MainActivity.filesDir, "downloaded_large_file.iso")
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP error code: ${connection.responseCode}")
            }

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputFile)

            val buffer = ByteArray(1024)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                // Check for cancellation
                if (cancelToken.get()) {
                    Log.d(TAG, "Download canceled!")
                    outputStream.close()
                    inputStream.close()
                    outputFile.delete()
                    emit("Download canceled!")
                    return@flow
                }

                // Check for pause
                while (queue.isPaused) {
                    Log.d(TAG, "Download paused... waiting to resume.")
                    // Sleep briefly to avoid busy-waiting while paused
                    delay(500)
                }

                // Continue downloading
                Log.d(TAG, "Downloading file $bytesRead")
                outputStream.write(buffer, 0, bytesRead)
            }

            emit("Download completed!")
            Log.d(TAG, "Download completed!")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file: ${e.message}")
            emit("Error downloading file: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO) // Ensure emissions happen on IO dispatcher

    companion object {
        private const val TAG = "MainActivity"
    }
}