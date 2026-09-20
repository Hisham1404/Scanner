package com.hisham.scanner.detect

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lens-glint detection.
 *
 * A camera is a lens focused onto a sensor. Light entering the lens is focused
 * to a point and a large share of it returns along the path it arrived on -
 * retroreflection. That is why a hidden lens throws back a small, very bright,
 * roughly circular spot when lit from beside your own camera, and why painted
 * walls, plastic and fabric never do.
 *
 * Seeing bright spots is easy; telling a lens from a chrome tap is the work.
 * Two things do most of it:
 *
 *   Torch modulation - analyse the difference between a torch-on and a
 *   torch-off frame. Anything self-luminous subtracts to nothing, leaving only
 *   what the torch lit, which a retroreflector dominates.
 *
 *   Persistence - a specular highlight on a flat glossy surface slides away and
 *   dies as the angle changes; a retroreflector keeps returning light to the
 *   source. Candidates are tracked across cycles and must be re-seen to score.
 *
 * Deliberately free of Android imports so the whole thing runs under JVM unit
 * tests. Everything here is a straight port of the web build's engine, which
 * means the two stay comparable and the same synthetic-frame tests apply.
 */

enum class Mode { PULSE, CONTINUOUS, INFRARED }

data class Blob(
    val x: Float,
    val y: Float,
    val r: Float,
    val area: Int,
    val peak: Float,
    val score: Float
)

class Candidate(
    var x: Float,
    var y: Float,
    var r: Float,
    var score: Float
) {
    var hits: Int = 1
    var misses: Int = 0
    var matched: Boolean = true
    var confidence: Int = 0
}

class GlintDetector {

    companion object {
        /** Candidate match radius, as a fraction of frame width. */
        private const val MATCH_DIST = 0.09f

        /** Cycles a candidate survives without being seen again. */
        private const val MAX_MISSES = 4

        /** sensitivity 1 (strict) .. 5 (permissive) */
        private val K_SIGMA = floatArrayOf(5.2f, 4.4f, 3.6f, 2.9f, 2.3f)
        private val LOCAL_DELTA = floatArrayOf(46f, 38f, 31f, 25f, 19f)
        private val FLOOR_PULSE = floatArrayOf(46f, 38f, 31f, 25f, 20f)
        private val FLOOR_IR = floatArrayOf(150f, 135f, 120f, 105f, 92f)
        private val FLOOR_DIRECT = floatArrayOf(238f, 232f, 224f, 214f, 204f)

        private const val MIN_AREA = 2
        private const val MAX_AREA_FRACTION = 0.018f
        private const val MIN_ASPECT = 0.4f
        private const val MAX_ASPECT = 2.5f
        private const val MIN_FILL = 0.35f
        private const val MAX_BLOBS = 8
        private const val MAX_CANDIDATES = 6
    }

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    /** 1 is strictest and gives fewest false alarms; 5 is most permissive. */
    var sensitivity: Int = 3
        set(value) {
            field = value.coerceIn(1, 5)
        }

    private val _candidates = mutableListOf<Candidate>()
    val candidates: List<Candidate> get() = _candidates

    private var integral: DoubleArray = DoubleArray(0)
    private var mask: ByteArray = ByteArray(0)
    private var seen: ByteArray = ByteArray(0)
    private var stack: IntArray = IntArray(0)

    fun resize(w: Int, h: Int) {
        if (w == width && h == height) return
        width = w
        height = h
        val n = w * h
        integral = DoubleArray((w + 1) * (h + 1))
        mask = ByteArray(n)
        seen = ByteArray(n)
        stack = IntArray(n)
        _candidates.clear()
    }

    fun reset() {
        _candidates.clear()
    }

    /**
     * Torch-on minus torch-off, clamped at zero. Room lights, standby LEDs,
     * windows and screens produce their own light, so they cancel here.
     */
    fun difference(on: FloatArray, off: FloatArray): FloatArray {
        val out = FloatArray(on.size)
        for (i in on.indices) {
            val d = on[i] - off[i]
            out[i] = if (d > 0f) d else 0f
        }
        return out
    }

