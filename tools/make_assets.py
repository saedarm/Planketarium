"""Generates every sound, the music loop and the launcher icon for Core Canvas.

Run from the project root:  python3 tools/make_assets.py
Needs numpy, scipy, Pillow and ffmpeg (for the .ogg music loop).
"""
import math
import os
import subprocess

import numpy as np
from scipy.io import wavfile
from PIL import Image, ImageDraw, ImageFilter

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AUDIO = os.path.join(ROOT, "assets", "audio")


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, attack=0.005, decay=0.3):
    t = np.arange(n) / SR
    a = np.clip(t / attack, 0, 1)
    return a * np.exp(-t / decay)


def bell(freq, sec=1.0, decay=0.35):
    t = t_axis(sec)
    partials = [(1, 1.0), (2.76, 0.45), (5.4, 0.2), (8.93, 0.08)]
    out = sum(a * np.sin(2 * np.pi * freq * m * t) * np.exp(-t * m / (decay * 3 + 0.1 * m)) for m, a in partials)
    return out * env(len(t), 0.002, decay * 2)


def pluck(freq, sec=0.6, decay=0.18):
    t = t_axis(sec)
    tone = np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 2 * t) + 0.1 * np.sin(2 * np.pi * freq * 3 * t)
    return tone * env(len(t), 0.004, decay)


def beep(freq, sec=0.16):
    t = t_axis(sec)
    tone = np.sin(2 * np.pi * freq * t) + 0.2 * np.sin(2 * np.pi * freq * 2 * t)
    e = np.clip(t / 0.005, 0, 1) * np.clip((sec - t) / 0.03, 0, 1)
    return tone * e


def sweep(f0, f1, sec):
    t = t_axis(sec)
    f = np.geomspace(f0, f1, len(t))
    phase = 2 * np.pi * np.cumsum(f) / SR
    e = np.sin(np.pi * t / sec) ** 2
    return (np.sin(phase) + 0.3 * np.sin(2 * phase)) * e


def mix(*parts):
    """parts: (signal, offset_seconds)"""
    n = max(int(o * SR) + len(s) for s, o in parts)
    out = np.zeros(n)
    for s, o in parts:
        i = int(o * SR)
        out[i:i + len(s)] += s
    return out


def save(name, sig, peak=0.8):
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * peak
    wavfile.write(os.path.join(AUDIO, name + ".wav"), SR, (sig * 32767).astype(np.int16))


def note(n):  # MIDI note to Hz
    return 440.0 * 2 ** ((n - 69) / 12)


def sfx():
    save("tick", pluck(note(91), 0.12, 0.03), 0.5)
    save("ding", bell(note(76), 1.1, 0.3), 0.7)            # E5; pitched up the scale in code
    save("bloom", mix((pluck(note(79), 0.7, 0.22), 0), (0.4 * pluck(note(86), 0.7, 0.15), 0)), 0.55)
    save("star", mix((bell(note(84), 1.4, 0.4), 0), (0.5 * bell(note(91), 1.0, 0.25), 0)), 0.65)
    save("beep", beep(note(76)), 0.6)
    save("go", mix((beep(note(84), 0.45), 0), (beep(note(88), 0.45), 0), (beep(note(91), 0.45), 0)), 0.7)
    arp = [note(n) for n in (72, 76, 79, 84)]
    save("move", mix(*[(bell(f, 1.0, 0.3), i * 0.09) for i, f in enumerate(arp)]), 0.7)
    save("lap", mix((bell(note(79), 0.9, 0.3), 0), (bell(note(86), 0.9, 0.3), 0.07)), 0.7)
    save("reverse", sweep(300, 1200, 0.4), 0.6)
    save("pause", mix((pluck(note(64), 0.5, 0.2), 0), (pluck(note(57), 0.7, 0.3), 0.15)), 0.6)
    save("resume", mix((pluck(note(57), 0.4, 0.15), 0), (pluck(note(64), 0.6, 0.25), 0.12)), 0.6)
    fan = [note(n) for n in (60, 64, 67, 72, 76, 79, 84)]
    save("done", mix(*[(bell(f, 2.2, 0.6), i * 0.08) for i, f in enumerate(fan)]), 0.75)
    # engine: exactly 1 second of an 80 Hz buzz (80 whole cycles), so it loops with no click
    t = t_axis(1.0)
    eng = sum((1.0 / k) * np.sin(2 * np.pi * 80 * k * t + k) for k in range(1, 9))
    eng += 0.3 * np.sin(2 * np.pi * 40 * t)
    save("engine", eng, 0.5)
    rng = np.random.default_rng(3)
    tc = t_axis(0.5)
    noise = rng.uniform(-1, 1, len(tc)) * np.exp(-tc / 0.08)
    thud = np.sin(2 * np.pi * 70 * tc) * np.exp(-tc / 0.15)
    save("crash", noise * 0.7 + thud, 0.8)
    save("select", mix((pluck(note(84), 0.4, 0.1), 0), (pluck(note(91), 0.5, 0.15), 0.06)), 0.6)


