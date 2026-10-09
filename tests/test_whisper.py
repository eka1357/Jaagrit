"""
Whisper test for Jaagrit voice commands.

Usage:
  1. Record 5 clips on your phone (see below for what to say)
  2. Transfer to this folder via USB
  3. Run: python test_whisper.py

Notes:
  - Whisper with language="hi" outputs DEVANAGARI, not romanized text
  - This script prints raw output for you to judge manually
  - Laptop PyTorch Whisper is best-case; phone version may be worse
  - Keep the button fallback regardless of results
"""

import whisper
import sys
import os
import time

# --- Config ---
MODEL_SIZE = "tiny"  # Change to "base" if tiny fails
CLIPS_DIR = "."      # Put your .m4a / .wav clips here

# What you should record (one clip each, 2-4 seconds):
COMMANDS = {
    "clip1": "कितनी देर से ड्राइव कर रहा हूँ  (Kitni der se drive kar raha hoon)",
    "clip2": "मेरा अलर्टनेस कैसा है  (Mera alertness kaisa hai)",
    "clip3": "आज कितने अलर्ट आये  (Aaj kitne alert aaye)",
    "clip4": "चुप रहो  (Chup raho)",
    "clip5": "बंद करो  (Band karo)",
}

# Find clip files
EXTENSIONS = (".m4a", ".wav", ".mp3", ".ogg", ".webm", ".flac")


def find_clips():
    """Look for clip1.*, clip2.*, etc. in the clips directory."""
    found = {}
    for f in os.listdir(CLIPS_DIR):
        name, ext = os.path.splitext(f)
        if name in COMMANDS and ext.lower() in EXTENSIONS:
            found[name] = os.path.join(CLIPS_DIR, f)
    return found


def main():
    clips = find_clips()
    if not clips:
        print("No clips found! Record these and place them here:")
        for key, desc in COMMANDS.items():
            print(f"  {key}.*  →  Say: {desc}")
        print(f"\nSupported formats: {EXTENSIONS}")
        sys.exit(1)

    print(f"Loading Whisper {MODEL_SIZE}...")
    model = whisper.load_model(MODEL_SIZE)
    print(f"Model loaded.\n")

    print("=" * 60)
    print(f"WHISPER {MODEL_SIZE.upper()} TEST — {len(clips)} clips found")
    print("=" * 60)

    for key in sorted(clips.keys()):
        path = clips[key]
        expected = COMMANDS[key]

        print(f"\n--- {key}: {os.path.basename(path)} ---")
        print(f"Expected: {expected}")

        # Transcribe with Hindi forced
        t0 = time.time()
        result = model.transcribe(path, language="hi")
        elapsed_ms = (time.time() - t0) * 1000

        print(f"Got (hi): {result['text'].strip()}")
        print(f"Time:     {elapsed_ms:.0f} ms")

        # Also try without forcing language (for auto-detect test)
        result_auto = model.transcribe(path)
        detected_lang = result_auto.get("language", "?")
        # Accept hi and ur as "Hindi detected"
        lang_ok = "PASS" if detected_lang in ("hi", "ur") else f"GOT: {detected_lang}"
        print(f"Auto-detect language: {detected_lang} → {lang_ok}")

        print(f"\n>> YOUR JUDGMENT: Does the transcription match? (read it yourself)")

    # Now test with English if you have an English clip
    en_clip = None
    for f in os.listdir(CLIPS_DIR):
        if f.startswith("clip_english") and os.path.splitext(f)[1].lower() in EXTENSIONS:
            en_clip = os.path.join(CLIPS_DIR, f)
            break

    if en_clip:
        print(f"\n--- English clip: {os.path.basename(en_clip)} ---")
        result = model.transcribe(en_clip)
        print(f"Got:      {result['text'].strip()}")
        print(f"Language: {result.get('language', '?')}")
        lang_ok = "PASS" if result.get("language") == "en" else f"GOT: {result.get('language')}"
        print(f"English detected: {lang_ok}")
    else:
        print("\nNo clip_english.* found — skipping English auto-detect test")
        print("Record one saying 'How long have I been driving?' to test language switch")

    print("\n" + "=" * 60)
    print("DECISION GUIDE")
    print("=" * 60)
    print("If >= 4/5 clips were correctly transcribed:")
    print(f"  → Use Whisper {MODEL_SIZE}")
    if MODEL_SIZE == "tiny":
        print("If < 3/5 correct:")
        print("  → Re-run with MODEL_SIZE = 'base' (edit line 18)")
    print("If both tiny and base struggle:")
    print("  → Use button UI for commands, Android SpeechRecognizer for dismiss only")
    print()
    print("REMEMBER: These are laptop (PyTorch) results = best case.")
    print("Phone (quantized LiteRT) may be slightly worse. Keep the button fallback.")


if __name__ == "__main__":
    main()
