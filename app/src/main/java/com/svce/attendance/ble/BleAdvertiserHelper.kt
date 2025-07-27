package com.svce.attendance.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
     * Start BLE advertising with a generic payload string (typically a roll number).
     * @param payload The string to advertise as service data
     * @param onSuccess Callback on successful advertising start
     * @param onFailure Callback on failure with error code
     */
    fun startAdvertising(payload: String, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), payload.toByteArray(Charsets.UTF_8))
            .setIncludeDeviceName(false)
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
    }
}
