#!/usr/bin/env python3
"""Typing sound of the cinematic dialog style (customnpcs:dialog.blip).

The same blip the approved HTML mockup makes with WebAudio: a short square wave
around 520-580 Hz. Three pitches so a line of text does not sound like a
machine gun. This script is the source: edit it and run it again.

    python tools/gen_dialog_blip.py      # needs numpy and ffmpeg (libvorbis)
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "customnpcs", "sounds", "dialog")
RATE = 44100
LENGTH = 0.025       # seconds, like the mockup
FADE = 0.004         # short fade in and out so the cut does not click
PEAK = 0.55          # Minecraft plays UI sounds at 0.25 volume
PITCHES = (520, 550, 580)


def blip(freq):
    n = int(RATE * LENGTH)
    t = np.arange(n) / RATE
    wave_ = np.sign(np.sin(2 * np.pi * freq * t))
    env = np.ones(n)
    f = int(RATE * FADE)
    env[:f] = np.linspace(0, 1, f)
    env[-f:] = np.linspace(1, 0, f)
    return (wave_ * env * PEAK * 32767).astype(np.int16)


def main():
    os.makedirs(OUT, exist_ok=True)
    for i, freq in enumerate(PITCHES, 1):
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, "blip.wav")
            with wave.open(wav, "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(RATE)
                w.writeframes(blip(freq).tobytes())
            ogg = os.path.normpath(os.path.join(OUT, "blip%d.ogg" % i))
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", ogg], check=True)
            print(ogg)


if __name__ == "__main__":
    main()
