package com.etomun.lorek

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class ScanMode : Parcelable {
    @Parcelize
    data class QRCode(private val mode: Int = 0) : ScanMode()
    @Parcelize
    data class BarCode(private val mode: Int = 1) : ScanMode()

    companion object {
        internal fun getParams(mode: Int): ScanMode =
            when (mode) {
                0 -> QRCode()
                1 -> BarCode()
                else -> QRCode()
            }
    }
}