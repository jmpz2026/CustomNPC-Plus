#!/usr/bin/env python3
"""Typing sound of the cinematic dialog style (customnpcs:dialog.blip).

The "Blip suave" picked in the audition page
(DbrServerPack/docs/mockups/customnpc-dialogos/sonidos.html): a sine at 420 Hz that
glides down to 92% in 50 ms, 4 ms attack and an exponential 55 ms decay. One file at
the base pitch: the game moves the pitch per NPC and per syllable. This script is the
source: edit it and run it again.

    python tools/gen_dialog_blip.py      # needs numpy and ffmpeg (libvorbis)
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "customnpcs", "sounds", "dialog", "blip.ogg")
RATE = 44100
BASE = 420.0        # Hz, "tonoBase" of the audition
GLIDE = 0.92        # pitch at the end of the glide
GLIDE_TIME = 0.05
ATTACK = 0.004
DECAY = 0.055
LENGTH = 0.07
# Minecraft plays UI sounds at 0.25 volume: the file goes near full scale to sound
# like the 55% volume of the audition
PEAK = 0.95


def blip():
    n = int(RATE * LENGTH)
    t = np.arange(n) / RATE
    # exponential glide like WebAudio exponentialRampToValueAtTime, phase integrated
    k = np.log(GLIDE) / GLIDE_TIME
    freq = np.where(t < GLIDE_TIME, BASE * np.exp(k * t), BASE * GLIDE)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    env = np.where(t < ATTACK, t / ATTACK, np.exp(np.log(0.0001) * (t - ATTACK) / DECAY))
    env[t > ATTACK + DECAY] = 0
    return (np.sin(phase) * env * PEAK * 32767).astype(np.int16)


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "blip.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(blip().tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "6",
                        os.path.normpath(OUT)], check=True)
    print(os.path.normpath(OUT))


if __name__ == "__main__":
    main()
