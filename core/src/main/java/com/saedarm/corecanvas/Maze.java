package com.saedarm.corecanvas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * Marble Maze. The board's tilt is gravity: the marble rolls, builds speed and bounces off walls.
 * Reach the glowing exit tile to get a new, tighter maze. Gems sit in dead ends; from maze 3 on,
 * holes in the dead ends send you back to the start.
 */
public class Maze extends Play {
    static final float ACC = 2300f, MAX_SPEED = 1100f, WALL = 12f;

    private int cols, rows, level, cleared, gems, falls;
    private float cell, ox, oy, ball;
    private boolean[] wallN, wallE; // wall above / right of each cell
    private int[] dist;              // steps to the exit from each cell
    private final Array<Rectangle> walls = new Array<>();
    private final Array<Vector2> gemList = new Array<>(), holes = new Array<>();
    private int startC, startR, exitC, exitR;
    private final Vector2 pos = new Vector2(), vel = new Vector2();
    private final Array<Vector2> trailPts = new Array<>();
    private float respawnT, clearT = 99f, levelT;
    private final Color c = new Color(Color.WHITE);

    Maze(CoreCanvas g, float duration) { super(g, duration); }

    @Override public Kind kind() { return Kind.MAZE; }

    @Override void start() { build(); }

    private int id(int c, int r) { return r * cols + c; }

    private void build() {
        cell = Math.max(92f, 160f - level * 12f);
        float areaW = g.W - 120f, areaH = CoreCanvas.H - CoreCanvas.HUD_H - 90f;
        cols = (int) (areaW / cell);
        rows = (int) (areaH / cell);
        ox = (g.W - cols * cell) / 2f;
        oy = 40f + (areaH - rows * cell) / 2f;
        ball = cell * 0.2f;
        int n = cols * rows;
        wallN = new boolean[n];
        wallE = new boolean[n];
        for (int i = 0; i < n; i++) wallN[i] = wallE[i] = true;

        // carve with a depth-first backtracker
        boolean[] seen = new boolean[n];
        IntArray stack = new IntArray();
        startC = 0;
        startR = MathUtils.random(rows - 1);
        stack.add(id(startC, startR));
        seen[id(startC, startR)] = true;
        int[] dc = {0, 1, 0, -1}, dr = {1, 0, -1, 0};
        while (stack.size > 0) {
            int cur = stack.peek(), cc = cur % cols, cr = cur / cols;
            IntArray opts = new IntArray();
            for (int d = 0; d < 4; d++) {
                int nc = cc + dc[d], nr = cr + dr[d];
                if (nc >= 0 && nc < cols && nr >= 0 && nr < rows && !seen[id(nc, nr)]) opts.add(d);
            }
            if (opts.size == 0) { stack.pop(); continue; }
            int d = opts.random(), nc = cc + dc[d], nr = cr + dr[d];
            open(cc, cr, d);
            seen[id(nc, nr)] = true;
            stack.add(id(nc, nr));
        }

        // exit = the cell farthest from the start
        int[] fromStart = bfs(startC, startR);
        int far = 0;
        for (int i = 0; i < n; i++) if (fromStart[i] > fromStart[far]) far = i;
        exitC = far % cols;
        exitR = far / cols;
        dist = bfs(exitC, exitR);

        // gems and holes go in dead ends that aren't on the way to the exit
        gemList.clear();
        holes.clear();
        IntArray deadEnds = new IntArray();
        for (int i = 0; i < n; i++) {
            int cc = i % cols, cr = i / cols;
            if (i == id(startC, startR) || i == far) continue;
            if (exits(cc, cr) == 1 && fromStart[i] + dist[i] != fromStart[far]) deadEnds.add(i);
        }
        deadEnds.shuffle();
        int nGems = Math.min(3, deadEnds.size), nHoles = Math.min(level >= 2 ? Math.min(level - 1, 4) : 0, deadEnds.size - nGems);
        for (int i = 0; i < nGems; i++) gemList.add(center(deadEnds.get(i)));
        for (int i = nGems; i < nGems + nHoles; i++) holes.add(center(deadEnds.get(i)));

        // walls as rectangles, for both drawing and collisions
        walls.clear();
        float W = cols * cell, Hh = rows * cell;
        walls.add(new Rectangle(ox - WALL / 2, oy - WALL / 2, W + WALL, WALL));
        walls.add(new Rectangle(ox - WALL / 2, oy + Hh - WALL / 2, W + WALL, WALL));
        walls.add(new Rectangle(ox - WALL / 2, oy - WALL / 2, WALL, Hh + WALL));
        walls.add(new Rectangle(ox + W - WALL / 2, oy - WALL / 2, WALL, Hh + WALL));
        for (int r = 0; r < rows; r++) {
            for (int cc = 0; cc < cols; cc++) {
                if (r < rows - 1 && wallN[id(cc, r)]) walls.add(new Rectangle(ox + cc * cell - WALL / 2, oy + (r + 1) * cell - WALL / 2, cell + WALL, WALL));
                if (cc < cols - 1 && wallE[id(cc, r)]) walls.add(new Rectangle(ox + (cc + 1) * cell - WALL / 2, oy + r * cell - WALL / 2, WALL, cell + WALL));
            }
        }
        respawn();
        levelT = 0f;
    }

