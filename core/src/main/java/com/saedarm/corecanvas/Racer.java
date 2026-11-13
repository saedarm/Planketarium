package com.saedarm.corecanvas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

/**
 * Night Drive: a pseudo-3D racer. Tilt left and right to steer. Curves push you toward the outside, so you lean
 * into them. Lean forward to go faster, lean back to brake. Pass traffic, stay on the road.
 */
public class Racer extends Play {
    static final float SEG = 200f, ROAD_W = 2000f, CAM_H = 1000f, MAX = SEG * 60f;
    static final int DRAW = 170, RUMBLE = 3, CARS = 36;
    static final float CAM_DEPTH = 1f / (float) Math.tan(Math.toRadians(50));
    static final float PLAYER_Z = CAM_H * CAM_DEPTH;

    private float[] curve;
    private int n;
    private float trackLen;
    private float position, speed, playerX, distance, topSpeed, skyOffset, offRoadT, hitT, shake;
    private int overtakes, crashes;
    private final float[] carZ = new float[CARS], carX = new float[CARS], carSpeed = new float[CARS], carHue = new float[CARS];
    private final boolean[] carAhead = new boolean[CARS];
    // projection of each drawn segment's near edge: x, y, half-width, plus the far edge of the last one
    private final float[] px = new float[DRAW + 2], py = new float[DRAW + 2], pw = new float[DRAW + 2];
    private long engineId = -1;
    private final Color c = new Color(Color.WHITE);

    Racer(CoreCanvas g, float duration) { super(g, duration); }

    @Override public Kind kind() { return Kind.RACE; }

    @Override void start() {
        Array<Float> segs = new Array<>();
        addRoad(segs, 60, 0f);
        while (segs.size < 1500) {
            int kind = MathUtils.random(4);
            float sign = MathUtils.randomBoolean() ? 1f : -1f;
            if (kind == 0) addRoad(segs, MathUtils.random(40, 90), 0f);
            else if (kind == 1) addRoad(segs, MathUtils.random(50, 100), sign * 2f);
            else if (kind == 2) addRoad(segs, MathUtils.random(50, 90), sign * 4f);
            else if (kind == 3) addRoad(segs, MathUtils.random(40, 70), sign * 6f);
            else { addRoad(segs, 50, sign * 3f); addRoad(segs, 50, -sign * 3f); }
        }
        addRoad(segs, 60, 0f);
        n = segs.size;
        curve = new float[n];
        for (int i = 0; i < n; i++) curve[i] = segs.get(i);
        trackLen = n * SEG;
        for (int i = 0; i < CARS; i++) {
            carZ[i] = MathUtils.random(20, n - 1) * SEG;
            carX[i] = MathUtils.random(-0.75f, 0.75f);
            carSpeed[i] = MAX * MathUtils.random(0.2f, 0.5f);
            carHue[i] = MathUtils.random(360f);
            carAhead[i] = true;
        }
        speed = MAX * 0.3f;
        engineId = g.sfx.engineStart();
    }

    /** A stretch of road that eases into a curve, holds it, then eases back out. */
    private static void addRoad(Array<Float> segs, int length, float c) {
        int ease = Math.max(1, length / 4);
        for (int i = 0; i < ease; i++) segs.add(c * (float) Math.pow(i / (float) ease, 2));
        for (int i = 0; i < length - 2 * ease; i++) segs.add(c);
        for (int i = 0; i < ease; i++) segs.add(c * (1f - (float) Math.pow(i / (float) ease, 2)));
    }

    private float wrap(float z) {
        z %= trackLen;
        return z < 0 ? z + trackLen : z;
    }

    private float curveAt(float z) { return curve[(int) (wrap(z) / SEG) % n]; }