def music():
    """A 32-second ambient pad loop. Chords wrap around the end so it loops without a seam."""
    total = 32.0
    n = int(SR * total)
    out = np.zeros(n)
    chords = [(48, 55, 64, 71, 74), (45, 52, 60, 67, 71), (41, 48, 57, 64, 69), (43, 50, 59, 62, 69)]
    seg = total / len(chords)
    rng = np.random.default_rng(7)
    for ci, ch in enumerate(chords):
        dur = seg + 3.0
        t = t_axis(dur)
        e = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5
        sig = np.zeros(len(t))
        for m in ch:
            f = note(m)
            for det in (-0.12, 0.0, 0.12):
                ph = rng.uniform(0, 2 * np.pi)
                lfo = 1 + 0.002 * np.sin(2 * np.pi * 0.2 * t + ph)
                sig += np.sin(2 * np.pi * f * (1 + det / 100) * t * lfo + ph) * (0.6 if m > 60 else 1.0)
        sig *= e
        start = int((ci * seg - 1.5) * SR)
        idx = (np.arange(len(sig)) + start) % n
        np.add.at(out, idx, sig)
    # gentle sparkle notes
    for k in range(16):
        f = note(int(rng.choice([76, 79, 83, 84, 86, 88])))
        s = bell(f, 2.5, 0.8) * 0.18
        start = int(k * 2.0 * SR + rng.integers(0, SR // 2))
        idx = (np.arange(len(s)) + start) % n
        np.add.at(out, idx, s)
    out = out / np.max(np.abs(out)) * 0.6
    tmp = os.path.join(AUDIO, "_music.wav")
    wavfile.write(tmp, SR, (out * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "3",
                    os.path.join(AUDIO, "music.ogg")], check=True)
    os.remove(tmp)


def icon():
    size = 512
    img = Image.new("RGBA", (size, size), (20, 24, 38, 255))
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    # a painted loop: a figure 8 of soft dots that shifts from teal to magenta to gold
    cols = [(64, 220, 200), (240, 80, 200), (255, 200, 70)]
    pts = 260
    for i in range(pts):
        th = i / pts * 2 * math.pi
        x = size / 2 + 170 * math.sin(th)
        y = size / 2 + 120 * math.sin(2 * th)
        f = i / pts * (len(cols) - 1)
        a, b = cols[int(f)], cols[min(int(f) + 1, len(cols) - 1)]
        k = f - int(f)
        c = tuple(int(a[j] + (b[j] - a[j]) * k) for j in range(3))
        r = 30 - 14 * abs(math.sin(th))
        d.ellipse([x - r, y - r, x + r, y + r], fill=c + (60,))
    glow = glow.filter(ImageFilter.GaussianBlur(6))
    img.alpha_composite(glow)
    d = ImageDraw.Draw(img)
    d.ellipse([size / 2 - 34, size / 2 - 34, size / 2 + 34, size / 2 + 34], outline=(255, 255, 255, 255), width=10)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=110, fill=255)
    img.putalpha(mask)
    for folder, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        out = os.path.join(ROOT, "android", "res", "mipmap-" + folder)
        os.makedirs(out, exist_ok=True)
        img.resize((px, px), Image.LANCZOS).save(os.path.join(out, "ic_launcher.png"))


if __name__ == "__main__":
    os.makedirs(AUDIO, exist_ok=True)
    sfx()
    music()
    icon()
    print("done")
