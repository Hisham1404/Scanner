package com.hisham.scanner.data

/**
 * Reference content, shared with the web build. Hiding spots and device types
 * are taken from reported cases, not invented.
 */

data class DeviceType(
    val name: String,
    val detail: String,
    val seen: String,
    val tell: String,
    val networkFindsIt: Reach,
    val glintFindsIt: Reach
)

enum class Reach { YES, SOMETIMES, NO }

data class CaseFile(
    val place: String,
    val year: String,
    val what: String,
    val device: String,
    val found: String
)

data class Checkpoint(val title: String, val hint: String, val case: String? = null)

data class Zone(
    val id: String,
    val name: String,
    val blurb: String,
    val checks: List<Checkpoint>
)

object Content {

    val devices = listOf(
        DeviceType(
            name = "Repurposed mobile phone",
            detail = "An ordinary switched-on phone, propped or taped out of sight. The cheapest option and the one that shows up most often in Indian cases.",
            seen = "Kottayam Medical College changing room (2025); Kozhikode hotel toilet ceiling; Indore office washroom.",
            tell = "A phone-sized rectangle where nothing should be: on top of a cupboard, inside a bag left behind, wedged above a false ceiling tile, face-down in a dustbin. Often warm, and usually plugged into a charger because the battery will not last a shift.",
            networkFindsIt = Reach.NO,
            glintFindsIt = Reach.YES
        ),
        DeviceType(
            name = "Disguised object camera",
            detail = "A working camera built into an everyday object. Sold openly in India on marketplaces as “nanny cams” for roughly ₹1,500–₹8,000.",
            seen = "Chargers and power adapters, wall clocks, photo frames, pens, bulb holders, smoke detectors, Wi-Fi routers, Bluetooth speakers, tissue boxes, air fresheners.",
            tell = "A pinhole on a face of the object that has no reason to be there — not a microphone grille, not a screw, not an LED. An object that is plugged in but does nothing. A charger in a socket that nobody owns.",
            networkFindsIt = Reach.SOMETIMES,
            glintFindsIt = Reach.YES
        ),
        DeviceType(
            name = "Pinhole / bare board camera",
            detail = "A lens module wired through a hole, with the body hidden on the other side of a surface. No enclosure to recognise — only the hole.",
            seen = "Hostel and trial-room ceilings, AC vents, exhaust grilles, mirror frames, false-ceiling gaps, shower fittings.",
            tell = "A hole 1–3 mm wide in a surface that should be solid. Fresh drill dust, a filled or repainted patch, silicone around a hole, or one grille slot that is darker than the rest.",
            networkFindsIt = Reach.NO,
            glintFindsIt = Reach.YES
        ),
        DeviceType(
            name = "Wi-Fi / IP camera",
            detail = "Consumer network cameras, or disguised cams with a Wi-Fi module, streaming or uploading footage.",
            seen = "Rented flats, hotels, guest houses. Also the category legitimate CCTV falls into.",
            tell = "The only category the network scan reliably finds — and it needs you on the same network as the camera, or the camera broadcasting its own pairing network.",
            networkFindsIt = Reach.YES,
            glintFindsIt = Reach.YES
        ),
        DeviceType(
            name = "Local storage only (SD card / DVR)",
            detail = "Records to a memory card or a wired recorder. The footage is collected by hand later. It transmits nothing, ever.",
            seen = "Delhi rented home — the landlord’s son pulled memory cards periodically and copied footage to a laptop.",
            tell = "Invisible to every network scanner and every RF detector while idle. Optics and physical search are the only things that find these. Treat any app that claims otherwise as dishonest.",
            networkFindsIt = Reach.NO,
            glintFindsIt = Reach.YES
        ),
        DeviceType(
            name = "Two-way mirror",
            detail = "A mirror with an observation space or camera behind it. Rare compared to the rest, but the classic trial-room fear.",
            seen = "Reported periodically in shop trial rooms across India; Kerala Police have run unannounced trial-room inspections.",
            tell = "Use the fingernail test and the cupped-hands test — both are in the sweep under Mirrors.",
            networkFindsIt = Reach.NO,
            glintFindsIt = Reach.SOMETIMES
        )
    )

