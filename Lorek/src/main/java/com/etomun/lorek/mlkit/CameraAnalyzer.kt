package com.etomun.lorek.mlkit

import android.annotation.SuppressLint
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.etomun.lorek.ScanMode
import com.etomun.lorek.mlFormat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.nio.ReadOnlyBufferException


class CameraAnalyzer(
    private val scanRect: Rect,
    private val mode: ScanMode,
    private val onDetected: (result: String) -> Unit
) : ImageAnalysis.Analyzer {
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        val img = image.image
        if (img != null) {
//            img.cropRect = scanRect
            val inputImage = InputImage.fromMediaImage(img, image.imageInfo.rotationDegrees)

//            Scan with Crop Rect
//            val imageArr = yuv420888ToNv21(img)
//            val croppedArr = cropNv21(imageArr, img.width, img.height, scanRect)
//            val inputImage = InputImage.fromByteArray(
//                croppedArr,
//                scanRect.width(),
//                scanRect.height(),
//                image.imageInfo.rotationDegrees,
//                InputImage.IMAGE_FORMAT_NV21
//            )

            val formats = mlFormat(mode)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(formats[0], *formats)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        for (barcode in barcodes) {
                            onDetected(barcode.rawValue.orEmpty())
                        }
                    }
                }
                .addOnFailureListener {
                    it.printStackTrace()
                    onDetected(it.stackTrace.joinToString("\n"))
                }
                .addOnCompleteListener {
                    image.close()
                }
        }
    }

    private fun cropNv21(src: ByteArray, width: Int, height: Int, cropRect: Rect): ByteArray {
        val cropLeft = cropRect.left * 2
        val cropTop = cropRect.top
        val cropWidth = cropRect.width()
        val cropHeight = cropRect.height()

        if (cropLeft > width || cropTop > height) {
            return ByteArray(0)
        }

        val yUnit = cropWidth * cropHeight
        val uv = yUnit / 2
        val nData = ByteArray(yUnit + uv)

        val uvIndexDst = cropWidth * cropHeight - cropTop / 2 * cropWidth
        val uvIndexSrc = width * height + cropLeft

        var srcPos0 = cropTop * width
        var destPos0 = 0
        var uvSrcPos0 = uvIndexSrc
        var uvDestPos0 = uvIndexDst

        for (i in cropTop until cropTop + cropHeight) {
            //y memory block copy
            System.arraycopy(src, srcPos0 + cropLeft, nData, destPos0, cropWidth)
            srcPos0 += width
            destPos0 += cropWidth
            if (i and 1 == 0) {
                //uv memory block copy
                System.arraycopy(src, uvSrcPos0, nData, uvDestPos0, cropWidth)
                uvSrcPos0 += width
                uvDestPos0 += cropWidth
            }
        }
        return nData
    }

    private fun cropNv21X(src: ByteArray, width: Int, height: Int, cropRect: Rect): ByteArray {
        val cropLeft = cropRect.left
        val cropTop = cropRect.top
        val cropWidth = cropRect.width()
        val cropHeight = cropRect.height()

        if (cropLeft > width || cropTop > height) {
            return ByteArray(0)
        }

        val yUnit = cropWidth * cropHeight
        val srcUnit = width * height

        val uv = yUnit / 2
        val nData = ByteArray(yUnit + uv)

        var nPos = (cropTop - 1) * width
        var mPos: Int

        for (i in cropTop until cropTop + cropHeight) {
            nPos += width
            mPos = srcUnit + (i shr 1) * width

            for (j in cropLeft until cropLeft + cropWidth) {
                val dest = (i - cropTop + 1) * cropWidth - j + cropLeft - 1
//                System.arraycopy(src, nPos + j, nData, dest, cropWidth)
                nData[dest] = src[nPos + j]

                if ((i and 1) == 0 && (mPos + j) < src.size) {
                    var m = yUnit + ((i - cropTop shr 1) + 1) * cropWidth - j + cropLeft - 1
//                    var m = cropWidth * cropHeight - cropTop / 2 * cropWidth
                    if ((m and 1) == 0 && (mPos + j) < src.size) {
                        m++
//                        System.arraycopy(src, mPos + j, nData, m, cropWidth)
                        nData[m] = src[mPos + j]
                        continue
                    }
                    m--
//                    System.arraycopy(src, mPos + j, nData, m, cropWidth)
                    nData[m] = src[mPos + j]
                }
            }
        }

        return nData
    }

    private fun yuv420888ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val size = ySize + uvSize * 2
        val nv21 = ByteArray(size)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        var rowStride = image.planes[0].rowStride

        assert(image.planes[0].pixelStride == 1)
        var pos = 0

        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            var yBufferPos = -rowStride
            while (pos < ySize) {
                yBufferPos += rowStride
                yBuffer.get(yBufferPos)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride
        assert(rowStride == image.planes[1].rowStride)
        assert(pixelStride == image.planes[1].pixelStride)

        if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
            val savePixel = vBuffer[1]
            try {
                vBuffer.put(1, savePixel)
                if (uBuffer[0] == savePixel) {
                    vBuffer.put(1, savePixel)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer.get(nv21, ySize, 1)
                    vBuffer.get(nv21, ySize, 1)
                }
            } catch (e: ReadOnlyBufferException) {
                e.printStackTrace()
            }
            vBuffer.put(1, savePixel)
        }

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer.get(vuPos)
                nv21[pos++] = uBuffer.get(vuPos)
            }
        }

        return nv21
    }


}