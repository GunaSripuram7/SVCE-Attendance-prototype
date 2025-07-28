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
import java.nio.ByteOrder
import java.util.UUID

@SuppressLint("MissingPermission")
class BleAdvertiserHelper(
    private val context: Context,
    private val serviceUuid: UUID
) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private val TAG = "BleAdvertiserHelper"

    /**
     * Begin BLE advertising of a 4-byte integer.
     *
     * @param payloadInt integer code that uniquely represents the student / roll-number
     * @param onSuccess  invoked when Android reports the advertiser is running
     * @param onFailure  invoked with the system error-code on failure
     */
    fun startAdvertising(
        payloadInt: Int,
        onSuccess: () -> Unit = {},
        onFailure: (Int) -> Unit = {}
    ) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter is OFF – cannot advertise")
            onFailure(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE advertiser not available on this device")
            onFailure(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
            return
        }

        /* ----------  PAYLOAD FIX  ----------
         * Write the 32-bit int in LITTLE-ENDIAN order so the scanner’s
         * ByteBuffer.order(ByteOrder.LITTLE_ENDIAN) decodes the same value.
         */
        val intBytes = ByteBuffer
            .allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)   // <<<<  critical line
            .putInt(payloadInt)
            .array()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), intBytes)
            .setIncludeDeviceName(false)
            .build()

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Advertising started, code=$payloadInt")
                onSuccess()
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed ($errorCode), code=$payloadInt")
                onFailure(errorCode)
            }
        }

        advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }

    /** Stops any ongoing BLE advertising session. */
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        Log.d(TAG, "Advertising stopped")
    }
}
