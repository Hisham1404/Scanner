/* Reference data for the sweep and the guide.
   Hiding spots and device types are taken from reported cases, not invented. */

const DEVICE_TYPES = [
  {
    id: 'phone',
    name: 'Repurposed mobile phone',
    detail: 'An ordinary switched-on phone, propped or taped out of sight. The cheapest option and the one that shows up most often in Indian cases.',
    seen: 'Kottayam Medical College changing room (2025); Kozhikode hotel toilet ceiling; Indore office washroom.',
    network: 'no',
    glint: 'yes',
    tell: 'A phone-sized rectangle where nothing should be: on top of a cupboard, inside a bag left behind, wedged above a false ceiling tile, face-down in a dustbin. Often warm, and usually plugged into a charger because the battery will not last a shift.'
  },
  {
    id: 'disguised',
    name: 'Disguised object camera',
    detail: 'A working camera built into an everyday object. Sold openly in India on marketplaces as "nanny cams" for roughly ₹1,500–₹8,000.',
    seen: 'Chargers and power adapters, wall clocks, photo frames, pens, bulb holders, smoke detectors, Wi-Fi routers, Bluetooth speakers, tissue boxes, air fresheners.',
    network: 'partial',
    glint: 'yes',
    tell: 'A pinhole on a face of the object that has no reason to be there — not a microphone grille, not a screw, not an LED. An object that is plugged in but does nothing. A charger in a socket that nobody owns.'
  },
  {
    id: 'pinhole',
    name: 'Pinhole / bare board camera',
    detail: 'A lens module wired through a hole, with the body hidden on the other side of a surface. No enclosure to recognise — only the hole.',
    seen: 'Hostel and trial-room ceilings, AC vents, exhaust grilles, mirror frames, false-ceiling gaps, shower fittings.',
    network: 'no',
    glint: 'yes',
    tell: 'A hole 1–3 mm wide in a surface that should be solid. Fresh drill dust, a filled or repainted patch, silicone around a hole, or one grille slot that is darker than the rest.'
  },
  {
    id: 'ipcam',
    name: 'Wi-Fi / IP camera',
    detail: 'Consumer network cameras, or disguised cams with a Wi-Fi module, streaming or uploading footage.',
    seen: 'Rented flats, hotels, guest houses. Also the category that legitimate CCTV falls into.',
    network: 'yes',
    glint: 'yes',
    tell: 'The only category a network scan can reliably find — and it needs you on the same network as the camera. This is the main reason a full phone app beats a website.'
  },
  {
    id: 'sdcard',
    name: 'Local storage only (SD card / DVR)',
    detail: 'Records to a memory card or a wired recorder. The footage is collected by hand later. It transmits nothing, ever.',
    seen: 'Delhi rented home — the landlord’s son pulled memory cards periodically and copied footage to a laptop.',
    network: 'no',
    glint: 'yes',
    tell: 'Invisible to every network scanner and every RF detector while idle. Optics and physical search are the only things that find these. Treat any app that claims otherwise as dishonest.'
  },
  {
    id: 'mirror',
    name: 'Two-way mirror',
    detail: 'A mirror with an observation space or camera behind it. Rare compared to the rest, but the classic trial-room fear.',
    seen: 'Reported periodically in shop trial rooms across India; Kerala Police have run unannounced trial-room inspections.',
    network: 'no',
    glint: 'partial',
    tell: 'Use the fingernail test and the cupped-hands test — both are in the sweep under Mirrors.'
  }
];

