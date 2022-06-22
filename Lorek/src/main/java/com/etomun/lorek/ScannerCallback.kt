package com.etomun.lorek

interface ScannerCallback {
    fun onScanResult(text: String?)
    fun onFailed(message: String)
}