    @Override void update(float dt) {
        float pct = speed / MAX;
        // lean forward to speed up, back to brake; you're never fully stopped
        float lean = g.ctrl.y;
        float target = lean >= -0.2f ? MAX * (0.4f + 0.6f * MathUtils.clamp((lean + 0.2f) / 0.75f, 0f, 1f)) : MAX * 0.15f;
        boolean off = Math.abs(playerX) > 1f;
        if (off) target = Math.min(target, MAX * 0.3f);
        if (speed < target) speed = Math.min(target, speed + MAX / 4f * dt);
        else speed = Math.max(target, speed - (lean < -0.2f ? MAX : MAX / 3f) * dt);
        topSpeed = Math.max(topSpeed, speed);

        float dx = dt * 2f * pct;
        float here = curveAt(position + PLAYER_Z);
        playerX += g.ctrl.x * dx * 1.6f;
        playerX -= dx * pct * here * 0.2f; // curves push you outward
        playerX = MathUtils.clamp(playerX, -2.2f, 2.2f);
        skyOffset += here * pct * dt * 0.02f;

        if (off) {
            offRoadT += dt;
            shake = 6f * pct;
            if (MathUtils.random() < dt * 4f) g.sfx.buzz(15);
        } else shake = 0f;

        position = wrap(position + speed * dt);
        distance += speed * dt;
        hitT = Math.max(0f, hitT - dt);

        float playerZ = wrap(position + PLAYER_Z);
        for (int i = 0; i < CARS; i++) {
            carZ[i] = wrap(carZ[i] + carSpeed[i] * dt);
            float dz = carZ[i] - playerZ;
            if (dz > trackLen / 2f) dz -= trackLen;
            if (dz < -trackLen / 2f) dz += trackLen;
            if (Math.abs(dz) < SEG * 0.8f && Math.abs(carX[i] - playerX) < 0.32f && speed > carSpeed[i] && dz > -SEG * 0.3f) {
                speed = carSpeed[i] * 0.5f;
                carZ[i] = wrap(playerZ + SEG * 1.2f);
                crashes++;
                hitT = 0.6f;
                g.sfx.crash();
                g.sfx.buzz(160);
            }
            boolean ahead = dz > 0f;
            if (carAhead[i] && !ahead && Math.abs(dz) < SEG * 4f) {
                overtakes++;
                g.sfx.tick();
            }
            carAhead[i] = ahead;
        }
        g.sfx.enginePitch(engineId, 0.55f + pct * 1.1f);
    }

    @Override void onPause() { g.sfx.engineStop(engineId); engineId = -1; }
    @Override void onResume() { engineId = g.sfx.engineStart(); }
    @Override void end() { g.sfx.engineStop(engineId); engineId = -1; }

    // ------------------------------------------------------------------------------------------- drawing