    private void open(int cc, int cr, int d) {
        if (d == 0) wallN[id(cc, cr)] = false;
        else if (d == 1) wallE[id(cc, cr)] = false;
        else if (d == 2) wallN[id(cc, cr - 1)] = false;
        else wallE[id(cc - 1, cr)] = false;
    }

    private boolean canGo(int cc, int cr, int d) {
        if (d == 0) return cr < rows - 1 && !wallN[id(cc, cr)];
        if (d == 1) return cc < cols - 1 && !wallE[id(cc, cr)];
        if (d == 2) return cr > 0 && !wallN[id(cc, cr - 1)];
        return cc > 0 && !wallE[id(cc - 1, cr)];
    }

    private int exits(int cc, int cr) {
        int k = 0;
        for (int d = 0; d < 4; d++) if (canGo(cc, cr, d)) k++;
        return k;
    }

    private int[] bfs(int sc, int sr) {
        int[] d = new int[cols * rows];
        java.util.Arrays.fill(d, -1);
        IntArray q = new IntArray();
        q.add(id(sc, sr));
        d[id(sc, sr)] = 0;
        int[] dc = {0, 1, 0, -1}, dr = {1, 0, -1, 0};
        for (int h = 0; h < q.size; h++) {
            int cur = q.get(h), cc = cur % cols, cr = cur / cols;
            for (int k = 0; k < 4; k++) {
                if (!canGo(cc, cr, k)) continue;
                int nx = id(cc + dc[k], cr + dr[k]);
                if (d[nx] < 0) { d[nx] = d[cur] + 1; q.add(nx); }
            }
        }
        return d;
    }

    private Vector2 center(int i) { return new Vector2(ox + (i % cols + 0.5f) * cell, oy + (i / cols + 0.5f) * cell); }

    private void respawn() {
        pos.set(ox + (startC + 0.5f) * cell, oy + (startR + 0.5f) * cell);
        vel.setZero();
        trailPts.clear();
    }

    @Override void update(float dt) {
        clearT += dt;
        levelT += dt;
        if (respawnT > 0f) {
            respawnT -= dt;
            if (respawnT <= 0f) respawn();
            return;
        }
        float ax = dead(g.ctrl.x), ay = dead(g.ctrl.y);
        vel.add(ax * ACC * dt, ay * ACC * dt);
        vel.scl(1f - Math.min(1f, 1.3f * dt));
        if (vel.len() > MAX_SPEED) vel.setLength(MAX_SPEED);

        int steps = Math.max(1, (int) Math.ceil(vel.len() * dt / 5f));
        for (int s = 0; s < steps; s++) {
            pos.mulAdd(vel, dt / steps);
            collide();
        }
        trailPts.add(new Vector2(pos));
        if (trailPts.size > 16) trailPts.removeIndex(0);

        for (int i = gemList.size - 1; i >= 0; i--) {
            if (gemList.get(i).dst(pos) < ball + 20f) {
                gemList.removeIndex(i);
                gems++;
                g.sfx.star(gems);
                g.sfx.buzz(30);
            }
        }
        for (Vector2 h : holes) {
            if (h.dst(pos) < cell * 0.26f - ball * 0.3f) {
                falls++;
                respawnT = 1.1f;
                g.sfx.pause();
                g.sfx.buzz(150);
                return;
            }
        }
        float exX = ox + (exitC + 0.5f) * cell, exY = oy + (exitR + 0.5f) * cell;
        if (Vector2.dst(pos.x, pos.y, exX, exY) < cell * 0.3f) {
            cleared++;
            level++;
            clearT = 0f;
            g.sfx.done();
            g.sfx.buzz(120);
            build();
        }
    }

