package com.hisham.scanner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.hisham.scanner.data.FindingsStore
import com.hisham.scanner.data.ReportWriter

@Composable
fun ReportScreen(store: FindingsStore, onOpenGuide: () -> Unit) {
    val context = LocalContext.current
    val findings by store.items.collectAsState()

    var who by remember { mutableStateOf(TextFieldValue(store.reporterName)) }
    var place by remember { mutableStateOf(TextFieldValue(store.placeName)) }
    var note by remember { mutableStateOf("") }

    val draft = ReportWriter.compose(findings, who.text, place.text)

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = Metrics.Gutter)) {
        item {
            Spacer(Modifier.height(26.dp))
            Text("FINDINGS", style = LabelStyle)
            Spacer(Modifier.height(10.dp))
            Display("What you flagged.")
            Spacer(Modifier.height(6.dp))
            Lead("Everything you marked suspicious during a sweep, a scan or a network probe, with the time you recorded it. This never leaves your phone.")
            Spacer(Modifier.height(28.dp))
        }

        if (findings.isEmpty()) {
            item {
                EmptyState("Nothing flagged yet. Run a sweep, a scan or a network probe, and anything you mark suspicious shows up here.")
                Spacer(Modifier.height(40.dp))
            }
        } else {
            items(findings.reversed(), key = { it.id }) { f ->
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Box(Modifier.width(1.dp).heightIn(min = 50.dp).background(Ink.Signal))
                    Column(Modifier.padding(start = 18.dp)) {
                        Text(
                            "${f.zone.uppercase()} — ${ReportWriter.time(f.at)}",
                            style = LabelStyle
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(f.title, style = TitleStyle)
                        if (f.note.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(f.note, style = BodyStyle)
                        }
                        Spacer(Modifier.height(10.dp))
                        Btn("Remove", { store.remove(f.id) }, small = true)
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Written record")
                Body("Fill these in and the draft updates. A dated, specific written account carries real weight at a police station — far more than describing it from memory at the counter.")
                Spacer(Modifier.height(24.dp))

                Text("YOUR NAME", style = LabelStyle)
                Spacer(Modifier.height(2.dp))
                UnderlineField(who, { who = it; store.reporterName = it.text }, "Name as you would sign it")

                Spacer(Modifier.height(22.dp))
                Text("PLACE — NAME AND ADDRESS", style = LabelStyle)
                Spacer(Modifier.height(2.dp))
                UnderlineField(place, { place = it; store.placeName = it.text }, "Hotel, hostel or flat, with the address")

                Spacer(Modifier.height(26.dp))
                DraftBox(draft)

                Spacer(Modifier.height(14.dp))
                BtnRow {
                    Btn("Copy", {
                        copyToClipboard(context, draft)
                        note = "Copied to the clipboard."
                    }, Modifier.weight(1f), BtnKind.Primary)
                    Btn("Share", {
                        shareText(context, draft)
                    }, Modifier.weight(1f))
                }
                if (note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(note.uppercase(), style = LabelStyle)
                }

                Spacer(Modifier.height(26.dp))
                CautionAside(
                    "This is your own account, not evidence in itself",
                    "It records what you observed and when. It is not a forensic finding and it is not legal advice. What makes it useful is that it is contemporaneous and specific — so write it before memory softens the details."
                )

                Spacer(Modifier.height(24.dp))
                BtnRow {
                    Btn("What to do next", onOpenGuide, Modifier.weight(1f))
                    Btn("Delete all", { store.clear() }, Modifier.weight(1f), BtnKind.Alert)
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun UnderlineField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    placeholder: String
) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
            if (value.text.isEmpty()) {
                Text(placeholder, style = BodyStyle.copy(color = Ink.Fg3))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = BodyStyle.copy(color = Ink.Fg),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink.Fg),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Rule(color = Ink.Line2)
    }
}

@Composable
private fun DraftBox(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Ink.Line, RoundedCornerShape(Metrics.Radius))
            .background(Ink.BgLift, RoundedCornerShape(Metrics.Radius))
            .padding(16.dp)
    ) {
        Text(
            text,
            style = MonoValueStyle.copy(
                fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                lineHeight = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Hidden camera report", text))
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Record of a hidden-camera search")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share the record"))
}
