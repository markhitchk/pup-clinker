package com.harleytg.puppyclicker

import android.content.Context
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

internal data class ExchangeLocalDescription(
    val sessionId: String,
    val sdp: String,
    val iceCandidates: List<IceCandidateSnapshot>
)

/**
 * Small WebRTC adapter for Puppy Exchange.
 *
 * It creates no audio/video tracks, asks for no capture permissions, and configures
 * STUN only. Manual signaling receives a final SDP plus gathered ICE candidates.
 */
internal class PuppyExchangeWebRtc(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onChannelOpen()
        fun onTextMessage(message: String)
        fun onDirectConnectionFailed(reason: String)
        fun onClosed()
    }

    private val appContext = context.applicationContext
    private val factory: PeerConnectionFactory

    @Volatile
    private var peerConnection: PeerConnection? = null

    @Volatile
    private var dataChannel: DataChannel? = null

    @Volatile
    private var activeSessionId: String? = null

    @Volatile
    private var gatheringDone: CompletableDeferred<Unit>? = null

    private val localCandidates = Collections.synchronizedList(mutableListOf<IceCandidateSnapshot>())

    init {
        initializeWebRtc(appContext)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    suspend fun createOffer(): ExchangeLocalDescription {
        closeCurrent(notify = false)
        val sessionId = PuppyExchangeProtocol.newSessionId()
        activeSessionId = sessionId
        val peer = createPeerConnection()
        val channel = peer.createDataChannel(CHANNEL_LABEL, DataChannel.Init())
            ?: error("Unable to create Puppy Exchange DataChannel")
        attachDataChannel(channel)

        val local = createSdp(peer, offer = true)
        gatheringDone = CompletableDeferred()
        setLocalDescription(peer, local)
        awaitIceGathering(peer)
        return ExchangeLocalDescription(
            sessionId = sessionId,
            sdp = peer.localDescription?.description ?: local.description,
            iceCandidates = snapshotLocalCandidates()
        )
    }

    suspend fun acceptOffer(
        sessionId: String,
        remoteSdp: String,
        remoteCandidates: List<IceCandidateSnapshot>
    ): ExchangeLocalDescription {
        closeCurrent(notify = false)
        activeSessionId = sessionId
        val peer = createPeerConnection()
        setRemoteDescription(peer, SessionDescription(SessionDescription.Type.OFFER, remoteSdp))
        addRemoteCandidates(peer, remoteCandidates)

        val answer = createSdp(peer, offer = false)
        gatheringDone = CompletableDeferred()
        setLocalDescription(peer, answer)
        awaitIceGathering(peer)
        return ExchangeLocalDescription(
            sessionId = sessionId,
            sdp = peer.localDescription?.description ?: answer.description,
            iceCandidates = snapshotLocalCandidates()
        )
    }

    suspend fun applyAnswer(
        sessionId: String,
        remoteSdp: String,
        remoteCandidates: List<IceCandidateSnapshot>
    ) {
        require(activeSessionId == sessionId) { "Answer belongs to a different Puppy Exchange session" }
        val peer = peerConnection ?: error("No active Puppy Exchange offer")
        setRemoteDescription(peer, SessionDescription(SessionDescription.Type.ANSWER, remoteSdp))
        addRemoteCandidates(peer, remoteCandidates)
    }

    fun sendText(text: String): Boolean {
        val channel = dataChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        val bytes = text.toByteArray(Charsets.UTF_8)
        return channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    fun close() {
        closeCurrent(notify = true)
    }

    private fun createPeerConnection(): PeerConnection {
        localCandidates.clear()
        gatheringDone = null
        val config = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder(STUN_PRIMARY).createIceServer(),
                PeerConnection.IceServer.builder(STUN_SECONDARY).createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }
        val peer = factory.createPeerConnection(config, peerObserver)
            ?: error("Unable to create Puppy Exchange peer connection")
        peerConnection = peer
        return peer
    }

    private val peerObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            when (newState) {
                PeerConnection.IceConnectionState.FAILED ->
                    listener.onDirectConnectionFailed("ICE negotiation failed.")
                PeerConnection.IceConnectionState.CLOSED -> listener.onClosed()
                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                gatheringDone?.complete(Unit)
            }
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            localCandidates += IceCandidateSnapshot(
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex,
                candidate = candidate.sdp
            )
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit

        override fun onDataChannel(channel: DataChannel) {
            attachDataChannel(channel)
        }

        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit
    }

    private fun attachDataChannel(channel: DataChannel) {
        dataChannel?.takeIf { it !== channel }?.let { old ->
            runCatching { old.close() }
            runCatching { old.dispose() }
        }
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                when (channel.state()) {
                    DataChannel.State.OPEN -> listener.onChannelOpen()
                    DataChannel.State.CLOSED -> listener.onClosed()
                    else -> Unit
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val source = buffer.data.duplicate()
                val bytes = ByteArray(source.remaining())
                source.get(bytes)
                listener.onTextMessage(bytes.toString(Charsets.UTF_8))
            }
        })
    }

    private suspend fun createSdp(peer: PeerConnection, offer: Boolean): SessionDescription {
        val result = CompletableDeferred<SessionDescription>()
        val observer = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                result.complete(description)
            }

            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) {
                result.completeExceptionally(IllegalStateException(error))
            }

            override fun onSetFailure(error: String) = Unit
        }
        if (offer) peer.createOffer(observer, MediaConstraints())
        else peer.createAnswer(observer, MediaConstraints())
        return result.await()
    }

    private suspend fun setLocalDescription(peer: PeerConnection, description: SessionDescription) {
        val result = CompletableDeferred<Unit>()
        peer.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onSetSuccess() { result.complete(Unit) }
            override fun onCreateFailure(error: String) = Unit
            override fun onSetFailure(error: String) {
                result.completeExceptionally(IllegalStateException(error))
            }
        }, description)
        result.await()
    }

    private suspend fun setRemoteDescription(peer: PeerConnection, description: SessionDescription) {
        val result = CompletableDeferred<Unit>()
        peer.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onSetSuccess() { result.complete(Unit) }
            override fun onCreateFailure(error: String) = Unit
            override fun onSetFailure(error: String) {
                result.completeExceptionally(IllegalStateException(error))
            }
        }, description)
        result.await()
    }

    private suspend fun awaitIceGathering(peer: PeerConnection) {
        if (peer.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) return
        val waiter = gatheringDone ?: CompletableDeferred<Unit>().also { gatheringDone = it }
        waiter.await()
    }

    private fun addRemoteCandidates(peer: PeerConnection, candidates: List<IceCandidateSnapshot>) {
        require(candidates.size <= MAX_ICE_CANDIDATES) { "Too many remote ICE candidates" }
        candidates.forEach { snapshot ->
            require(snapshot.candidate.length <= MAX_ICE_CANDIDATE_CHARS) { "Remote ICE candidate is too large" }
            val added = peer.addIceCandidate(
                IceCandidate(snapshot.sdpMid, snapshot.sdpMLineIndex, snapshot.candidate)
            )
            check(added) { "Unable to add remote ICE candidate" }
        }
    }

    private fun snapshotLocalCandidates(): List<IceCandidateSnapshot> = synchronized(localCandidates) {
        localCandidates.distinct().take(MAX_ICE_CANDIDATES)
    }

    private fun closeCurrent(notify: Boolean) {
        val channel = dataChannel
        dataChannel = null
        if (channel != null) {
            runCatching { channel.unregisterObserver() }
            runCatching { channel.close() }
            runCatching { channel.dispose() }
        }
        val peer = peerConnection
        peerConnection = null
        if (peer != null) {
            runCatching { peer.close() }
            runCatching { peer.dispose() }
        }
        activeSessionId = null
        gatheringDone?.cancel()
        gatheringDone = null
        localCandidates.clear()
        if (notify) listener.onClosed()
    }

    companion object {
        private const val CHANNEL_LABEL = "puppy-exchange-v1"
        private const val STUN_PRIMARY = "stun:stun.l.google.com:19302"
        private const val STUN_SECONDARY = "stun:stun1.l.google.com:19302"
        private const val MAX_ICE_CANDIDATES = 128
        private const val MAX_ICE_CANDIDATE_CHARS = 8_192
        private val initialized = AtomicBoolean(false)

        private fun initializeWebRtc(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
            }
        }
    }
}
