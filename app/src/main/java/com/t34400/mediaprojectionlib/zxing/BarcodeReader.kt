package com.t34400.mediaprojectionlib.zxing

import android.media.Image
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.Result
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.t34400.mediaprojectionlib.core.IEventListener
import com.t34400.mediaprojectionlib.core.MediaProjectionManager
import com.t34400.mediaprojectionlib.utils.ImageUtils
import serializeResult
import java.io.Closeable

class BarcodeReader (
    private val mediaProjectionManager: MediaProjectionManager,
    possibleFormatString: String,
    private val cropRequired: Boolean,
    private val cropLeft: Int,
    private val cropTop: Int,
    private val cropWidth: Int,
    private val cropHeight: Int,
    private val tryHarder: Boolean
) : IEventListener<Image>, Closeable {

    private val reader: MultiFormatReader

    private var isReading = false
    private var result: Result? = null

    init {
        mediaProjectionManager.imageAvailableEvent.addListener(this)

        val possibleFormats = possibleFormatString.split(" ")
            .map { BarcodeFormat.valueOf(it) }
        val hints: HashMap<DecodeHintType?, Any> =
            object : HashMap<DecodeHintType?, Any>() {
                init {
                    put(DecodeHintType.POSSIBLE_FORMATS, possibleFormats)
                    put(DecodeHintType.TRY_HARDER, tryHarder)
                }
            }
        reader = MultiFormatReader().apply {
            setHints(hints)
        }
    }

    override fun onEvent(data: Image) {
        val pixels = ImageUtils.convertToPixels(data)
        val source = RGBLuminanceSource(data.width, data.height, pixels).apply {
            if (cropRequired) {
                crop(cropLeft, cropTop, cropWidth, cropHeight)
            }
        }

        synchronized(this) {
            if (isReading) {
                return
            }
            isReading = true
        }

        Thread {
            readBarcode(source)?.let {
                synchronized(this) {
                    result = it
                    isReading = false
                }
            }
        }.start()
    }

    override fun close() {
        mediaProjectionManager.imageAvailableEvent.removeListener(this)
    }

    fun getLatestResult() : String {
        synchronized(this) {
            return result?.let {
                serializeResult(it)
            } ?: ""
        }
    }

    private fun readBarcode(source: RGBLuminanceSource): Result? {
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(binaryBitmap)
        } catch (e: NotFoundException) {
            Log.v(TAG, "Barcode Not Found")
            return null
        }
    }

    companion object {
        private val TAG = BarcodeReader::class.java.simpleName
    }
}