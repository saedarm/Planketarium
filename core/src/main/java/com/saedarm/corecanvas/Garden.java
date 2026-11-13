package com.saedarm.corecanvas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Flower Garden. Seeds appear around the canvas. Steer onto one and hold still to grow it into a flower; the
 * steadier you are, the faster it grows. A thin green vine follows the brush, so the finished garden is a painting.
 * Most seeds sit off-center, so each flower is a plank held at a lean.
 */
public class Garden extends Play {
    static final float R = 80f, BLOOM = 5f, LAYER = 0.25f;

    private final Vector2 seed = new Vector2();
    private final Array<Vector2> planted = new Array<>();
    private float growth, layerT, growingT;
    private int layers, flowers;
    // the current flower's look
    private float hue, rot;
    private int petals;
    private boolean spiky;
    private final Color c = new Color(Color.WHITE), vine = new Color(Color.WHITE);

    Garden(CoreCanvas g, float duration) { super(g, duration); }

    @Override public Kind kind() { return Kind.GARDEN; }
    @Override boolean usesBrush() { return true; }
    @Override boolean paints() { return true; }
    @Override Color brushColor() { return c.fromHsv(hue, 0.55f, 1f); }

    @Override void start() { newSeed(); }

    private void newSeed() {
        for (int i = 0; i < 40; i++) {
            seed.set(g.cx + MathUtils.random(-0.82f, 0.82f) * g.rx, g.cy + MathUtils.random(-0.78f, 0.78f) * g.ry);
            boolean ok = seed.dst(g.b) > g.ry * 0.45f;
            for (Vector2 p : planted) if (p.dst(seed) < 230f) ok = false;
            if (ok) break;
        }
        growth = layerT = 0f;
        layers = 0;
        hue = MathUtils.random(360f);
        petals = MathUtils.random(5, 11);
        spiky = MathUtils.randomBoolean(0.35f);
        rot = MathUtils.random(360f);
    }

    private boolean inside() { return g.b.dst(seed) < R; }

    @Override void update(float dt) {
        g.paint.begin();
        trail(dt, vine.fromHsv(105f + 20f * MathUtils.sin(t * 0.4f), 0.55f, 0.75f), 0.3f, 12f, 6f);
        if (inside()) {
            // steady hands grow flowers fastest
            float rate = brushSpeed < 120f ? 1f : brushSpeed < 320f ? 0.5f : 0.15f;
            growth += dt * rate;
            growingT += dt;
            layerT += dt * rate;
            while (layerT >= LAYER) {
                layerT -= LAYER;
                layer(layers++);
                if (layers % 3 == 0) g.sfx.bloom(0.7f + layers * 0.04f);
            }
            if (growth >= BLOOM) bloom();
        }
        g.paint.end();
    }

    /** Each layer adds one ring of dabs further out, so the petals unfold as you hold. */
    private void layer(int k) {
        float f = k / (BLOOM / LAYER);
        float r = 18f + f * 120f;
        float size = spiky ? MathUtils.lerp(30f, 8f, f) : 22f + 34f * MathUtils.sin(f * MathUtils.PI * 0.85f);
        c.fromHsv((hue + f * 25f) % 360f, 0.75f - f * 0.3f, 1f);
        for (int i = 0; i < petals; i++) {
            float a = rot + i * 360f / petals + (spiky ? 0f : 6f * MathUtils.sin(f * 6f));
            g.paint.stamp(seed.x + MathUtils.cosDeg(a) * r, seed.y + MathUtils.sinDeg(a) * r, size, c, 0.33f);
        }
    }

    private void bloom() {
        c.fromHsv((hue + 180f) % 360f, 0.5f, 1f);
        g.paint.stamp(seed.x, seed.y, 64f, c, 0.8f);
        g.paint.stamp(seed.x, seed.y, 30f, Color.WHITE, 0.6f);
        for (int i = 0; i < 10; i++) {
            float a = i * 36f;
            g.paint.stamp(seed.x + MathUtils.cosDeg(a) * 30f, seed.y + MathUtils.sinDeg(a) * 30f, 10f, c, 0.9f);
        }
        planted.add(new Vector2(seed));
        flowers++;
        g.sfx.lap();
        g.sfx.buzz(60);
        newSeed();
    }

    @Override void draw(ShapeRenderer sr) {
        g.drawCanvas();
        g.blend();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        boolean in = inside();
        float p = 1f + 0.08f * MathUtils.sin(t * 5f);
        c.fromHsv(hue, 0.5f, 1f);
        sr.setColor(c.r, c.g, c.b, in ? 0.12f : 0.2f);
        sr.circle(seed.x, seed.y, R * p, 40);
        sr.setColor(0.55f, 0.95f, 0.5f, 0.9f);
        sr.circle(seed.x, seed.y, 10f, 16);
        // growth arc
        sr.setColor(1f, 1f, 1f, 0.15f);
        Move.ring(sr, seed.x, seed.y, R, 5f);
        sr.setColor(c.r, c.g, c.b, 0.95f);
        int seg = Math.round(64 * growth / BLOOM);
        for (int i = 0; i < seg; i++) {
            float a0 = 90f - i * 360f / 64, a1 = 90f - (i + 1) * 360f / 64;
            sr.rectLine(seed.x + MathUtils.cosDeg(a0) * R, seed.y + MathUtils.sinDeg(a0) * R,
                    seed.x + MathUtils.cosDeg(a1) * R, seed.y + MathUtils.sinDeg(a1) * R, 7f);
        }
        sr.end();
    }

    @Override String counter() {
        String s = flowers + (flowers == 1 ? " flower" : " flowers");
        if (inside()) s += brushSpeed < 120f ? "  ·  growing" : "  ·  hold steadier";
        return s;
    }

    @Override int score() { return flowers; }
    @Override String scoreText() { return flowers + (flowers == 1 ? " FLOWER" : " FLOWERS"); }

    @Override Array<String[]> summary() {
        Array<String[]> rows = new Array<>();
        rows.add(new String[]{"Flowers grown", String.valueOf(flowers)});
        rows.add(new String[]{"Time spent growing", pct(growingT / Math.max(t, 0.01f))});
        return rows;
    }

    @Override void auto(Vector2 ctrl) { aim(ctrl, seed.x, seed.y); }
}
