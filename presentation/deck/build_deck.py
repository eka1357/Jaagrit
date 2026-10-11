#!/usr/bin/env python3
"""
Jaagrit (जागृत) — Keynote Competition Pitch Deck Generator
Generates presentation/out/Jaagrit.pptx conforming to the Horizon design identity.
16:9 widescreen, bold typography (Mukta), real assets, verified numbers, and no invented stats.
"""

import os
import sys
from PIL import Image
from pptx import Presentation
from pptx.util import Inches, Pt, Length
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml import parse_xml
from pptx.oxml.ns import nsdecls

# ---------------------------------------------------------------------------
# COLOR SYSTEM (Horizon Identity)
# ---------------------------------------------------------------------------
IVORY = RGBColor(0xF4, 0xF0, 0xE6)       # #F4F0E6 (Light slide bg / light text)
FOREST = RGBColor(0x18, 0x39, 0x2B)      # #18392B (Primary brand dark green)
LIME = RGBColor(0xB9, 0xEE, 0x45)        # #B9EE45 (Keynote accent lime)
DARK_BG = RGBColor(0x0A, 0x12, 0x0E)     # #0A120E (Near-black dark slide bg)
DARK_CARD = RGBColor(0x13, 0x22, 0x1B)   # #13221B (Card container on dark slides)
MUTED_SAND = RGBColor(0xE8, 0xE1, 0xD3)  # #E8E1D3 (Card borders & light card bg)
MUTED_TEXT = RGBColor(0x66, 0x77, 0x6E)  # #66776E (Secondary caption text)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)       # Pure white
ALERT_RED = RGBColor(0xB7, 0x1C, 0x1C)   # #B71C1C (Level 3 Critical Alert ONLY)
AMBER = RGBColor(0xD9, 0x77, 0x06)       # #D97706 (L2 caution/fatigue)

FONT_PRIMARY = "Mukta"

# ---------------------------------------------------------------------------
# XML HELPERS
# ---------------------------------------------------------------------------
def apply_mukta_cs(run):
    """Ensure Devanagari text uses Mukta complex-script font via OpenXML."""
    try:
        rPr = run._r.get_or_add_rPr()
        cs = rPr.find(f"{{{nsdecls('a')['a']}}}cs")
        if cs is None:
            cs = parse_xml(f'<a:cs {nsdecls("a")} typeface="{FONT_PRIMARY}"/>')
            rPr.append(cs)
        else:
            cs.set("typeface", FONT_PRIMARY)
        
        ea = rPr.find(f"{{{nsdecls('a')['a']}}}ea")
        if ea is None:
            ea = parse_xml(f'<a:ea {nsdecls("a")} typeface="{FONT_PRIMARY}"/>')
            rPr.append(ea)
        else:
            ea.set("typeface", FONT_PRIMARY)
    except Exception:
        pass

def set_alt_text(shape, text):
    """Set descriptive accessibility alt text on shape."""
    try:
        cNvPr = shape._element.xpath(".//p:cNvPr")
        if cNvPr:
            cNvPr[0].set("descr", text)
            cNvPr[0].set("title", text)
    except Exception:
        pass

