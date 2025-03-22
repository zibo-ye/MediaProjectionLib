package com.t34400.mediaprojectionlib.zxing

import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.t34400.mediaprojectionlib.core.ICapturedScreenData
import com.t34400.mediaprojectionlib.core.IEventListener
import com.t34400.mediaprojectionlib.core.ScreenImageProcessManager
import serializeResult
import java.io.Closeable

@Suppress("unused")
class BarcodeReader (
    private val screenImageProcessManager: ScreenImageProcessManager,
    possibleFormatString: String,
    private val cropRequired: Boolean,
    private val cropLeft: Int,
    private val cropTop: Int,
    private val cropWidth: Int,
    private val cropHeight: Int,
    private val tryHarder: Boolean
) : IEventListener<ICapturedScreenData>, Closeable {

    private val reader: MultiFormatReader

    private var isReading = false
    private var latestResult: Result? = null

    init {
        screenImageProcessManager.screenDataAvailableEvent.addListener(this)

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

    override fun onEvent(data: ICapturedScreenData) {
        val source = when (data.type) {
            ICapturedScreenData.Type.ARGB8888 -> RGBLuminanceSource(data.width, data.height, data.getPixels())
            ICapturedScreenData.Type.YUV420 -> PlanarYUVLuminanceSource(data.getByteArray(), data.width, data.height, 0, 0, data.width, data.height, false)
        }.apply {
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
            readBarcode(source).let { result ->
                synchronized(this) {
                    result?.let {
                        latestResult = it
                    }
                    isReading = false
                }
            }
        }.start()
    }

    override fun close() {
        screenImageProcessManager.screenDataAvailableEvent.removeListener(this)
    }

    @Suppress("unused")
    fun getLatestResult() : String {
        synchronized(this) {
            return latestResult?.let {
                serializeResult(it)
            } ?: ""
        }
    }

    private fun readBarcode(source: LuminanceSource): Result? {
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