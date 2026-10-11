#!/usr/bin/env python3
"""
Render QA for Jaagrit Pitch Deck:
1. Exports presentation/out/Jaagrit.pdf via PowerPoint COM
2. Exports individual slide PNGs to presentation/out/qa_slides/
3. Generates presentation/out/contact_sheet.png assembling all slides into a contact sheet
"""

import os
import win32com.client
from PIL import Image, ImageDraw, ImageFont

def render_deck():
    ppt_path = os.path.abspath("presentation/out/Jaagrit.pptx")
    pdf_path = os.path.abspath("presentation/out/Jaagrit.pdf")
    qa_dir = os.path.abspath("presentation/out/qa_slides")
    os.makedirs(qa_dir, exist_ok=True)

    print(f"Opening {ppt_path} in PowerPoint COM...")
    ppt_app = win32com.client.Dispatch("PowerPoint.Application")
    deck = ppt_app.Presentations.Open(ppt_path, WithWindow=False)

    print(f"Exporting PDF to {pdf_path}...")
    deck.SaveAs(pdf_path, 32) # 32 = ppSaveAsPDF
    print("PDF exported successfully.")

    slide_count = deck.Slides.Count
    print(f"Exporting {slide_count} slides to PNG...")
    png_paths = []

    for i in range(1, slide_count + 1):
        slide = deck.Slides(i)
        out_png = os.path.join(qa_dir, f"slide_{i:02d}.png")
        slide.Export(out_png, "PNG", 1920, 1080)
        png_paths.append(out_png)
        print(f"  Slide {i:02d} exported.")

    deck.Close()
    ppt_app.Quit()

    # Build Contact Sheet
    print("Generating contact sheet...")
    # 6 columns x 3 rows = 18 slides
    cols = 6
    rows = 3
    thumb_w = 480
    thumb_h = 270
    margin = 20

    sheet_w = (cols * thumb_w) + ((cols + 1) * margin)
    sheet_h = (rows * thumb_h) + ((rows + 1) * margin) + 60 # extra top title space

    contact_sheet = Image.new("RGB", (sheet_w, sheet_h), color="#0A120E")
    draw = ImageDraw.Draw(contact_sheet)

    # Title
    draw.text((margin, 18), "JAAGRIT (जागृत) — Pitch Deck Contact Sheet (18 Slides)", fill="#B9EE45")

    for idx, png_file in enumerate(png_paths):
        c = idx % cols
        r = idx // cols
        x = margin + c * (thumb_w + margin)
        y = 60 + margin + r * (thumb_h + margin)

        thumb = Image.open(png_file).resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)
        contact_sheet.paste(thumb, (x, y))
        draw.rectangle([x, y, x + thumb_w, y + thumb_h], outline="#284232", width=2)
        # Slide label
        label = f"#{idx+1:02d}"
        draw.rectangle([x + 6, y + 6, x + 44, y + 26], fill="#0A120E")
        draw.text((x + 10, y + 8), label, fill="#B9EE45")

    sheet_path = os.path.abspath("presentation/out/contact_sheet.png")
    contact_sheet.save(sheet_path)
    print(f"Contact sheet saved to: {sheet_path}")

if __name__ == "__main__":
    render_deck()