    @Override void draw(ShapeRenderer sr) {
        float viewH = CoreCanvas.H - CoreCanvas.HUD_H, horizon = viewH * 0.56f;
        float W = g.W, half = W / 2f;
        float sx = shake > 0 ? MathUtils.random(-shake, shake) : 0f, sy = shake > 0 ? MathUtils.random(-shake, shake) : 0f;
        g.blend();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        // sky
        Color top = new Color(0.05f, 0.03f, 0.15f, 1f), low = new Color(0.45f, 0.12f, 0.4f, 1f);
        sr.rect(0, horizon, W, CoreCanvas.H - horizon, low, low, top, top);
        // sun
        float sunX = half - skyOffset * 600f % W;
        sr.setColor(1f, 0.55f, 0.3f, 1f);
        sr.circle(((sunX % W) + W) % W, horizon + 40f, 150f, 48);
        for (int i = 0; i < 5; i++) {
            sr.setColor(0.45f, 0.12f, 0.4f, 1f);
            sr.rect(0, horizon + 5f + i * 22f, W, 6f + i * 1.5f);
        }
        // mountains, scrolling with the curves
        sr.setColor(0.12f, 0.05f, 0.22f, 1f);
        float mw = 260f, off = ((skyOffset * 900f) % mw + mw) % mw;
        for (float x = -mw * 2 - off; x < W + mw; x += mw) {
            float h = 70f + 50f * MathUtils.sin(x * 0.013f + 1f) * MathUtils.sin(x * 0.007f);
            sr.triangle(x, horizon, x + mw / 2f, horizon + 60f + Math.abs(h), x + mw, horizon);
        }
        // ground under the horizon
        sr.setColor(0.08f, 0.04f, 0.14f, 1f);
        sr.rect(0, 0, W, horizon);

        // road, near to far
        int base = (int) (position / SEG);
        float basePct = (position % SEG) / SEG;
        float camX = playerX * ROAD_W;
        float x = 0f, dx = -(curve[base % n] * basePct);
        float maxY = -1f;
        int drawn = 0;
        for (int i = 0; i < DRAW; i++) {
            int si = (base + i) % n;
            float z1 = i * SEG - basePct * SEG, z2 = z1 + SEG;
            float cz1 = Math.max(z1, 1f), cz2 = z2;
            float p1x = half + sx + (CAM_DEPTH / cz1) * (x - camX) * half;
            float p2x = half + sx + (CAM_DEPTH / cz2) * (x + dx - camX) * half;
            float p1y = horizon + sy - (CAM_DEPTH / cz1) * CAM_H * viewH / 2f;
            float p2y = horizon + sy - (CAM_DEPTH / cz2) * CAM_H * viewH / 2f;
            float w1 = (CAM_DEPTH / cz1) * ROAD_W * half, w2 = (CAM_DEPTH / cz2) * ROAD_W * half;
            x += dx;
            dx += curve[si];
            px[i] = p1x; py[i] = p1y; pw[i] = w1;
            px[i + 1] = p2x; py[i + 1] = p2y; pw[i + 1] = w2;
            drawn = i + 1;
            if (p2y <= maxY) continue;
            maxY = p2y;
            boolean dark = (si / RUMBLE) % 2 == 0;
            float fog = MathUtils.clamp(i / (float) DRAW, 0f, 1f);
            // grass
            sr.setColor(MathUtils.lerp(dark ? 0.1f : 0.13f, 0.3f, fog * fog), MathUtils.lerp(dark ? 0.05f : 0.07f, 0.09f, fog * fog), MathUtils.lerp(dark ? 0.2f : 0.24f, 0.3f, fog * fog), 1f);
            sr.rect(0, p1y, W, p2y - p1y + 1f);
            // rumble strips
            if (dark) sr.setColor(1f, 0.25f, 0.75f, 1f); else sr.setColor(0.95f, 0.95f, 1f, 1f);
            quad(sr, p1x - w1 * 1.15f, p1x - w1, p1y, p2x - w2 * 1.15f, p2x - w2, p2y);
            quad(sr, p1x + w1, p1x + w1 * 1.15f, p1y, p2x + w2, p2x + w2 * 1.15f, p2y);
            // road
            float r = dark ? 0.16f : 0.18f;
            sr.setColor(MathUtils.lerp(r, 0.3f, fog * fog), MathUtils.lerp(r, 0.1f, fog * fog), MathUtils.lerp(r + 0.08f, 0.3f, fog * fog), 1f);
            quad(sr, p1x - w1, p1x + w1, p1y, p2x - w2, p2x + w2, p2y);
            // lane lines
            if (!dark) {
                sr.setColor(0.4f, 0.95f, 1f, 1f - fog);
                for (int l = -1; l <= 1; l += 2) {
                    float lx1 = p1x + w1 * l / 3f, lx2 = p2x + w2 * l / 3f;
                    quad(sr, lx1 - w1 * 0.015f, lx1 + w1 * 0.015f, p1y, lx2 - w2 * 0.015f, lx2 + w2 * 0.015f, p2y);
                }
            }
        }

        // street lamps and traffic, far to near so nearer things cover farther ones
        for (int i = drawn - 1; i >= 1; i--) {
            int si = (base + i) % n;
            if (si % 12 == 0 && py[i] < horizon) {
                float pole = pw[i] * 0.9f, thick = Math.max(1.5f, pw[i] * 0.02f);
                for (int side = -1; side <= 1; side += 2) {
                    float lx = px[i] + side * pw[i] * 1.35f;
                    sr.setColor(0.25f, 0.2f, 0.35f, 1f);
                    sr.rect(lx - thick / 2f, py[i], thick, pole);
                    sr.setColor(1f, 0.85f, 0.5f, 0.9f);
                    sr.circle(lx, py[i] + pole, Math.max(2f, thick * 2.2f), 12);
                }
            }
            for (int k = 0; k < CARS; k++) {
                int cs = (int) (carZ[k] / SEG);
                if (cs != si) continue;
                float f = (carZ[k] % SEG) / SEG;
                float cx = MathUtils.lerp(px[i], px[i + 1], f), cy = MathUtils.lerp(py[i], py[i + 1], f), cw = MathUtils.lerp(pw[i], pw[i + 1], f);
                drawCar(sr, cx + cw * carX[k], cy, cw * 0.36f, carHue[k]);
            }
        }

        // the player's car
        float pw2 = 330f, ph = 120f, pcx = half + g.ctrl.x * 25f + sx, pcy = 30f + (Math.abs(playerX) > 1f ? MathUtils.random(-4f, 4f) : 0f);
        float flash = hitT > 0 && (int) (hitT * 20) % 2 == 0 ? 1f : 0f;
        sr.setColor(0f, 0f, 0f, 0.4f);
        sr.rect(pcx - pw2 / 2f - 10f, pcy - 12f, pw2 + 20f, 20f);
        sr.setColor(0.15f + flash, 0.1f + flash * 0.5f, 0.3f, 1f);
        sr.rect(pcx - pw2 / 2f, pcy, pw2, ph * 0.55f);
        sr.setColor(0.25f, 0.15f, 0.45f, 1f);
        quad(sr, pcx - pw2 * 0.38f, pcx + pw2 * 0.38f, pcy + ph * 0.55f, pcx - pw2 * 0.28f, pcx + pw2 * 0.28f, pcy + ph);
        sr.setColor(0.5f, 0.85f, 1f, 0.6f);
        quad(sr, pcx - pw2 * 0.33f, pcx + pw2 * 0.33f, pcy + ph * 0.6f, pcx - pw2 * 0.26f, pcx + pw2 * 0.26f, pcy + ph * 0.93f);
        sr.setColor(1f, 0.15f, 0.3f, 1f);
        sr.rect(pcx - pw2 / 2f + 14f, pcy + ph * 0.3f, pw2 * 0.25f, 16f);
        sr.rect(pcx + pw2 / 2f - 14f - pw2 * 0.25f, pcy + ph * 0.3f, pw2 * 0.25f, 16f);
        sr.setColor(1f, 0.3f, 0.75f, 1f);
        sr.rect(pcx - pw2 / 2f, pcy + ph * 0.52f, pw2, 5f);
        if (speed / MAX > 0.7f) {
            sr.setColor(0.4f, 0.8f, 1f, MathUtils.random(0.3f, 0.8f));
            sr.circle(pcx - pw2 * 0.3f, pcy + 4f, 10f, 12);
            sr.circle(pcx + pw2 * 0.3f, pcy + 4f, 10f, 12);
        }
        sr.end();
    }

