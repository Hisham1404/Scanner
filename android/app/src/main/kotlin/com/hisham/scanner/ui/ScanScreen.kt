package com.hisham.scanner.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.hisham.scanner.data.FindingsStore
import com.hisham.scanner.detect.Mark
import com.hisham.scanner.detect.Mode
import com.hisham.scanner.detect.Readout
import com.hisham.scanner.detect.ScanEngine
import com.hisham.scanner.detect.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

private val MODE_HINTS = mapOf(
    Mode.PULSE to "Strobes the torch and reads only the light the torch put there. Room lights, standby LEDs and windows cancel out. Slowest but by far the most trustworthy — use this one.",
    Mode.CONTINUOUS to "Torch stays on and every frame is analysed. Faster for covering a whole room, but it reacts to chrome, glass and glossy tile. Confirm anything it finds in Pulse mode.",
    Mode.INFRARED to "Front camera, torch off, lights off. Looks for infrared LEDs on night-vision cameras. Treat a negative result as meaningless — flagship IR-cut filters block most of this, with measured detection rates under 10%."
)

private class ScanState {
    val marks = MutableStateFlow<List<Mark>>(emptyList())
    val frameSize = MutableStateFlow(240 to 320)
    var torchSupported = MutableStateFlow(true)
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun ScanScreen(store: FindingsStore) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val askCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamera = it }

    var modeIndex by remember { mutableIntStateOf(0) }
    var sensitivity by remember { mutableFloatStateOf(3f) }
    val mode = Mode.entries[modeIndex]

    val engine = remember { ScanEngine() }
    val state = remember { ScanState() }
    val executor = remember { Executors.newSingleThreadExecutor() }

    val marks by state.marks.collectAsState()
    val frameSize by state.frameSize.collectAsState()
    val torchSupported by state.torchSupported.collectAsState()

    LaunchedEffect(mode, sensitivity) {
        engine.mode = mode
        engine.sensitivity = sensitivity.toInt()
    }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    val shown = Readout.visibleMarks(marks)
    val top = shown.firstOrNull()
    val verdict = Readout.verdictForMark(top)

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = Metrics.Gutter)) {
        item {
            Spacer(Modifier.height(26.dp))
            Text("LENS-GLINT SCANNER", style = LabelStyle)
            Spacer(Modifier.height(10.dp))
            Display("Look for the lens looking back.")
            Spacer(Modifier.height(20.dp))

            Segmented(
                options = listOf("Pulse", "Continuous", "Infrared"),
                selectedIndex = modeIndex,
                onSelect = { modeIndex = it }
            )
            Spacer(Modifier.height(10.dp))
            Text(MODE_HINTS[mode] ?: "", style = BodyStyle)
            Spacer(Modifier.height(22.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .border(1.dp, Ink.Line, RoundedCornerShape(Metrics.Radius))
                    .background(Color.Black, RoundedCornerShape(Metrics.Radius))
            ) {
                if (hasCamera) {
                    CameraLayer(
                        engine = engine,
                        state = state,
                        mode = mode,
                        executor = executor,
                        lifecycleOwner = lifecycleOwner
                    )
                    DetectionOverlay(shown, frameSize)
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(30.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Camera access is needed to look for a lens.",
                            style = BodyStyle.copy(color = Ink.Fg3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                        Spacer(Modifier.height(16.dp))
                        Btn("Allow camera", { askCamera.launch(Manifest.permission.CAMERA) }, kind = BtnKind.Primary)
                    }
                }

                CornerTicks()

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 42.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter)
                ) {
                    HudPill(mode.name.lowercase().replaceFirstChar { it.uppercase() }, Ink.Fg3)
                    Spacer(Modifier.width(8.dp))
                    Spacer(Modifier.weight(1f))
                    HudPill(
                        when {
                            !hasCamera -> "Idle"
                            verdict == Verdict.DETECTED -> "Detected"
                            verdict == Verdict.UNCERTAIN -> "Checking"
                            else -> "Scanning"
                        },
                        when (verdict) {
                            Verdict.DETECTED -> Ink.Signal
                            Verdict.UNCERTAIN -> Ink.Caution
                            else -> Ink.Fg2
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            ReadoutPanel(verdict, top?.confidence, mode)

            if (!torchSupported && mode != Mode.INFRARED) {
                Spacer(Modifier.height(16.dp))
                CautionAside(
                    "No torch on this camera",
                    "Pulse mode needs the torch. Hold a separate light right next to the phone camera and the same physics still applies — the light has to sit beside the lens you are looking through."
                )
            }

            Spacer(Modifier.height(22.dp))
            Text("SENSITIVITY", style = LabelStyle)
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                valueRange = 1f..5f,
                steps = 3,
                colors = SliderDefaults.colors(
                    thumbColor = Ink.Fg,
                    activeTrackColor = Ink.Fg2,
                    inactiveTrackColor = Ink.Line2
                )
            )
            Text(
                "Lower is stricter and gives fewer false alarms. Raise it only for something far away or badly lit, and expect more noise.",
                style = BodyStyle
            )

            Spacer(Modifier.height(20.dp))
            Btn(
                "Log this spot",
                {
                    val c = shown.firstOrNull()
                    store.add(
                        "scan",
                        "Lens scan",
                        "Candidate at ${c?.confidence ?: 0}% confidence",
                        if (c != null)
                            "Seen across ${c.hits} detection cycles in ${mode.name.lowercase()} mode."
                        else
                            "Logged manually during a ${mode.name.lowercase()} scan with no candidate on screen."
                    )
                },
                Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(34.dp))
            SectionLabel("How to hold it")
            listOf(
                "Kill the lights and draw the curtains" to "The scanner reads the difference the torch makes. The darker the room, the larger that difference is.",
                "Hold the phone at arm’s length, screen towards you" to "The torch has to be beside the lens you are looking through. Watch the screen, not the room.",
                "Go slowly — about three seconds per metre" to "A candidate has to be seen across several cycles before it scores highly, so sweeping fast defeats the thing that makes this reliable.",
                "Stand one to three metres back" to "Too close and the beam overshoots; too far and a lens is too few pixels to resolve.",
                "Cross every suspect spot twice, from different angles" to "A chrome tap throws a highlight that dies when you move. A lens keeps returning light to you. That difference is the real test, and it is yours to make, not the app’s.",
                "Sweep the ceiling as its own pass" to "Then above eye level, then waist height. Do the ceiling with nothing else on your mind."
            ).forEachIndexed { i, (t, b) -> IndexRow(i + 1, t, b) }

            Spacer(Modifier.height(28.dp))
            CautionAside(
                "What this will flag that is not a camera",
                "Polished taps and shower fittings, mirror edges, glass and spectacle lenses, screw heads, smooth tile, screens, glossy paint, foil, animal eyes, and reflective thread in curtains. Retroreflection is a property of a lens, not something unique to cameras. The two-angle test above is what separates them — do it every time."
            )
            Spacer(Modifier.height(56.dp))
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraLayer(
    engine: ScanEngine,
    state: ScanState,
    mode: Mode,
    executor: java.util.concurrent.Executor,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

    // Keyed on mode alone. AndroidView's update block would re-run on every
    // recomposition - and this screen recomposes on every detection cycle - so
    // binding there would tear the camera down and rebuild it several times a
    // second.
    DisposableEffect(mode) {
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            p.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            val selector = if (mode == Mode.INFRARED) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val camera = runCatching {
                p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            }.getOrNull() ?: return@addListener

            val torchAvailable = camera.cameraInfo.hasFlashUnit()
            state.torchSupported.value = torchAvailable
            engine.torchAvailable = torchAvailable
            engine.reset()

            // Lock exposure and white balance so the torch-on and torch-off
            // frames are genuinely comparable. This is the part the browser
            // cannot do, and it is most of why native pulse mode is steadier:
            // the web build has to pad its settle time to absorb the camera
            // re-metering between the two frames.
            runCatching {
                Camera2CameraControl.from(camera.cameraControl).captureRequestOptions =
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, mode == Mode.PULSE)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, mode == Mode.PULSE)
                        .build()
            }

            if (torchAvailable) {
                runCatching { camera.cameraControl.enableTorch(mode == Mode.CONTINUOUS) }
            }

            analysis.setAnalyzer(executor) { proxy ->
                try {
                    val image = proxy.image
                    if (image != null) {
                        val outcome = engine.onFrame(
                            image,
                            proxy.imageInfo.rotationDegrees,
                            System.currentTimeMillis()
                        )
                        if (outcome.wantTorchChange && torchAvailable) {
                            runCatching { camera.cameraControl.enableTorch(outcome.wantTorchOn) }
                        }
                        if (outcome.analysed) {
                            state.frameSize.value = engine.workWidth to engine.workHeight
                            state.marks.value = engine.marks()
                        }
                    }
                } finally {
                    proxy.close()
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            // Release the camera and make sure the torch is not left burning.
            runCatching { provider?.unbindAll() }
        }
    }
}

/**
 * The detection marks: a thin ring with four outward sight ticks and a mono
 * readout beside it. Red at 70 and up, amber from 48, grey below.
 */
@Composable
private fun DetectionOverlay(shown: List<Mark>, frameSize: Pair<Int, Int>) {
    val (fw, fh) = frameSize
    Canvas(Modifier.fillMaxSize()) {
        if (fw <= 0 || fh <= 0) return@Canvas
        val sx = size.width / fw
        val sy = size.height / fh

        shown.forEach { c ->
            val x = c.x * sx
            val y = c.y * sy
            val r = maxOf(18f, c.r * sx * 2.2f)
            val color = when {
                c.confidence >= Readout.HIT_THRESHOLD -> Ink.Signal
                c.confidence >= Readout.MAYBE_THRESHOLD -> Ink.Caution
                else -> Ink.Fg.copy(alpha = 0.5f)
            }
            val stroke = maxOf(1.4f, size.width / 560f)

            drawCircle(color, radius = r, center = Offset(x, y), style = Stroke(width = stroke))

            val t0 = r * 1.4f
            val t1 = r * 1.85f
            listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f).forEach { (dx, dy) ->
                drawLine(
                    color,
                    Offset(x + dx * t0, y + dy * t0),
                    Offset(x + dx * t1, y + dy * t1),
                    strokeWidth = stroke
                )
            }

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = color.value.toLong().let { android.graphics.Color.argb(
                        (color.alpha * 255).toInt(),
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt()
                    ) }
                    textSize = size.width / 26f
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                drawText(
                    c.confidence.toString().padStart(2, '0'),
                    x + t1 + size.width / 80f,
                    y + paint.textSize / 3f,
                    paint
                )
            }
        }
    }
}

@Composable
private fun CornerTicks() {
    Canvas(Modifier.fillMaxSize().padding(13.dp)) {
        val len = 15f * density
        val c = Ink.Fg.copy(alpha = 0.28f)
        val w = 1f * density
        // top-left
        drawLine(c, Offset(0f, 0f), Offset(len, 0f), w)
        drawLine(c, Offset(0f, 0f), Offset(0f, len), w)
        // top-right
        drawLine(c, Offset(size.width, 0f), Offset(size.width - len, 0f), w)
        drawLine(c, Offset(size.width, 0f), Offset(size.width, len), w)
        // bottom-left
        drawLine(c, Offset(0f, size.height), Offset(len, size.height), w)
        drawLine(c, Offset(0f, size.height), Offset(0f, size.height - len), w)
        // bottom-right
        drawLine(c, Offset(size.width, size.height), Offset(size.width - len, size.height), w)
        drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - len), w)
    }
}

