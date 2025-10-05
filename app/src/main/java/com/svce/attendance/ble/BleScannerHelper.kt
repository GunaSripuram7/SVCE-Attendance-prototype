package com.svce.attendance.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*


// ADD THIS DATA CLASS HERE (after imports, before class declaration)
data class StudentPayload(
    val rollNumberHash: Int,
    val androidIdHash: Int,
    val deviceFingerprintHash: Int,
    val deviceAddress: String,
    val rollNumber: String = ""
)
@SuppressLint("MissingPermission")
class BleScannerHelper(
    private val context: Context,
    private val serviceUuid: UUID,
    private val onStudentFound: (StudentPayload) -> Unit,
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
            if (data != null && data.size >= 12) { // Now expecting 12 bytes
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                // Decode 12-byte payload
                val rollNumberHash = buffer.int         // First 4 bytes: Roll number hash
                val androidIdHash = buffer.int          // Next 4 bytes: Android ID hash
                val deviceFingerprintHash = buffer.int  // Last 4 bytes: Device fingerprint hash

                Log.d("BleScannerHelper", "Decoded - Roll hash: $rollNumberHash, AndroidID: $androidIdHash, Fingerprint: $deviceFingerprintHash from $deviceAddress")

                val payload = StudentPayload(rollNumberHash, androidIdHash, deviceFingerprintHash, deviceAddress)
                onStudentFound(payload)
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
