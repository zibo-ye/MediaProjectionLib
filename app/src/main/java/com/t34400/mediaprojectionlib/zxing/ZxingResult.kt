import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.google.zxing.Result
import com.google.zxing.ResultPoint

@Serializable
data class ZxingResult(
    val text: String,
    val format: String,
    val numBits: Int,
    val rawBytes: List<Int>,
    val resultPoints: List<ResultPointData>,
    val timestamp: Long
) {
    companion object {
        fun from(result: Result): ZxingResult {
            return ZxingResult(
                text = result.text,
                format = result.barcodeFormat.toString(),
                numBits = result.numBits,
                rawBytes = result.rawBytes?.map { it.toInt() } ?: emptyList(),
                resultPoints = result.resultPoints?.map { ResultPointData.from(it) } ?: emptyList(),
                timestamp = result.timestamp
            )
        }
    }
}

@Serializable
data class ResultPointData(
    val x: Float,
    val y: Float
) {
    companion object {
        fun from(point: ResultPoint): ResultPointData {
            return ResultPointData(point.x, point.y)
        }
    }
}

fun serializeResult(result: Result): String {
    val zxingResult = ZxingResult.from(result)
    return Json.encodeToString(zxingResult)
}