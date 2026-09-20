package com.hisham.scanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hisham.scanner.data.Content
import com.hisham.scanner.data.FindingsStore
import com.hisham.scanner.data.Zone

@Composable
fun SweepScreen(store: FindingsStore, onOpenReport: () -> Unit) {
    var zoneIndex by remember { mutableIntStateOf(0) }
    val marks by store.sweep.collectAsState()
    val zone = Content.zones[zoneIndex]
    val total = Content.totalCheckpoints
    val done = marks.size

    Column(Modifier.fillMaxWidth()) {

        // zone strip
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Metrics.Gutter),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Content.zones.forEachIndexed { i, z ->
                val selected = i == zoneIndex
                val complete = z.checks.indices.all { marks.containsKey("${z.id}:$it") }
                Column(
                    Modifier.clickable { zoneIndex = i },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        z.name.uppercase(),
                        style = LabelStyle.copy(
                            color = when {
                                selected -> Ink.Fg
                                complete -> Ink.Fg2
                                else -> Ink.Fg3
                            }
                        ),
                        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (selected) Ink.Fg else Color.Transparent)
                    )
                }
            }
        }
        Rule()

        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Metrics.Gutter)
                        .padding(top = 18.dp, bottom = 34.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Meter(
                        fraction = if (total == 0) 0f else done.toFloat() / total,
                        color = Ink.Fg2,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("$done / $total", style = LabelStyle)
                }

                Column(Modifier.padding(horizontal = Metrics.Gutter)) {
                    Text("ZONE ${zoneIndex + 1} OF ${Content.zones.size}", style = LabelStyle)
                    Spacer(Modifier.height(10.dp))
                    Display(zone.name)
                    Spacer(Modifier.height(6.dp))
                    Lead(zone.blurb)
                    Spacer(Modifier.height(28.dp))
                }
                Rule()
            }

            itemsIndexed(zone.checks) { i, check ->
                val key = "${zone.id}:$i"
                val mark = marks[key]
                CheckpointRow(
                    title = check.title,
                    hint = check.hint,
                    caseNote = check.case,
                    mark = mark,
                    onMark = { act -> toggle(store, zone, i, act) }
                )
            }

            item {
                Column(Modifier.padding(Metrics.Gutter)) {
                    BtnRow {
                        Btn(
                            "Previous",
                            onClick = { if (zoneIndex > 0) zoneIndex-- },
                            enabled = zoneIndex > 0,
                            modifier = Modifier.weight(1f)
                        )
                        Btn(
                            "Next zone",
                            onClick = { if (zoneIndex < Content.zones.lastIndex) zoneIndex++ },
                            kind = BtnKind.Primary,
                            enabled = zoneIndex < Content.zones.lastIndex,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (zoneIndex == Content.zones.lastIndex) {
                        Spacer(Modifier.height(24.dp))
                        Aside(
                            "That is the full sweep",
                            "You have walked every zone. If you flagged anything, the report turns it into a written record with timestamps, plus a complaint draft you can take to a police station."
                        )
                        Spacer(Modifier.height(16.dp))
                        Btn("Open the report", onOpenReport, Modifier.fillMaxWidth(), BtnKind.Primary)
                    }

                    Spacer(Modifier.height(28.dp))
                    Aside(
                        "Marking",
                        "Clear means checked, not safe — it is a memory aid so you do not lose your place halfway through a room at midnight. Flag puts the checkpoint into the report with a timestamp."
                    )
                    Spacer(Modifier.height(20.dp))
                    Btn(
                        "Reset sweep",
                        onClick = { store.clearSweep() },
                        kind = BtnKind.Alert,
                        small = true
                    )
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

private fun toggle(store: FindingsStore, zone: Zone, index: Int, act: String) {
    val key = "${zone.id}:$index"
    val check = zone.checks[index]
    val current = store.sweep.value[key]

    if (current == act) {
        store.setSweepMark(key, null)
        if (act == "flag") store.removeMatching("sweep", zone.name, check.title)
    } else {
        store.setSweepMark(key, act)
        if (act == "flag") {
            store.add("sweep", zone.name, check.title, check.hint)
        } else {
            store.removeMatching("sweep", zone.name, check.title)
        }
    }
}

@Composable
private fun CheckpointRow(
    title: String,
    hint: String,
    caseNote: String?,
    mark: String?,
    onMark: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (mark == "flag") {
                        Modifier.padding(start = Metrics.Gutter - 6.dp)
                    } else {
                        Modifier.padding(start = Metrics.Gutter)
                    }
                )
                .padding(end = Metrics.Gutter, top = 18.dp, bottom = 18.dp)
        ) {
            if (mark == "flag") {
                Box(Modifier.width(1.dp).heightIn(min = 40.dp).background(Ink.Signal))
                Spacer(Modifier.width(5.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = TitleStyle)
                Spacer(Modifier.height(4.dp))
                Text(hint, style = BodyStyle)
                if (caseNote != null) {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Box(Modifier.width(1.dp).heightIn(min = 18.dp).background(Ink.Line))
                        Text(
                            caseNote,
                            style = BodyStyle.copy(color = Ink.Fg3),
                            modifier = Modifier.padding(start = 11.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(
                Modifier.width(62.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MicroButton("Clear", mark == "ok", Ink.Fg, Ink.Bg) { onMark("ok") }
                MicroButton("Flag", mark == "flag", Ink.Signal, Color.White) { onMark("flag") }
            }
        }
        Rule()
    }
}

@Composable
private fun MicroButton(
    label: String,
    active: Boolean,
    activeBg: Color,
    activeFg: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(Metrics.Radius))
            .background(if (active) activeBg else Color.Transparent)
            .border(
                1.dp,
                if (active) activeBg else Ink.Line,
                RoundedCornerShape(Metrics.Radius)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.uppercase(),
            style = CenteredLabel.copy(
                color = if (active) activeFg else Ink.Fg3,
                fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
        )
    }
}