    private static void quad(ShapeRenderer sr, float x1a, float x1b, float y1, float x2a, float x2b, float y2) {
        sr.triangle(x1a, y1, x1b, y1, x2b, y2);
        sr.triangle(x1a, y1, x2b, y2, x2a, y2);
    }

    private void drawCar(ShapeRenderer sr, float x, float y, float w, float hue) {
        if (w < 2f) return;
        w = Math.min(w, 340f);
        float h = w * 0.5f;
        c.fromHsv(hue, 0.6f, 0.8f);
        sr.setColor(0f, 0f, 0f, 0.35f);
        sr.rect(x - w / 2f - w * 0.04f, y - h * 0.08f, w * 1.08f, h * 0.15f);
        sr.setColor(c);
        sr.rect(x - w / 2f, y, w, h * 0.6f);
        sr.setColor(c.r * 0.7f, c.g * 0.7f, c.b * 0.7f, 1f);
        sr.rect(x - w * 0.35f, y + h * 0.6f, w * 0.7f, h * 0.4f);
        sr.setColor(1f, 0.15f, 0.2f, 1f);
        sr.rect(x - w / 2f + w * 0.05f, y + h * 0.3f, w * 0.2f, h * 0.12f);
        sr.rect(x + w / 2f - w * 0.25f, y + h * 0.3f, w * 0.2f, h * 0.12f);
    }

    @Override void drawOverlay() {
        g.text(g.fTitle, String.valueOf(Math.round(speed / MAX * 240f)), g.W - 60f, 260f, Align.right, Color.WHITE, 0.9f);
        g.text(g.fSmall, "KM/H", g.W - 60f, 120f, Align.right, Color.WHITE, 0.7f);
        if (Math.abs(playerX) > 1f) g.text(g.fBig, "OFF THE ROAD", g.W / 2f, g.cy + 120f, Align.center, Color.WHITE, 0.6f + 0.4f * MathUtils.sin(t * 12f));
        if (hitT > 0f) g.text(g.fBig, "BONK!", g.W / 2f, g.cy + 200f, Align.center, Color.WHITE, hitT / 0.6f);
        if (t < 3f) g.text(g.fBody, "Lean forward to speed up  ·  lean into the curves", g.W / 2f, g.cy + 260f, Align.center, Color.WHITE, 1f - t / 3f);
    }

    float km() { return distance / 180000f; }

    @Override String counter() { return String.format("%.2f km  ·  %d passed", km(), overtakes); }
    @Override int score() { return Math.round(km() * 1000f); }
    @Override String scoreText() { return String.format("%.2f KM", km()); }

    @Override Array<String[]> summary() {
        Array<String[]> rows = new Array<>();
        rows.add(new String[]{"Distance", String.format("%.2f km", km())});
        rows.add(new String[]{"Top speed", Math.round(topSpeed / MAX * 240f) + " km/h"});
        rows.add(new String[]{"Cars passed", String.valueOf(overtakes)});
        rows.add(new String[]{"Bonks", String.valueOf(crashes)});
        rows.add(new String[]{"On the road", pct(1f - offRoadT / Math.max(t, 0.01f))});
        return rows;
    }

    /** Autopilot: pick a lane clear of the next car ahead, and lean into the curve. */
    @Override void auto(Vector2 ctrl) {
        float playerZ = wrap(position + PLAYER_Z), lane = 0f, best = Float.MAX_VALUE;
        int ahead = -1;
        for (int i = 0; i < CARS; i++) {
            float dz = wrap(carZ[i] - playerZ);
            if (dz < SEG * 12f && dz < best && Math.abs(carX[i] - playerX) < 0.5f) { best = dz; ahead = i; }
        }
        if (ahead >= 0) lane = carX[ahead] > 0 ? -0.55f : 0.55f;
        float here = curveAt(playerZ);
        float pct = speed / MAX;
        ctrl.set(MathUtils.clamp((lane - playerX) * 2.5f + here * pct * 0.13f, -1f, 1f), 0.45f);
    }
}
