package com.hisham.scanner

import com.hisham.scanner.detect.Blob
import com.hisham.scanner.detect.GlintDetector
import com.hisham.scanner.detect.Mode
import com.hisham.scanner.detect.Readout
import com.hisham.scanner.detect.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * The same synthetic-frame cases the web build is held to, so the two engines
 * stay comparable. Each one encodes a thing the detector must NOT do, because
 * false positives are what make this class of tool untrustworthy.
 */
class GlintDetectorTest {

    private val w = 240
    private val h = 320
    private lateinit var d: GlintDetector

    @Before
    fun setUp() {
        d = GlintDetector()
        d.resize(w, h)
        d.sensitivity = 3
    }

    private fun flat(v: Float) = FloatArray(w * h) { v }

    private fun disc(a: FloatArray, cx: Int, cy: Int, r: Int, v: Float): FloatArray {
        for (y in maxOf(0, cy - r - 1)..minOf(h - 1, cy + r + 1)) {
            for (x in maxOf(0, cx - r - 1)..minOf(w - 1, cx + r + 1)) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r * r) a[y * w + x] = v
            }
        }
        return a
    }

    private fun rect(a: FloatArray, x0: Int, y0: Int, rw: Int, rh: Int, v: Float): FloatArray {
        for (y in y0 until y0 + rh) for (x in x0 until x0 + rw) a[y * w + x] = v
        return a
    }

    @Test
    fun `detects a small compact lens return`() {
        val blobs = d.analyse(disc(flat(18f), 120, 160, 3, 252f), Mode.PULSE)
        assertEquals("one compact bright return should be found", 1, blobs.size)
        assertTrue("centroid should sit on the disc", abs(blobs[0].x - 120f) < 3f)
        assertTrue("centroid should sit on the disc", abs(blobs[0].y - 160f) < 3f)
    }

    @Test
    fun `ignores a uniformly bright frame`() {
        // bright everywhere means bright relative to nothing: no local contrast
        assertEquals(0, d.analyse(flat(250f), Mode.PULSE).size)
    }

    @Test
    fun `rejects a long thin streak`() {
        // a strip light is bright and compact-ish but the wrong shape for a lens
        assertEquals(0, d.analyse(rect(flat(18f), 40, 160, 90, 3, 252f), Mode.PULSE).size)
    }

    @Test
    fun `rejects an oversized bright blob`() {
        // a window or a lamp: too large to be a lens return
        assertEquals(0, d.analyse(disc(flat(18f), 120, 160, 40, 252f), Mode.PULSE).size)
    }

    @Test
    fun `ignores a smooth gradient`() {
        val grad = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) grad[y * w + x] = (x.toFloat() / w) * 255f
        assertEquals(0, d.analyse(grad, Mode.PULSE).size)
    }

    @Test
    fun `finds two separate returns`() {
        val f = disc(disc(flat(18f), 70, 100, 3, 250f), 170, 240, 3, 250f)
        assertEquals(2, d.analyse(f, Mode.PULSE).size)
    }

    @Test
    fun `torch modulation cancels a self-luminous source`() {
        // a standby LED is on in both frames; a torch-lit lens return is only in one
        val ambientLed = disc(flat(12f), 60, 60, 4, 254f)
        val onFrame = disc(ambientLed.copyOf(), 180, 250, 3, 250f)

        val diff = d.difference(onFrame, ambientLed)
        val blobs = d.analyse(diff, Mode.PULSE)

        assertEquals("the LED must cancel, the torch-lit return must survive", 1, blobs.size)
        assertTrue("survivor must be the torch-lit spot", abs(blobs[0].x - 180f) < 8f)
        assertTrue("survivor must be the torch-lit spot", abs(blobs[0].y - 250f) < 8f)
    }

    @Test
    fun `strict sensitivity is never looser than permissive`() {
        val faint = disc(flat(18f), 120, 160, 3, 62f)
        d.sensitivity = 1
        val strict = d.analyse(faint, Mode.PULSE).size
        d.sensitivity = 5
        val loose = d.analyse(faint, Mode.PULSE).size
        assertTrue("sensitivity 1 ($strict) must not exceed 5 ($loose)", strict <= loose)
    }

    @Test
    fun `sensitivity is clamped to the valid range`() {
        d.sensitivity = 99
        assertEquals(5, d.sensitivity)
        d.sensitivity = -4
        assertEquals(1, d.sensitivity)
    }

    @Test
    fun `confidence climbs only as a candidate is re-seen`() {
        val blob = Blob(100f, 100f, 4f, 12, 250f, 0.9f)
        val c1 = d.track(listOf(blob))[0].confidence
        val c2 = d.track(listOf(blob))[0].confidence
        val c3 = d.track(listOf(blob))[0].confidence

        assertTrue("confidence must rise with persistence: $c1 -> $c2 -> $c3", c1 < c2 && c2 < c3)
        assertTrue("a first sighting must never reach the detection threshold", c1 < Readout.HIT_THRESHOLD)
    }

    @Test
    fun `a single sighting is never shown`() {
        val shown = Readout.visible(d.track(listOf(Blob(100f, 100f, 4f, 12, 250f, 0.95f))))
        assertTrue("one frame is not evidence", shown.isEmpty())
    }

    @Test
    fun `stale candidates age out`() {
        d.track(listOf(Blob(100f, 100f, 4f, 12, 250f, 0.9f)))
        repeat(6) { d.track(emptyList()) }
        assertEquals("a candidate that stops being seen must be dropped", 0, d.candidates.size)
    }

    @Test
    fun `a moving candidate stays a single track`() {
        // the same reflector drifting across the frame as the user pans
        var x = 100f
        repeat(4) {
            d.track(listOf(Blob(x, 100f, 4f, 12, 250f, 0.9f)))
            x += 8f
        }
        assertEquals("smooth movement must not spawn duplicate candidates", 1, d.candidates.size)
        assertTrue("the track should follow the blob", d.candidates[0].x > 100f)
    }

    @Test
    fun `verdict tiers map to confidence`() {
        assertEquals(Verdict.CLEAR, Readout.verdictFor(null))

        val strong = Blob(10f, 10f, 4f, 12, 250f, 1.0f)
        repeat(4) { d.track(listOf(strong)) }
        val top = d.candidates.firstOrNull()
        assertNotNull(top)
        assertEquals(Verdict.DETECTED, Readout.verdictFor(top))
    }

    @Test
    fun `resize clears stale tracking state`() {
        d.track(listOf(Blob(100f, 100f, 4f, 12, 250f, 0.9f)))
        assertTrue(d.candidates.isNotEmpty())
        d.resize(160, 120)
        assertEquals("candidates from a different frame size are meaningless", 0, d.candidates.size)
    }

    @Test
    fun `undersized frames are handled without crashing`() {
        val small = GlintDetector()
        small.resize(0, 0)
        assertEquals(0, small.analyse(FloatArray(0), Mode.PULSE).size)
    }

    @Test
    fun `continuous mode needs near-saturation where pulse mode does not`() {
        // In continuous mode the frame is raw luminance, so the floor is high.
        // The same mid-bright spot should pass as a torch difference and fail raw.
        val mid = disc(flat(18f), 120, 160, 3, 90f)
        assertTrue("a mid-bright difference is a real pulse detection", d.analyse(mid, Mode.PULSE).isNotEmpty())
        assertTrue("but raw luminance that dim is not", d.analyse(mid, Mode.CONTINUOUS).isEmpty())
    }
}
