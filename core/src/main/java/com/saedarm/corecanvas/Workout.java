package com.saedarm.corecanvas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

/** The guided paint workout: a list of moves played back to back. */
public class Workout extends Play {
    public final Array<Move> moves = new Array<>();
    public int idx;
    public float moveT, bannerT, flashT = 99f;
    public String flashText;
    private final Color hud = new Color(Color.WHITE);

    Workout(CoreCanvas g, Length len) {
        super(g, 0f);
        switch (len) {
            case QUICK:
                add(new Move.Hold("Hold Steady", 20));
                add(new Move.Gates(true, 22));
                add(new Move.Halo(26));
                add(new Move.Stars(22));
                break;
            case STANDARD:
                add(new Move.Hold("Hold Steady", 25));
                add(new Move.Gates(true, 30));
                add(new Move.Gates(false, 25));
                add(new Move.Halo(35));
                add(new Move.Infinity(35));
                add(new Move.Stars(30));
                break;
            default:
                add(new Move.Hold("Hold Steady", 35));
                add(new Move.Gates(true, 45));
                add(new Move.Gates(false, 40));
                add(new Move.Halo(55));
                add(new Move.Infinity(55));
                add(new Move.Stars(45));
                add(new Move.Hold("Final Hold", 25));
        }
        for (Move m : moves) duration += m.duration;
    }

    private void add(Move m) {
        m.bind(g, this);
        moves.add(m);
    }

    @Override public Kind kind() { return Kind.WORKOUT; }
    @Override boolean usesBrush() { return true; }
    @Override boolean paints() { return true; }

    public Move cur() { return moves.get(Math.min(idx, moves.size - 1)); }

    void flash(String text) {
        flashText = text;
        flashT = 0f;
    }

    @Override void start() { moves.first().start(); }

    @Override void update(float dt) {
        bannerT += dt;
        flashT += dt;
        moveT += dt;
        Move m = cur();
        g.paint.begin();
        trail(dt, m.paint(t), 0.2f, 46f, 12f);
        m.update(dt, g.b.x, g.b.y);
        g.paint.end();
        if (moveT >= m.duration && idx < moves.size - 1) {
            idx++;
            moveT = 0f;
            bannerT = 0f;
            cur().start();
            g.sfx.move();
            g.sfx.buzz(60);
        }
    }

    @Override void draw(ShapeRenderer sr) {
        g.drawCanvas();
        g.blend();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        cur().drawGuide(sr);
        sr.end();
    }

    @Override void drawProgress(ShapeRenderer sr, float x, float y, float w) {
        for (int i = 0; i < moves.size; i++) {
            Move mi = moves.get(i);
            float sw = w * mi.duration / duration;
            hud.fromHsv(mi.hue, 0.55f, 1f);
            sr.setColor(hud.r, hud.g, hud.b, 0.25f);
            sr.rect(x + 2f, y, sw - 4f, 8f);
            float done = i < idx ? 1f : i == idx ? moveT / mi.duration : 0f;
            sr.setColor(hud.r, hud.g, hud.b, 0.95f);
            sr.rect(x + 2f, y, (sw - 4f) * MathUtils.clamp(done, 0f, 1f), 8f);
            x += sw;
        }
    }

    @Override void drawOverlay() {
        Move m = cur();
        Color c = titleColor();
        if (bannerT < 2.4f) {
            float a = bannerT < 0.2f ? bannerT / 0.2f : 1f - MathUtils.clamp((bannerT - 1.6f) / 0.8f, 0f, 1f);
            g.text(g.fTitle, m.name.toUpperCase(), g.W / 2f, g.cy + 110f, Align.center, c, 0.85f * a);
            g.text(g.fBody, m.cue, g.W / 2f, g.cy - 40f, Align.center, Color.WHITE, 0.9f * a);
        }
        if (flashT < 1.3f) g.text(g.fTitle, flashText.toUpperCase(), g.W / 2f, g.cy + 60f, Align.center, Color.WHITE, 1f - flashT / 1.3f);
    }

    @Override String title() { return cur().name.toUpperCase(); }
    @Override String sub() { return cur().muscles + "  ·  " + cur().cue + "   (move " + (idx + 1) + " of " + moves.size + ")"; }
    @Override String counter() { return cur().counter(); }
    @Override Color titleColor() { return tmp.fromHsv(cur().hue, 0.5f, 1f); }
    @Override Color brushColor() { return cur().paint(t); }

    /** 0..100, weighted by how long each move actually ran. */
    @Override int score() {
        float sum = 0f, w = 0f;
        for (int i = 0; i <= idx; i++) {
            Move m = moves.get(i);
            sum += m.score() * m.t;
            w += m.t;
        }
        return w <= 0f ? 0 : Math.round(100f * sum / w);
    }

    @Override String scoreText() { return "SCORE " + score(); }

    @Override Array<String[]> summary() {
        Array<String[]> rows = new Array<>();
        for (int i = 0; i <= idx; i++) {
            Move m = moves.get(i);
            if (m.t > 0f) rows.add(new String[]{m.name, m.summary()});
        }
        return rows;
    }

    private final Vector2 aimAt = new Vector2();

    @Override void auto(Vector2 ctrl) {
        cur().target(aimAt);
        aim(ctrl, aimAt.x, aimAt.y);
    }
}
