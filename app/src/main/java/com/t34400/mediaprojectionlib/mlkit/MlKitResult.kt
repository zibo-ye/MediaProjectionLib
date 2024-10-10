package com.t34400.mediaprojectionlib.mlkit

import android.graphics.Point
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.serialization.Serializable

@Serializable
data class MlKitResults(
    val results: List<MlKitResult>
) {
    companion object {
        fun from(results: List<Barcode>, timestamp: Long): MlKitResults {
            return MlKitResults(
                results = results.map { MlKitResult.from(it, timestamp) }
            )
        }
    }
}

@Serializable
data class MlKitResult(
    val text: String,
    val format: String,
    val numBits: Int,
    val rawBytes: List<Int>,
    val resultPoints: List<ResultPointData>,
    val timestamp: Long
) {
    companion object {
        fun from(result: Barcode, timestamp: Long): MlKitResult {
            return MlKitResult(
                text = result.displayValue ?: "",
                format = formatToString(result.format),
                numBits = result.rawBytes?.size ?: 0,
                rawBytes = result.rawBytes?.map { it.toInt() } ?: emptyList(),
                resultPoints = result.cornerPoints?.map { ResultPointData.from(it) } ?: emptyList(),
                timestamp = timestamp
            )
        }
    }
}

@Serializable
data class ResultPointData(
    val x: Int,
    val y: Int
) {
    companion object {
        fun from(point: Point): ResultPointData {
            return ResultPointData(point.x, point.y)
        }
    }
}