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
import com.svce.attendance.utils.CustomDeviceFingerprint


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

    /**
     * Advertise roll number + device fingerprint for proxy detection
     */
    fun startAdvertisingWithRollAndFingerprint(
        context: Context,
        rollNumber: String,
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

        // Get separate hashes
        val rollNumberHash = CustomDeviceFingerprint.getRollNumberHash(rollNumber)
        val androidIdHash = CustomDeviceFingerprint.getAndroidIdHash(context)
        val deviceFingerprintHash = CustomDeviceFingerprint.getDeviceFingerprintHash(context)

        Log.d(TAG, "Advertising Roll: $rollNumber (hash: $rollNumberHash)")
        Log.d(TAG, "Android ID: ${CustomDeviceFingerprint.getAndroidId(context)} (hash: $androidIdHash)")
        Log.d(TAG, "Device fingerprint: ${CustomDeviceFingerprint.getDeviceFingerprint(context)} (hash: $deviceFingerprintHash)")

        // Create 12-byte payload: [4-byte Roll hash][4-byte Android ID hash][4-byte Device fingerprint hash]
        val combinedData = ByteBuffer
            .allocate(12)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(rollNumberHash)          // First 4 bytes: Roll number hash
            .putInt(androidIdHash)           // Next 4 bytes: Android ID hash
            .putInt(deviceFingerprintHash)   // Last 4 bytes: Device fingerprint hash
            .array()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), combinedData)
            .setIncludeDeviceName(false)
            .build()

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Advertising started - Roll: $rollNumberHash, AndroidID: $androidIdHash, Fingerprint: $deviceFingerprintHash")
                onSuccess()
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed ($errorCode) for roll: $rollNumber")
                onFailure(errorCode)
            }
        }

        advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }


}
