package com.hisham.scanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Hairline rules and space do the separating. There are no cards in here. */

@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = Ink.Line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(text.uppercase(), style = LabelStyle)
        Spacer(Modifier.height(9.dp))
        Rule()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun Display(text: String, modifier: Modifier = Modifier) {
    Text(text, style = DisplayStyle, modifier = modifier.fillMaxWidth())
}

@Composable
fun Lead(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LeadStyle, modifier = modifier.fillMaxWidth())
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Ink.Fg2) {
    Text(text, style = BodyStyle.copy(color = color), modifier = modifier.fillMaxWidth())
}

/**
 * Replaces the coloured callout box. A hairline on the left, a mono label, and
 * the text. Tone is carried by the rule colour alone.
 */
@Composable
fun Aside(
    label: String,
    text: String,
    tone: Color = Ink.Line2,
    labelColor: Color = Ink.Fg3,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth()) {
        Box(Modifier.width(1.dp).heightIn(min = 40.dp).background(tone))
        Column(Modifier.padding(start = 16.dp)) {
            Text(label.uppercase(), style = LabelStyle.copy(color = labelColor))
            Spacer(Modifier.height(7.dp))
            Text(text, style = BodyStyle)
        }
    }
}

@Composable
fun AlertAside(label: String, text: String, modifier: Modifier = Modifier) =
    Aside(label, text, Ink.Signal, Ink.Signal, modifier)

@Composable
fun CautionAside(label: String, text: String, modifier: Modifier = Modifier) =
    Aside(label, text, Ink.Caution, Ink.Caution, modifier)

/** The numbered-step pattern: mono numeral, title, body, hairline below. */
@Composable
fun IndexRow(
    number: Int,
    title: String,
    body: String? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
            Text(
                number.toString().padStart(2, '0'),
                style = LabelStyle.copy(fontSize = 11.sp),
                modifier = Modifier.width(38.dp).padding(top = 3.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = TitleStyle)
                if (body != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(body, style = BodyStyle)
                }
            }
        }
        Rule()
    }
}

/** A label/value pair, mono label above on narrow screens. */
@Composable
fun Field(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(top = 9.dp)) {
        Text(label.uppercase(), style = LabelStyle)
        Spacer(Modifier.height(2.dp))
        Text(value, style = BodyStyle)
    }
}

enum class BtnKind { Primary, Outline, Alert }

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Outline,
    enabled: Boolean = true,
    small: Boolean = false
) {
    val alpha = if (enabled) 1f else 0.3f
    val bg = if (kind == BtnKind.Primary) Ink.Fg.copy(alpha = alpha) else Color.Transparent
    val fg = when (kind) {
        BtnKind.Primary -> Ink.Bg
        BtnKind.Alert -> Ink.Signal.copy(alpha = alpha)
        BtnKind.Outline -> Ink.Fg.copy(alpha = alpha)
    }
    val border = when (kind) {
        BtnKind.Primary -> Ink.Fg.copy(alpha = alpha)
        BtnKind.Alert -> Ink.Signal.copy(alpha = 0.4f * alpha)
        BtnKind.Outline -> Ink.Line2.copy(alpha = 0.18f * alpha)
    }

    Box(
        modifier
            .heightIn(min = if (small) 38.dp else 46.dp)
            .clip(RoundedCornerShape(Metrics.Radius))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(Metrics.Radius))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = if (small) 14.dp else 20.dp, vertical = if (small) 9.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = TitleStyle.copy(
                color = fg,
                fontSize = if (small) 12.sp else 14.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}

/** A row of buttons that share the width evenly. */
@Composable
fun BtnRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Segmented control: filled means selected, mono uppercase throughout. */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Metrics.Radius))
            .border(1.dp, Ink.Line, RoundedCornerShape(Metrics.Radius))
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .background(if (selected) Ink.Fg else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label.uppercase(),
                    style = CenteredLabel.copy(color = if (selected) Ink.Bg else Ink.Fg3)
                )
            }
            if (i < options.lastIndex) {
                Box(Modifier.width(1.dp).heightIn(min = 42.dp).background(Ink.Line))
            }
        }
    }
}

/** A thin meter, used for confidence and for progress. */
@Composable
fun Meter(fraction: Float, color: Color = Ink.Fg3, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(Ink.Line)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(2.dp)
                .background(color)
        )
    }
}

@Composable
fun EmptyState(text: String) {
    Column(Modifier.fillMaxWidth()) {
        Rule()
        Text(
            text,
            style = BodyStyle.copy(color = Ink.Fg3, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
        )
        Rule()
    }
}
