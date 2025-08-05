package com.svce.attendance.mesh

import android.content.Context
import android.util.Log
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.MeshManagerCallbacks
import no.nordicsemi.android.mesh.MeshProvisioningStatusCallbacks
import no.nordicsemi.android.mesh.MeshStatusCallbacks
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.GenericOnOffSet
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState

class MeshManagerHelper(
    private val context: Context,
    private val onMeshReady: () -> Unit,
    private val onNodeProvisioned: (ProvisionedMeshNode) -> Unit,
    private val onMeshMessageReceived: (String) -> Unit // Roll number received
) : MeshManagerCallbacks, MeshProvisioningStatusCallbacks, MeshStatusCallbacks {

    private val TAG = "MeshManagerHelper"
    private val meshManagerApi = MeshManagerApi(context)
    private var meshNetwork: MeshNetwork? = null
    private var isProvisioner = false

    fun initializeMesh(asProvisioner: Boolean = false) {
        isProvisioner = asProvisioner
        meshManagerApi.setMeshManagerCallbacks(this)
        meshManagerApi.setProvisioningStatusCallbacks(this)
        meshManagerApi.setMeshStatusCallbacks(this)
        meshManagerApi.createMeshNetwork() // or loadMeshNetwork() if reloading instead of new
        Log.d(TAG, "Mesh initialized as ${if (isProvisioner) "Provisioner" else "Node"}")
        onMeshReady()
    }

    /** Placeholder for actual provisioning scan/start, to be implemented as needed */
    fun startProvisioning() {
        if (!isProvisioner) {
            Log.w(TAG, "startProvisioning() called but this device is not the provisioner")
            return
        }
        Log.d(TAG, "Provisioning start requested. Implement scan logic here if needed.")
        // Actual scan/connect code should go here!
    }

    /** Example: Send attendance as mesh message (update logic/model as needed) */
    fun sendAttendanceMessage(rollNumber: String, destAddress: Int, appKeyIndex: Int) {
        Log.d(TAG, "sendAttendanceMessage(): roll=$rollNumber, dest=$destAddress, appKeyIndex=$appKeyIndex")
        meshNetwork?.let { network ->
            val appKey = network.appKeys.getOrNull(appKeyIndex)
            if (appKey == null) {
                Log.e(TAG, "No AppKey at index $appKeyIndex")
                return
            }
            val tid = (System.currentTimeMillis() % 255).toInt()
            val message = GenericOnOffSet(appKey, true, tid)
            Log.d(TAG, "Creating mesh PDU for dest $destAddress, message TID=$tid")
            meshManagerApi.createMeshPdu(destAddress, message)
        } ?: run {
            Log.e(TAG, "Mesh network not available in sendAttendanceMessage")
        }
    }

    /** Cleanup listeners when done */
    fun cleanup() {
        Log.d(TAG, "cleanup() called. Removing mesh callbacks")
        //meshManagerApi.setMeshManagerCallbacks(null)
        //meshManagerApi.setProvisioningStatusCallbacks(null)
        //meshManagerApi.setMeshStatusCallbacks(null)
    }

    /** MESH CALLBACKS **/

    // MeshManagerCallbacks
    override fun onNetworkLoaded(meshNetwork: MeshNetwork) {
        this.meshNetwork = meshNetwork
        Log.d(TAG, "Mesh network loaded: ${meshNetwork.meshName}")
    }

    override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
        this.meshNetwork = meshNetwork
        Log.d(TAG, "Mesh network updated: ${meshNetwork.meshName}")
    }

    override fun onNetworkLoadFailed(error: String?) {
        Log.e(TAG, "Network load failed: $error")
    }

    override fun onNetworkImported(meshNetwork: MeshNetwork) {
        this.meshNetwork = meshNetwork
        Log.d(TAG, "Mesh network imported: ${meshNetwork.meshName}")
    }

    override fun onNetworkImportFailed(error: String?) {
        Log.e(TAG, "Network import failed: $error")
    }

    override fun getMtu(): Int {
        Log.d(TAG, "getMtu() called. Returning 517")
        return 517 // or the negotiated value
    }

    // MeshProvisioningStatusCallbacks
    override fun onProvisioningStateChanged(
        meshNode: UnprovisionedMeshNode,
        state: ProvisioningState.States,
        data: ByteArray?
    ) {
        Log.d(TAG, "Provisioning state: $state for node deviceUuid=${meshNode.deviceUuid}")

        data?.let { Log.d(TAG, "Provisioning state data: ${it.joinToString()}") }
    }

    override fun onProvisioningFailed(
        meshNode: UnprovisionedMeshNode,
        state: ProvisioningState.States,
        data: ByteArray?
    ) {
        Log.e(TAG, "Provisioning failed at state=$state for node: $meshNode")
        data?.let { Log.e(TAG, "Provisioning failed data: ${it.joinToString()}") }
    }

    override fun onProvisioningCompleted(
        meshNode: ProvisionedMeshNode,
        state: ProvisioningState.States,
        data: ByteArray?
    ) {
        Log.d(TAG, "Node provisioned: name=${meshNode.nodeName}, address=${meshNode.unicastAddress}")

        data?.let { Log.d(TAG, "Provisioning completed data: ${it.joinToString()}") }
        onNodeProvisioned(meshNode)
    }

    override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode, pdu: ByteArray) {
        Log.d(TAG, "sendProvisioningPdu() for node: $meshNode, pdu size=${pdu.size}")
    }

    // MeshStatusCallbacks
    override fun onTransactionFailed(dst: Int, hasIncompleteTimerExpired: Boolean) {
        Log.e(TAG, "Transaction failed to dst=$dst. IncompleteTimerExpired=$hasIncompleteTimerExpired")
    }

    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray) {
        Log.w(TAG, "Unknown PDU received from src=$src, payloadLen=${accessPayload.size}")
    }

    override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
        Log.d(TAG, "Block ACK processed for dst=$dst, message=$message")
    }

    override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
        Log.d(TAG, "Block ACK received from src=$src, message=$message")
    }

    override fun onMeshPduCreated(pdu: ByteArray) {
        Log.d(TAG, "Mesh PDU created, length=${pdu.size}")
        // Typically, send this PDU over BLE using your proxy manager.
    }

    override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
        Log.d(TAG, "Mesh message processed for dst=$dst, message=$meshMessage")
        onMeshMessageReceived(dst.toString())
    }

    override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
        Log.d(TAG, "Mesh message received from src=$src, message=$meshMessage")
        onMeshMessageReceived(src.toString())
    }

    override fun onHeartbeatMessageReceived(src: Int, message: ControlMessage) {
        Log.d(TAG, "Heartbeat message received from src=$src: $message")
    }

    override fun onMessageDecryptionFailed(meshLayer: String, errorMessage: String) {
        Log.e(TAG, "Message decryption failed at meshLayer=$meshLayer: $errorMessage")
    }
}
