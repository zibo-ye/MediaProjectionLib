package com.t34400.mediaprojectionlib.mlkit

import com.google.mlkit.vision.barcode.common.Barcode

val formatMap = mapOf(
    "FORMAT_CODE_128" to Barcode.FORMAT_CODE_128,
    "FORMAT_CODE_39" to Barcode.FORMAT_CODE_39,
    "FORMAT_CODE_93" to Barcode.FORMAT_CODE_93,
    "FORMAT_CODABAR" to Barcode.FORMAT_CODABAR,
    "FORMAT_DATA_MATRIX" to Barcode.FORMAT_DATA_MATRIX,
    "FORMAT_EAN_13" to Barcode.FORMAT_EAN_13,
    "FORMAT_EAN_8" to Barcode.FORMAT_EAN_8,
    "FORMAT_ITF" to Barcode.FORMAT_ITF,
    "FORMAT_QR_CODE" to Barcode.FORMAT_QR_CODE,
    "FORMAT_UPC_A" to Barcode.FORMAT_UPC_A,
    "FORMAT_UPC_E" to Barcode.FORMAT_UPC_E,
    "FORMAT_PDF417" to Barcode.FORMAT_PDF417,
    "FORMAT_AZTEC" to Barcode.FORMAT_AZTEC
)

val reverseFormatMap = formatMap.entries.associate { it.value to it.key }

fun stringToFormat(formatString: String): Int {
    return formatMap[formatString] ?: Barcode.FORMAT_UNKNOWN
}

fun formatToString(format: Int): String {
    return reverseFormatMap[format] ?: "FORMAT_UNKNOWN"
}