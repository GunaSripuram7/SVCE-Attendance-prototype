package com.svce.attendance.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*

class BleScannerHelper(
    private val context: Context,
    private val serviceUuid: UUID
) {
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    fun startScan(onDeviceFound: (roll: String) -> Unit, onScanFailure: (Int) -> Unit = {}) {
        val hasBleScanPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasBleScanPermission) {
            onScanFailure(-1)
            return
        }

        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            scanner = bluetoothAdapter.bluetoothLeScanner

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()
            val setting = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.scanRecord?.getServiceData(ParcelUuid(serviceUuid))?.let { data ->
                        val roll = data.toString(Charsets.UTF_8)
                        Log.d("BleScannerHelper", "Got BLE: $roll")
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
        } catch (e: SecurityException) {
            e.printStackTrace()
            onScanFailure(-1)
        }
    }

    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        scanCallback = null
    }
}
