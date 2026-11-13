package com.saedarm.corecanvas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/** One exercise in a workout. Each one is a small drawing challenge that works a different part of the core. */
public abstract class Move {
    public final String name, muscles, cue;
    public final float duration, hue;
    protected CoreCanvas g;
    protected Workout s;
    protected float t;
    private final Color paintColor = new Color(Color.WHITE);
    protected final Color tmp = new Color(Color.WHITE);

    Move(String name, String muscles, String cue, float duration, float hue) {
        this.name = name;
        this.muscles = muscles;
        this.cue = cue;
        this.duration = duration;
        this.hue = hue;
    }

    void bind(CoreCanvas g, Workout s) { this.g = g; this.s = s; }

    void start() {}

    final void update(float dt, float bx, float by) {
        t += dt;
        step(dt, bx, by);
    }

    abstract void step(float dt, float bx, float by);

    /** Draws targets and paths on top of the painting. Called inside a Filled ShapeRenderer batch. */
    abstract void drawGuide(ShapeRenderer sr);

    /** Short live counter shown under the header, like "7 reps". */
    abstract String counter();

    /** 0..1, how well this move went for the time it ran. */
    abstract float score();

    abstract String summary();

    /** Where the desktop autopilot should steer the brush. */
    abstract void target(Vector2 out);

    /** The brush colour, drifting a little around this move's hue. */
    Color paint(float time) {
        float h = hue + 22f * MathUtils.sin(time * 0.6f);
        return paintColor.fromHsv(((h % 360f) + 360f) % 360f, 0.62f, 1f);
    }

    Color guide(float alpha) {
        tmp.fromHsv(hue, 0.45f, 1f);
        tmp.a = alpha;
        return tmp;
    }

    protected float px(float n) { return g.cx + n * g.rx; }
    protected float py(float n) { return g.cy + n * g.ry; }
    protected float pulse(float speed, float amount) { return 1f + amount * MathUtils.sin(t * speed); }

    static void ring(ShapeRenderer sr, float x, float y, float r, float width) { ellipse(sr, x, y, r, r, width); }

