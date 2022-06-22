package com.etomun.lorek.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.etomun.lorek.ScanMode
import com.etomun.lorek.mlFormat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class FileAnalyzer private constructor(
    private var mode: ScanMode,
    private val context: Context?,
    private val uri: Uri?,
    private val bitmap: Bitmap?,
    private val onDetected: ((String: String) -> Unit)?,
    private val onError: ((message: String) -> Unit)?
) {

    fun analyze() {
        try {
            val image = if (context != null && uri != null) {
                InputImage.fromFilePath(context, uri)
            } else if (bitmap != null) {
                InputImage.fromBitmap(bitmap, 0)
            } else {
                null
            }

            val formats = mlFormat(mode)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(formats[0], *formats)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            image?.let {
                scanner.process(it)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            for (barcode in barcodes) {
                                onDetected?.let { it1 -> it1(barcode.rawValue.orEmpty()) }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        showError(e.message.toString())
                    }
            } ?: showError("Failed create InputImage")

        } catch (e: Exception) {
            e.printStackTrace()
            showError(e.message.toString())
        }
    }

    private fun showError(message: String) {
        onError?.let { it(message) }
    }

    data class Builder(
        private var mode: ScanMode,
        private var context: Context? = null,
        private var uri: Uri? = null,
        private var bitmap: Bitmap? = null,
        private var onDetected: ((String: String) -> Unit)? = null,
        private var onError: ((message: String) -> Unit)? = null
    ) {
        fun load(bitmap: Bitmap) = apply { this.bitmap = bitmap }

        fun load(context: Context, uri: Uri) = apply {
            this.context = context
            this.uri = uri
        }

        fun onDetected(onDetected: (String: String) -> Unit) = apply {
            this.onDetected = onDetected
        }

        fun onError(onError: (message: String) -> Unit) = apply {
            this.onError = onError
        }

        fun build() =
            FileAnalyzer(mode, context, uri, bitmap, onDetected, onError)

    }
}