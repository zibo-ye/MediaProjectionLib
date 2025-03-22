package com.t34400.mediaprojectionlib.mlkit

import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.t34400.mediaprojectionlib.core.ICapturedScreenData
import com.t34400.mediaprojectionlib.core.IEventListener
import com.t34400.mediaprojectionlib.core.ScreenImageProcessManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Suppress("unused")
class MlKitBarcodeReader (
    private val screenImageProcessManager: ScreenImageProcessManager,
    possibleFormatString: String,
) : IEventListener<ICapturedScreenData>, Closeable {

    private val scanner: BarcodeScanner
    private val executor: Executor

    private var isReading = false
    private var latestResults: MlKitResults? = null

    init {
        screenImageProcessManager.screenDataAvailableEvent.addListener(this)

        executor = Executors.newSingleThreadExecutor()

        val possibleFormats = possibleFormatString.split(" ")
            .map(::stringToFormat)
            .toIntArray()
        Log.d(TAG, "Possible formats: ${possibleFormats.joinToString(" ")}")

        val options = BarcodeScannerOptions.Builder().apply {
                when (possibleFormats.size) {
                    0 -> enableAllPotentialBarcodes()
                    1 -> setBarcodeFormats(possibleFormats[0])
                    else -> setBarcodeFormats(possibleFormats[0], *possibleFormats.sliceArray(1 until possibleFormats.size))
                }
            }
            .build()
        scanner = BarcodeScanning.getClient(options)
    }

    override fun onEvent(data: ICapturedScreenData) {
        synchronized(this) {
            if (isReading) {
                return
            }

            isReading = true
        }

        val image = InputImage.fromBitmap(data.getBitmap(), 0)
        val timestamp = data.timestamp

        scanner.process(image)
            .addOnCompleteListener(executor) { task ->
                synchronized(this) {
                    isReading = false
                }

                if (task.isSuccessful) {
                    val results = task.result

                    Log.v(TAG, "${results.size} barcode found.")

                    synchronized(this) {
                        latestResults = MlKitResults.from(results, timestamp)
                    }
                } else {
                    Log.v(TAG, "No barcode found.")
                }
            }
    }

    override fun close() {
        screenImageProcessManager.screenDataAvailableEvent.removeListener(this)
        scanner.close()
    }

    @Suppress("unused")
    fun getLatestResult() : String {
        synchronized(this) {
            return latestResults?.let {
                Json.encodeToString(latestResults)
            } ?: ""
        }
    }

    companion object {
        private val TAG = MlKitBarcodeReader::class.java.simpleName
    }
}