@Composable
private fun HudPill(text: String, color: Color) {
    Box(
        Modifier
            .background(Ink.Bg.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text.uppercase(), style = LabelStyle.copy(color = color))
    }
}

/** State word, confidence, hairline meter. An instrument panel, not a badge. */
@Composable
private fun ReadoutPanel(verdict: Verdict, confidence: Int?, mode: Mode) {
    val color = when (verdict) {
        Verdict.DETECTED -> Ink.Signal
        Verdict.UNCERTAIN -> Ink.Caution
        else -> Ink.Fg3
    }
    val title = when (verdict) {
        Verdict.DETECTED -> "Strong retroreflection"
        Verdict.UNCERTAIN -> "Possible reflector"
        Verdict.CLEAR -> if (confidence == null) "Nothing standing out" else "Weak return"
        Verdict.STANDBY -> "Camera off"
    }
    val sub = when (verdict) {
        Verdict.DETECTED -> "Something at the mark is bouncing light straight back. Hold still, move a step left and right, and see whether it stays bright. If it does, go and look at that spot with your hands."
        Verdict.UNCERTAIN -> "Could be a lens, could be chrome or glass. Re-scan it in Pulse mode from a different angle before deciding."
        Verdict.CLEAR ->
            if (mode == Mode.PULSE)
                "Keep moving slowly and re-cross the same spots from two or three angles before you call it clear."
            else
                "Keep sweeping. Switch to Pulse mode to rule out glossy surfaces."
        Verdict.STANDBY -> "Pick a mode and start the scan."
    }

    Column(Modifier.fillMaxWidth()) {
        Rule()
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp)) {
            Text(
                verdict.name.lowercase().replaceFirstChar { it.uppercase() }.uppercase(),
                style = LabelStyle.copy(color = color),
                modifier = Modifier.weight(1f)
            )
            Text(
                confidence?.toString()?.padStart(2, '0') ?: "——",
                style = MonoValueStyle.copy(color = color)
            )
        }
        Meter((confidence ?: 0) / 100f, color)
        Spacer(Modifier.height(12.dp))
        Text(title, style = TitleStyle.copy(color = if (verdict == Verdict.DETECTED) Ink.Signal else Ink.Fg))
        Spacer(Modifier.height(2.dp))
        Text(sub, style = BodyStyle)
        Spacer(Modifier.height(14.dp))
        Rule()
    }
}
