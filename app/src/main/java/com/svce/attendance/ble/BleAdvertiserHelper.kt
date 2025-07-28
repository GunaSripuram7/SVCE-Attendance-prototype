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
import java.nio.ByteBuffer
import java.util.*

@SuppressLint("MissingPermission")
class BleAdvertiserHelper(
    private val context: Context,
    private val serviceUuid: UUID
) {
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    /**
     * Start BLE advertising with a 4-byte integer payload.
     * @param payloadInt The integer code to advertise as service data
     * @param onSuccess Callback on successful advertising start
     * @param onFailure Callback on failure with error code
     */
    fun startAdvertising(
        payloadInt: Int,
        onSuccess: () -> Unit,
        onFailure: (Int) -> Unit
    ) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        advertiser = adapter.bluetoothLeAdvertiser

        // Convert int to 4-byte array (big-endian)
        val intBytes = ByteBuffer.allocate(4).putInt(payloadInt).array()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), intBytes)
            .setIncludeDeviceName(false)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BleAdvertiserHelper", "Advertising started, code=$payloadInt")
                onSuccess()
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BleAdvertiserHelper", "Advertise failed($errorCode), code=$payloadInt")
                onFailure(errorCode)
            }
        }

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    /** Stop any ongoing BLE advertising. */
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }
}