    private static float dead(float v) {
        float a = Math.abs(v);
        return a < 0.06f ? 0f : Math.signum(v) * (a - 0.06f) / 0.94f;
    }

    private void collide() {
        for (Rectangle r : walls) {
            float nx = MathUtils.clamp(pos.x, r.x, r.x + r.width), ny = MathUtils.clamp(pos.y, r.y, r.y + r.height);
            float dx = pos.x - nx, dy = pos.y - ny, d2 = dx * dx + dy * dy;
            if (d2 >= ball * ball) continue;
            float d = (float) Math.sqrt(d2);
            if (d < 0.0001f) { dx = 0f; dy = 1f; d = 1f; }
            float push = ball - d;
            dx /= d;
            dy /= d;
            pos.add(dx * push, dy * push);
            float vn = vel.x * dx + vel.y * dy;
            if (vn < 0f) {
                vel.sub(dx * vn * 1.3f, dy * vn * 1.3f); // a little bounce
                if (vn < -350f) g.sfx.tick();
            }
        }
    }

    @Override void draw(ShapeRenderer sr) {
        g.blend();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(Paint.BG);
        sr.rect(0, 0, g.W, CoreCanvas.H);
        // floor tiles
        for (int r = 0; r < rows; r++) {
            for (int cc = 0; cc < cols; cc++) {
                float shade = ((r + cc) % 2 == 0) ? 0.035f : 0.0f;
                sr.setColor(0.12f + shade, 0.13f + shade, 0.2f + shade, 1f);
                sr.rect(ox + cc * cell + 2f, oy + r * cell + 2f, cell - 4f, cell - 4f);
            }
        }
        // start and exit tiles
        sr.setColor(0.3f, 0.4f, 0.7f, 0.5f);
        sr.rect(ox + startC * cell + 6f, oy + startR * cell + 6f, cell - 12f, cell - 12f);
        float pulse = 0.55f + 0.35f * MathUtils.sin(t * 5f);
        sr.setColor(0.3f, 1f, 0.6f, pulse);
        sr.rect(ox + exitC * cell + 6f, oy + exitR * cell + 6f, cell - 12f, cell - 12f);
        sr.setColor(0.05f, 0.2f, 0.1f, 1f);
        sr.circle(ox + (exitC + 0.5f) * cell, oy + (exitR + 0.5f) * cell, cell * 0.22f, 32);
        // holes
        for (Vector2 h : holes) {
            sr.setColor(0.9f, 0.3f, 0.3f, 0.6f);
            sr.circle(h.x, h.y, cell * 0.3f, 32);
            sr.setColor(0.01f, 0.01f, 0.02f, 1f);
            sr.circle(h.x, h.y, cell * 0.26f, 32);
        }
        // gems
        for (Vector2 gm : gemList) {
            float s = 18f + 3f * MathUtils.sin(t * 6f + gm.x);
            c.fromHsv((t * 60f + gm.y) % 360f, 0.5f, 1f);
            sr.setColor(c);
            sr.triangle(gm.x, gm.y + s, gm.x - s * 0.7f, gm.y, gm.x + s * 0.7f, gm.y);
            sr.triangle(gm.x, gm.y - s, gm.x - s * 0.7f, gm.y, gm.x + s * 0.7f, gm.y);
        }
        // walls, with a soft glow
        c.fromHsv((200f + level * 35f) % 360f, 0.6f, 1f);
        sr.setColor(c.r, c.g, c.b, 0.15f);
        for (Rectangle r : walls) sr.rect(r.x - 6f, r.y - 6f, r.width + 12f, r.height + 12f);
        sr.setColor(c.r, c.g, c.b, 1f);
        for (Rectangle r : walls) sr.rect(r.x, r.y, r.width, r.height);
        // marble
        if (respawnT <= 0f) {
            for (int i = 0; i < trailPts.size; i++) {
                Vector2 p = trailPts.get(i);
                sr.setColor(1f, 1f, 1f, 0.12f * i / trailPts.size);
                sr.circle(p.x, p.y, ball * (0.4f + 0.6f * i / trailPts.size), 20);
            }
            sr.setColor(0f, 0f, 0f, 0.4f);
            sr.circle(pos.x + 5f, pos.y - 6f, ball, 28);
            sr.setColor(0.85f, 0.88f, 0.95f, 1f);
            sr.circle(pos.x, pos.y, ball, 28);
            sr.setColor(1f, 1f, 1f, 1f);
            sr.circle(pos.x - ball * 0.3f, pos.y + ball * 0.3f, ball * 0.35f, 16);
        } else {
            float f = respawnT / 1.1f;
            sr.setColor(0.85f, 0.88f, 0.95f, f);
            sr.circle(pos.x, pos.y, ball * f, 28);
        }
        sr.end();
    }

