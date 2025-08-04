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
        if (!isProvisioner) return
        Log.d(TAG, "Provisioning start requested. Implement scan logic here if needed.")
        // Actual scan/connect code should go here!
    }

    /** Example: Send attendance as mesh message (update logic/model as needed) */
    fun sendAttendanceMessage(rollNumber: String, destAddress: Int, appKeyIndex: Int) {
        meshNetwork?.let { network ->
            val appKey = network.appKeys.getOrNull(appKeyIndex) ?: return
            // "1" here represents state "ON" or "present"
            val message = GenericOnOffSet(appKey, true, (System.currentTimeMillis() % 255).toInt())
            meshManagerApi.createMeshPdu(destAddress, message)
        }
    }


    /** Cleanup listeners when done */
    fun cleanup() {
        //meshManagerApi.setMeshManagerCallbacks(null)
        //meshManagerApi.setProvisioningStatusCallbacks(null)
        //meshManagerApi.setMeshStatusCallbacks(null)
    }

    /** MESH CALLBACKS **/

    // MeshManagerCallbacks
    override fun onNetworkLoaded(meshNetwork: MeshNetwork) {
        this.meshNetwork = meshNetwork
        Log.d(TAG, "Network loaded")
    }

    override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
        this.meshNetwork = meshNetwork
    }

    override fun onNetworkLoadFailed(error: String?) {
        Log.e(TAG, "Network load failed: $error")
    }

    override fun onNetworkImported(meshNetwork: MeshNetwork) {
        this.meshNetwork = meshNetwork
    }

    override fun onNetworkImportFailed(error: String?) {
        Log.e(TAG, "Network import failed: $error")
    }

    override fun getMtu(): Int = 517 // or the negotiated value

    // MeshProvisioningStatusCallbacks
    override fun onProvisioningStateChanged(
        meshNode: UnprovisionedMeshNode,
        state: ProvisioningState.States,
        data: ByteArray?
    ) {
        Log.d(TAG, "Provisioning state: $state for UnprovisionedMeshNode")

    }

    override fun onProvisioningFailed(
        meshNode: UnprovisionedMeshNode,
        state: ProvisioningState.States,
        data: ByteArray?
    ) {
        Log.e(TAG, "Provisioning failed at $state for node $meshNode")

    }

    override fun onProvisioningCompleted(
        meshNode: ProvisionedMeshNode,
        state: ProvisioningState.States,
        data: ByteArray?
    ) {
        Log.d(TAG, "Node provisioned: ${meshNode.nodeName}")
        onNodeProvisioned(meshNode)
    }

    override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode, pdu: ByteArray) {
        Log.d(TAG, "sendProvisioningPdu for node: $meshNode")
    }

    // MeshStatusCallbacks
    override fun onTransactionFailed(dst: Int, hasIncompleteTimerExpired: Boolean) {
        Log.e(TAG, "Transaction failed to $dst")
    }

    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray) {
        Log.d(TAG, "Unknown PDU from $src")
    }

    override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
        Log.d(TAG, "Block ACK processed from $dst")
    }

    override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
        Log.d(TAG, "Block ACK received from $src")
    }

    override fun onMeshPduCreated(pdu: ByteArray) {
        Log.d(TAG, "Mesh PDU created")
        // Typically, send this PDU over BLE using your proxy manager.
    }

    override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
        Log.d(TAG, "Mesh message processed from $dst")
        onMeshMessageReceived(dst.toString())
    }

    override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
        Log.d(TAG, "Mesh message received from $src")
        onMeshMessageReceived(src.toString())
    }

    override fun onHeartbeatMessageReceived(src: Int, message: ControlMessage) {
        Log.d(TAG, "Heartbeat message received from $src: $message")
    }

    override fun onMessageDecryptionFailed(meshLayer: String, errorMessage: String) {
        Log.e(TAG, "Message decryption failed at $meshLayer: $errorMessage")
    }
}
