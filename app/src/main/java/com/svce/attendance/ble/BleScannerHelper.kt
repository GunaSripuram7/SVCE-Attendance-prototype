package com.svce.attendance.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer
import java.util.*

@SuppressLint("MissingPermission")
class BleScannerHelper(
    private val context: Context,
    private val serviceUuid: UUID,
    private val onDeviceFound: (Int) -> Unit,
    private val onScanFailure: (Int) -> Unit
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    private val scanner = bluetoothManager.adapter.bluetoothLeScanner ?: throw IllegalStateException("Bluetooth LE Scanner not available")

    private val scanFilters: List<android.bluetooth.le.ScanFilter> = listOf(
        android.bluetooth.le.ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()
    )

    private val scanSettings: android.bluetooth.le.ScanSettings = android.bluetooth.le.ScanSettings.Builder()
        .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY) // Fast scan to get frequent results
        .build()

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceAddress = result.device.address
            val rssi = result.rssi
            Log.d("BleScannerHelper", "ScanResult from device: $deviceAddress, RSSI: $rssi")

            val serviceData = result.scanRecord?.serviceData
            if (serviceData == null) {
                Log.d("BleScannerHelper", "No service data present in scan record from $deviceAddress")
                return
            }

            val data = serviceData[ParcelUuid(serviceUuid)]
            if (data != null && data.size >= 4) {
                // Decode 4-byte little endian int representing BLE code
                val code = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                Log.d("BleScannerHelper", "Decoded BLE code: $code from device $deviceAddress")

                // Notify listener every time a scan is received for the device
                onDeviceFound(code)
            } else {
                Log.d("BleScannerHelper", "Service data missing or too short (size=${data?.size ?: 0}) from $deviceAddress")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScannerHelper", "BLE Scan failed with error code $errorCode")
            onScanFailure(errorCode)
        }
    }

    fun startScanning() {
        Log.d("BleScannerHelper", "Starting BLE scan with UUID $serviceUuid")
        scanner.startScan(scanFilters, scanSettings, scanCallback)
    }

    fun stopScanning() {
        Log.d("BleScannerHelper", "Stopping BLE scan")
        scanner.stopScan(scanCallback)
    }
}
