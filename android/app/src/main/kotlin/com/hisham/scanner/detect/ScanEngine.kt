package com.hisham.scanner.detect

import android.media.Image
import kotlin.math.roundToInt

/**
 * Turns camera frames into detections.
 *
 * Owns the torch-pulse state machine and the frame sampling. The actual
 * detection maths lives in GlintDetector, which has no Android dependencies so
 * it can be unit tested; this class is the part that only makes sense on a
 * handset.
 *
 * One real advantage over the web build: CameraX hands over YUV_420_888, whose
 * Y plane *is* luminance. The browser had to read RGBA out of a canvas and
 * compute luma per pixel. Here the data arrives in the form the detector wants.
 */
class ScanEngine {

    companion object {
        const val WORK_WIDTH = 240

        /** Let auto-exposure settle after a torch change before sampling. */
        const val SETTLE_MS = 140L

        /** Minimum gap between analysed frames in the non-pulsing modes. */
        const val LIVE_INTERVAL_MS = 110L
    }

    enum class Phase { TORCH_ON, WAIT_ON, TORCH_OFF, WAIT_OFF }

    val detector = GlintDetector()

    var mode: Mode = Mode.PULSE
        set(value) {
            if (field != value) {
                field = value
                reset()
            }
        }

    var sensitivity: Int
        get() = detector.sensitivity
        set(v) { detector.sensitivity = v }

    /** Set by the screen once it knows whether the device exposes a torch. */
    var torchAvailable: Boolean = true

    var phase: Phase = Phase.TORCH_ON
        private set

    private var phaseAt = 0L
    private var lastLive = 0L
    private var frameOn: FloatArray? = null
    private var frameOff: FloatArray? = null

    /** Width and height of the buffer the detector works on, upright. */
    var workWidth = 0
        private set
    var workHeight = 0
        private set

    /** Immutable copy of the current candidates, safe to hand to the UI thread. */
    fun marks(): List<Mark> = Readout.marksOf(detector.candidates)

    fun reset() {
        detector.reset()
        frameOn = null
        frameOff = null
        phase = Phase.TORCH_ON
        phaseAt = 0L
    }

    /**
     * Feed one frame. Returns true when the caller should flip the torch, with
     * [wantTorchOn] saying which way. Detections land in [detector].candidates.
     */
    fun onFrame(image: Image, rotationDegrees: Int, now: Long): FrameOutcome {
        val luma = sample(image, rotationDegrees) ?: return FrameOutcome(false, false, false)
        detector.resize(workWidth, workHeight)

        if (mode != Mode.PULSE || !torchAvailable) {
            if (now - lastLive < LIVE_INTERVAL_MS) return FrameOutcome(false, false, false)
            lastLive = now
            val blobs = detector.analyse(luma, mode)
            detector.track(blobs)
            return FrameOutcome(
                analysed = true,
                wantTorchChange = false,
                wantTorchOn = mode == Mode.CONTINUOUS
            )
        }

        if (phaseAt == 0L) phaseAt = now
        val elapsed = now - phaseAt

        return when (phase) {
            Phase.TORCH_ON -> {
                phase = Phase.WAIT_ON
                phaseAt = now
                FrameOutcome(false, wantTorchChange = true, wantTorchOn = true)
            }

            Phase.WAIT_ON -> {
                if (elapsed >= SETTLE_MS) {
                    frameOn = luma
                    phase = Phase.TORCH_OFF
                    phaseAt = now
                }
                FrameOutcome(false, false, true)
            }

            Phase.TORCH_OFF -> {
                phase = Phase.WAIT_OFF
                phaseAt = now
                FrameOutcome(false, wantTorchChange = true, wantTorchOn = false)
            }

            Phase.WAIT_OFF -> {
                var analysed = false
                if (elapsed >= SETTLE_MS) {
                    frameOff = luma
                    val on = frameOn
                    val off = frameOff
                    if (on != null && off != null && on.size == off.size) {
                        val diff = detector.difference(on, off)
                        detector.track(detector.analyse(diff, Mode.PULSE))
                        analysed = true
                    }
                    phase = Phase.TORCH_ON
                    phaseAt = now
                }
                FrameOutcome(analysed, false, false)
            }
        }
    }

    data class FrameOutcome(
        val analysed: Boolean,
        val wantTorchChange: Boolean,
        val wantTorchOn: Boolean
    )

    /**
     * Downsample the Y plane into an upright buffer of [WORK_WIDTH] px wide.
     *
     * Rotation is applied here rather than later so detector coordinates line
     * up with what the user sees in the viewfinder; an overlay drawn a quarter
     * turn away from the thing it is marking is worse than no overlay.
     */
    private fun sample(image: Image, rotationDegrees: Int): FloatArray? {
        val plane = image.planes.getOrNull(0) ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val srcW = image.width
        val srcH = image.height
        if (srcW <= 0 || srcH <= 0) return null

        val swap = rotationDegrees == 90 || rotationDegrees == 270
        val uprightW = if (swap) srcH else srcW
        val uprightH = if (swap) srcW else srcH

        val outW = WORK_WIDTH
        val outH = ((uprightH.toFloat() / uprightW) * outW).roundToInt().coerceAtLeast(2)
        workWidth = outW
        workHeight = outH

        val out = FloatArray(outW * outH)
        for (oy in 0 until outH) {
            val uy = (oy.toFloat() / outH * uprightH).toInt().coerceIn(0, uprightH - 1)
            for (ox in 0 until outW) {
                val ux = (ox.toFloat() / outW * uprightW).toInt().coerceIn(0, uprightW - 1)

                val sx: Int
                val sy: Int
                when (rotationDegrees) {
                    90 -> { sx = uy; sy = srcH - 1 - ux }
                    180 -> { sx = srcW - 1 - ux; sy = srcH - 1 - uy }
                    270 -> { sx = srcW - 1 - uy; sy = ux }
                    else -> { sx = ux; sy = uy }
                }

                val index = sy * rowStride + sx * pixelStride
                out[oy * outW + ox] =
                    if (index in 0 until buffer.limit()) {
                        (buffer.get(index).toInt() and 0xFF).toFloat()
                    } else {
                        0f
                    }
            }
        }
        return out
    }
}
