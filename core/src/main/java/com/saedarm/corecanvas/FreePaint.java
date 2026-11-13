package com.saedarm.corecanvas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/** No targets: paint whatever you like. The score is how much of the canvas you covered. */
public class FreePaint extends Play {
    static final int GX = 48, GY = 22;
    private final boolean[] covered = new boolean[GX * GY];
    private int coveredN;
    private float travel;
    private final Color c = new Color(Color.WHITE);

    FreePaint(CoreCanvas g, float duration) { super(g, duration); }

    @Override public Kind kind() { return Kind.FREE; }
    @Override boolean usesBrush() { return true; }
    @Override boolean paints() { return true; }

    @Override Color brushColor() { return c.fromHsv((t * 14f) % 360f, 0.65f, 1f); }

    @Override void update(float dt) {
        g.paint.begin();
        trail(dt, brushColor(), 0.22f, 56f, 10f);
        g.paint.end();
        travel += brushSpeed * dt;
        int gx = (int) ((g.b.x - (g.cx - g.rx)) / (2f * g.rx) * GX), gy = (int) ((g.b.y - (g.cy - g.ry)) / (2f * g.ry) * GY);
        if (gx >= 0 && gx < GX && gy >= 0 && gy < GY && !covered[gy * GX + gx]) {
            covered[gy * GX + gx] = true;
            coveredN++;
        }
    }

    float coverage() { return coveredN / (float) covered.length; }

    @Override void draw(ShapeRenderer sr) { g.drawCanvas(); }

    @Override String counter() { return "Covered " + pct(coverage()); }
    @Override int score() { return Math.round(coverage() * 100f); }
    @Override String scoreText() { return pct(coverage()) + " COVERED"; }

    @Override Array<String[]> summary() {
        Array<String[]> rows = new Array<>();
        rows.add(new String[]{"Canvas covered", pct(coverage())});
        rows.add(new String[]{"Brush traveled", Math.round(travel / 1000f) + " canvas-meters"});
        return rows;
    }

    @Override void auto(Vector2 ctrl) {
        ctrl.set(0.8f * MathUtils.sin(t * 0.9f) * MathUtils.cos(t * 0.23f), 0.8f * MathUtils.sin(t * 1.3f + 1f));
    }
}