# ---------------------------------------------------------------------------
# CORE SLIDE BUILDER CLASS
# ---------------------------------------------------------------------------
class JaagritDeckBuilder:
    def __init__(self, out_path="presentation/out/Jaagrit.pptx"):
        self.prs = Presentation()
        self.prs.slide_width = Inches(13.333)
        self.prs.slide_height = Inches(7.5)
        self.blank_layout = self.prs.slide_layouts[6]
        self.out_path = out_path
        self.total_slides = 18  # 13 main + 5 appendix
        self.slide_index = 0

    def add_slide(self, theme="dark"):
        """Create a new slide with full-bleed background."""
        self.slide_index += 1
        slide = self.prs.slides.add_slide(self.blank_layout)
        bg = slide.shapes.add_shape(
            MSO_SHAPE.RECTANGLE, 0, 0, Inches(13.333), Inches(7.5)
        )
        bg.fill.solid()
        bg.fill.fore_color.rgb = DARK_BG if theme == "dark" else IVORY
        bg.line.fill.background()
        set_alt_text(bg, "Slide background")
        return slide

    def add_header(self, slide, title, category=None, theme="dark", title_size=32, max_width=Inches(11.733)):
        """Keynote action title with optional category kicker."""
        tb = slide.shapes.add_textbox(Inches(0.8), Inches(0.48), max_width, Inches(1.35))
        tf = tb.text_frame
        tf.word_wrap = True
        tf.margin_left = tf.margin_top = tf.margin_right = tf.margin_bottom = 0

        p = tf.paragraphs[0]
        p.space_after = Pt(2)
        if category:
            r_cat = p.add_run()
            r_cat.text = category.upper() + "\n"
            r_cat.font.name = FONT_PRIMARY
            r_cat.font.size = Pt(13)
            r_cat.font.bold = True
            r_cat.font.color.rgb = LIME if theme == "dark" else FOREST
            apply_mukta_cs(r_cat)

        r_title = p.add_run()
        r_title.text = title
        r_title.font.name = FONT_PRIMARY
        r_title.font.size = Pt(title_size)
        r_title.font.bold = True
        r_title.font.color.rgb = IVORY if theme == "dark" else FOREST
        apply_mukta_cs(r_title)

    def add_footer(self, slide, current_num, theme="dark", is_appendix=False):
        """Minimal footer with wordmark and slide counter."""
        tb = slide.shapes.add_textbox(Inches(0.8), Inches(6.9), Inches(11.733), Inches(0.4))
        tf = tb.text_frame
        tf.word_wrap = False
        tf.margin_left = tf.margin_top = tf.margin_right = tf.margin_bottom = 0

        p = tf.paragraphs[0]
        r_left = p.add_run()
        r_left.text = "JAAGRIT / जागृत  •  iQOO 15 (Snapdragon 8 Elite)  •  100% Offline"
        r_left.font.name = FONT_PRIMARY
        r_left.font.size = Pt(11)
        r_left.font.color.rgb = MUTED_TEXT
        apply_mukta_cs(r_left)

        prefix = "A" if is_appendix else ""
        r_right = p.add_run()
        r_right.text = f"            [{prefix}{current_num:02d} / {self.total_slides:02d}]"
        r_right.font.name = FONT_PRIMARY
        r_right.font.size = Pt(11)
        r_right.font.bold = True
        r_right.font.color.rgb = LIME if theme == "dark" else FOREST
        apply_mukta_cs(r_right)

    def add_notes(self, slide, script_en, script_hi=None, demo_prompt=None):
        """Add speaker notes with spoken English, Hindi anchor line, and demo trigger."""
        notes_tf = slide.notes_slide.notes_text_frame
        notes_tf.text = f"[SPOKEN SCRIPT - ~40 words]:\n{script_en}\n"
        if script_hi:
            notes_tf.text += f"\n[HINDI ANCHOR]:\n{script_hi}\n"
        if demo_prompt:
            notes_tf.text += f"\n*** {demo_prompt} ***\n"

    def draw_card(self, slide, left, top, width, height, fill_color, border_color=None):
        """Draw container card."""
        shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
        shape.fill.solid()
        shape.fill.fore_color.rgb = fill_color
        if border_color:
            shape.line.color.rgb = border_color
            shape.line.width = Pt(1.5)
        else:
            shape.line.fill.background()
        set_alt_text(shape, "Content card container")
        return shape

    def phone_mockup(self, slide, image_path, left, top, height, width=None, alt_text="Phone UI Screenshot"):
        """Draw realistic smartphone chassis and place screenshot neatly inside with preserved aspect ratio."""
        if width is None and os.path.exists(image_path):
            try:
                with Image.open(image_path) as img:
                    img_w, img_h = img.size
                    width = Length(int(height * (img_w / img_h)))
            except Exception:
                width = Length(int(height * 0.4545))
        elif width is None:
            width = Length(int(height * 0.4545))

        bezel_pad = Inches(0.12)
        chassis = slide.shapes.add_shape(
            MSO_SHAPE.ROUNDED_RECTANGLE,
            left - bezel_pad, top - bezel_pad,
            width + (bezel_pad * 2), height + (bezel_pad * 2)
        )
        chassis.fill.solid()
        chassis.fill.fore_color.rgb = RGBColor(0x11, 0x19, 0x14)
        chassis.line.color.rgb = RGBColor(0x28, 0x38, 0x2E)
        chassis.line.width = Pt(2.0)
        set_alt_text(chassis, "Smartphone chassis frame")

        # Speaker slot
        speaker_w = min(Inches(0.8), Length(int(width * 0.35)))
        speaker = slide.shapes.add_shape(
            MSO_SHAPE.ROUNDED_RECTANGLE,
            left + (width / 2) - (speaker_w / 2), top - Inches(0.06),
            speaker_w, Inches(0.04)
        )
        speaker.fill.solid()
        speaker.fill.fore_color.rgb = RGBColor(0x28, 0x38, 0x2E)
        speaker.line.fill.background()

        # Actual screenshot
        pic = slide.shapes.add_picture(image_path, left, top, width, height)
        set_alt_text(pic, alt_text)
        return pic

    def metric_tile(self, slide, left, top, width, height, number_str, label_str, sub_str=None, theme="dark"):
        """Keynote oversized metric tile."""
        card_bg = DARK_CARD if theme == "dark" else WHITE
        border = RGBColor(0x1F, 0x36, 0x2A) if theme == "dark" else MUTED_SAND
        self.draw_card(slide, left, top, width, height, card_bg, border)

        tb = slide.shapes.add_textbox(left + Inches(0.2), top + Inches(0.15), width - Inches(0.4), height - Inches(0.3))
        tf = tb.text_frame
        tf.word_wrap = True
        tf.margin_left = tf.margin_top = tf.margin_right = tf.margin_bottom = 0

        p1 = tf.paragraphs[0]
        r_num = p1.add_run()
        r_num.text = number_str
        r_num.font.name = FONT_PRIMARY
        r_num.font.size = Pt(38)
        r_num.font.bold = True
        r_num.font.color.rgb = LIME if theme == "dark" else FOREST
        apply_mukta_cs(r_num)

        p2 = tf.add_paragraph()
        p2.space_before = Pt(4)
        r_lbl = p2.add_run()
        r_lbl.text = label_str
        r_lbl.font.name = FONT_PRIMARY
        r_lbl.font.size = Pt(14)
        r_lbl.font.bold = True
        r_lbl.font.color.rgb = IVORY if theme == "dark" else FOREST
        apply_mukta_cs(r_lbl)

        if sub_str:
            p3 = tf.add_paragraph()
            p3.space_before = Pt(2)
            r_sub = p3.add_run()
            r_sub.text = sub_str
            r_sub.font.name = FONT_PRIMARY
            r_sub.font.size = Pt(11)
            r_sub.font.color.rgb = MUTED_TEXT
            apply_mukta_cs(r_sub)

    # -----------------------------------------------------------------------
    # SLIDE IMPLEMENTATIONS
    # -----------------------------------------------------------------------

    def build_slide_01_title(self):
        """Slide 1: Title & Hero."""
        slide = self.add_slide(theme="dark")
        self.add_footer(slide, 1, theme="dark")

        # Left Column: Brand Typography in a single cohesive vertical flow
        tb = slide.shapes.add_textbox(Inches(0.8), Inches(1.3), Inches(8.0), Inches(3.9))
        tf = tb.text_frame
        tf.word_wrap = True
        tf.margin_left = tf.margin_top = tf.margin_right = tf.margin_bottom = 0

        p1 = tf.paragraphs[0]
        r1 = p1.add_run()
        r1.text = "JAAGRIT  "
        r1.font.name = FONT_PRIMARY
        r1.font.size = Pt(56)
        r1.font.bold = True
        r1.font.color.rgb = IVORY
        apply_mukta_cs(r1)

        r1_hi = p1.add_run()
        r1_hi.text = "जागृत"
        r1_hi.font.name = FONT_PRIMARY
        r1_hi.font.size = Pt(46)
        r1_hi.font.bold = True
        r1_hi.font.color.rgb = LIME
        apply_mukta_cs(r1_hi)

        p2 = tf.add_paragraph()
        p2.space_before = Pt(18)
        r2 = p2.add_run()
        r2.text = "Engages before it alarms.\nCompletely offline."
        r2.font.name = FONT_PRIMARY
        r2.font.size = Pt(26)
        r2.font.bold = True
        r2.font.color.rgb = IVORY
        apply_mukta_cs(r2)

        p3 = tf.add_paragraph()
        p3.space_before = Pt(16)
        r3 = p3.add_run()
        r3.text = "Real-time driver alertness assistant running locally on the iQOO 15."
        r3.font.name = FONT_PRIMARY
        r3.font.size = Pt(16)
        r3.font.color.rgb = MUTED_TEXT
        apply_mukta_cs(r3)

        # Badges row at y = Inches(5.45)
        badges = [
            ("100% OFFLINE", LIME),
            ("18 ms VISION", IVORY),
            ("GRADUATED LADDER", IVORY)
        ]
        b_x = Inches(0.8)
        for text, col in badges:
            self.draw_card(slide, b_x, Inches(5.45), Inches(2.2), Inches(0.46), DARK_CARD, RGBColor(0x28, 0x3E, 0x30))
            btb = slide.shapes.add_textbox(b_x, Inches(5.48), Inches(2.2), Inches(0.4))
            bp = btb.text_frame.paragraphs[0]
            bp.alignment = PP_ALIGN.CENTER
            brun = bp.add_run()
            brun.text = text
            brun.font.name = FONT_PRIMARY
            brun.font.size = Pt(12)
            brun.font.bold = True
            brun.font.color.rgb = col
            apply_mukta_cs(brun)
            b_x += Inches(2.35)

        # Right: Phone Mockup
        phone_path = "presentation/out/shots_clean/landing_page_english.png"
        if os.path.exists(phone_path):
            self.phone_mockup(slide, phone_path, Inches(9.2), Inches(1.1), height=Inches(5.4), alt_text="Jaagrit Home Screen")

        self.add_notes(
            slide,
            "Good morning judges. This is Jaagrit. An offline Android driver alertness assistant built for the iQOO 15 that sits on the dashboard, estimates driver fatigue in real time, and talks you awake before sounding the alarm.",
            "सजग रहें, सुरक्षित चलें — पूरी तरह ऑन-डिवाइस, बिना इंटरनेट।"
        )

    def build_slide_02_problem(self):
        """Slide 2: Problem statement."""
        slide = self.add_slide(theme="dark")
        self.add_header(slide, "Drowsiness does not announce itself — it arrives in silence.", "THE REALITY OF THE ROAD", "dark", title_size=32)
        self.add_footer(slide, 2, theme="dark")

        # 3 Tension Cards
        cards_data = [
            ("01", "Micro-sleep is stealthy", "Fatigue accumulates quietly over hours. A driver doesn't realize their alertness has eroded until the first involuntary 2-second eye closure."),
            ("02", "Buzzers trigger too late", "Traditional cab hardware screams only after a driver is asleep. The shock causes panic, road swerving, and drivers routinely unplug them."),
            ("03", "Highway blind spots", "Cloud-dependent fleet monitoring fails precisely when needed most: remote highway corridors with zero cellular data connectivity.")
        ]

        card_w = Inches(3.64)
        card_h = Inches(4.5)
        start_x = Inches(0.8)
        y = Inches(1.95)

        for i, (num, headline, body) in enumerate(cards_data):
            x = start_x + (i * Inches(3.95))
            self.draw_card(slide, x, y, card_w, card_h, DARK_CARD, RGBColor(0x22, 0x36, 0x2A))

            tb = slide.shapes.add_textbox(x + Inches(0.3), y + Inches(0.3), card_w - Inches(0.6), card_h - Inches(0.6))
            tf = tb.text_frame
            tf.word_wrap = True

            p_num = tf.paragraphs[0]
            r_num = p_num.add_run()
            r_num.text = num
            r_num.font.name = FONT_PRIMARY
            r_num.font.size = Pt(28)
            r_num.font.bold = True
            r_num.font.color.rgb = LIME
            apply_mukta_cs(r_num)

            p_head = tf.add_paragraph()
            p_head.space_before = Pt(14)
            r_head = p_head.add_run()
            r_head.text = headline
            r_head.font.name = FONT_PRIMARY
            r_head.font.size = Pt(20)
            r_head.font.bold = True
            r_head.font.color.rgb = IVORY
            apply_mukta_cs(r_head)

            p_body = tf.add_paragraph()
            p_body.space_before = Pt(12)
            r_body = p_body.add_run()
            r_body.text = body
            r_body.font.name = FONT_PRIMARY
            r_body.font.size = Pt(14)
            r_body.font.color.rgb = MUTED_TEXT
            apply_mukta_cs(r_body)

        self.add_notes(
            slide,
            "Drowsiness doesn't announce itself. On dark highways between midnight and dawn, alertness drains gradually. By the time a traditional buzzer screams, a truck has traveled fifty meters blind. Drivers hate loud buzzers and unplug them. We need prevention, not just panic.",
            "नींद आने से पहले ही चेतावनी ज़रूरी है — चिल्लाने वाले अलार्म बहुत देर कर देते हैं।"
        )

    def build_slide_03_solution(self):
        """Slide 3: Solution - Three Pillars."""
        slide = self.add_slide(theme="light")
        self.add_header(slide, "Three pillars that turn an everyday phone into a guardian.", "THE JAAGRIT SOLUTION", "light", title_size=32)
        self.add_footer(slide, 3, theme="light")

        pillars = [
            ("Pillar 1", "Continuous Vision (18 ms)", "Tracks Eye Aspect Ratio (EAR) and head posture at 24.8 FPS directly on the phone's CPU, without network latency or external sensors."),
            ("Pillar 2", "Graduated Ladder (L1–L5)", "Engages early with friendly spoken check-ins while fatigue builds. Reserves high-decibel alarms, family audio, and SMS for true emergencies."),
            ("Pillar 3", "Zero-Cloud Privacy (0 Bytes)", "100% on-device operation. No camera frames or voice recordings ever touch storage or the cloud. Tested in complete airplane mode.")
        ]

        card_w = Inches(3.64)
        card_h = Inches(4.5)
        start_x = Inches(0.8)
        y = Inches(1.95)

        for i, (tag, title, desc) in enumerate(pillars):
            x = start_x + (i * Inches(3.95))
            self.draw_card(slide, x, y, card_w, card_h, WHITE, MUTED_SAND)

            tb = slide.shapes.add_textbox(x + Inches(0.3), y + Inches(0.3), card_w - Inches(0.6), card_h - Inches(0.6))
            tf = tb.text_frame
            tf.word_wrap = True

            p_tag = tf.paragraphs[0]
            r_tag = p_tag.add_run()
            r_tag.text = tag.upper()
            r_tag.font.name = FONT_PRIMARY
            r_tag.font.size = Pt(13)
            r_tag.font.bold = True
            r_tag.font.color.rgb = FOREST
            apply_mukta_cs(r_tag)

            p_title = tf.add_paragraph()
            p_title.space_before = Pt(8)
            r_title = p_title.add_run()
            r_title.text = title
            r_title.font.name = FONT_PRIMARY
            r_title.font.size = Pt(18)
            r_title.font.bold = True
            r_title.font.color.rgb = FOREST
            apply_mukta_cs(r_title)

            p_desc = tf.add_paragraph()
            p_desc.space_before = Pt(12)
            r_desc = p_desc.add_run()
            r_desc.text = desc
            r_desc.font.name = FONT_PRIMARY
            r_desc.font.size = Pt(14)
            r_desc.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(r_desc)

        self.add_notes(
            slide,
            "Jaagrit solves this with three principles. First, continuous on-device vision running in eighteen milliseconds per frame. Second, a graduated ladder that talks first and alarms last. And third, total privacy: zero bytes leave the phone, and photos never touch disk.",
            "तीन सिद्धांत: तेज़ ऑन-डिवाइस विज़न, पाँच चरणों की सीढ़ी, और शत-प्रतिशत ऑफ़लाइन सुरक्षा।"
        )

    def build_slide_04_ladder(self):
        """Slide 4: The 5-stage escalation ladder."""
        slide = self.add_slide(theme="dark")
        self.add_header(
            slide,
            "A five-stage intervention ladder that steps in gradually.",
            "GRADUATED ESCALATION",
            "dark",
            title_size=30,
            max_width=Inches(7.8)
        )
        self.add_footer(slide, 4, theme="dark")

        # Left: Vertical Ladder Timeline
        ladder_steps = [
            ("L1", "Early Warning", "Blink rate +20% or 2 yawns", "Spoken friendly question (TTS Hindi)", LIME),
            ("L2", "Active Engagement", "PERCLOS ≥ 0.12 (60 s)", "Spoken cognitive question; latency tracked", AMBER),
            ("L3", "Direct Alert", "Eyes closed ≥ 2.5 s", "Loud siren + urgent Hindi phrase + vibration", ALERT_RED),
            ("L4", "Family Voice", "No response 10 s after L3", "Pre-recorded child/family voice clip", RGBColor(0x9C, 0x27, 0xB0)),
            ("L5", "Emergency SOS", "No response 20 s after L4", "GPS emergency SMS sent over cellular radio", RGBColor(0xD3, 0x2F, 0x2F))
        ]

        step_y = Inches(1.95)
        step_h = Inches(0.86)

        for lvl, title, trigger, action, badge_col in ladder_steps:
            # Step card container
            self.draw_card(slide, Inches(0.8), step_y, Inches(7.5), step_h, DARK_CARD, RGBColor(0x22, 0x36, 0x2A))

            # Badge pill
            badge = self.draw_card(slide, Inches(1.0), step_y + Inches(0.18), Inches(0.7), Inches(0.48), badge_col)
            btb = slide.shapes.add_textbox(Inches(1.0), step_y + Inches(0.2), Inches(0.7), Inches(0.4))
            bp = btb.text_frame.paragraphs[0]
            bp.alignment = PP_ALIGN.CENTER
            brun = bp.add_run()
            brun.text = lvl
            brun.font.name = FONT_PRIMARY
            brun.font.size = Pt(14)
            brun.font.bold = True
            brun.font.color.rgb = DARK_BG if badge_col in [LIME, AMBER] else WHITE
            apply_mukta_cs(brun)

            # Details text
            tb = slide.shapes.add_textbox(Inches(1.85), step_y + Inches(0.1), Inches(6.3), Inches(0.65))
            tf = tb.text_frame
            tf.word_wrap = True
            p = tf.paragraphs[0]

            r_t = p.add_run()
            r_t.text = f"{title}: "
            r_t.font.name = FONT_PRIMARY
            r_t.font.size = Pt(14)
            r_t.font.bold = True
            r_t.font.color.rgb = IVORY
            apply_mukta_cs(r_t)

            r_trig = p.add_run()
            r_trig.text = f"{trigger}  →  "
            r_trig.font.name = FONT_PRIMARY
            r_trig.font.size = Pt(12)
            r_trig.font.color.rgb = LIME
            apply_mukta_cs(r_trig)

            r_act = p.add_run()
            r_act.text = action
            r_act.font.name = FONT_PRIMARY
            r_act.font.size = Pt(12)
            r_act.font.color.rgb = MUTED_TEXT
            apply_mukta_cs(r_act)

            step_y += Inches(0.95)

        # Right: Real Critical Red Alert Phone Mockup
        shot_red = "presentation/out/shots_clean/monitoring_critical_red.jpg"
        if os.path.exists(shot_red):
            self.phone_mockup(slide, shot_red, Inches(9.2), Inches(1.85), height=Inches(4.8), alt_text="Real Critical Alert Red Screen")

        self.add_notes(
            slide,
            "Here is the heart of Jaagrit: the graduated ladder. At level one, a conversational check. At level two, a simple cognitive question to gauge reaction speed. If eyes close for two and a half seconds, Level Three fires instantly. If there is no response, Level Four plays a family voice. After twenty seconds, Level Five sends an emergency SMS with GPS.",
            "हल्की बातचीत से शुरू होकर, परिवार की आवाज़ और आपातकालीन एसएमएस तक।"
        )

    def build_slide_05_demo_clip(self):
        """Slide 5: Live demo video playback & handover."""
        slide = self.add_slide(theme="dark")
        self.add_header(slide, "From subtle drowsiness to critical intervention in real time.", "LIVE PROOF & DEMO BACKUP", "dark", title_size=32)
        self.add_footer(slide, 5, theme="dark")

        # Left: Video player inside realistic smartphone chassis
        video_path = "presentation/out/alarm_clip_loop.mp4"
        poster_path = "presentation/out/shots_clean/alarm_clip_poster.jpg"

        v_left = Inches(0.8)
        v_top = Inches(2.10)
        v_height = Inches(4.55)
        v_width = Length(int(v_height * (608 / 1080)))  # 9:16 aspect ratio

        # Chassis
        bezel_pad = Inches(0.12)
        chassis = slide.shapes.add_shape(
            MSO_SHAPE.ROUNDED_RECTANGLE,
            v_left - bezel_pad, v_top - bezel_pad,
            v_width + (bezel_pad * 2), v_height + (bezel_pad * 2)
        )
        chassis.fill.solid()
        chassis.fill.fore_color.rgb = RGBColor(0x11, 0x19, 0x14)
        chassis.line.color.rgb = RGBColor(0x28, 0x38, 0x2E)
        chassis.line.width = Pt(2.0)
        set_alt_text(chassis, "Smartphone chassis frame for demo")

        if os.path.exists(video_path) and os.path.exists(poster_path):
            try:
                slide.shapes.add_movie(
                    video_path,
                    v_left, v_top, v_width, v_height,
                    poster_frame_image=poster_path,
                    mime_type="video/mp4"
                )
            except Exception:
                pic = slide.shapes.add_picture(poster_path, v_left, v_top, v_width, v_height)
                set_alt_text(pic, "Alarm Demo Recording Poster")
        elif os.path.exists(poster_path):
            pic = slide.shapes.add_picture(poster_path, v_left, v_top, v_width, v_height)
            set_alt_text(pic, "Alarm Demo Recording Poster")

        # Right side: Breakdown cards explaining what happens in this real recording
        breakdowns = [
            ("01. Normal Monitoring (98/100)", "Green UI tracks facial landmarks continuously at 24.8 FPS. Zero CPU stutter or frame lag."),
            ("02. Drowsiness Detected (35/100)", "Driver simulates eye closure. High PERCLOS (56%) immediately penalizes heuristic score into amber 'Fatigued'."),
            ("03. Critical L3 Trigger (2.5 s)", "2,500 ms sustained closure immediately trips Level 3. Full-bleed red screen, alarm on USAGE_ALARM stream, and urgent Hindi TTS."),
            ("04. Instant Hands-Free Recovery", "Eyes open for ≥ 3.0 s OR tapping the big 'I'M AWAKE' button silences alarm and cancels pending SMS.")
        ]

        by = Inches(2.10)
        for title, desc in breakdowns:
            self.draw_card(slide, Inches(3.9), by, Inches(8.6), Inches(1.02), DARK_CARD, RGBColor(0x22, 0x36, 0x2A))
            tb = slide.shapes.add_textbox(Inches(4.1), by + Inches(0.10), Inches(8.2), Inches(0.8))
            tf = tb.text_frame
            tf.word_wrap = True

            p1 = tf.paragraphs[0]
            r1 = p1.add_run()
            r1.text = title
            r1.font.name = FONT_PRIMARY
            r1.font.size = Pt(16)
            r1.font.bold = True
            r1.font.color.rgb = LIME
            apply_mukta_cs(r1)

            p2 = tf.add_paragraph()
            p2.space_before = Pt(2)
            r2 = p2.add_run()
            r2.text = desc
            r2.font.name = FONT_PRIMARY
            r2.font.size = Pt(13)
            r2.font.color.rgb = MUTED_TEXT
            apply_mukta_cs(r2)

            by += Inches(1.18)

        self.add_notes(
            slide,
            "Let's watch it happen. When my eyes close during a fatigue event, the PERCLOS metric drops my score to thirty-five. The moment two point five seconds elapse, Level Three triggers: high-contrast red flash, max-volume alarm, and urgent Hindi voice. Tapping 'I'm Awake' or opening eyes immediately cancels the alert.",
            "दो दशमलव पाँच सेकंड आँखें बंद — और सिस्टम तुरंत 'जागो' का अलर्ट देता है।",
            demo_prompt="[NOW SWITCH TO LIVE PHONE DEMO ON iQOO 15]"
        )

    def build_slide_06_calibration(self):
        """Slide 6: Personal calibration and honest accuracy."""
        slide = self.add_slide(theme="light")
        self.add_header(slide, "Learned from the driver's own face, not a generic average.", "PERSONAL CALIBRATION & ACCURACY", "light", title_size=32)
        self.add_footer(slide, 6, theme="light")

        # Left 4 Metric Tiles
        self.metric_tile(slide, Inches(0.8), Inches(1.95), Inches(2.7), Inches(2.15), "18 ms", "Inference Latency", "Snapdragon 8 Elite CPU", "light")
        self.metric_tile(slide, Inches(3.7), Inches(1.95), Inches(2.7), Inches(2.15), "24.8 FPS", "Camera Pipeline Rate", "Continuous real-time flow", "light")
        self.metric_tile(slide, Inches(0.8), Inches(4.35), Inches(2.7), Inches(2.15), "5.6x", "Separation Dynamic Range", "0.34 Open vs 0.06 Closed EAR", "light")
        self.metric_tile(slide, Inches(3.7), Inches(4.35), Inches(2.7), Inches(2.15), "< 34°C", "Sustained Phone Temp", "Throttling threshold: 42°C", "light")

        # Right: Explanation Card
        self.draw_card(slide, Inches(6.8), Inches(1.95), Inches(5.7), Inches(4.55), WHITE, MUTED_SAND)
        tb = slide.shapes.add_textbox(Inches(7.1), Inches(2.2), Inches(5.1), Inches(4.0))
        tf = tb.text_frame
        tf.word_wrap = True

        p1 = tf.paragraphs[0]
        r1 = p1.add_run()
        r1.text = "How Personal Calibration Works\n"
        r1.font.name = FONT_PRIMARY
        r1.font.size = Pt(22)
        r1.font.bold = True
        r1.font.color.rgb = FOREST
        apply_mukta_cs(r1)

        points = [
            ("Statistical Medians (D4):", "Uses medians across open/closed eyes, MAR, and response time — completely immune to single-frame outliers."),
            ("Custom Midpoint Threshold:", "Threshold = ClosedMedian + 0.5 × (OpenMedian − ClosedMedian). Automatically adapts to thick spectacles and narrow eye profiles."),
            ("Relative Dashboard Tilt (D18):", "Normalizes against the 7–9° dashboard mount tilt, so looking at road mirrors never triggers false head-droop alarms.")
        ]

        for head, body in points:
            p = tf.add_paragraph()
            p.space_before = Pt(12)
            rh = p.add_run()
            rh.text = head + " "
            rh.font.name = FONT_PRIMARY
            rh.font.size = Pt(13)
            rh.font.bold = True
            rh.font.color.rgb = FOREST
            apply_mukta_cs(rh)

            rb = p.add_run()
            rb.text = body
            rb.font.name = FONT_PRIMARY
            rb.font.size = Pt(13)
            rb.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(rb)

        self.add_notes(
            slide,
            "Every driver has different eyes, posture, and glasses. Jaagrit runs a short calibration before driving. We take the statistical median of open and closed eyes, yielding a five-point-six times dynamic separation. We also calibrate neutral dashboard tilt, preventing false alerts when glancing at road mirrors.",
            "हर ड्राइवर की आँखें अलग होती हैं — हमारा कैलिब्रेशन हर चेहरे के हिसाब से सीमा तय करता है।"
        )

    def build_slide_07_emergency_sms(self):
        """Slide 7: Level 5 Emergency SMS with GPS."""
        slide = self.add_slide(theme="light")
        self.add_header(slide, "Automated SOS with live GPS when a driver is unresponsive.", "THE SAFETY NET (LEVEL 5)", "light", title_size=32)
        self.add_footer(slide, 7, theme="light")

        # Left: Clean SMS Template Visual
        self.draw_card(slide, Inches(0.8), Inches(1.95), Inches(5.6), Inches(4.55), WHITE, MUTED_SAND)
        tb = slide.shapes.add_textbox(Inches(1.1), Inches(2.2), Inches(5.0), Inches(4.1))
        tf = tb.text_frame
        tf.word_wrap = True

        p_t = tf.paragraphs[0]
        rt = p_t.add_run()
        rt.text = "Verified SMS Template\n"
        rt.font.name = FONT_PRIMARY
        rt.font.size = Pt(18)
        rt.font.bold = True
        rt.font.color.rgb = FOREST
        apply_mukta_cs(rt)

        sms_box = tf.add_paragraph()
        sms_box.space_before = Pt(8)
        r_box = sms_box.add_run()
        r_box.text = (
            "ALERT: Driver may be unresponsive while driving.\n"
            "Last alert: 10:37:32 AM\n"
            "Alerts in this trip: 1\n"
            "Location: 28.5450° N, 77.1926° E\n"
            "— Sent by Jaagrit (automated safety alert)"
        )
        r_box.font.name = "Consolas"
        r_box.font.size = Pt(13)
        r_box.font.color.rgb = FOREST

        p_sub = tf.add_paragraph()
        p_sub.space_before = Pt(14)
        r_sub = p_sub.add_run()
        r_sub.text = "Sent strictly via cellular GSM radio. Works with zero data plan or cloud server."
        r_sub.font.name = FONT_PRIMARY
        r_sub.font.size = Pt(12)
        r_sub.font.color.rgb = MUTED_TEXT
        apply_mukta_cs(r_sub)

        # Right: Engineering Protections
        self.draw_card(slide, Inches(6.8), Inches(1.95), Inches(5.7), Inches(4.55), WHITE, MUTED_SAND)
        rtb = slide.shapes.add_textbox(Inches(7.1), Inches(2.2), Inches(5.1), Inches(4.1))
        rtf = rtb.text_frame
        rtf.word_wrap = True

        rp1 = rtf.paragraphs[0]
        rr1 = rp1.add_run()
        rr1.text = "Failsafe Design Principles\n"
        rr1.font.name = FONT_PRIMARY
        rr1.font.size = Pt(22)
        rr1.font.bold = True
        rr1.font.color.rgb = FOREST
        apply_mukta_cs(rr1)

        failsafes = [
            ("Pre-Drive Readiness Check:", "The app checks cellular SIM and GPS before departure. In airplane mode, it explicitly warns the driver that SMS is offline."),
            ("False-Positive Circuit Breaker:", "If >3 alerts are dismissed in 10 minutes, the app suggests recalibration rather than escalating to emergency contacts."),
            ("Any Awake Action Cancels:", "Tapping 'I'M AWAKE' or keeping eyes open for 3 seconds instantly halts the pending Level 5 SMS countdown.")
        ]

        for h, b in failsafes:
            p = rtf.add_paragraph()
            p.space_before = Pt(12)
            rh = p.add_run()
            rh.text = h + " "
            rh.font.name = FONT_PRIMARY
            rh.font.size = Pt(13)
            rh.font.bold = True
            rh.font.color.rgb = FOREST
            apply_mukta_cs(rh)

            rb = p.add_run()
            rb.text = b
            rb.font.name = FONT_PRIMARY
            rb.font.size = Pt(13)
            rb.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(rb)

        self.add_notes(
            slide,
            "If a driver suffers an emergency and doesn't respond to the alarm or family voice after thirty seconds, Level Five triggers. An automated SMS with GPS coordinates is sent directly over cellular radio. It requires no mobile internet and costs pennies.",
            "यदि चालक कोई प्रतिक्रिया नहीं देता, तो जीपीएस लोकेशन के साथ आपातकालीन संदेश तुरंत भेजा जाता है।"
        )

    def build_slide_08_trips_fleet(self):
        """Slide 8: Trips, History & Office Kit Fleet Transfer."""
        slide = self.add_slide(theme="light")
        self.add_header(slide, "Complete trip telemetry stored locally and shared seamlessly.", "FLEET PROOF & OFFICE KIT", "light", title_size=28)
        self.add_footer(slide, 8, theme="light")

        # Two Side-by-Side Clean Shots
        shot_th = "presentation/out/shots_clean/trip_history.jpg"
        shot_db = "presentation/out/shots_clean/dashboard_safety_score_cards.jpg"

        top_y = Inches(2.15)
        h_all = Inches(4.50)

        # Left: Trip History Phone Mockup
        if os.path.exists(shot_th):
            self.phone_mockup(slide, shot_th, Inches(0.8), top_y, height=h_all, alt_text="Room Database Trip History")

        # Middle: Styled Container Card for Dashboard Safety Score & Graph
        if os.path.exists(shot_db):
            m_card = self.draw_card(slide, Inches(3.3), top_y, Inches(4.8), h_all, WHITE, MUTED_SAND)
            card_img = slide.shapes.add_picture(shot_db, Inches(3.5), top_y + Inches(0.15), Inches(4.4), h_all - Inches(0.3))
            set_alt_text(card_img, "Safety Score & Alertness Graph")

        # Right: Office Kit callout card
        self.draw_card(slide, Inches(8.4), top_y, Inches(4.1), h_all, WHITE, MUTED_SAND)
        tb = slide.shapes.add_textbox(Inches(8.6), top_y + Inches(0.2), Inches(3.7), h_all - Inches(0.4))
        tf = tb.text_frame
        tf.word_wrap = True

        p1 = tf.paragraphs[0]
        r1 = p1.add_run()
        r1.text = "Office Kit Fleet Transfer\n"
        r1.font.name = FONT_PRIMARY
        r1.font.size = Pt(20)
        r1.font.bold = True
        r1.font.color.rgb = FOREST
        apply_mukta_cs(r1)

        ok_points = [
            ("Offline Database:", "On-device Room database logs 48 real trips with alertness telemetry sampled every 5 seconds."),
            ("PDF Report Export:", "One-page visual PDF generated via Android PdfDocument and saved to Download/Jaagrit/."),
            ("Instant Laptop Mirror:", "Fleet managers mirror the dashboard and transfer reports in seconds via Office Kit — zero cloud portal required.")
        ]

        for h, b in ok_points:
            p = tf.add_paragraph()
            p.space_before = Pt(12)
            rh = p.add_run()
            rh.text = h + " "
            rh.font.name = FONT_PRIMARY
            rh.font.size = Pt(13)
            rh.font.bold = True
            rh.font.color.rgb = FOREST
            apply_mukta_cs(rh)

            rb = p.add_run()
            rb.text = b
            rb.font.name = FONT_PRIMARY
            rb.font.size = Pt(12)
            rb.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(rb)

        self.add_notes(
            slide,
            "Every drive is cataloged in an on-device Room database. Here you see our real test log of forty-eight drives. Jaagrit scores trip safety, visualizes the alertness curve, and generates a one-page PDF report. Fleet managers can transfer this report to their laptop in seconds using Office Kit.",
            "यात्रा का पूरा डेटा स्थानीय डेटाबेस में सुरक्षित रहता है और बिना इंटरनेट पीडीएफ़ में एक्सपोर्ट होता है।"
        )

    def build_slide_09_prevention(self):
        """Slide 9: Prevention Before the Alarm."""
        slide = self.add_slide(theme="dark")
        self.add_header(slide, "Engaging the driver's brain before their eyes close.", "COGNITIVE PREVENTION (L1/L2)", "dark", title_size=32)
        self.add_footer(slide, 9, theme="dark")

        # Two interaction cards
        # Card 1: L1 Opener
        self.draw_card(slide, Inches(0.8), Inches(1.95), Inches(5.6), Inches(4.55), DARK_CARD, RGBColor(0x22, 0x36, 0x2A))
        tb1 = slide.shapes.add_textbox(Inches(1.1), Inches(2.2), Inches(5.0), Inches(4.1))
        tf1 = tb1.text_frame
        tf1.word_wrap = True

        p_t1 = tf1.paragraphs[0]
        rt1 = p_t1.add_run()
        rt1.text = "Level 1: Spoken Openers\n"
        rt1.font.name = FONT_PRIMARY
        rt1.font.size = Pt(20)
        rt1.font.bold = True
        rt1.font.color.rgb = LIME
        apply_mukta_cs(rt1)

        p_b1 = tf1.add_paragraph()
        p_b1.space_before = Pt(10)
        rb1 = p_b1.add_run()
        rb1.text = (
            "Spoken Hindi check-in:\n"
            "\"भाई, बहुत देर हो गई। घर पे कौन इंतज़ार कर रहा है?\"\n\n"
            "• Natural conversational tone under 8 seconds\n"
            "• 60-second cooldown between prompts\n"
            "• 2 ignored openers → 5-minute polite backoff\n"
            "• \"चुप रहो\" silences the companion for 2 minutes"
        )
        rb1.font.name = FONT_PRIMARY
        rb1.font.size = Pt(14)
        rb1.font.color.rgb = IVORY
        apply_mukta_cs(rb1)

        # Card 2: L2 Cognitive Math
        self.draw_card(slide, Inches(6.8), Inches(1.95), Inches(5.7), Inches(4.55), DARK_CARD, RGBColor(0x22, 0x36, 0x2A))
        tb2 = slide.shapes.add_textbox(Inches(7.1), Inches(2.2), Inches(5.1), Inches(4.1))
        tf2 = tb2.text_frame
        tf2.word_wrap = True

        p_t2 = tf2.paragraphs[0]
        rt2 = p_t2.add_run()
        rt2.text = "Level 2: Cognitive Latency Test\n"
        rt2.font.name = FONT_PRIMARY
        rt2.font.size = Pt(20)
        rt2.font.bold = True
        rt2.font.color.rgb = AMBER
        apply_mukta_cs(rt2)

        p_b2 = tf2.add_paragraph()
        p_b2.space_before = Pt(10)
        rb2 = p_b2.add_run()
        rb2.text = (
            "Spoken mental check:\n"
            "\"सौ में से तीस घटा। कितना बचा?\"\n\n"
            "• Measures response latency against calibration\n"
            "• Latency > 2x baseline confirms creeping fatigue\n"
            "• Microphone locked during active alarms (L3+)\n"
            "• 4 fallback touch buttons ensure zero distraction"
        )
        rb2.font.name = FONT_PRIMARY
        rb2.font.size = Pt(14)
        rb2.font.color.rgb = IVORY
        apply_mukta_cs(rb2)

        self.add_notes(
            slide,
            "True safety starts before the eyes close. At Level One and Two, Jaagrit speaks short, natural Hindi questions and simple mental arithmetic. It measures the driver's response latency. If latency doubles, fatigue is confirmed. And during loud alarms, the microphone stays locked to prevent audio distortion.",
            "अलार्म बजाने से पहले, हल्की दिमागी बातचीत से ड्राइवर को जाग्रत रखा जाता है।"
        )

    def build_slide_10_bilingual_privacy(self):
        """Slide 10: Private, Bilingual, On-Device."""
        slide = self.add_slide(theme="light")
        self.add_header(slide, "Designed for Indian drivers: Hindi first, zero cloud compromise.", "BILINGUAL & PRIVATE", "light", title_size=28)
        self.add_footer(slide, 10, theme="light")

        top_y = Inches(2.15)
        h_all = Inches(4.50)

        # Side-by-side Hindi & English Mockups
        shot_hi = "presentation/out/shots_clean/landing_page_hindi.jpg"
        shot_en = "presentation/out/shots_clean/landing_page_english.png"

        if os.path.exists(shot_hi):
            self.phone_mockup(slide, shot_hi, Inches(1.0), top_y, height=h_all, alt_text="Hindi Interface")

        if os.path.exists(shot_en):
            self.phone_mockup(slide, shot_en, Inches(3.6), top_y, height=h_all, alt_text="English Interface")

        # Right Column: Privacy Architecture Callout
        self.draw_card(slide, Inches(6.5), top_y, Inches(6.0), h_all, WHITE, MUTED_SAND)
        tb = slide.shapes.add_textbox(Inches(6.8), top_y + Inches(0.2), Inches(5.4), h_all - Inches(0.4))
        tf = tb.text_frame
        tf.word_wrap = True

        p1 = tf.paragraphs[0]
        r1 = p1.add_run()
        r1.text = "Zero-Cloud Guarantee\n"
        r1.font.name = FONT_PRIMARY
        r1.font.size = Pt(22)
        r1.font.bold = True
        r1.font.color.rgb = FOREST
        apply_mukta_cs(r1)

        points = [
            ("INTERNET Stripped in Manifest:", "android.permission.INTERNET is removed with tools:node=\"remove\" — proven by automated security tests."),
            ("Offline Hindi Voice:", "Uses Google Android offline TTS Hindi engine installed locally. Zero network queries."),
            ("No Face Storage:", "Camera frames are analyzed in ephemeral RAM and instantly discarded. Zero image files on disk."),
            ("No Account Required:", "No driver sign-up, phone binding, or fleet surveillance subscriptions.")
        ]

        for h, b in points:
            p = tf.add_paragraph()
            p.space_before = Pt(10)
            rh = p.add_run()
            rh.text = h + " "
            rh.font.name = FONT_PRIMARY
            rh.font.size = Pt(13)
            rh.font.bold = True
            rh.font.color.rgb = FOREST
            apply_mukta_cs(rh)

            rb = p.add_run()
            rb.text = b
            rb.font.name = FONT_PRIMARY
            rb.font.size = Pt(12)
            rb.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(rb)

        self.add_notes(
            slide,
            "Jaagrit was designed specifically for Indian commercial drivers. The entire interface and voice guidance toggle between English and natural Hindi. Crucially, the app requests zero internet permissions. Drivers don't have to worry about surveillance, cellular data consumption, or privacy leaks.",
            "पूरी तरह हिंदी और अंग्रेज़ी में, बिना किसी सर्वर या इंटरनेट की निर्भरता के।"
        )

    def build_slide_11_architecture_audit(self):
        """Slide 11: Under the hood - Architecture & Independent Audit."""
        slide = self.add_slide(theme="dark")
        self.add_header(slide, "Engineered for resilience: clean architecture and verified audit fixes.", "TECHNICAL DEPTH & AUDIT", "dark", title_size=32)
        self.add_footer(slide, 11, theme="dark")

        # Top: Pipeline Flow Cards
        pipe_steps = [
            ("CameraX", "KEEP_ONLY_LATEST\nFront Camera"),
            ("MediaPipe", "FaceLandmarker\n18 ms on CPU"),
            ("FatigueEngine", "Pure Kotlin\nZero Android APIs"),
            ("Ladder", "5-Level Logic\nInjected Clock"),
            ("Action Handlers", "TTS / Siren /\nSMS / Office PDF")
        ]

        px = Inches(0.8)
        pw = Inches(2.1)
        for i, (name, role) in enumerate(pipe_steps):
            self.draw_card(slide, px, Inches(1.95), pw, Inches(1.25), DARK_CARD, RGBColor(0x28, 0x42, 0x32))
            tb = slide.shapes.add_textbox(px + Inches(0.1), Inches(2.05), pw - Inches(0.2), Inches(1.05))
            tf = tb.text_frame
            tf.word_wrap = True

            p1 = tf.paragraphs[0]
            r1 = p1.add_run()
            r1.text = name + "\n"
            r1.font.name = FONT_PRIMARY
            r1.font.size = Pt(15)
            r1.font.bold = True
            r1.font.color.rgb = LIME
            apply_mukta_cs(r1)

            r2 = p1.add_run()
            r2.text = role
            r2.font.name = FONT_PRIMARY
            r2.font.size = Pt(11)
            r2.font.color.rgb = MUTED_TEXT
            apply_mukta_cs(r2)

            px += Inches(2.4)

        # Bottom Left: Independent Audit Card
        self.draw_card(slide, Inches(0.8), Inches(3.45), Inches(5.8), Inches(3.25), DARK_CARD, RGBColor(0x28, 0x42, 0x32))
        tb_a = slide.shapes.add_textbox(Inches(1.1), Inches(3.65), Inches(5.2), Inches(2.85))
        tf_a = tb_a.text_frame
        tf_a.word_wrap = True

        pa = tf_a.paragraphs[0]
        ra = pa.add_run()
        ra.text = "Independent Code Audit (AUDIT-001)\n"
        ra.font.name = FONT_PRIMARY
        ra.font.size = Pt(18)
        ra.font.bold = True
        ra.font.color.rgb = LIME
        apply_mukta_cs(ra)

        pa_body = tf_a.add_paragraph()
        pa_body.space_before = Pt(8)
        ra_body = pa_body.add_run()
        ra_body.text = (
            "• Vulnerability Found: Mixed time bases between system uptime (frames) "
            "and epoch time (engine ticks) caused timing miscalculations.\n"
            "• Fix Implemented: Injected a single monotonic Clock (elapsedRealtime) across the entire engine.\n"
            "• Verified with 8 dedicated regression unit tests in AuditRegressionTest.kt."
        )
        ra_body.font.name = FONT_PRIMARY
        ra_body.font.size = Pt(13)
        ra_body.font.color.rgb = IVORY
        apply_mukta_cs(ra_body)

        # Bottom Right: Test Suite & Decoupling Card
        self.draw_card(slide, Inches(6.9), Inches(3.45), Inches(5.6), Inches(3.25), DARK_CARD, RGBColor(0x28, 0x42, 0x32))
        tb_t = slide.shapes.add_textbox(Inches(7.2), Inches(3.65), Inches(5.0), Inches(2.85))
        tf_t = tb_t.text_frame
        tf_t.word_wrap = True

        pt = tf_t.paragraphs[0]
        rt = pt.add_run()
        rt.text = "198 Unit Tests • 0 Failures\n"
        rt.font.name = FONT_PRIMARY
        rt.font.size = Pt(18)
        rt.font.bold = True
        rt.font.color.rgb = LIME
        apply_mukta_cs(rt)

        pt_body = tf_t.add_paragraph()
        pt_body.space_before = Pt(8)
        rt_body = pt_body.add_run()
        rt_body.text = (
            "• 27 Test Suites covering vision, engine, ladder, SMS, and reports.\n"
            "• Pure Kotlin engine: runs anywhere without Android emulator dependencies.\n"
            "• Pipeline UI-Independence: Vision engine is decoupled from Compose Activity, "
            "ready to run in an Android Foreground Service."
        )
        rt_body.font.name = FONT_PRIMARY
        rt_body.font.size = Pt(13)
        rt_body.font.color.rgb = IVORY
        apply_mukta_cs(rt_body)

        self.add_notes(
            slide,
            "Under the hood, Jaagrit separates vision from Android lifecycle. Our pure-Kotlin fatigue engine has zero Android imports, tested across one hundred ninety-eight unit tests. During an independent architectural audit, we caught and fixed a subtle clock-drift bug with dedicated regression tests. This system is rock-solid.",
            "स्वतंत्र कोड ऑडिट और एक सौ अट्ठानवे यूनिट टेस्ट्स द्वारा प्रमाणित आर्किटेक्चर।"
        )

    def build_slide_12_limits_roadmap(self):
        """Slide 12: Honest limits and what's next."""
        slide = self.add_slide(theme="light")
        self.add_header(slide, "Engineering honesty: operational boundaries and what's next.", "TRANSPARENCY & ROADMAP", "light", title_size=32)
        self.add_footer(slide, 12, theme="light")

        # Left: Honest Limits
        self.draw_card(slide, Inches(0.8), Inches(1.95), Inches(5.6), Inches(4.55), WHITE, MUTED_SAND)
        tb1 = slide.shapes.add_textbox(Inches(1.1), Inches(2.2), Inches(5.0), Inches(4.1))
        tf1 = tb1.text_frame
        tf1.word_wrap = True

        p1 = tf1.paragraphs[0]
        r1 = p1.add_run()
        r1.text = "Honest Operational Limits\n"
        r1.font.name = FONT_PRIMARY
        r1.font.size = Pt(22)
        r1.font.bold = True
        r1.font.color.rgb = FOREST
        apply_mukta_cs(r1)

        limits = [
            ("Low Ambient Light:", "Relies on phone screen at max brightness (UI-4) as fill light; pitch-black truck cabins require ambient cabin lighting."),
            ("Dark Sunglasses:", "Obscures eye landmarks; system gracefully shifts primary reliance to head-droop angle and vocal checkpoints."),
            ("Extreme Head Angles:", "Tracking degrades beyond 30° yaw/roll."),
            ("Medical Disclaimer:", "Jaagrit estimates alertness heuristically — it does not claim medical diagnosis or guarantee accident immunity.")
        ]

        for h, b in limits:
            p = tf1.add_paragraph()
            p.space_before = Pt(8)
            rh = p.add_run()
            rh.text = h + " "
            rh.font.name = FONT_PRIMARY
            rh.font.size = Pt(13)
            rh.font.bold = True
            rh.font.color.rgb = FOREST
            apply_mukta_cs(rh)

            rb = p.add_run()
            rb.text = b
            rb.font.name = FONT_PRIMARY
            rb.font.size = Pt(12)
            rb.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(rb)

        # Right: Roadmap Ahead
        self.draw_card(slide, Inches(6.8), Inches(1.95), Inches(5.7), Inches(4.55), WHITE, MUTED_SAND)
        tb2 = slide.shapes.add_textbox(Inches(7.1), Inches(2.2), Inches(5.1), Inches(4.1))
        tf2 = tb2.text_frame
        tf2.word_wrap = True

        p2 = tf2.paragraphs[0]
        r2 = p2.add_run()
        r2.text = "The Roadmap Ahead\n"
        r2.font.name = FONT_PRIMARY
        r2.font.size = Pt(22)
        r2.font.bold = True
        r2.font.color.rgb = FOREST
        apply_mukta_cs(r2)

        roadmap = [
            ("Android Foreground Service:", "Move the decoupled vision pipeline to a background foreground service to support split-screen Google Maps navigation."),
            ("Dual-Camera Support:", "Integrate rear camera for road-lane departure detection alongside driver facial monitoring."),
            ("Two-Way Office Kit Depot Sync:", "Automatic check-in report push to depot dispatcher laptops over local Wi-Fi pairing.")
        ]

        for h, b in roadmap:
            p = tf2.add_paragraph()
            p.space_before = Pt(12)
            rh = p.add_run()
            rh.text = h + " "
            rh.font.name = FONT_PRIMARY
            rh.font.size = Pt(13)
            rh.font.bold = True
            rh.font.color.rgb = FOREST
            apply_mukta_cs(rh)

            rb = p.add_run()
            rb.text = b
            rb.font.name = FONT_PRIMARY
            rb.font.size = Pt(12)
            rb.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(rb)

        self.add_notes(
            slide,
            "We believe in engineering honesty. Jaagrit estimates alertness; it does not claim medical diagnosis. Dark sunglasses and pitch-black cabins reduce eye tracking accuracy, falling back to head droop. Our next milestone moves the pipeline into a Foreground Service to support Google Maps navigation.",
            "हम सतर्कता का अनुमान लगाते हैं, कोई मेडिकल दावा नहीं करते — ईमानदारी ही हमारी ताक़त है।"
        )

    def build_slide_13_close(self):
        """Slide 13: Close & Vision."""
        slide = self.add_slide(theme="dark")
        self.add_footer(slide, 13, theme="dark")

        # Large center focal box
        tb = slide.shapes.add_textbox(Inches(1.5), Inches(1.15), Inches(10.3), Inches(3.6))
        tf = tb.text_frame
        tf.word_wrap = True

        p1 = tf.paragraphs[0]
        p1.alignment = PP_ALIGN.CENTER
        r1 = p1.add_run()
        r1.text = "JAAGRIT / जागृत\n"
        r1.font.name = FONT_PRIMARY
        r1.font.size = Pt(56)
        r1.font.bold = True
        r1.font.color.rgb = IVORY
        apply_mukta_cs(r1)

        r2 = p1.add_run()
        r2.text = "Stay alert. Drive safe.\n"
        r2.font.name = FONT_PRIMARY
        r2.font.size = Pt(34)
        r2.font.bold = True
        r2.font.color.rgb = LIME
        apply_mukta_cs(r2)

        p2 = tf.add_paragraph()
        p2.alignment = PP_ALIGN.CENTER
        p2.space_before = Pt(14)
        r3 = p2.add_run()
        r3.text = "Commercial drivers are the backbone of India's economy.\nThey deserve protection on the phone they already own."
        r3.font.name = FONT_PRIMARY
        r3.font.size = Pt(18)
        r3.font.color.rgb = IVORY
        apply_mukta_cs(r3)

        # 3 Recap Badges
        p3 = tf.add_paragraph()
        p3.alignment = PP_ALIGN.CENTER
        p3.space_before = Pt(18)
        r4 = p3.add_run()
        r4.text = "18 ms Vision Engine  •  5-Stage Graduated Ladder  •  100% Offline & Private"
        r4.font.name = FONT_PRIMARY
        r4.font.size = Pt(15)
        r4.font.bold = True
        r4.font.color.rgb = LIME
        apply_mukta_cs(r4)

        # Repository / Live Demo Card
        self.draw_card(slide, Inches(3.4), Inches(4.95), Inches(6.53), Inches(1.55), DARK_CARD, RGBColor(0x28, 0x42, 0x32))
        tb_repo = slide.shapes.add_textbox(Inches(3.5), Inches(5.05), Inches(6.33), Inches(1.35))
        tf_repo = tb_repo.text_frame
        tf_repo.word_wrap = True
        tf_repo.margin_left = tf_repo.margin_top = tf_repo.margin_right = tf_repo.margin_bottom = 0

        p_rp1 = tf_repo.paragraphs[0]
        p_rp1.alignment = PP_ALIGN.CENTER
        r_rp1 = p_rp1.add_run()
        r_rp1.text = "OPEN REPOSITORY & LIVE PHONE DEMO"
        r_rp1.font.name = FONT_PRIMARY
        r_rp1.font.size = Pt(12)
        r_rp1.font.bold = True
        r_rp1.font.color.rgb = LIME
        apply_mukta_cs(r_rp1)

        p_rp2 = tf_repo.add_paragraph()
        p_rp2.alignment = PP_ALIGN.CENTER
        p_rp2.space_before = Pt(4)
        r_rp2 = p_rp2.add_run()
        r_rp2.text = "github.com/eka1357/Jaagrit"
        r_rp2.font.name = FONT_PRIMARY
        r_rp2.font.size = Pt(18)
        r_rp2.font.bold = True
        r_rp2.font.color.rgb = IVORY
        apply_mukta_cs(r_rp2)

        p_rp3 = tf_repo.add_paragraph()
        p_rp3.alignment = PP_ALIGN.CENTER
        p_rp3.space_before = Pt(4)
        r_rp3 = p_rp3.add_run()
        r_rp3.text = "iQOO 15 (Snapdragon 8 Elite)  •  Direct APK  •  Office Kit Mirror Ready"
        r_rp3.font.name = FONT_PRIMARY
        r_rp3.font.size = Pt(11)
        r_rp3.font.color.rgb = MUTED_TEXT
        apply_mukta_cs(r_rp3)

        self.add_notes(
            slide,
            "Commercial drivers are the backbone of our economy. They deserve safety technology that works with them, respects their privacy, and protects them on every dark highway. That is Jaagrit. Thank you, and I look forward to your questions.",
            "सजग रहें, सुरक्षित चलें। धन्यवाद!"
        )

    # -----------------------------------------------------------------------
    # APPENDIX Q&A SLIDES
    # -----------------------------------------------------------------------

    def build_slide_a1_accuracy(self):
        """Appendix 1: Detection accuracy & hostile conditions."""
        slide = self.add_slide(theme="light")
        self.add_header(slide, "Appendix: Detection accuracy under hostile edge conditions.", "Q&A DEFENSE", "light", title_size=30)
        self.add_footer(slide, 14, theme="light", is_appendix=True)

        rows = [
            ("Spectacles & Glare", "Tested with clear glasses. Isotropic pixel-space EAR preserves a 5.6x separation gap. Personal calibration absorbs lens refractions into the driver's custom baseline."),
            ("Night & Dim Cabins", "The app enforces maximum display brightness (UI-4) and keeps the screen on, utilizing the phone display as an active fill-light for the driver's face."),
            ("Road Vibration & Shaking", "MediaPipe FaceLandmarker operates at 24.8 FPS in live-stream mode with face-loss glitch tolerance of 300 ms, preventing momentary bumps from corrupting closure timers."),
            ("Mirror Glances vs True Nods", "Head-pitch droop threshold is calculated relative to calibrated neutral dashboard tilt (7–9°), avoiding false triggers during routine mirror checks.")
        ]

        y = Inches(1.95)
        for title, desc in rows:
            self.draw_card(slide, Inches(0.8), y, Inches(11.733), Inches(1.05), WHITE, MUTED_SAND)
            tb = slide.shapes.add_textbox(Inches(1.1), y + Inches(0.12), Inches(11.1), Inches(0.8))
            tf = tb.text_frame
            tf.word_wrap = True

            p = tf.paragraphs[0]
            rt = p.add_run()
            rt.text = title + ": "
            rt.font.name = FONT_PRIMARY
            rt.font.size = Pt(15)
            rt.font.bold = True
            rt.font.color.rgb = FOREST
            apply_mukta_cs(rt)

            rd = p.add_run()
            rd.text = desc
            rd.font.name = FONT_PRIMARY
            rd.font.size = Pt(13)
            rd.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(rd)

            y += Inches(1.2)

        self.add_notes(slide, "Q&A backup on camera robustness, glasses, and night illumination.")

    def build_slide_a2_why_ondevice(self):
        """Appendix 2: Why On-Device Only."""
        slide = self.add_slide(theme="dark")
        self.add_header(slide, "Appendix: Why an uncompromising on-device architecture?", "Q&A DEFENSE", "dark", title_size=30)
        self.add_footer(slide, 15, theme="dark", is_appendix=True)

        reasons = [
            ("Deterministic Sub-500 ms Latency", "Highway safety cannot tolerate cloud API round-trips. Analyzing frames locally on the Snapdragon 8 Elite guarantees instant 18 ms decisions."),
            ("Highway Cellular Blackspots", "Thousands of kilometers of Indian national highways lack reliable 4G/5G data. A safety app that stops working in tunnels or ghats is lethal."),
            ("Zero Biometric Liability", "Drivers fiercely resist continuous video streaming into employer cloud dashboards. Keeping all computations ephemeral in RAM earns driver adoption."),
            ("Zero Cloud Infrastructure Cost", "Scales infinitely across millions of commercial vehicles without running massive GPU inference clusters or monthly cloud bills.")
        ]

        y = Inches(1.95)
        for title, desc in reasons:
            self.draw_card(slide, Inches(0.8), y, Inches(11.733), Inches(1.05), DARK_CARD, RGBColor(0x28, 0x42, 0x32))
            tb = slide.shapes.add_textbox(Inches(1.1), y + Inches(0.12), Inches(11.1), Inches(0.8))
            tf = tb.text_frame
            tf.word_wrap = True

            p = tf.paragraphs[0]
            rt = p.add_run()
            rt.text = title + ": "
            rt.font.name = FONT_PRIMARY
            rt.font.size = Pt(15)
            rt.font.bold = True
            rt.font.color.rgb = LIME
            apply_mukta_cs(rt)

            rd = p.add_run()
            rd.text = desc
            rd.font.name = FONT_PRIMARY
            rd.font.size = Pt(13)
            rd.font.color.rgb = IVORY
            apply_mukta_cs(rd)

            y += Inches(1.2)

        self.add_notes(slide, "Q&A backup on why on-device processing was strictly chosen over cloud APIs.")

    def build_slide_a3_distraction(self):
        """Appendix 3: Driver Distraction Defense."""
        slide = self.add_slide(theme="light")
        self.add_header(slide, "Appendix: Defending against driver distraction and cognitive load.", "Q&A DEFENSE", "light", title_size=30)
        self.add_footer(slide, 16, theme="light", is_appendix=True)

        points = [
            ("Silence During Normal Driving", "The companion never speaks during normal alert driving (L0). It intervenes strictly when biological fatigue metrics cross cautionary bands."),
            ("Strict Microphone Isolation During Sirens", "During Level 3, 4, and 5 alarms, the microphone is blocked. This eliminates acoustic siren echo and driver speech recognition failures."),
            ("One-Word Immediate Silencing", "The driver can speak \"chup raho\" or tap anywhere to silence conversational check-ins for 2 minutes without disabling passive eye monitoring."),
            ("Glanceable Minimal UI", "The driver UI features only a high-contrast state numeral and color word. No telemetry graphs or complex dials compete for eye gaze.")
        ]

        y = Inches(1.95)
        for title, desc in points:
            self.draw_card(slide, Inches(0.8), y, Inches(11.733), Inches(1.05), WHITE, MUTED_SAND)
            tb = slide.shapes.add_textbox(Inches(1.1), y + Inches(0.12), Inches(11.1), Inches(0.8))
            tf = tb.text_frame
            tf.word_wrap = True

            p = tf.paragraphs[0]
            rt = p.add_run()
            rt.text = title + ": "
            rt.font.name = FONT_PRIMARY
            rt.font.size = Pt(15)
            rt.font.bold = True
            rt.font.color.rgb = FOREST
            apply_mukta_cs(rt)

            rd = p.add_run()
            rd.text = desc
            rd.font.name = FONT_PRIMARY
            rd.font.size = Pt(13)
            rd.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(rd)

            y += Inches(1.2)

        self.add_notes(slide, "Q&A backup proving why the app reduces distraction rather than causing it.")

    def build_slide_a4_helmets_twowheelers(self):
        """Appendix 4: Helmets & Two-Wheelers vs Cabs."""
        slide = self.add_slide(theme="dark")
        self.add_header(slide, "Appendix: Adapting for two-wheelers, helmets, and vehicle types.", "Q&A DEFENSE", "dark", title_size=30)
        self.add_footer(slide, 17, theme="dark", is_appendix=True)

        rows = [
            ("Commercial Cabs, Buses & Trucks (Primary)", "Optimal environment: phone sits firmly in a dashboard cradle 40–80 cm from the driver at windshield height, capturing full facial mesh."),
            ("Two-Wheeler Visor Challenges", "Full-face helmets with tinted visors block eye landmark extraction. Jaagrit's architecture falls back gracefully to head posture pitch and vocal response latency."),
            ("Extreme Road Vibrations", "Motorcycle handlebar mounts impart severe vibrations; our frame glitch filter ignores brief landmark drops (<300 ms) to prevent accidental triggers.")
        ]

        y = Inches(2.0)
        for title, desc in rows:
            self.draw_card(slide, Inches(0.8), y, Inches(11.733), Inches(1.35), DARK_CARD, RGBColor(0x28, 0x42, 0x32))
            tb = slide.shapes.add_textbox(Inches(1.1), y + Inches(0.18), Inches(11.1), Inches(0.95))
            tf = tb.text_frame
            tf.word_wrap = True

            p = tf.paragraphs[0]
            rt = p.add_run()
            rt.text = title + "\n"
            rt.font.name = FONT_PRIMARY
            rt.font.size = Pt(17)
            rt.font.bold = True
            rt.font.color.rgb = LIME
            apply_mukta_cs(rt)

            rd = tf.add_paragraph()
            rd.space_before = Pt(4)
            r_desc = rd.add_run()
            r_desc.text = desc
            r_desc.font.name = FONT_PRIMARY
            r_desc.font.size = Pt(13)
            r_desc.font.color.rgb = IVORY
            apply_mukta_cs(r_desc)

            y += Inches(1.5)

        self.add_notes(slide, "Q&A backup on commercial truck geometry vs motorcycle helmet constraints.")

    def build_slide_a5_sim_degradation(self):
        """Appendix 5: SMS & SIM-Free Graceful Degradation."""
        slide = self.add_slide(theme="light")
        self.add_header(slide, "Appendix: Graceful degradation without SIM or cellular signal.", "Q&A DEFENSE", "light", title_size=30)
        self.add_footer(slide, 18, theme="light", is_appendix=True)

        rows = [
            ("Pre-Drive Readiness Honesty", "Live screenshot proof: When no SIM card is present or airplane mode is enabled, the Home screen checklist flags 'no SIM card' with clear yellow status."),
            ("Core Safety Functions Remain 100% Active", "Emergency SMS is strictly an auxiliary Level 5 backup. Levels 1 through 4 (TTS voice, generated siren tone, family clip, vibration) operate entirely offline without radio."),
            ("Crash-Proof Telephony Layer", "The SMS dispatcher handles missing permissions, airplane mode, and carrier transmission failures gracefully without throwing unhandled exceptions.")
        ]

        y = Inches(2.0)
        for title, desc in rows:
            self.draw_card(slide, Inches(0.8), y, Inches(11.733), Inches(1.35), WHITE, MUTED_SAND)
            tb = slide.shapes.add_textbox(Inches(1.1), y + Inches(0.18), Inches(11.1), Inches(0.95))
            tf = tb.text_frame
            tf.word_wrap = True

            p = tf.paragraphs[0]
            rt = p.add_run()
            rt.text = title + "\n"
            rt.font.name = FONT_PRIMARY
            rt.font.size = Pt(17)
            rt.font.bold = True
            rt.font.color.rgb = FOREST
            apply_mukta_cs(rt)

            rd = tf.add_paragraph()
            rd.space_before = Pt(4)
            r_desc = rd.add_run()
            r_desc.text = desc
            r_desc.font.name = FONT_PRIMARY
            r_desc.font.size = Pt(13)
            r_desc.font.color.rgb = RGBColor(0x44, 0x55, 0x4C)
            apply_mukta_cs(r_desc)

            y += Inches(1.5)

        self.add_notes(slide, "Q&A backup demonstrating that the app never crashes or degrades core safety when SIM is missing.")

    # -----------------------------------------------------------------------
    # BUILD ORCHESTRATION
    # -----------------------------------------------------------------------
    def build_all(self):
        """Build the full 18-slide presentation deck."""
        print("Building Jaagrit Keynote Pitch Deck...")
        os.makedirs(os.path.dirname(self.out_path), exist_ok=True)

        # 13 Main slides
        self.build_slide_01_title()
        self.build_slide_02_problem()
        self.build_slide_03_solution()
        self.build_slide_04_ladder()
        self.build_slide_05_demo_clip()
        self.build_slide_06_calibration()
        self.build_slide_07_emergency_sms()
        self.build_slide_08_trips_fleet()
        self.build_slide_09_prevention()
        self.build_slide_10_bilingual_privacy()
        self.build_slide_11_architecture_audit()
        self.build_slide_12_limits_roadmap()
        self.build_slide_13_close()

        # 5 Appendix slides
        self.build_slide_a1_accuracy()
        self.build_slide_a2_why_ondevice()
        self.build_slide_a3_distraction()
        self.build_slide_a4_helmets_twowheelers()
        self.build_slide_a5_sim_degradation()

        self.prs.save(self.out_path)
        print(f"Presentation successfully saved to: {self.out_path}")
        return self.out_path


if __name__ == "__main__":
    builder = JaagritDeckBuilder()
    builder.build_all()
