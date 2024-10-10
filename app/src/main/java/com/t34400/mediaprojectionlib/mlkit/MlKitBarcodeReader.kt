package com.t34400.mediaprojectionlib.mlkit

import android.media.Image
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.t34400.mediaprojectionlib.core.BitmapData
import com.t34400.mediaprojectionlib.core.IEventListener
import com.t34400.mediaprojectionlib.core.MediaProjectionManager
import com.t34400.mediaprojectionlib.zxing.BarcodeReader
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Suppress("unused")
class MlKitBarcodeReader (
    private val mediaProjectionManager: MediaProjectionManager,
    possibleFormatString: String,
) : IEventListener<BitmapData>, Closeable {

    private val scanner: BarcodeScanner
    private val executor: Executor

    private var isReading = false
    private var latestResults: MlKitResults? = null

    init {
        mediaProjectionManager.bitmapAvailableEvent.addListener(this)

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

    override fun onEvent(data: BitmapData) {
        synchronized(this) {
            if (isReading) {
                return
            }

            isReading = true
        }

        val image = InputImage.fromBitmap(data.bitmap, 0)
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
        mediaProjectionManager.bitmapAvailableEvent.removeListener(this)
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