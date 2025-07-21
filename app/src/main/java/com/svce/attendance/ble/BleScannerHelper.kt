package com.svce.attendance.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.*

class BleScannerHelper(
    private val context: Context,
    private val serviceUuid: UUID
) {
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    fun startScan(onDeviceFound: (roll: String) -> Unit, onScanFailure: (Int) -> Unit = {}) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        scanner = bluetoothAdapter.bluetoothLeScanner

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid)).build()
        val setting = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.scanRecord?.getServiceData(ParcelUuid(serviceUuid))?.let { data ->
                    val roll = data.toString(Charsets.UTF_8)
                    Handler(Looper.getMainLooper()).post {
                        onDeviceFound(roll)
                    }
                }
            }
            override fun onScanFailed(errorCode: Int) {
                onScanFailure(errorCode)
            }
        }
        scanner?.startScan(listOf(filter), setting, scanCallback)
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        scanCallback = null
    }
}
