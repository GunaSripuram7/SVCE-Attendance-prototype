package com.svce.attendance.mesh

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.app.ActivityCompat

/**
 * Minimal BLE Mesh Proxy GATT client.
 * Handles connection, notification, and write to Mesh Proxy/Provisioning Service.
 * You must route notifications and write acks to meshManagerApi in your Activity.
 */
class MeshProxyBleManager(
    private val context: Context,
    private val meshManagerApi: no.nordicsemi.android.mesh.MeshManagerApi,
    private val deviceAddress: String,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val mtuSize: Int = 517 // MTU, can negotiate lower if needed
) {

    companion object {
        val PROXY_SERVICE_UUID: UUID = UUID.fromString("00001828-0000-1000-8000-00805f9b34fb")
        val DATA_IN_CHAR_UUID: UUID = UUID.fromString("00002add-0000-1000-8000-00805f9b34fb")
        val DATA_OUT_CHAR_UUID: UUID = UUID.fromString("00002ade-0000-1000-8000-00805f9b34fb")

        val PROVISIONING_SERVICE_UUID: UUID = UUID.fromString("00001827-0000-1000-8000-00805f9b34fb")
        val PROV_DATA_IN_CHAR_UUID: UUID = UUID.fromString("00002adb-0000-1000-8000-00805f9b34fb")
        val PROV_DATA_OUT_CHAR_UUID: UUID = UUID.fromString("00002adc-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var proxyDataIn: BluetoothGattCharacteristic? = null
    private var proxyDataOut: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false

    private val TAG = "MeshProxyBleManager"

    fun connect() {
        Log.d(TAG, "connect() called. Target device: $deviceAddress")
        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "connect(): Missing BLUETOOTH_CONNECT permission")
            onError("Missing BLUETOOTH_CONNECT permission")
            return
        }
        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            Log.d(TAG, "Connecting to Mesh Proxy node at $deviceAddress")
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "connect(): SecurityException: ${e.message}", e)
            onError("Bluetooth permission error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "connect(): Exception: ${e.message}", e)
            onError("BLE connection exception: ${e.message}")
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect() called")
        isConnected = false
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "disconnect(): Missing BLUETOOTH_CONNECT permission")
            onError("Missing BLUETOOTH_CONNECT permission")
            return
        }
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            Log.d(TAG, "Disconnected from Mesh Proxy node")
        } catch (e: SecurityException) {
            Log.e(TAG, "disconnect(): SecurityException: ${e.message}", e)
            onError("Bluetooth permission error (disconnect): ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "disconnect(): Exception: ${e.message}", e)
            onError("BLE disconnect exception: ${e.message}")
        }
    }

    /** Writes a mesh PDU to the Proxy Data In characteristic and waits for ACK. */
    fun writeMeshPdu(pdu: ByteArray) {
        Log.d(TAG, "writeMeshPdu() called. PDU length: ${pdu.size}")
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "writeMeshPdu(): Missing BLUETOOTH_CONNECT permission")
            onError("Missing BLUETOOTH_CONNECT permission")
            return
        }
        try {
            proxyDataIn?.let { char ->
                char.value = pdu
                val ok = bluetoothGatt?.writeCharacteristic(char) ?: false
                Log.d(TAG, "Writing mesh PDU to Data In char, ok=$ok (bytes=${pdu.size})")
                if (!ok) {
                    Log.e(TAG, "Failed to write PDU to characteristic")
                    onError("Failed to write PDU")
                }
            } ?: run {
                Log.e(TAG, "Proxy Data In characteristic not discovered")
                onError("Proxy Data In characteristic not discovered")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "writeMeshPdu(): SecurityException: ${e.message}", e)
            onError("Bluetooth permission error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "writeMeshPdu(): Exception: ${e.message}", e)
            onError("BLE writeCharacteristic exception: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, state=$newState")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                Log.d(TAG, "Connection established. Discovering services...")
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onConnectionStateChange(): Missing BLUETOOTH_CONNECT permission for service discovery")
                    onError("Missing BLUETOOTH_CONNECT permission for service discovery")
                    return
                }
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.e(TAG, "onConnectionStateChange(): SecurityException in discoverServices: ${e.message}", e)
                    onError("Bluetooth permission error (discoverServices): ${e.message}")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                Log.d(TAG, "Disconnected from proxy device (state=$newState, status=$status)")
                onDisconnected()
            } else {
                Log.w(TAG, "Connection state changed to $newState with status $status")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val proxyService = gatt.getService(PROXY_SERVICE_UUID)
                    ?: gatt.getService(PROVISIONING_SERVICE_UUID)
                proxyDataIn = proxyService?.getCharacteristic(DATA_IN_CHAR_UUID)
                    ?: proxyService?.getCharacteristic(PROV_DATA_IN_CHAR_UUID)
                proxyDataOut = proxyService?.getCharacteristic(DATA_OUT_CHAR_UUID)
                    ?: proxyService?.getCharacteristic(PROV_DATA_OUT_CHAR_UUID)

                Log.d(TAG, "Service discovery complete. proxyDataIn=${proxyDataIn != null}, proxyDataOut=${proxyDataOut != null}")

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onServicesDiscovered(): Missing BLUETOOTH_CONNECT permission for notifications")
                    onError("Missing BLUETOOTH_CONNECT permission for notifications")
                    return
                }
                proxyDataOut?.let { outChar ->
                    try {
                        Log.d(TAG, "Enabling notifications for proxyDataOut: ${outChar.uuid}")
                        gatt.setCharacteristicNotification(outChar, true)
                        val cccd = outChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        cccd?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                            Log.d(TAG, "Notification descriptor written for ${outChar.uuid}")
                        } ?: Log.e(TAG, "No CCCD descriptor found for notification characteristic!")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException enabling notifications: ${e.message}", e)
                        onError("Bluetooth permission error (notification): ${e.message}")
                        return
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception enabling notifications: ${e.message}", e)
                        onError("Error enabling notifications: ${e.message}")
                        return
                    }
                } ?: Log.e(TAG, "Proxy Data Out characteristic not discovered")
                handler.post {
                    Log.d(TAG, "Calling user onConnected() callback")
                    onConnected()
                }
                Log.d(TAG, "Proxy characteristics discovered and notifications enabled")
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                onError("Service discovery failed: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d(TAG, "onCharacteristicChanged: uuid=${characteristic.uuid}, valueLen=${characteristic.value.size}")
            if ((characteristic.uuid == DATA_OUT_CHAR_UUID) || (characteristic.uuid == PROV_DATA_OUT_CHAR_UUID)) {
                val pdu = characteristic.value
                Log.d(TAG, "Mesh notification received (${pdu?.size} bytes): ${pdu?.joinToString()}")
                // Route to mesh library; use default MTU if unknown
                try {
                    meshManagerApi.handleNotifications(mtuSize, pdu)
                    Log.d(TAG, "handleNotifications delivered to meshManagerApi")
                } catch (e: Exception) {
                    Log.e(TAG, "Error routing mesh notification: ${e.message}", e)
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicWrite: uuid=${characteristic.uuid}, status=$status, valueLen=${characteristic.value.size}")
            if ((characteristic.uuid == DATA_IN_CHAR_UUID) || (characteristic.uuid == PROV_DATA_IN_CHAR_UUID)) {
                val pdu = characteristic.value
                Log.d(TAG, "Mesh PDU write ACKed (${pdu?.size} bytes, status=$status)")
                try {
                    meshManagerApi.handleWriteCallbacks(mtuSize, pdu)
                    Log.d(TAG, "handleWriteCallbacks delivered to meshManagerApi")
                } catch (e: Exception) {
                    Log.e(TAG, "Error routing mesh write ACK: ${e.message}", e)
                }
            }
        }
    }
}