const CASES = [
  {
    place: 'Kottayam, Kerala',
    year: 'March 2025',
    what: 'A 24-year-old trainee nurse was arrested for placing a hidden camera in the changing room used by nurses and staff at the Government Medical College Hospital.',
    device: 'A switched-on mobile phone left in the room.',
    found: 'A staff member noticed the phone was powered on and recording.'
  },
  {
    place: 'Kuttiady, Kozhikode, Kerala',
    year: 'June 2025',
    what: 'A 33-year-old lab operator was arrested for planting a camera in the women’s bathroom of staff accommodation near the Taluk Hospital.',
    device: 'Concealed camera in the bathroom.',
    found: 'Discovered by the women living there, who held him until police arrived.'
  },
  {
    place: 'Vandiperiyar, Idukki, Kerala',
    year: 'June 2025',
    what: 'A policeman was arrested for placing a hidden camera in the women’s dressing area of a police station.',
    device: 'Concealed camera in the dressing area.',
    found: 'Only because a woman constable was sent a photograph of herself changing. Nobody had spotted the camera.'
  },
  {
    place: 'Kozhikode, Kerala',
    year: '—',
    what: 'A camera was found in the ladies’ toilet of a prominent hotel. A hotel employee was arrested under the IT Act.',
    device: 'A live mobile phone camera fixed to the ceiling. Over an hour of footage was recovered.',
    found: 'A woman using the toilet noticed a suspicious object overhead.'
  },
  {
    place: 'Kochi, Kerala',
    year: '2021',
    what: 'The Crime Branch recovered hidden cameras from a guest house and massage centre during an investigation.',
    device: 'Multiple concealed cameras.',
    found: 'During a police search, not by an occupant.'
  },
  {
    place: 'Delhi',
    year: '—',
    what: 'A woman found hidden cameras in the home she was renting. The landlord’s son was arrested.',
    device: 'Memory-card cameras. He retrieved the cards periodically and copied footage to a laptop.',
    found: 'By the tenant, physically. No network scan could have found these — they never transmitted.'
  },
  {
    place: 'Chandigarh',
    year: '—',
    what: 'A spy camera was found in the washroom of a paying-guest accommodation. A resident and a male friend were arrested.',
    device: 'Concealed camera in the washroom.',
    found: 'By residents.'
  },
  {
    place: 'Tamil Nadu',
    year: '—',
    what: 'A camera was found in a women’s hostel bathroom. Footage was recovered.',
    device: 'Camera concealed inside a dustbin, with wiring running out of it.',
    found: 'A resident noticed wiring that did not belong on a dustbin.'
  },
  {
    place: 'Indore, Madhya Pradesh',
    year: '—',
    what: 'A camera was found in an office washroom. A cleaner was arrested and two videos were recovered.',
    device: 'A mobile phone.',
    found: 'By staff.'
  },
  {
    place: 'Madhya Pradesh',
    year: '—',
    what: 'A camera was found in the changing room of a clothing shop. The shop owner was arrested for filming women.',
    device: 'Concealed camera in the trial room.',
    found: 'By a customer.'
  }
];

/* Zones follow the order you should physically walk a room:
   highest-value targets first, because most people stop sweeping early. */
