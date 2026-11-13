# Planketarium

**Working out is fun now.** I replaced my plank board's app with my own.

Planketarium (the app installs as **Core Canvas**) is five plank games for tilt boards like the Stealth core trainer, built with libGDX for Android (tuned for the Galaxy S23). You need a tilt board, or anything that rocks with your phone on it. The phone sits in the board's slot and the board is the controller. Every menu can be worked by tilting alone. Taps work too if you're off the board.

## Menus

- **Pick by tilting.** Move the brush onto a tile and hold it there for about 1.5 seconds. A tile the brush is already sitting on when a screen opens won't fire until you move off it and back on, so nothing gets picked by accident.
- **Title screen:** five game tiles, plus Sensitivity and Sound.
- **Length screen:** Quick (1:30), Standard (3:00) or Long (5:00), with your best for each. **Back** returns to the games.
- **Get set:** hold the board level and still for 2 seconds. Wherever you settle becomes the center. Then a 3-2-1 countdown.

## Pausing

Any of these pauses the game:
- Dropping off the board (tilting way past the play area, or the board slumping against an edge without moving)
- Tapping the screen
- The phone's Back button
- Leaving the app

The pause menu has **Resume** in the middle, **Restart** at the bottom left and **End & Save** at the bottom right. When you get back in plank the brush lands on Resume. Hold it there and a 3-2-1 countdown starts before play continues. Back on the pause menu also resumes. After 60 seconds paused, the session ends on its own and saves.

## The games

| Game | How it plays | What it works | Score |
| --- | --- | --- | --- |
| **Paint Workout** | Six guided moves back to back: Hold Steady, Windshield Wipers, Rock the Boat, Halo, Infinity, Constellation. Each one paints part of the picture. | Every part of the core, in order | 0–100 |
| **Flower Garden** | Seeds appear around the canvas. Steer onto one and hold still to grow it. The steadier you are, the faster it grows (about 5 s per flower). A green vine follows the brush between flowers. | Planks held at different leans | Flowers grown |
| **Marble Maze** | Tilt is gravity, so the marble rolls, builds speed and bounces off walls. Reach the green exit tile and a new, tighter maze appears. Gems sit in dead ends. From maze 3 on, holes in the dead ends send you back to the start. | Constant small corrections in every direction | 100 per maze + 25 per gem |
| **Night Drive** | Pseudo-3D synthwave racer. Tilt left and right to steer. Curves push you toward the outside, so you have to lean into them. Lean forward to go faster, lean back to brake. Pass traffic. Hitting a car ("bonk") kills your speed, and going off the road slows you down. | Obliques in the curves, front plank to hold speed | Distance |
| **Free Paint** | No targets. Paint whatever you want until time runs out. The color drifts through the rainbow. | Whatever you do | % of the canvas covered |

Paint Workout, Flower Garden and Free Paint make a painting. It's framed on the results screen with a random gallery title and saved to **Gallery › Pictures › Core Canvas**. Marble Maze and Night Drive show a score card instead. Bests are kept per game and length.

Reps, flowers and gems ring up a little scale. The phone buzzes on hits and bonks. Night Drive has an engine sound that rises with your speed. The screen stays on while the app is open.

## Settings

- **Sensitivity**: how far you tilt to reach the edge. Low = 15°, Medium = 11° (default), High = 8°. This also sets how hard the marble and the car respond.
- **Sound**: on or off.

## Installing on the S23

**Without a cable:** download `Planketarium.apk` from [Releases](https://github.com/saedarm/Planketarium/releases), copy it to the phone, tap it in My Files, and allow your file app to install unknown apps when asked.

**Over USB:** turn on Developer options and USB debugging, then run `./gradlew android:installDebug`.

If the phone says "App not installed" when updating, the APK was signed by a different computer's debug key. Uninstall the old version first.

## Project layout

```
core/      all game code
android/   launcher, manifest, icon, saving paintings to the Gallery
lwjgl3/    desktop launcher for testing (the mouse is the board)
assets/    fonts (Bungee, SIL OFL; DejaVu Sans Bold), audio
tools/     make_assets.py, which generates every sound, the music loop and the icon
```

| File | What it does |
| --- | --- |
| `CoreCanvas.java` | Screens (title, length, get set, play, results), dwell/tap buttons, pause menu, HUD, and the framed PNG export |
| `Session.java` | Runs one game: the plank timer, pausing (drop, tap, Back), the resume countdown |
| `Play.java` | Base class every game extends, plus the game list and lengths |
| `Workout.java`, `Move.java` | Paint Workout and its six moves |
| `Garden.java` | Flower Garden |
| `Maze.java` | Marble Maze: maze generation, marble physics, gems and holes |
| `Racer.java` | Night Drive: the pseudo-3D road, traffic and steering |
| `FreePaint.java` | Free Paint |
| `Tilt.java` | Turns the accelerometer into screen-relative tilt, smooths it, finds level, and detects stillness |
| `Paint.java` | The persistent canvas (a frame buffer) and the soft brush stamps |
| `Sfx.java` | Sounds, the music loop and vibration |
| `Stats.java` | Saved bests, streak, totals and settings |

## Desktop testing

```
./gradlew lwjgl3:run
```

The mouse is the board. Esc works like the phone's Back button.

To watch a game play itself and save screenshots:

```
./gradlew lwjgl3:run -Dauto=true -Dgame=RACE -Dlength=QUICK -Dspeed=3 -Dshots=/tmp/shots -Dpause=20
```

`-Dgame` is one of WORKOUT, GARDEN, MAZE, RACE or FREE. `-Dpause=20` pauses 20 seconds in to test the pause menu.

Paintings from desktop runs go to `~/Pictures/Core Canvas`.

## Tuning

- Target pace (used for scoring): Wipers and Rock expect a rep every 2.4 s, Halo a lap every 6.5 s, Infinity a loop every 8 s, and Constellation a star every 2.6 s. These are in each move's `score()`.
- Move lengths per workout: the `Session` constructor.
- Pause rules: `Session.update`. A drop is a tilt past 1.7× the sensitivity angle for 1.2 s, or sitting pinned at an edge for 3 s.
- Night Drive: `Racer.update`. Curve push is `0.2`, and steering strength is `1.6`. Raise the curve push to make you lean harder.
- Marble Maze: `ACC` (how hard tilt pulls the marble) and the friction in `Maze.update`.
- Sounds: edit `tools/make_assets.py` and run `python3 tools/make_assets.py` (needs numpy, scipy, Pillow and ffmpeg).
