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

    /**
     * Start scanning for BLE devices. This version handles both integer codes and string payloads.
     * @param onDeviceFound Callback that receives an integer bleCode (from a student).
     * @param onPayloadFound Callback that receives a string payload (from a teacher).
     * @param onScanFailure Callback on failure with an error code.
     */
    // CHANGED: The signature now accepts two different callbacks for the two data types.
    fun startScan(
        onDeviceFound: (bleCode: Int) -> Unit,
        onPayloadFound: (payload: String) -> Unit,
        onScanFailure: (Int) -> Unit = {}
    ) {
        val hasBleScanPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasBleScanPermission) {
            Log.e("BleScannerHelper", "BLE Scan permission not granted.")
            onScanFailure(-1) // Use a specific code for permission failure
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
                        if (data.isEmpty()) return@let

                        // CHANGED: Check payload length to decide how to parse it
                        if (data.size == 1) {
                            // It's a single-byte integer (bleCode from a student)
                            // Use "and 0xFF" to treat the byte as unsigned (0-255)
                            val receivedBleCode = data[0].toInt() and 0xFF
                            Log.d("BleScannerHelper", "Got BLE code: $receivedBleCode")
                            Handler(Looper.getMainLooper()).post {
                                onDeviceFound(receivedBleCode)
                            }
                        } else {
                            // It's a string payload (rollNumber from a teacher)
                            val receivedPayload = data.toString(Charsets.UTF_8)
                            Log.d("BleScannerHelper", "Got BLE payload: $receivedPayload")
                            Handler(Looper.getMainLooper()).post {
                                onPayloadFound(receivedPayload)
                            }
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("BleScannerHelper", "BLE scan failed with error code: $errorCode")
                    onScanFailure(errorCode)
                }
            }
            scanner?.startScan(listOf(filter), setting, scanCallback)
        } catch (e: SecurityException) {
            Log.e("BleScannerHelper", "SecurityException during scan start.", e)
            e.printStackTrace()
            onScanFailure(-1)
        }
    }

    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e("BleScannerHelper", "SecurityException during scan stop.", e)
            e.printStackTrace()
        }
        scanCallback = null
    }
}