const ZONES = [
  {
    id: 'bath',
    name: 'Bathroom',
    blurb: 'Start here. In the Kerala cases the bathroom, toilet or changing area is the target far more often than the bedroom.',
    checks: [
      { t: 'Ceiling directly above the shower and the toilet', h: 'Look straight up. Check ceiling tiles that sit slightly proud, gaps at the edge of a false ceiling, and anything taped up there.', c: 'Kozhikode hotel: a phone was taped to the toilet ceiling.' },
      { t: 'Exhaust fan and ventilation grille', h: 'Shine a light through the slots. One slot darker than its neighbours, or a lens sitting behind the blades, is the tell.' },
      { t: 'Geyser, pipe boxing and false panelling', h: 'Anywhere with a removable panel and a clear view of the shower. Press panels to see which ones are loose.' },
      { t: 'Shower fittings, holder and rail', h: 'Check the shower head, its bracket and the curtain rail ends for a hole or an added part.' },
      { t: 'Dustbin, bucket, cleaning supplies', h: 'Look inside and underneath. Any wire leaving a bin is not normal.', c: 'Tamil Nadu hostel: the camera was inside a dustbin, given away by its wiring.' },
      { t: 'Toilet-paper holder, towel rail, hooks', h: 'Small wall fittings at waist to chest height make good mounts and get ignored.' },
      { t: 'Light fitting and bulb holder', h: 'Bulb-socket cameras exist and sell cheaply. Look at the housing around the bulb, not the bulb.' },
      { t: 'Any plug point or socket in the bathroom', h: 'A charger or adapter plugged in and powering nothing deserves a hard look.' }
    ]
  },
  {
    id: 'mirror',
    name: 'Mirrors & changing',
    blurb: 'Where you undress. Also where the two-way mirror tests belong.',
    checks: [
      { t: 'Fingernail test on every mirror', h: 'Touch your fingernail to the glass. On a normal mirror there is a visible gap between your nail and its reflection — the silvering is behind the glass. If nail and reflection touch with no gap, treat the mirror as suspect.' },
      { t: 'Cupped-hands test', h: 'Cup your hands around your eyes against the glass and block out room light. A normal mirror stays black. If you can see through into a space behind, stop and leave.' },
      { t: 'Mirror frame, edges and corners', h: 'A pinhole drilled into a frame is easier than building a two-way mirror, and far more common.' },
      { t: 'Wardrobe interior, shelf edges, hanging rail', h: 'Look at the rail ends and the underside of shelves at eye level.' },
      { t: 'Clothes hooks and hangers', h: 'Hook cameras are a stock item. A hook that is a different make from the others is worth pulling off the wall.' },
      { t: 'Trial-room curtain rail and gap', h: 'In shops, check the rail and the wall opposite the gap.', c: 'Kerala Police have run unannounced inspections of shop trial rooms.' }
    ]
  },
  {
    id: 'bed',
    name: 'Bed & sleeping area',
    blurb: 'Anything with a straight line of sight to the bed, roughly at or above eye level.',
    checks: [
      { t: 'Smoke detector or anything ceiling-mounted above the bed', h: 'Classic host object: high, unquestioned, perfect view. Check whether it is aimed at the bed rather than sitting flat and central.' },
      { t: 'Air conditioner and its vents', h: 'Look into the louvres and along the top edge of the unit.' },
      { t: 'Bedside chargers, adapters and plug-in devices', h: 'The single most common disguise, because it needs power and nobody questions a charger. Count them: is there one more than there should be?' },
      { t: 'Alarm clock, radio, Bluetooth speaker', h: 'Look for a pinhole on a face that has no grille and no screen.' },
      { t: 'Lamps, shades and the underside of fittings', h: 'Check the inside of shades and the base of the lamp.' },
      { t: 'Headboard, wall art, photo frames', h: 'Frames are sold pre-fitted with cameras. Tilt them and look at the edges and the back.' },
      { t: 'Plug-in air freshener or night light', h: 'Warm, always on, never inspected.' }
    ]
  },
  {
    id: 'ceiling',
    name: 'Ceiling, vents & fittings',
    blurb: 'Everything above head height, in every room. Most people never look up.',
    checks: [
      { t: 'Walk the room once looking only at the ceiling', h: 'Do a full slow circuit with your head back. Do not multi-task this pass.' },
      { t: 'False-ceiling tiles and edges', h: 'A tile sitting slightly high or out of line is the most common sign. Push tiles gently to find loose ones.' },
      { t: 'Ceiling fan housing and rose', h: 'Check the canopy where the rod meets the ceiling.' },
      { t: 'Recessed and spot lights', h: 'Look into the housing beside the lamp itself.' },
      { t: 'Curtain rods, pelmets and the tops of cupboards', h: 'High, flat, out of sight from below, and an easy place to rest a phone.' },
      { t: 'Any hole in a wall or ceiling, however small', h: 'Especially a hole with fresh dust below it, fresh filler, or silicone around it.' }
    ]
  },
  {
    id: 'electrical',
    name: 'Electrical & electronics',
    blurb: 'A hidden camera needs power or a battery. Follow the power.',
    checks: [
      { t: 'Every socket, switchboard and extension board', h: 'Look for a lens-sized hole in the faceplate and for boards that look newer than the wall.' },
      { t: 'Wi-Fi router and set-top box', h: 'Router-shaped cameras exist. Check for a pinhole on the front face that lines up with no button, LED or grille.' },
      { t: 'Television — bezel, sensor window, and the wall behind it', h: 'The IR sensor window is a plausible place to hide a lens because it is already dark plastic.' },
      { t: 'Unexplained wires', h: 'A wire that disappears into a wall, runs to nothing, or has been added to an object that came without one.' },
      { t: 'Count the chargers and adapters in the room', h: 'Unplug anything plugged in that you cannot account for, then look at it in good light. In a rented place, photograph it first.' },
      { t: 'Anything unexpectedly warm', h: 'A camera recording continuously gets warm. Touch the back of objects you are unsure about.' }
    ]
  },
  {
    id: 'objects',
    name: 'Objects & décor',
    blurb: 'Small things at seated or standing eye level, especially ones that seem out of place for the room.',
    checks: [
      { t: 'Tissue box, books, décor pieces, artificial plants', h: 'Look at the side facing the bed or the changing area.' },
      { t: 'Bags, boxes or anything left behind by a previous occupant', h: 'A bag left in a wardrobe with a small opening facing out is a known setup.' },
      { t: 'Desk, shelves and their undersides', h: 'Run a hand along undersides where you cannot easily see.' },
      { t: 'Door frame, top of the door, and the gap beneath', h: 'Check the frame at head height on both sides.' },
      { t: 'Anything screwed to a wall that you would not expect', h: 'Especially with a fresh screw, a bright screw head, or paint disturbed around it.' }
    ]
  },
  {
    id: 'network',
    name: 'Network & radio',
    blurb: 'A browser cannot scan Wi-Fi, list devices on a network, or read MAC addresses — that is a hard limit of the web platform, not a missing feature. Here is what you can still do by hand.',
    web: false,
    checks: [
      { t: 'Open the Wi-Fi list on your phone and read every network name', h: 'Camera modules often broadcast their own setup network. Watch for names containing CAM, IPC, IPCAM, DVR, HD, SPY, or a model number followed by a long digit string.' },
      { t: 'Scan for Bluetooth devices from your phone settings', h: 'Some cameras advertise over Bluetooth LE during setup. Note any device name you do not recognise.' },
      { t: 'If it is your own network, open the router admin page', h: 'Look at the connected-devices list for anything you cannot name. Match unknown entries against the people and devices actually in the building.' },
      { t: 'Kill the lights and check the room in full darkness', h: 'Many night-vision cameras run infrared LEDs that produce a faint dull-red glow to the naked eye in a genuinely dark room. Give your eyes two full minutes to adjust before you decide.' },
      { t: 'Check the room with your phone’s front camera in the dark', h: 'Front cameras usually have a weaker infrared-cut filter than the rear camera, so they sometimes show an IR LED as a pale violet or white dot. Treat a negative result as meaningless — modern flagship filters block most of this.' }
    ]
  }
];

const HOTSPOTS_TOP = [
  'Ceiling directly above the shower or toilet',
  'Exhaust fan and ventilation grilles',
  'Smoke detectors and ceiling fittings above the bed',
  'Chargers, adapters and plug-in devices',
  'Mirrors and mirror frames',
  'Dustbins and objects with wires that should not have wires',
  'False-ceiling tiles sitting slightly out of line',
  'Clothes hooks, towel rails and small wall fittings',
  'Wi-Fi routers, set-top boxes and TV bezels',
  'Wall clocks, photo frames and alarm clocks'
];
