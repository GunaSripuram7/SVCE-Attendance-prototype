package com.svce.attendance.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.*

@SuppressLint("MissingPermission")
class BleAdvertiserHelper(
    private val context: Context,
    private val serviceUuid: UUID
) {
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    /**
     * Start BLE advertising with the student's unique integer BLE code.
     * This is used by the STUDENT.
     * @param bleCode The integer code to advertise as service data.
     */
    fun startAdvertising(bleCode: Int, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser

        // Convert the Int to a single-byte array for the payload
        val dataPayload = byteArrayOf(bleCode.toByte())

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), dataPayload)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BleAdvertiserHelper", "BLE advertising started with code: $bleCode")
                onSuccess()
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("BleAdvertiserHelper", "BLE advertising failed with error code: $errorCode, code: $bleCode")
                onFailure(errorCode)
            }
        }
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    /**
     * Overloaded function to start BLE advertising with a String payload.
     * This is used by the TEACHER for confirmation broadcasts.
     * @param payload The String (roll number) to advertise.
     */
    fun startAdvertising(payload: String, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser

        // Convert the String to a byte array
        val dataPayload = payload.toByteArray(Charsets.UTF_8)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), dataPayload)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BleAdvertiserHelper", "BLE advertising started with payload: $payload")
                onSuccess()
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("BleAdvertiserHelper", "BLE advertising failed with error code: $errorCode, payload: $payload")
                onFailure(errorCode)
            }
        }
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    /**
     * Stop any ongoing BLE advertising.
     */
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        advertiseCallback = null // Clear the callback to prevent memory leaks
    }
}