    /**
     * Two conditions, both required: the pixel is genuinely bright for this
     * frame, AND it stands well clear of its own surroundings. A brightly but
     * evenly lit wall passes the first and fails the second.
     */
    fun analyse(src: FloatArray, mode: Mode): List<Blob> {
        val w = width
        val h = height
        if (w <= 0 || h <= 0 || src.size < w * h) return emptyList()
        val n = w * h

        var sum = 0.0
        var sumSq = 0.0
        var peak = 0f
        for (i in 0 until n) {
            val v = src[i]
            sum += v
            sumSq += v.toDouble() * v
            if (v > peak) peak = v
        }
        val mean = sum / n
        val variance = max(0.0, sumSq / n - mean * mean)
        val std = sqrt(variance)

        val s = sensitivity - 1
        val kSigma = K_SIGMA[s]
        val localDelta = LOCAL_DELTA[s]
        val absFloor = when (mode) {
            Mode.PULSE -> FLOOR_PULSE[s]
            Mode.INFRARED -> FLOOR_IR[s]
            Mode.CONTINUOUS -> FLOOR_DIRECT[s]
        }

        val thresh = max(absFloor.toDouble(), mean + kSigma * std).toFloat()
        if (peak < thresh) return emptyList()

        buildIntegral(src, w, h)
        val radius = max(4, (w * 0.045f).roundToInt())

        java.util.Arrays.fill(mask, 0)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                val v = src[i]
                if (v < thresh) continue
                val local = boxMean(w, h, x, y, radius)
                if (v - local >= localDelta) mask[i] = 1
            }
        }

        return findBlobs(src, w, h, thresh)
    }

    private fun buildIntegral(src: FloatArray, w: Int, h: Int) {
        val iw = w + 1
        java.util.Arrays.fill(integral, 0.0)
        for (y in 0 until h) {
            var rowSum = 0.0
            val srcRow = y * w
            val outRow = (y + 1) * iw
            val prevRow = y * iw
            for (x in 0 until w) {
                rowSum += src[srcRow + x]
                integral[outRow + x + 1] = integral[prevRow + x + 1] + rowSum
            }
        }
    }

    private fun boxMean(w: Int, h: Int, cx: Int, cy: Int, r: Int): Float {
        val iw = w + 1
        val x0 = max(0, cx - r)
        val y0 = max(0, cy - r)
        val x1 = min(w, cx + r + 1)
        val y1 = min(h, cy + r + 1)
        val area = (x1 - x0) * (y1 - y0)
        if (area <= 0) return 0f
        val sum = integral[y1 * iw + x1] - integral[y0 * iw + x1] -
                  integral[y1 * iw + x0] + integral[y0 * iw + x0]
        return (sum / area).toFloat()
    }

    /**
     * Flood-fill the mask into connected blobs, then keep only the ones shaped
     * like a lens return: small, compact and roughly round.
     */
    private fun findBlobs(src: FloatArray, w: Int, h: Int, thresh: Float): List<Blob> {
        java.util.Arrays.fill(seen, 0)
        val out = ArrayList<Blob>()
        val maxArea = (w * h * MAX_AREA_FRACTION).roundToInt()

        for (start in mask.indices) {
            if (mask[start].toInt() == 0 || seen[start].toInt() != 0) continue

            var sp = 0
            stack[sp++] = start
            seen[start] = 1

            var area = 0
            var sx = 0L
            var sy = 0L
            var peak = 0f
            var sumV = 0.0
            var minX = w
            var maxX = 0
            var minY = h
            var maxY = 0

            while (sp > 0) {
                val i = stack[--sp]
                val x = i % w
                val y = i / w
                val v = src[i]

                area++
                sx += x
                sy += y
                sumV += v
                if (v > peak) peak = v
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        val ni = ny * w + nx
                        if (mask[ni].toInt() == 1 && seen[ni].toInt() == 0) {
                            seen[ni] = 1
                            stack[sp++] = ni
                        }
                    }
                }
            }

            if (area < MIN_AREA || area > maxArea) continue

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val aspect = bw.toFloat() / bh
            if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) continue

            val fill = area.toFloat() / (bw * bh)
            if (fill < MIN_FILL) continue

            // how far above the detection floor this blob actually sits
            val avg = (sumV / area).toFloat()
            val excess = min(1f, (avg - thresh) / max(20f, thresh * 0.45f))

            val roundness = 1f - min(1f, abs(1f - aspect))
            val compactness = min(1f, fill / 0.8f)
            val sizeFit = if (area <= maxArea * 0.35f) 1f else 0.6f

            val score = (0.50f * max(0f, excess) +
                         0.22f * roundness +
                         0.18f * compactness +
                         0.10f * sizeFit).coerceIn(0f, 1f)

            out.add(
                Blob(
                    x = sx.toFloat() / area,
                    y = sy.toFloat() / area,
                    r = max(3f, sqrt(area / Math.PI).toFloat() * 1.9f),
                    area = area,
                    peak = peak,
                    score = score
                )
            )
        }

        out.sortByDescending { it.score }
        return if (out.size > MAX_BLOBS) out.subList(0, MAX_BLOBS).toList() else out
    }

    /**
     * Match this cycle's blobs onto persistent candidates. A reflection that
     * only ever shows up once never scores highly.
     */
    fun track(blobs: List<Blob>): List<Candidate> {
        val maxDist = width * MATCH_DIST

        _candidates.forEach { it.matched = false }

        for (b in blobs) {
            var best: Candidate? = null
            var bestD = Float.MAX_VALUE
            for (c in _candidates) {
                if (c.matched) continue
                val d = hypot(c.x - b.x, c.y - b.y)
                if (d < bestD) {
                    bestD = d
                    best = c
                }
            }
            if (best != null && bestD <= maxDist) {
                best.matched = true
                best.misses = 0
                best.hits++
                best.x = best.x * 0.55f + b.x * 0.45f
                best.y = best.y * 0.55f + b.y * 0.45f
                best.r = best.r * 0.6f + b.r * 0.4f
                best.score = best.score * 0.6f + b.score * 0.4f
            } else {
                _candidates.add(Candidate(b.x, b.y, b.r, b.score))
            }
        }

        val it = _candidates.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (!c.matched) c.misses++
            if (c.misses > MAX_MISSES) it.remove()
        }

        for (c in _candidates) {
            val persistence = min(1f, (c.hits - 1) / 2f)
            c.confidence = (c.score * (0.55f + 0.45f * persistence) * 100f).roundToInt()
        }

        _candidates.sortByDescending { it.confidence }
        while (_candidates.size > MAX_CANDIDATES) _candidates.removeAt(_candidates.size - 1)

        return _candidates
    }
}

