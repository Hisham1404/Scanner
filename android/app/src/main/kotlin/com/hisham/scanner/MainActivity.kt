package com.hisham.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hisham.scanner.data.FindingsStore
import com.hisham.scanner.ui.Ink
import com.hisham.scanner.ui.LabelStyle
import com.hisham.scanner.ui.Metrics
import com.hisham.scanner.ui.Rule
import com.hisham.scanner.ui.ScannerTheme
import com.hisham.scanner.ui.TitleStyle
import com.hisham.scanner.ui.GuideScreen
import com.hisham.scanner.ui.NetworkScreen
import com.hisham.scanner.ui.ReportScreen
import com.hisham.scanner.ui.ScanScreen
import com.hisham.scanner.ui.SweepScreen

enum class Tab(val label: String) {
    SCAN("Scan"),
    NETWORK("Network"),
    SWEEP("Sweep"),
    REPORT("Report"),
    GUIDE("Guide")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScannerTheme {
                Surface(Modifier.fillMaxSize(), color = Ink.Bg) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val store = remember { FindingsStore(context) }
    val findings by store.items.collectAsState()
    var tab by remember { mutableStateOf(Tab.SCAN) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Masthead(
            current = tab,
            findingCount = findings.size,
            onSelect = { tab = it }
        )

        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.SCAN -> ScanScreen(store)
                Tab.NETWORK -> NetworkScreen(store)
                Tab.SWEEP -> SweepScreen(store, onOpenReport = { tab = Tab.REPORT })
                Tab.REPORT -> ReportScreen(store, onOpenGuide = { tab = Tab.GUIDE })
                Tab.GUIDE -> GuideScreen()
            }
        }
    }
}

/**
 * Brand mark, then an underlined text nav. No pills: the selected tab is the
 * one at full opacity with a rule under it.
 */
@Composable
private fun Masthead(current: Tab, findingCount: Int, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink.Bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.Gutter)
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Mark()
            Spacer(Modifier.width(9.dp))
            Text("Scanner", style = TitleStyle)
            Spacer(Modifier.weight(1f))
            Text("ROOM SWEEP", style = LabelStyle)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Metrics.Gutter)
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Tab.entries.forEach { t ->
                val selected = t == current
                val label = if (t == Tab.REPORT && findingCount > 0) {
                    "${t.label} $findingCount"
                } else {
                    t.label
                }
                Column(
                    Modifier.clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label.uppercase(),
                        style = LabelStyle.copy(color = if (selected) Ink.Fg else Ink.Fg3),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (selected) Ink.Fg else androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
        Rule()
    }
}

@Composable
private fun Mark() {
    androidx.compose.foundation.Canvas(Modifier.width(15.dp).height(15.dp)) {
        val r = size.minDimension / 2f
        drawCircle(
            color = Ink.Fg,
            radius = r - 0.8f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f)
        )
        drawCircle(color = Ink.Fg, radius = r * 0.27f)
    }
}