    val cases = listOf(
        CaseFile(
            "Kottayam, Kerala", "March 2025",
            "A 24-year-old trainee nurse was arrested for placing a hidden camera in the changing room used by nurses and staff at the Government Medical College Hospital.",
            "A switched-on mobile phone left in the room.",
            "A staff member noticed the phone was powered on and recording."
        ),
        CaseFile(
            "Kuttiady, Kozhikode, Kerala", "June 2025",
            "A 33-year-old lab operator was arrested for planting a camera in the women’s bathroom of staff accommodation near the Taluk Hospital.",
            "Concealed camera in the bathroom.",
            "Discovered by the women living there, who held him until police arrived."
        ),
        CaseFile(
            "Vandiperiyar, Idukki, Kerala", "June 2025",
            "A policeman was arrested for placing a hidden camera in the women’s dressing area of a police station.",
            "Concealed camera in the dressing area.",
            "Only because a woman constable was sent a photograph of herself changing. Nobody had spotted the camera."
        ),
        CaseFile(
            "Kozhikode, Kerala", "—",
            "A camera was found in the ladies’ toilet of a prominent hotel. A hotel employee was arrested under the IT Act.",
            "A live mobile phone camera fixed to the ceiling. Over an hour of footage was recovered.",
            "A woman using the toilet noticed a suspicious object overhead."
        ),
        CaseFile(
            "Kochi, Kerala", "2021",
            "The Crime Branch recovered hidden cameras from a guest house and massage centre during an investigation.",
            "Multiple concealed cameras.",
            "During a police search, not by an occupant."
        ),
        CaseFile(
            "Delhi", "—",
            "A woman found hidden cameras in the home she was renting. The landlord’s son was arrested.",
            "Memory-card cameras. He retrieved the cards periodically and copied footage to a laptop.",
            "By the tenant, physically. No network scan could have found these — they never transmitted."
        ),
        CaseFile(
            "Chandigarh", "—",
            "A spy camera was found in the washroom of a paying-guest accommodation. A resident and a male friend were arrested.",
            "Concealed camera in the washroom.",
            "By residents."
        ),
        CaseFile(
            "Tamil Nadu", "—",
            "A camera was found in a women’s hostel bathroom. Footage was recovered.",
            "Camera concealed inside a dustbin, with wiring running out of it.",
            "A resident noticed wiring that did not belong on a dustbin."
        ),
        CaseFile(
            "Indore, Madhya Pradesh", "—",
            "A camera was found in an office washroom. A cleaner was arrested and two videos were recovered.",
            "A mobile phone.",
            "By staff."
        ),
        CaseFile(
            "Madhya Pradesh", "—",
            "A camera was found in the changing room of a clothing shop. The shop owner was arrested for filming women.",
            "Concealed camera in the trial room.",
            "By a customer."
        )
    )

