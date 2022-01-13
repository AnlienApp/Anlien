package com.zitrouille.anlien

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

/**
 * This class is used to lunch and analyse picture of qr code
 */

class ScannerActivity : AppCompatActivity() {

    var mCodeScanner: CodeScanner? = null
    private var mScanView: CodeScannerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        mScanView = findViewById(R.id.scanner_view)
        mCodeScanner = CodeScanner(this, mScanView!!)
        mCodeScanner!!.setDecodeCallback {
            setResult(RESULT_OK, Intent().putExtra("qrCode", it.text))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        requestForCamera()
    }

    override fun onPause() {
        mCodeScanner!!.releaseResources()
        super.onPause()
    }

    private fun requestForCamera() {
        Dexter.withActivity(this)
            .withPermissions(
                android.Manifest.permission.CAMERA,
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        mCodeScanner!!.startPreview()
                    }
                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@ScannerActivity,
                            "You have denied location permission. Please allow it is mandatory.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {

                }
            }).onSameThread()
            .check()
    }
}