/** What the readout shows, derived from the strongest tracked candidate. */
enum class Verdict { STANDBY, CLEAR, UNCERTAIN, DETECTED }

/**
 * An immutable snapshot of a candidate, for handing to a renderer.
 *
 * Candidate is mutable and is written by the camera analyser thread every
 * cycle. Passing those objects straight to the UI would let the renderer read
 * a position that is being rewritten underneath it, so the engine copies them
 * across the thread boundary instead.
 */
data class Mark(
    val x: Float,
    val y: Float,
    val r: Float,
    val confidence: Int,
    val hits: Int
)

object Readout {
    const val HIT_THRESHOLD = 70
    const val MAYBE_THRESHOLD = 48
    const val SHOW_THRESHOLD = 30
    const val MIN_HITS_TO_SHOW = 2

    fun visible(candidates: List<Candidate>): List<Candidate> =
        candidates.filter { it.confidence >= SHOW_THRESHOLD && it.hits >= MIN_HITS_TO_SHOW }

    fun marksOf(candidates: List<Candidate>): List<Mark> =
        candidates.map { Mark(it.x, it.y, it.r, it.confidence, it.hits) }

    fun visibleMarks(marks: List<Mark>): List<Mark> =
        marks.filter { it.confidence >= SHOW_THRESHOLD && it.hits >= MIN_HITS_TO_SHOW }

    fun verdictFor(top: Candidate?): Verdict = verdictFor(top?.confidence)

    fun verdictForMark(top: Mark?): Verdict = verdictFor(top?.confidence)

    private fun verdictFor(confidence: Int?): Verdict = when {
        confidence == null -> Verdict.CLEAR
        confidence >= HIT_THRESHOLD -> Verdict.DETECTED
        confidence >= MAYBE_THRESHOLD -> Verdict.UNCERTAIN
        else -> Verdict.CLEAR
    }
}
