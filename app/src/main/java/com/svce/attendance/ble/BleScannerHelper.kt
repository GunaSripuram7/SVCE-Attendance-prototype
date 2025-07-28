package com.svce.attendance.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import java.nio.ByteBuffer
import java.util.*

@SuppressLint("MissingPermission")
class BleScannerHelper(
    private val context: Context,
    private val serviceUuid: UUID,
    private val onCodeFound: (Int) -> Unit,
    private val onScanFailure: (Int) -> Unit
) {
    private val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
        .adapter.bluetoothLeScanner

    private val filters = listOf(android.bluetooth.le.ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(serviceUuid))
        .build())

    private val settings = android.bluetooth.le.ScanSettings.Builder()
        .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.scanRecord
                ?.serviceData
                ?.get(ParcelUuid(serviceUuid))
                ?.let { data ->
                    if (data.size >= 4) {
                        // Decode little‐endian 4-byte integer
                        val code = ByteBuffer.wrap(data).int
                        onCodeFound(code)
                    }
                }
        }
        override fun onScanFailed(errorCode: Int) {
            onScanFailure(errorCode)
        }
    }

    fun startScanning() {
        scanner.startScan(filters, settings, callback)
    }
    fun stopScanning() {
        scanner.stopScan(callback)
    }
}
