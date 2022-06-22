package com.etomun.lorek

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.TypedValue
import android.view.View
import android.widget.CompoundButton
import androidx.camera.core.AspectRatio
import androidx.core.view.isVisible
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val RATIO_4_3_VALUE = 4.0 / 3.0
const val RATIO_16_9_VALUE = 16.0 / 9.0

fun zxingFormat(mode: ScanMode): EnumSet<BarcodeFormat> {
    return if (mode is ScanMode.BarCode) {
        EnumSet.of(
            BarcodeFormat.CODABAR,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.EAN_8,
            BarcodeFormat.EAN_13,
            BarcodeFormat.ITF,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.UPC_EAN_EXTENSION
        )
    } else {
        EnumSet.of(
            BarcodeFormat.AZTEC,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.MAXICODE,
            BarcodeFormat.PDF_417,
            BarcodeFormat.QR_CODE,
        )
    }
}

fun mlFormat(mode: ScanMode): IntArray {
    return if (mode is ScanMode.BarCode) {
        intArrayOf(
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
        )
    } else {
        intArrayOf(
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_QR_CODE,
        )
    }
}

/* Convert dp to px */
fun Number.dp() =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()

fun View.visible() {
    if (this.visibility != View.VISIBLE) visibility = View.VISIBLE
}

fun View.invisible() {
    if (this.visibility != View.INVISIBLE) visibility = View.INVISIBLE
}

fun View.gone() {
    if (this.visibility != View.GONE) visibility = View.GONE
}

fun View.isVisible() = this.visibility == View.VISIBLE

fun View.toggleInvisible() {
    if (this.isVisible) {
        this.invisible()
    } else {
        this.visible()
    }
}

fun View.toggleGone() {
    if (this.isVisible) {
        this.gone()
    } else {
        this.visible()
    }
}

fun View.toggleInvisible(show: Boolean) {
    if (show) {
        this.visible()
    } else {
        this.invisible()
    }
}

fun View.toggleGone(show: Boolean) {
    if (show) {
        this.visible()
    } else {
        this.gone()
    }
}

fun CompoundButton.toggleCheck() {
    this.isChecked = !this.isChecked
}

fun ByteBuffer.toByteArray(): ByteArray {
    rewind()
    val data = ByteArray(remaining())
    get(data)
    return data
}

fun aspectRatio(w: Int, h: Int): Int {
    val previewRatio = max(w, h).toDouble() / min(w, h)
    if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
        return AspectRatio.RATIO_4_3
    }
    return AspectRatio.RATIO_16_9
}

@Suppress("DEPRECATION")
fun getBitmap(mActivity: Context, uri: Uri): Bitmap? {
    return try {
        return MediaStore.Images.Media.getBitmap(mActivity.contentResolver, uri)
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}