    @Override void drawOverlay() {
        if (clearT < 1.5f) g.text(g.fTitle, "MAZE " + cleared + " DONE!", g.W / 2f, g.cy + 70f, com.badlogic.gdx.utils.Align.center, Color.WHITE, 1f - clearT / 1.5f);
        else if (levelT < 2f && level == 0) g.text(g.fBody, "Roll to the green tile", g.W / 2f, g.cy + 20f, com.badlogic.gdx.utils.Align.center, Color.WHITE, 1f - levelT / 2f);
        if (respawnT > 0f) g.text(g.fBig, "FELL IN! BACK TO START", g.W / 2f, g.cy + 40f, com.badlogic.gdx.utils.Align.center, Color.WHITE, 1f);
    }

    @Override String counter() { return "Maze " + (level + 1) + "  ·  " + gems + (gems == 1 ? " gem" : " gems"); }
    @Override int score() { return cleared * 100 + gems * 25; }
    @Override String scoreText() { return score() + " POINTS"; }

    @Override Array<String[]> summary() {
        Array<String[]> rows = new Array<>();
        rows.add(new String[]{"Mazes cleared", String.valueOf(cleared)});
        rows.add(new String[]{"Gems", String.valueOf(gems)});
        rows.add(new String[]{"Fell in a hole", falls + (falls == 1 ? " time" : " times")});
        return rows;
    }

    /** Autopilot: head for the next cell on the shortest path, and steer against the current velocity. */
    @Override void auto(Vector2 ctrl) {
        int cc = MathUtils.clamp((int) ((pos.x - ox) / cell), 0, cols - 1), cr = MathUtils.clamp((int) ((pos.y - oy) / cell), 0, rows - 1);
        int[] dc = {0, 1, 0, -1}, dr = {1, 0, -1, 0};
        float tx = ox + (cc + 0.5f) * cell, ty = oy + (cr + 0.5f) * cell;
        int here = dist[id(cc, cr)];
        float cxOff = Math.abs(pos.x - tx), cyOff = Math.abs(pos.y - ty);
        for (int d = 0; d < 4; d++) {
            if (!canGo(cc, cr, d)) continue;
            int n = id(cc + dc[d], cr + dr[d]);
            if (dist[n] == here - 1) {
                // line up with the corridor before turning into it
                boolean vertical = d == 0 || d == 2;
                if ((vertical && cxOff < cell * 0.15f) || (!vertical && cyOff < cell * 0.15f)) {
                    tx = ox + (cc + dc[d] + 0.5f) * cell;
                    ty = oy + (cr + dr[d] + 0.5f) * cell;
                }
            }
        }
        float wantX = (tx - pos.x) * 4f, wantY = (ty - pos.y) * 4f;
        ctrl.set(MathUtils.clamp((wantX - vel.x) * 0.004f, -1f, 1f), MathUtils.clamp((wantY - vel.y) * 0.004f, -1f, 1f));
    }
}
