package com.hisham.scanner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hisham.scanner.data.Content
import com.hisham.scanner.data.Reach

@Composable
fun GuideScreen() {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = Metrics.Gutter)) {

        item {
            Spacer(Modifier.height(26.dp))
            Text("KNOW THE TARGET", style = LabelStyle)
            Spacer(Modifier.height(10.dp))
            Display("Six kinds of camera, and what each gives away.")
            Spacer(Modifier.height(6.dp))
            Lead("Drawn from reported cases in Kerala and elsewhere in India. The line that matters most tells you which of these a network scan will never find.")
            Spacer(Modifier.height(36.dp))
            SectionLabel("Device types")
        }

        items(Content.devices) { d ->
            Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                Text(d.name, style = TitleStyle)
                Spacer(Modifier.height(7.dp))
                Body(d.detail)
                Field("Seen in", d.seen)
                Field("Tell", d.tell)
                Spacer(Modifier.height(10.dp))
                Row {
                    ReachChip("Network scan", d.networkFindsIt)
                    Spacer(Modifier.width(18.dp))
                    ReachChip("Lens glint", d.glintFindsIt)
                }
                Spacer(Modifier.height(20.dp))
                Rule()
            }
        }

        item {
            Spacer(Modifier.height(14.dp))
            AlertAside(
                "The row that matters is local storage",
                "A camera recording to an SD card transmits nothing. No network scan, no RF detector and no app will ever see it. In the Delhi rented-home case the landlord’s son simply pulled the memory cards by hand. Only optics and a physical search reach these, which is why the lens scan and the sweep come before anything else here."
            )
            Spacer(Modifier.height(40.dp))
            SectionLabel("Reported cases")
            Body("What the device actually turned out to be, and how it surfaced. That last part is the useful one — in nearly every case a person noticed something out of place, not a gadget.")
            Spacer(Modifier.height(22.dp))
        }

        items(Content.cases) { c ->
            Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(c.place, style = TitleStyle, modifier = Modifier.weight(1f))
                    Text(c.year.uppercase(), style = LabelStyle)
                }
                Spacer(Modifier.height(7.dp))
                Body(c.what)
                Field("Device", c.device)
                Field("Surfaced", c.found)
                Spacer(Modifier.height(20.dp))
                Rule()
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionLabel("If you find one")
            AlertAside(
                "Do not",
                "Unplug it, switch it off, or pull the memory card. Pocket it or move it. Wipe it or add your fingerprints. Tell the hotel, landlord, warden or shop staff first — if it is their device, that warning gives them time to remove it. Post it online before you report it."
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            val steps = listOf(
                "Get somewhere it cannot see you" to "Step out of frame first. Everything else can wait ninety seconds.",
                "Photograph it exactly where it is" to "A wide shot showing which room and which wall, a medium shot showing what it is mounted on, and a close-up. Record a slow video walking from the door to it. Keep timestamps on.",
                "Cover the lens without moving the device" to "A towel, tape or a bag over it. This stops further recording while leaving the scene intact.",
                "Call the police" to "112 for emergency. In Kerala, the police control room and the district cyber cell both take this seriously — there is a record of arrests in these cases.",
                "File online as well" to "cybercrime.gov.in is the National Cyber Crime Reporting Portal, with a women-and-children stream that allows anonymous reporting.",
                "Write it down while it is fresh" to "The Report tab builds a timestamped account and a complaint draft from whatever you flagged.",
                "Insist on a written acknowledgement" to "Ask for the FIR number or the portal acknowledgement number, and keep it.",
                "Stay out of the room if you can" to "Ask for a different room in a different part of the building, or leave. Do not accept an offer to have someone come and check it."
            )
            Column(Modifier.fillMaxWidth()) {
                Rule()
                steps.forEachIndexed { i, (title, body) ->
                    IndexRow(i + 1, title, body)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        item {
            SectionLabel("Who to contact")
            Contact("112", "National emergency number. Use it if the person may still be present or you feel unsafe.")
            Contact("cybercrime.gov.in", "National Cyber Crime Reporting Portal. Complaints about recording, sharing or publishing images go here, including anonymously.")
            Contact("1091", "Women’s helpline.")
            Contact("1930", "Cybercrime helpline.")
            Contact("Local station", "Go in person to the police station and district cyber cell and file a written complaint as well as reporting online. The portal alone does not always produce an FIR.")
            Spacer(Modifier.height(36.dp))
        }

        item {
            SectionLabel("The law that applies")
            Body("Enough to know your footing. For anything beyond that, talk to a lawyer — this is not legal advice.")
            Spacer(Modifier.height(22.dp))

            Row(Modifier.fillMaxWidth()) {
                Text("Voyeurism", style = TitleStyle, modifier = Modifier.weight(1f))
                Text("BNS 2023 · S.77", style = LabelStyle)
            }
            Spacer(Modifier.height(7.dp))
            Body("Covers watching or capturing the image of a woman engaged in a private act where she would expect not to be observed, and covers passing those images on.")
            Field("Sentence", "First conviction, one to three years and a fine. Subsequent conviction, three to seven years and a fine.")
            Spacer(Modifier.height(22.dp))
            Rule()
            Spacer(Modifier.height(22.dp))

            Row(Modifier.fillMaxWidth()) {
                Text("Violation of privacy", style = TitleStyle, modifier = Modifier.weight(1f))
                Text("IT ACT 2000 · S.66E", style = LabelStyle)
            }
            Spacer(Modifier.height(7.dp))
            Body("Covers intentionally capturing, publishing or transmitting an image of the private area of any person without consent, in circumstances violating their privacy. Unlike section 77, this one is not limited to women.")
            Field("Sentence", "Up to three years, or a fine up to two lakh rupees, or both.")

            Spacer(Modifier.height(26.dp))
            CautionAside(
                "Both usually apply together",
                "In the reported Kerala cases, arrests were made under the IT Act and the criminal law together. Depending on facts there may be more — POCSO where a minor is involved, and additional provisions where footage was shared or sold. Say what happened and let the police and a lawyer decide the sections."
            )

            Spacer(Modifier.height(36.dp))
            Aside(
                "Sources behind the technical claims",
                "LAPD, Hidden Spy Camera Detection using Smartphone Time-of-Flight Sensors, ACM SenSys 2021 (NUS / Yonsei) — 88.9% detection from lens retroreflection against 46% for the naked eye. DeWiCam, AsiaCCS 2018, and later traffic-pattern work — wireless cameras are identifiable from traffic shape even when the payload is encrypted. Case details are from reported Indian news coverage."
            )
            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun ReachChip(label: String, reach: Reach) {
    val (text, color) = when (reach) {
        Reach.YES -> "Finds it" to Ink.Fg
        Reach.SOMETIMES -> "Sometimes" to Ink.Caution
        Reach.NO -> "Blind" to Ink.Signal
    }
    Column {
        Text(label.uppercase(), style = LabelStyle)
        Spacer(Modifier.height(3.dp))
        Text(text.uppercase(), style = LabelStyle.copy(color = color))
    }
}

@Composable
private fun Contact(number: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
        Text(number, style = MonoValueStyle.copy(color = Ink.Fg))
        Spacer(Modifier.height(3.dp))
        Body(detail)
    }
    Rule()
}
