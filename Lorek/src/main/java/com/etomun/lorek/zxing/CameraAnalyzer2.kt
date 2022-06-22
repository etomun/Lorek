package com.etomun.lorek.zxing

import android.graphics.ImageFormat.*
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.etomun.lorek.ScanMode
import com.etomun.lorek.toByteArray
import com.etomun.lorek.zxingFormat
import com.google.zxing.*
import com.google.zxing.DecodeHintType.CHARACTER_SET
import com.google.zxing.DecodeHintType.POSSIBLE_FORMATS
import com.google.zxing.common.HybridBinarizer
import java.util.*

class CameraAnalyzer2(
    private val scanRect: Rect,
    private val mode: ScanMode,
    private val onDetected: (result: Result) -> Unit
) : ImageAnalysis.Analyzer {
    private val reader: MultiFormatReader by lazy { initReader() }

    private fun initReader(): MultiFormatReader =
        Hashtable<DecodeHintType, Any>().run {
            this[POSSIBLE_FORMATS] = Vector<BarcodeFormat>().apply { addAll(zxingFormat(mode)) }
            this[CHARACTER_SET] = "UTF-8"
            MultiFormatReader().also { it.setHints(this) }
        }

    override fun analyze(image: ImageProxy) {
        if (!arrayOf(YUV_420_888, YUV_422_888, YUV_444_888).contains(image.format)) {
            image.close()
            throw Throwable("expect YUV_420_888, now = ${image.format}")
        }

        val data = image.planes[0].buffer.toByteArray()
        val width = image.width
        val height = image.height
        val rotateByteArray = rotateImageByte(data, width, height)
        val source = PlanarYUVLuminanceSource(
            rotateByteArray,
            height,
            width,
            scanRect.left,
            scanRect.top,
            scanRect.width(),
            scanRect.height(),
            false
        )

        try {
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            onDetected(reader.decodeWithState(bitmap))
        } catch (e: Exception) {
            image.close()
        } finally {
            image.close()
        }
    }

    private fun rotateImageByte(oldByteData: ByteArray, width: Int, height: Int): ByteArray {
        val rotationData = ByteArray(oldByteData.size)
        var j: Int
        var k: Int
        for (y in 0 until height) {
            for (x in 0 until width) {
                j = x * height + height - y - 1
                k = x + y * width
                rotationData[j] = oldByteData[k]
            }
        }
        return rotationData
    }
}