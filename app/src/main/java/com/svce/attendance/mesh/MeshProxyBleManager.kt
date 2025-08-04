package com.svce.attendance.mesh

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

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
        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        Log.d(TAG, "Connecting to Mesh Proxy node at $deviceAddress")
        // Optionally request larger MTU (requires API 21+)
        // bluetoothGatt?.requestMtu(mtuSize)
    }

    fun disconnect() {
        isConnected = false
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        Log.d(TAG, "Disconnected from Mesh Proxy node")
    }

    /** Writes a mesh PDU to the Proxy Data In characteristic and waits for ACK. */
    fun writeMeshPdu(pdu: ByteArray) {
        proxyDataIn?.let { char ->
            char.value = pdu
            val ok = bluetoothGatt?.writeCharacteristic(char) ?: false
            Log.d(TAG, "Writing mesh PDU to Data In char, ok=$ok (bytes=${pdu.size})")
            if (!ok) onError("Failed to write PDU")
        } ?: onError("Proxy Data In characteristic not discovered")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, state=$newState")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                onDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Try to get Proxy service/characteristics
                val proxyService = gatt.getService(PROXY_SERVICE_UUID) ?: gatt.getService(PROVISIONING_SERVICE_UUID)
                proxyDataIn = proxyService?.getCharacteristic(DATA_IN_CHAR_UUID)
                    ?: proxyService?.getCharacteristic(PROV_DATA_IN_CHAR_UUID)
                proxyDataOut = proxyService?.getCharacteristic(DATA_OUT_CHAR_UUID)
                    ?: proxyService?.getCharacteristic(PROV_DATA_OUT_CHAR_UUID)
                // Enable notifications on Data Out
                proxyDataOut?.let { outChar ->
                    gatt.setCharacteristicNotification(outChar, true)
                    // Set descriptor
                    val cccd = outChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    cccd?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(it)
                    }
                }
                handler.post { onConnected() }
                Log.d(TAG, "Proxy characteristics discovered and notifications enabled")
            } else {
                onError("Service discovery failed: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if ((characteristic.uuid == DATA_OUT_CHAR_UUID) || (characteristic.uuid == PROV_DATA_OUT_CHAR_UUID)) {
                val pdu = characteristic.value
                Log.d(TAG, "Mesh notification received (${pdu?.size} bytes)")
                // Route to mesh library; use default MTU if unknown
                meshManagerApi.handleNotifications(mtuSize, pdu)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if ((characteristic.uuid == DATA_IN_CHAR_UUID) || (characteristic.uuid == PROV_DATA_IN_CHAR_UUID)) {
                val pdu = characteristic.value
                Log.d(TAG, "Mesh PDU write ACKed (${pdu?.size} bytes, status=$status)")
                meshManagerApi.handleWriteCallbacks(mtuSize, pdu)
            }
        }
    }
}