    static void ellipse(ShapeRenderer sr, float x, float y, float rx, float ry, float width) {
        int seg = 64;
        float px = x + rx, py = y;
        for (int i = 1; i <= seg; i++) {
            float a = i * MathUtils.PI2 / seg;
            float nx = x + MathUtils.cos(a) * rx, ny = y + MathUtils.sin(a) * ry;
            sr.rectLine(px, py, nx, ny, width);
            px = nx;
            py = ny;
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Front plank: keep the brush in a small ring. Holding still grows a flower, one petal at a time. */
    static class Hold extends Move {
        static final float R = 95f;
        float in, streak, bestStreak, petalT;
        int petals;

        Hold(String name, float duration) {
            super(name, "Front plank", "Keep the brush in the ring. Hold still to grow the flower.", duration, 322f);
        }

        @Override void step(float dt, float bx, float by) {
            boolean inside = Vector2.dst(bx, by, g.cx, g.cy) < R;
            if (inside) {
                in += dt;
                streak += dt;
                bestStreak = Math.max(bestStreak, streak);
                petalT += dt;
                if (petalT >= 0.45f) {
                    petalT -= 0.45f;
                    petal();
                }
            } else {
                streak = 0f;
                petalT = 0f;
            }
        }

        private void petal() {
            float a = petals * 137.508f + (s.idx * 41f); // golden angle, offset per hold so two holds don't overlap exactly
            petals++;
            float len = Math.min(60f + streak * 24f, g.ry * 0.95f);
            float dx = MathUtils.cosDeg(a), dy = MathUtils.sinDeg(a);
            Color c = paint(s.t + petals * 0.3f);
            int n = Math.max(18, (int) (len / 6f));
            for (int i = 0; i <= n; i++) {
                float f = i / (float) n;
                float d = R * 0.3f + f * len;
                float bulge = MathUtils.sin(f * MathUtils.PI);
                g.paint.stamp(g.cx + dx * d, g.cy + dy * d, MathUtils.lerp(16f, 4f, f) + 30f * bulge, c, 0.22f);
            }
            g.sfx.bloom(0.8f + Math.min(streak, 20f) * 0.03f);
        }

        @Override void drawGuide(ShapeRenderer sr) {
            boolean inside = Vector2.dst(g.b.x, g.b.y, g.cx, g.cy) < R;
            sr.setColor(guide(inside ? 0.18f : 0.08f));
            sr.circle(g.cx, g.cy, R, 48);
            sr.setColor(guide(inside ? 0.95f : 0.55f * pulse(4f, 0.4f)));
            ring(sr, g.cx, g.cy, R, inside ? 7f : 5f);
        }

        @Override String counter() { return streak > 0.3f ? String.format("Steady %.0fs", streak) : "Back to the ring"; }
        @Override float score() { return t <= 0 ? 0 : Math.min(1f, in / (t * 0.85f)); }
        @Override String summary() { return Math.round(100 * in / Math.max(t, 0.01f)) + "% steady, longest " + Math.round(bestStreak) + "s"; }

        @Override void target(Vector2 out) { out.set(g.cx + 20f * MathUtils.sin(t * 1.3f), g.cy + 20f * MathUtils.cos(t * 0.9f)); }
    }

    /** Side to side (obliques) or forward and back (deep core): hit targets on alternating sides. */
    static class Gates extends Move {
        static final float R = 100f;
        final boolean horizontal;
        final Vector2 tg = new Vector2();
        int reps, side = 1;

        Gates(boolean horizontal, float duration) {
            super(horizontal ? "Windshield Wipers" : "Rock the Boat",
                    horizontal ? "Obliques" : "Deep core",
                    horizontal ? "Tilt side to side. Hit each target." : "Rock forward and back. Hit each target.",
                    duration, horizontal ? 172f : 32f);
            this.horizontal = horizontal;
        }

        @Override void start() { pick(); }

        private void pick() {
            float j = MathUtils.random(-0.4f, 0.4f);
            if (horizontal) tg.set(px(side * 0.78f), py(j));
            else tg.set(px(j), py(side * 0.78f));
        }

        @Override void step(float dt, float bx, float by) {
            if (tg.dst(bx, by) < R) {
                reps++;
                splash(tg.x, tg.y);
                g.sfx.ding(reps);
                g.sfx.buzz(25);
                side = -side;
                pick();
            }
        }

        private void splash(float x, float y) {
            Color c = paint(s.t);
            g.paint.stamp(x, y, 70f, c, 0.3f);
            for (int i = 0; i < 16; i++) {
                float a = i * 360f / 16 + MathUtils.random(-10f, 10f);
                float d = MathUtils.random(30f, 95f);
                g.paint.stamp(x + MathUtils.cosDeg(a) * d, y + MathUtils.sinDeg(a) * d, MathUtils.random(8f, 26f), c, 0.5f);
            }
        }

        @Override void drawGuide(ShapeRenderer sr) {
            float p = pulse(6f, 0.07f);
            sr.setColor(guide(0.12f));
            sr.rectLine(g.b.x, g.b.y, tg.x, tg.y, 3f);
            sr.setColor(guide(0.2f));
            sr.circle(tg.x, tg.y, R * p, 48);
            sr.setColor(guide(0.95f));
            ring(sr, tg.x, tg.y, R * p, 6f);
        }

        @Override String counter() { return reps + (reps == 1 ? " rep" : " reps"); }
        @Override float score() { return t <= 0 ? 0 : Math.min(1f, reps / (t / 2.4f)); }
        @Override String summary() { return reps + " reps"; }
        @Override void target(Vector2 out) { out.set(tg); }
    }

    /** Trace a circle through 8 checkpoints, then reverse halfway through. */
    static class Halo extends Move {
        static final int N = 8;
        static final float R = 85f, SIZE = 0.62f;
        int next, dir = 1, hits;
        boolean reversed;

        Halo(float duration) {
            super("Halo", "Full core", "Trace the circle. Follow the glowing dot.", duration, 268f);
        }

        float hx(int i) { return px(SIZE * MathUtils.cosDeg(90f - i * 360f / N)); }
        float hy(int i) { return py(SIZE * MathUtils.sinDeg(90f - i * 360f / N)); }

        @Override void step(float dt, float bx, float by) {
            if (!reversed && t > duration / 2f) {
                reversed = true;
                next = Math.floorMod(next - 2 * dir, N);
                dir = -dir;
                s.flash("Reverse!");
                g.sfx.reverse();
            }
            if (Vector2.dst(bx, by, hx(next), hy(next)) < R) {
                hits++;
                g.paint.stamp(hx(next), hy(next), 40f, paint(s.t), 0.4f);
                next = Math.floorMod(next + dir, N);
                if (hits % N == 0) { g.sfx.lap(); g.sfx.buzz(40); } else g.sfx.tick();
            }
        }

        @Override void drawGuide(ShapeRenderer sr) {
            sr.setColor(guide(0.1f));
            ellipse(sr, g.cx, g.cy, SIZE * g.rx, SIZE * g.ry, 4f);
            for (int i = 0; i < N; i++) {
                sr.setColor(guide(0.35f));
                sr.circle(hx(i), hy(i), 9f, 16);
            }
            int after = Math.floorMod(next + dir, N);
            sr.setColor(guide(0.25f));
            sr.rectLine(hx(next), hy(next), hx(after), hy(after), 4f);
            float p = pulse(7f, 0.1f);
            sr.setColor(guide(0.25f));
            sr.circle(hx(next), hy(next), R * 0.6f * p, 32);
            sr.setColor(guide(0.95f));
            ring(sr, hx(next), hy(next), R * 0.6f * p, 5f);
        }

        float laps() { return hits / (float) N; }
        @Override String counter() { int l = (int) laps(); return l + (l == 1 ? " lap" : " laps"); }
        @Override float score() { return t <= 0 ? 0 : Math.min(1f, laps() / (t / 6.5f)); }
        @Override String summary() { return String.format("%.1f laps", laps()); }
        @Override void target(Vector2 out) { out.set(hx(next), hy(next)); }
    }

    /** Trace a sideways figure 8 through 12 checkpoints. */
    static class Infinity extends Move {
        static final int N = 12;
        static final float R = 80f;
        int next = 1, hits;

        Infinity(float duration) {
            super("Infinity", "Obliques + deep core", "Trace the figure 8. Follow the glowing dot.", duration, 96f);
        }

        float ix(float th) { return px(0.74f * MathUtils.sin(th)); }
        float iy(float th) { return py(0.62f * MathUtils.sin(2f * th)); }
        float th(int i) { return i * MathUtils.PI2 / N; }

        @Override void step(float dt, float bx, float by) {
            float x = ix(th(next)), y = iy(th(next));
            if (Vector2.dst(bx, by, x, y) < R) {
                hits++;
                g.paint.stamp(x, y, 36f, paint(s.t), 0.4f);
                next = (next + 1) % N;
                if (hits % N == 0) { g.sfx.lap(); g.sfx.buzz(40); } else g.sfx.tick();
            }
        }

        @Override void drawGuide(ShapeRenderer sr) {
            sr.setColor(guide(0.1f));
            int seg = 96;
            for (int i = 0; i < seg; i++) {
                float a = i * MathUtils.PI2 / seg, b = (i + 1) * MathUtils.PI2 / seg;
                sr.rectLine(ix(a), iy(a), ix(b), iy(b), 4f);
            }
            for (int k = 2; k >= 1; k--) {
                int i = (next + k) % N;
                sr.setColor(guide(0.35f / k));
                sr.circle(ix(th(i)), iy(th(i)), 14f, 16);
            }
            float x = ix(th(next)), y = iy(th(next)), p = pulse(7f, 0.1f);
            sr.setColor(guide(0.25f));
            sr.circle(x, y, R * 0.6f * p, 32);
            sr.setColor(guide(0.95f));
            ring(sr, x, y, R * 0.6f * p, 5f);
        }

        float laps() { return hits / (float) N; }
        @Override String counter() { int l = (int) laps(); return l + (l == 1 ? " loop" : " loops"); }
        @Override float score() { return t <= 0 ? 0 : Math.min(1f, laps() / (t / 8f)); }
        @Override String summary() { return String.format("%.1f loops", laps()); }
        @Override void target(Vector2 out) { out.set(ix(th(next)), iy(th(next))); }
    }

    /** Stars pop up anywhere; reaching one draws a line from the last, so you finish with a constellation. */
    static class Stars extends Move {
        static final float R = 75f;
        final Vector2 star = new Vector2(), prev = new Vector2();
        boolean hasPrev;
        int count;

        Stars(float duration) {
            super("Constellation", "Everything", "Reach each star to draw a constellation.", duration, 48f);
        }

        @Override void start() { spawn(); }

        private void spawn() {
            for (int i = 0; i < 30; i++) {
                star.set(px(MathUtils.random(-0.85f, 0.85f)), py(MathUtils.random(-0.85f, 0.85f)));
                if (star.dst(g.b) > g.ry * 0.6f) break;
            }
        }

        @Override void step(float dt, float bx, float by) {
            if (star.dst(bx, by) < R) {
                count++;
                Color c = tmp.fromHsv((hue + count * 11f) % 360f, 0.35f, 1f);
                if (hasPrev) g.paint.stroke(prev.x, prev.y, star.x, star.y, 7f, c, 0.6f);
                g.paint.stamp(star.x, star.y, 90f, c, 0.3f);
                g.paint.stamp(star.x, star.y, 26f, Color.WHITE, 0.95f);
                prev.set(star);
                hasPrev = true;
                g.sfx.star(count);
                g.sfx.buzz(30);
                spawn();
            }
        }

        @Override void drawGuide(ShapeRenderer sr) {
            if (hasPrev) {
                sr.setColor(guide(0.15f));
                sr.rectLine(prev.x, prev.y, star.x, star.y, 3f);
            }
            float p = pulse(5f, 0.15f), rot = t * 40f;
            sr.setColor(guide(0.2f));
            sr.circle(star.x, star.y, R * p, 32);
            sr.setColor(1f, 0.97f, 0.85f, 0.95f);
            for (int i = 0; i < 4; i++) {
                float a = rot + i * 90f;
                sr.rectLine(star.x, star.y, star.x + MathUtils.cosDeg(a) * 42f * p, star.y + MathUtils.sinDeg(a) * 42f * p, 6f);
            }
            sr.circle(star.x, star.y, 12f, 16);
        }

        @Override String counter() { return count + (count == 1 ? " star" : " stars"); }
        @Override float score() { return t <= 0 ? 0 : Math.min(1f, count / (t / 2.6f)); }
        @Override String summary() { return count + " stars"; }
        @Override void target(Vector2 out) { out.set(star); }
    }
}
