package com.saedarm.corecanvas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * One game you can play on the board. The Session around it handles the timer, pausing and dropping off;
 * a Play only runs while you're actually planking.
 */
public abstract class Play {
    public enum Kind {
        WORKOUT("Paint Workout", "Six guided moves. Every workout makes a painting.", 322f),
        GARDEN("Flower Garden", "Hold still over each seed to grow a flower.", 128f),
        MAZE("Marble Maze", "Tilt is gravity. Roll the marble to the exit.", 200f),
        RACE("Night Drive", "Lean into the curves. Lean forward to go faster.", 290f),
        FREE("Free Paint", "No rules. Paint whatever you want until time runs out.", 48f);

        public final String label, blurb;
        public final float hue;
        Kind(String label, String blurb, float hue) { this.label = label; this.blurb = blurb; this.hue = hue; }

        public Play create(CoreCanvas g, Length len) {
            switch (this) {
                case WORKOUT: return new Workout(g, len);
                case GARDEN: return new Garden(g, len.seconds);
                case MAZE: return new Maze(g, len.seconds);
                case RACE: return new Racer(g, len.seconds);
                default: return new FreePaint(g, len.seconds);
            }
        }

        public String formatBest(int best) {
            switch (this) {
                case WORKOUT: return "best " + best;
                case GARDEN: return "best " + best + (best == 1 ? " flower" : " flowers");
                case MAZE: return "best " + best + " pts";
                case RACE: return String.format("best %.2f km", best / 1000f);
                default: return "best " + best + "%";
            }
        }
    }

    public enum Length {
        QUICK("Quick", 90), STANDARD("Standard", 180), LONG("Long", 300);
        public final String label;
        public final int seconds;
        Length(String label, int seconds) { this.label = label; this.seconds = seconds; }
    }

    protected final CoreCanvas g;
    public float t;
    public float duration;
    protected final Color tmp = new Color(Color.WHITE);

    Play(CoreCanvas g, float duration) {
        this.g = g;
        this.duration = duration;
    }

    public abstract Kind kind();

    void start() {}

    /** Runs only while planking (not paused, not counting down). */
    abstract void update(float dt);

    /** Draws the scene under the header. */
    abstract void draw(ShapeRenderer sr);

    /** Banners and anything else that goes on top of the header. */
    void drawOverlay() {}

    /** Draws the progress bar along the bottom of the header. */
    void drawProgress(ShapeRenderer sr, float x, float y, float w) {
        Color c = tmp.fromHsv(kind().hue, 0.55f, 1f);
        sr.setColor(c.r, c.g, c.b, 0.25f);
        sr.rect(x, y, w, 8f);
        sr.setColor(c.r, c.g, c.b, 0.95f);
        sr.rect(x, y, w * MathUtils.clamp(t / duration, 0f, 1f), 8f);
    }

    String title() { return kind().label.toUpperCase(); }
    String sub() { return kind().blurb; }
    abstract String counter();
    Color titleColor() { return tmp.fromHsv(kind().hue, 0.5f, 1f); }

    /** True if the game steers the paint brush (and so draws it). */
    boolean usesBrush() { return false; }
    /** True if the game paints the canvas, which is then framed and saved at the end. */
    boolean paints() { return false; }
    Color brushColor() { return Color.WHITE; }

    abstract int score();
    abstract String scoreText();
    /** Rows for the results screen: {label, value}. */
    abstract Array<String[]> summary();

    /** Desktop autopilot: what tilt to fake, in the same -1..1 units as Tilt.nx()/ny(). */
    abstract void auto(Vector2 ctrl);

    void onPause() {}
    void onResume() {}
    void end() {}

    // ---------------------------------------------------------------------------------- shared helpers

    private final Vector2 last = new Vector2();
    private boolean hasLast;
    protected float brushSpeed;

    /** Paints a stroke from where the brush was last frame. Slow is fat, fast is thin. Call between paint.begin/end. */
    protected void trail(float dt, Color c, float alpha, float fat, float thin) {
        if (!hasLast) {
            last.set(g.b);
            hasLast = true;
            return;
        }
        float d = last.dst(g.b);
        brushSpeed = MathUtils.lerp(brushSpeed, d / Math.max(dt, 0.001f), 0.3f);
        if (d < 3f) return;
        float size = MathUtils.lerp(fat, thin, MathUtils.clamp(brushSpeed / 1500f, 0f, 1f));
        g.paint.stroke(last.x, last.y, g.b.x, g.b.y, size, c, alpha);
        last.set(g.b);
    }

    /** Forget the last brush position, so resuming after a pause doesn't draw a line across the canvas. */
    protected void breakTrail() { hasLast = false; }

    /** Autopilot for brush games: in position mode the tilt that puts the brush on a point is just its offset. */
    protected void aim(Vector2 ctrl, float x, float y) {
        ctrl.set(MathUtils.clamp((x - g.cx) / g.rx, -1f, 1f), MathUtils.clamp((y - g.cy) / g.ry, -1f, 1f));
    }

    static String pct(float f) { return Math.round(f * 100f) + "%"; }
}
