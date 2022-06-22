package com.etomun.lorek.zxing

import android.graphics.Bitmap
import com.etomun.lorek.ScanMode
import com.etomun.lorek.zxingFormat
import com.google.zxing.*
import com.google.zxing.DecodeHintType.CHARACTER_SET
import com.google.zxing.DecodeHintType.POSSIBLE_FORMATS
import com.google.zxing.common.HybridBinarizer
import java.util.*

class BitmapAnalyzer2(
    private val bitmap: Bitmap,
    private val mode: ScanMode,
    private val onDetected: (result: Result) -> Unit,
    private val onNotFound: (message: String) -> Unit
) {
    private val reader: MultiFormatReader by lazy { initReader() }

    private fun initReader(): MultiFormatReader =
        Hashtable<DecodeHintType, Any>().run {
            this[POSSIBLE_FORMATS] = Vector<BarcodeFormat>().apply { addAll(zxingFormat(mode)) }
            this[CHARACTER_SET] = "UTF-8"
            MultiFormatReader().also { it.setHints(this) }
        }

    private fun getRGBLuminanceSource(): RGBLuminanceSource {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return RGBLuminanceSource(width, height, pixels)
    }

    fun analyze() {
        try {
            val bitmap = BinaryBitmap(HybridBinarizer(getRGBLuminanceSource()))
            onDetected(reader.decodeWithState(bitmap))
        } catch (e: NotFoundException) {
            e.printStackTrace()
            onNotFound(e.message.toString())
        }
    }

}