package com.etomun.zebracross

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import com.etomun.lorek.ScannerCallback
import com.etomun.lorek.ScannerFragment
import com.etomun.lorek.toggleGone
import com.etomun.zebracross.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity(), ScannerCallback {
    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding get() = _binding!!
    private lateinit var fragment: ScannerFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btQrcode.setOnClickListener {
            fragment = ScannerFragment.qrcode(getString(R.string.subtext_qr_scanner))
            attachFragment()
        }
        binding.btBarcode.setOnClickListener {
            fragment = ScannerFragment.barcode(getString(R.string.subtext_barcode_scanner))
            attachFragment()
        }
    }

    override fun onBackPressed() {
        if (binding.fragmentContainer.display != null && binding.tvResult.isGone) {
            onScannerVisible(false)
        }
        super.onBackPressed()
    }

    override fun onScanResult(text: String?) {
        supportFragmentManager.beginTransaction().let {
            it.remove(fragment)
            it.commit()
            onScannerVisible(false)
        }
        binding.tvResult.text = text.orEmpty()
    }

    override fun onFailed(message: String) {
        binding.root.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }

    private fun attachFragment() {
        supportFragmentManager.beginTransaction().let {
            it.replace(R.id.fragment_container, fragment)
            it.addToBackStack(null)
            it.commit()
            onScannerVisible(true)
        }
    }

    private fun onScannerVisible(visible: Boolean) {
        binding.btBarcode.toggleGone(!visible)
        binding.btQrcode.toggleGone(!visible)
        binding.tvResult.toggleGone(!visible)
    }
}