    /** Walked in the order that finds things fastest, not room order. */
    val zones = listOf(
        Zone(
            "bath", "Bathroom",
            "Start here. In the Kerala cases the bathroom, toilet or changing area is the target far more often than the bedroom.",
            listOf(
                Checkpoint("Ceiling directly above the shower and the toilet", "Look straight up. Check ceiling tiles that sit slightly proud, gaps at the edge of a false ceiling, and anything taped up there.", "Kozhikode hotel: a phone was taped to the toilet ceiling."),
                Checkpoint("Exhaust fan and ventilation grille", "Shine a light through the slots. One slot darker than its neighbours, or a lens sitting behind the blades, is the tell."),
                Checkpoint("Geyser, pipe boxing and false panelling", "Anywhere with a removable panel and a clear view of the shower. Press panels to see which ones are loose."),
                Checkpoint("Shower fittings, holder and rail", "Check the shower head, its bracket and the curtain rail ends for a hole or an added part."),
                Checkpoint("Dustbin, bucket, cleaning supplies", "Look inside and underneath. Any wire leaving a bin is not normal.", "Tamil Nadu hostel: the camera was inside a dustbin, given away by its wiring."),
                Checkpoint("Toilet-paper holder, towel rail, hooks", "Small wall fittings at waist to chest height make good mounts and get ignored."),
                Checkpoint("Light fitting and bulb holder", "Bulb-socket cameras exist and sell cheaply. Look at the housing around the bulb, not the bulb."),
                Checkpoint("Any plug point or socket in the bathroom", "A charger or adapter plugged in and powering nothing deserves a hard look.")
            )
        ),
        Zone(
            "mirror", "Mirrors & changing",
            "Where you undress. Also where the two-way mirror tests belong.",
            listOf(
                Checkpoint("Fingernail test on every mirror", "Touch your fingernail to the glass. On a normal mirror there is a visible gap between your nail and its reflection — the silvering is behind the glass. If nail and reflection touch with no gap, treat the mirror as suspect."),
                Checkpoint("Cupped-hands test", "Cup your hands around your eyes against the glass and block out room light. A normal mirror stays black. If you can see through into a space behind, stop and leave."),
                Checkpoint("Mirror frame, edges and corners", "A pinhole drilled into a frame is easier than building a two-way mirror, and far more common."),
                Checkpoint("Wardrobe interior, shelf edges, hanging rail", "Look at the rail ends and the underside of shelves at eye level."),
                Checkpoint("Clothes hooks and hangers", "Hook cameras are a stock item. A hook that is a different make from the others is worth pulling off the wall."),
                Checkpoint("Trial-room curtain rail and gap", "In shops, check the rail and the wall opposite the gap.", "Kerala Police have run unannounced inspections of shop trial rooms.")
            )
        ),
        Zone(
            "bed", "Bed & sleeping area",
            "Anything with a straight line of sight to the bed, roughly at or above eye level.",
            listOf(
                Checkpoint("Smoke detector or anything ceiling-mounted above the bed", "Classic host object: high, unquestioned, perfect view. Check whether it is aimed at the bed rather than sitting flat and central."),
                Checkpoint("Air conditioner and its vents", "Look into the louvres and along the top edge of the unit."),
                Checkpoint("Bedside chargers, adapters and plug-in devices", "The most common disguise, because it needs power and nobody questions a charger. Count them: is there one more than there should be?"),
                Checkpoint("Alarm clock, radio, Bluetooth speaker", "Look for a pinhole on a face that has no grille and no screen."),
                Checkpoint("Lamps, shades and the underside of fittings", "Check the inside of shades and the base of the lamp."),
                Checkpoint("Headboard, wall art, photo frames", "Frames are sold pre-fitted with cameras. Tilt them and look at the edges and the back."),
                Checkpoint("Plug-in air freshener or night light", "Warm, always on, never inspected.")
            )
        ),
        Zone(
            "ceiling", "Ceiling, vents & fittings",
            "Everything above head height, in every room. Most people never look up.",
            listOf(
                Checkpoint("Walk the room once looking only at the ceiling", "Do a full slow circuit with your head back. Do not multi-task this pass."),
                Checkpoint("False-ceiling tiles and edges", "A tile sitting slightly high or out of line is the most common sign. Push tiles gently to find loose ones."),
                Checkpoint("Ceiling fan housing and rose", "Check the canopy where the rod meets the ceiling."),
                Checkpoint("Recessed and spot lights", "Look into the housing beside the lamp itself."),
                Checkpoint("Curtain rods, pelmets and the tops of cupboards", "High, flat, out of sight from below, and an easy place to rest a phone."),
                Checkpoint("Any hole in a wall or ceiling, however small", "Especially a hole with fresh dust below it, fresh filler, or silicone around it.")
            )
        ),
        Zone(
            "electrical", "Electrical & electronics",
            "A hidden camera needs power or a battery. Follow the power.",
            listOf(
                Checkpoint("Every socket, switchboard and extension board", "Look for a lens-sized hole in the faceplate and for boards that look newer than the wall."),
                Checkpoint("Wi-Fi router and set-top box", "Router-shaped cameras exist. Check for a pinhole on the front face that lines up with no button, LED or grille."),
                Checkpoint("Television — bezel, sensor window, and the wall behind it", "The IR sensor window is a plausible place to hide a lens because it is already dark plastic."),
                Checkpoint("Unexplained wires", "A wire that disappears into a wall, runs to nothing, or has been added to an object that came without one."),
                Checkpoint("Count the chargers and adapters in the room", "Unplug anything you cannot account for, then look at it in good light. In a rented place, photograph it first."),
                Checkpoint("Anything unexpectedly warm", "A camera recording continuously gets warm. Touch the back of objects you are unsure about.")
            )
        ),
        Zone(
            "objects", "Objects & décor",
            "Small things at seated or standing eye level, especially ones out of place for the room.",
            listOf(
                Checkpoint("Tissue box, books, décor pieces, artificial plants", "Look at the side facing the bed or the changing area."),
                Checkpoint("Bags, boxes or anything left behind by a previous occupant", "A bag left in a wardrobe with a small opening facing out is a known setup."),
                Checkpoint("Desk, shelves and their undersides", "Run a hand along undersides where you cannot easily see."),
                Checkpoint("Door frame, top of the door, and the gap beneath", "Check the frame at head height on both sides."),
                Checkpoint("Anything screwed to a wall that you would not expect", "Especially with a fresh screw, a bright screw head, or paint disturbed around it.")
            )
        ),
        Zone(
            "darkness", "Lights out",
            "The last pass, and the one people skip. Some things only show up when the room is genuinely dark.",
            listOf(
                Checkpoint("Kill every light and let your eyes adjust for two full minutes", "Do not shortcut this. Two minutes, no phone screen. Then look slowly around the whole room."),
                Checkpoint("Look for a faint dull-red glow", "Many night-vision cameras run infrared LEDs that leak a weak red glow visible to the naked eye in a genuinely dark room."),
                Checkpoint("Check the room again with the Infrared scan mode", "The front camera has a weaker infrared-cut filter than the rear. Treat a negative result as meaningless — flagship filters block most of this."),
                Checkpoint("Run a final Pulse scan in the dark", "This is the condition the lens scan works best in. The darker the room, the larger the difference the torch makes."),
                Checkpoint("Look for any standby LED you cannot account for", "A pinprick of light from an object that should be inert.")
            )
        )
    )

    val topHotspots = listOf(
        "Ceiling directly above the shower or toilet",
        "Exhaust fan and ventilation grilles",
        "Smoke detectors and ceiling fittings above the bed",
        "Chargers, adapters and plug-in devices",
        "Mirrors and mirror frames",
        "Dustbins and objects with wires that should not have wires",
        "False-ceiling tiles sitting slightly out of line",
        "Clothes hooks, towel rails and small wall fittings",
        "Wi-Fi routers, set-top boxes and TV bezels",
        "Wall clocks, photo frames and alarm clocks"
    )

    val totalCheckpoints: Int get() = zones.sumOf { it.checks.size }
}
