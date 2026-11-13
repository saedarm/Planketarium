package com.saedarm.corecanvas;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Core Canvas: plank games for tilt boards like the Stealth core trainer. The phone lies in the board's slot and
 * the board is the controller. Menus are picked by holding the brush over a button, so nothing needs a finger
 * (though taps work too).
 */
public class CoreCanvas extends ApplicationAdapter {
    public static final float H = 1080f, HUD_H = 150f;
    static final float DWELL = 1.4f;
    static final float[] SENS_DEG = {15f, 11f, 8f};
    static final String[] SENS_NAME = {"Low", "Medium", "High"};
    static final Color INK = new Color(0.12f, 0.12f, 0.16f, 1f);
    static final Color WALL = new Color(0.05f, 0.055f, 0.08f, 1f);

    public float W, cx, cy, rx, ry;

    private final PictureSaver saver;
    /** Desktop testing: steer automatically, run time faster, save screenshots, pause partway through. */
    public boolean autopilot;
    public float speed = 1f, pauseTestAt = -1f;
    public String shotsDir;
    public Play.Kind autoKind = Play.Kind.WORKOUT;
    public Play.Length autoLength = Play.Length.STANDARD;

    OrthographicCamera cam;
    FitViewport viewport;
    SpriteBatch batch;
    ShapeRenderer sr;
    BitmapFont fTitle, fBig, fMid, fBody, fSmall;
    final GlyphLayout layout = new GlyphLayout();
    Texture brush;
    Texture lastPainting;
    public Tilt tilt;
    public Sfx sfx;
    Stats stats;
    public Paint paint;

    enum State { TITLE, LENGTH, GETSET, PLAY, RESULTS }
    State state = State.TITLE;
    float stateT, time;

    /** The brush (menus and brush games). */
    public final Vector2 b = new Vector2();
    /** Board tilt in play, -1..1 on each axis relative to your level plank. */
    public final Vector2 ctrl = new Vector2();
    private final Vector2 autoTarget = new Vector2(), autoCtrl = new Vector2(), touch = new Vector2();
    private final Array<Vector2> titleTrail = new Array<>();
    private final Array<Dwell> buttons = new Array<>();

    Session session;
    Play.Kind kind = Play.Kind.WORKOUT;
    Play.Length length = Play.Length.STANDARD;
    float stillT, countdown;
    int lastBeep;

    // results
    int score;
    boolean newBest;
    String artTitle, savedWhere;

    private float nextShot = 1f;
    private int shotN;
    private final Color tc = new Color(Color.WHITE);

    public CoreCanvas(PictureSaver saver) { this.saver = saver; }

    // ---------------------------------------------------------------------------------------------- setup

    @Override public void create() {
        float aspect = Gdx.graphics.getWidth() / (float) Gdx.graphics.getHeight();
        W = Math.round(H * Math.max(aspect, 1.5f));
        cx = W / 2f;
        cy = (H - HUD_H) / 2f;
        rx = W / 2f - 90f;
        ry = (H - HUD_H) / 2f - 60f;

        cam = new OrthographicCamera();
        viewport = new FitViewport(W, H, cam);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        batch = new SpriteBatch();
        sr = new ShapeRenderer();

        float scale = Gdx.graphics.getHeight() / H;
        fTitle = font("fonts/Bungee-Regular.ttf", 118, scale);
        fBig = font("fonts/Bungee-Regular.ttf", 54, scale);
        fMid = font("fonts/Bungee-Regular.ttf", 40, scale);
        fBody = font("fonts/DejaVuSans-Bold.ttf", 38, scale);
        fSmall = font("fonts/DejaVuSans-Bold.ttf", 27, scale);

        brush = makeBrush();
        paint = new Paint((int) W, (int) H, batch, brush);
        tilt = new Tilt();
        stats = new Stats();
        sfx = new Sfx();
        tilt.maxDeg = SENS_DEG[stats.sensitivity()];
        sfx.setOn(stats.sound());

        FileHandle last = Gdx.files.local("paintings/last.png");
        if (last.exists()) {
            try { lastPainting = new Texture(last); } catch (Exception e) { lastPainting = null; }
        }
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        enterTitle();
    }

    private static BitmapFont font(String file, int size, float scale) {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal(file));
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.max(8, Math.round(size * scale));
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "·‹›";
        BitmapFont f = gen.generateFont(p);
        gen.dispose();
        f.getData().setScale(1f / scale);
        f.setUseIntegerPositions(false);
        return f;
    }

    /** A soft round dab: the brush tip every stroke is made of. */
    private static Texture makeBrush() {
        int n = 128;
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float d = (float) Math.hypot(x - n / 2f + 0.5f, y - n / 2f + 0.5f) / (n / 2f);
                float a = MathUtils.clamp(1f - d, 0f, 1f);
                a = a * a * (3f - 2f * a);
                pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a));
            }
        }
        Texture t = new Texture(pm, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }

    /** Leaving the app mid-game pauses it. */
    @Override public void pause() {
        if (state == State.PLAY && session != null) session.pause("Paused");
    }

    // ---------------------------------------------------------------------------------------------- screens

    void clearButtons() { buttons.clear(); }

    private void addButton(Dwell d) {
        d.armed = !d.over(b); // a button the brush is already sitting on has to be left and re-entered
        buttons.add(d);
    }

    void enterTitle() {
        state = State.TITLE;
        stateT = 0f;
        session = null;
        tilt.resetNeutral();
        titleTrail.clear();
        sfx.startMusic();
        buttons.clear();
        Play.Kind[] kinds = Play.Kind.values();
        String[] short_ = {"WORKOUT", "GARDEN", "MAZE", "NIGHT DRIVE", "FREE PAINT"};
        String[] subs = {"Guided moves", "Grow flowers", "Roll the marble", "Lean to race", "Just paint"};
        float gap = Math.min(40f, W / 60f), w = Math.min(400f, (W - 160f - gap * 4) / 5f), h = 250f, y = 170f;
        float x0 = W / 2f - (w * 5 + gap * 4) / 2f;
        for (int i = 0; i < kinds.length; i++) {
            final Play.Kind k = kinds[i];
            Dwell d = new Dwell(x0 + i * (w + gap), y, w, h, short_[i], subs[i], k.hue, () -> enterLength(k));
            d.kind = k;
            d.icon = i;
            addButton(d);
        }
        float sw = Math.min(420f, W / 5f), sy = 660f;
        Dwell sens = new Dwell(W / 2f - W * 0.29f - sw / 2f, sy, sw, 96f, "Sensitivity: " + SENS_NAME[stats.sensitivity()], null, 268f, null);
        sens.action = () -> {
            int s = (stats.sensitivity() + 1) % 3;
            stats.setSensitivity(s);
            tilt.maxDeg = SENS_DEG[s];
            sens.label = "Sensitivity: " + SENS_NAME[s];
        };
        Dwell sound = new Dwell(W / 2f + W * 0.29f - sw / 2f, sy, sw, 96f, "Sound: " + (stats.sound() ? "On" : "Off"), null, 96f, null);
        sound.action = () -> {
            boolean on = !stats.sound();
            stats.setSound(on);
            sfx.setOn(on);
            sound.label = "Sound: " + (on ? "On" : "Off");
        };
        sens.small = sound.small = true;
        addButton(sens);
        addButton(sound);
    }

    void enterLength(Play.Kind k) {
        kind = k;
        state = State.LENGTH;
        stateT = 0f;
        buttons.clear();
        Play.Length[] lens = Play.Length.values();
        String[] moves = {"  ·  4 moves", "  ·  6 moves", "  ·  7 moves"};
        float y = 190f, w = Math.min(440f, W / 3.6f), h = 200f, gap = w + Math.min(90f, W / 26f);
        for (int i = 0; i < lens.length; i++) {
            final Play.Length l = lens[i];
            int best = stats.best(k, l);
            String sub = clock(l.seconds) + (k == Play.Kind.WORKOUT ? moves[i] : "");
            if (best > 0) sub += "\n" + k.formatBest(best);
            Dwell d = new Dwell(W / 2f + (i - 1) * gap - w / 2f, y, w, h, l.label.toUpperCase(), sub, k.hue, () -> enterGetSet(l));
            d.length = l;
            addButton(d);
        }
        Dwell back = new Dwell(W / 2f - W * 0.3f - 170f, 640f, 340f, 96f, "‹  Back", null, 0f, this::enterTitle);
        back.small = true;
        addButton(back);
    }

    void enterGetSet(Play.Length l) {
        length = l;
        state = State.GETSET;
        stateT = 0f;
        stillT = 0f;
        countdown = -1f;
        buttons.clear();
        tilt.clearHistory();
        paint.clear();
    }

    void buildPauseMenu() {
        buttons.clear();
        float bw = 440f, bh = 190f;
        Dwell resume = new Dwell(W / 2f - bw / 2f, H / 2f - bh / 2f - 20f, bw, bh, "RESUME", "Plank level to land here", 160f, () -> session.resume());
        Dwell restart = new Dwell(W * 0.22f - 200f, 150f, 400f, 140f, "RESTART", null, 32f, () -> {
            session.finished = true;
            session.play.end();
            enterGetSet(length);
        });
        Dwell quit = new Dwell(W * 0.78f - 200f, 150f, 400f, 140f, "END & SAVE", null, 322f, () -> session.finish(true));
        addButton(resume);
        addButton(restart);
        addButton(quit);
    }

    void enterResults() {
        state = State.RESULTS;
        stateT = 0f;
        buttons.clear();
        float bw = 330f, bh = 130f;
        float x0 = session.play.paints() ? resultsColumnX() : W / 2f - bw - 20f;
        addButton(new Dwell(x0, 80f, bw, bh, "AGAIN", null, 172f, () -> enterGetSet(length)));
        addButton(new Dwell(x0 + bw + 40f, 80f, bw, bh, "MENU", null, 268f, this::enterTitle));
    }

    void onSessionFinished(Session s) {
        if (s.play.t < 8f) {
            enterTitle();
            return;
        }
        score = s.play.score();
        newBest = stats.record(s, score);
        savedWhere = null;
        if (s.play.paints()) {
            artTitle = makeArtTitle(stats.paintings());
            savedWhere = exportPainting(s);
        }
        sfx.done();
        sfx.buzz(120);
        enterResults();
    }

    // ---------------------------------------------------------------------------------------------- loop

    @Override public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1f / 20f) * speed;
        time += dt;
        stateT += dt;

        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) back();

        tilt.update(dt);
        moveBrush(dt);
        if (Gdx.input.justTouched()) tapped();

        switch (state) {
            case TITLE:
                titleTrail.add(new Vector2(b));
                if (titleTrail.size > 50) titleTrail.removeIndex(0);
                updateButtons(dt);
                break;
            case LENGTH: updateButtons(dt); break;
            case GETSET: updateGetSet(dt); break;
            case PLAY:
                session.update(dt);
                if (state == State.PLAY && session.paused) updateButtons(dt);
                break;
            case RESULTS:
                updateButtons(dt);
                if (stateT > 90f && !autopilot) enterTitle();
                break;
        }

        viewport.apply();
        Gdx.gl.glClearColor(WALL.r, WALL.g, WALL.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        sr.setProjectionMatrix(cam.combined);
        switch (state) {
            case TITLE: drawTitle(); break;
            case LENGTH: drawLength(); break;
            case GETSET: drawCanvas(); drawGetSet(); break;
            case PLAY: drawPlay(); break;
            case RESULTS: drawResults(); break;
        }
        if (shotsDir != null) screenshots();
    }

    private void back() {
        switch (state) {
            case TITLE: Gdx.app.exit(); break;
            case LENGTH: enterTitle(); break;
            case GETSET: enterLength(kind); break;
            case PLAY:
                if (session.paused) session.resume();
                else session.pause("Paused");
                break;
            default: enterTitle();
        }
    }

    /** Taps are optional: tap during play to pause, tap a button to pick it. */
    private void tapped() {
        viewport.unproject(touch.set(Gdx.input.getX(), Gdx.input.getY()));
        if (state == State.PLAY && !session.paused) {
            session.pause("Paused");
            return;
        }
        for (Dwell d : buttons) {
            if (d.over(touch)) {
                fire(d);
                return;
            }
        }
    }

    private boolean menuCursor() {
        return state == State.TITLE || state == State.LENGTH || state == State.RESULTS || (state == State.PLAY && session.paused);
    }

    private void moveBrush(float dt) {
        if (state == State.PLAY && !session.paused) {
            if (autopilot) {
                session.play.auto(autoCtrl);
                float k = 1f - (float) Math.exp(-dt * 8f);
                ctrl.x += (autoCtrl.x - ctrl.x) * k;
                ctrl.y += (autoCtrl.y - ctrl.y) * k;
            } else {
                ctrl.set(tilt.nx(), tilt.ny());
            }
            b.set(cx + ctrl.x * rx, cy + ctrl.y * ry);
            return;
        }
        if (autopilot) {
            autoTarget.set(W / 2f, H / 2f);
            for (Dwell d : buttons) {
                if ((state == State.TITLE && d.kind == autoKind) || (state == State.LENGTH && d.length == autoLength)) {
                    if (d.armed) autoTarget.set(d.x + d.w / 2f, d.y + d.h / 2f);
                    else autoTarget.set(W / 2f, H / 2f); // step off first, like a person would
                }
            }
            if (state == State.PLAY && session.paused && session.pausedT < 1.5f) autoTarget.set(W / 2f, 60f);
            if (state == State.GETSET) autoTarget.set(cx, cy);
            float step = 1100f * dt, d = b.dst(autoTarget);
            if (d > step) b.add((autoTarget.x - b.x) / d * step, (autoTarget.y - b.y) / d * step);
            else b.set(autoTarget);
            return;
        }
        if (menuCursor()) b.set(W / 2f + tilt.nx() * (W / 2f - 80f), H / 2f + tilt.ny() * (H / 2f - 60f));
        else b.set(cx + tilt.nx() * rx, cy + tilt.ny() * ry);
    }

    private void updateButtons(float dt) {
        if (stateT < 0.8f) return;
        for (Dwell d : buttons) {
            boolean over = d.over(b);
            if (!over) d.armed = true;
            if (over && d.armed) {
                d.fill += dt / DWELL;
                if (d.fill >= 1f) {
                    fire(d);
                    return;
                }
            } else {
                d.fill = Math.max(0f, d.fill - dt * 2f);
            }
        }
    }

    private void fire(Dwell d) {
        d.fill = 0f;
        sfx.select();
        sfx.buzz(50);
        d.action.run();
    }

    private void updateGetSet(float dt) {
        if (countdown < 0f) {
            boolean still = autopilot || (tilt.wobble() < 3f && tilt.fromFlat() < 30f);
            stillT = still ? stillT + dt : Math.max(0f, stillT - dt * 1.5f);
            if (stillT >= 2f) {
                tilt.calibrate();
                countdown = 3f;
                lastBeep = 3;
                sfx.beep();
            }
            return;
        }
        countdown -= dt;
        if (countdown > 0f && (int) Math.ceil(countdown) < lastBeep) {
            lastBeep = (int) Math.ceil(countdown);
            sfx.beep();
        }
        if (countdown <= 0f) {
            ctrl.setZero();
            b.set(cx, cy);
            session = new Session(this, kind, length);
            state = State.PLAY;
            stateT = 0f;
            sfx.go();
            sfx.buzz(80);
        }
    }

    // ---------------------------------------------------------------------------------------------- drawing

    void blend() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    void drawCanvas() {
        batch.begin();
        batch.disableBlending();
        batch.draw(paint.texture(), 0, 0, W, H, 0, 0, paint.w, paint.h, false, true);
        batch.enableBlending();
        batch.end();
    }

    private void drawBrush(Color c) {
        batch.begin();
        batch.setColor(c.r, c.g, c.b, 0.45f);
        batch.draw(brush, b.x - 60f, b.y - 60f, 120f, 120f);
        batch.setColor(Color.WHITE);
        batch.end();
        blend();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(1f, 1f, 1f, 0.95f);
        Move.ring(sr, b.x, b.y, 24f, 4f);
        sr.setColor(c.r, c.g, c.b, 1f);
        sr.circle(b.x, b.y, 11f, 20);
        sr.end();
    }

    private void dim(float a) {
        blend();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, a);
        sr.rect(0, 0, W, H);
        sr.end();
    }

    private void drawTitle() {
        if (lastPainting != null) {
            float s = Math.max(W / lastPainting.getWidth(), H / (lastPainting.getHeight() - 200f));
            float w = lastPainting.getWidth() * s, h = (lastPainting.getHeight() - 200f) * s;
            batch.begin();
            batch.setColor(1f, 1f, 1f, 0.18f);
            // skip the placard strip along the bottom of the saved image
            int tw = lastPainting.getWidth(), th = lastPainting.getHeight() - 200;
            batch.draw(lastPainting, (W - w) / 2f, (H - h) / 2f, w, h, 0, 0, tw, th, false, false);
            batch.setColor(Color.WHITE);
            batch.end();
        }
        // a short fading trail behind the brush so the menu feels like paint too
        batch.begin();
        for (int i = 0; i < titleTrail.size; i++) {
            float f = i / (float) titleTrail.size;
            tc.fromHsv((time * 40f + i * 4f) % 360f, 0.6f, 1f);
            batch.setColor(tc.r, tc.g, tc.b, 0.18f * f);
            float sz = 20f + 50f * f;
            Vector2 p = titleTrail.get(i);
            batch.draw(brush, p.x - sz / 2f, p.y - sz / 2f, sz, sz);
        }
        batch.setColor(Color.WHITE);
        batch.end();

        drawButtons();

        batch.begin();
        text(fTitle, "CORE CANVAS", W / 2f, H - 60f, Align.center, Color.WHITE, 1f);
        text(fBody, "Plank games for your tilt board. Pick one by tilting the brush onto it.", W / 2f, H - 215f, Align.center, Color.WHITE, 0.85f);
        text(fSmall, "Tilt to move the brush  ·  hold it on a tile to pick  ·  tapping works too", W / 2f, 500f, Align.center, Color.WHITE, 0.6f);
        String footer;
        if (!tilt.hasSensor()) footer = "Desktop mode: the mouse is the board";
        else footer = "Streak " + stats.streak() + (stats.streak() == 1 ? " day" : " days") + "   ·   " + clock(stats.totalPlank())
                + " total plank   ·   " + stats.paintings() + (stats.paintings() == 1 ? " painting" : " paintings");
        text(fSmall, footer, W / 2f, 100f, Align.center, Color.WHITE, 0.7f);
        batch.end();
        drawBrush(Color.WHITE);
    }

    private void drawLength() {
        drawButtons();
        tc.fromHsv(kind.hue, 0.5f, 1f);
        batch.begin();
        text(fTitle, kind.label.toUpperCase(), W / 2f, H - 60f, Align.center, tc, 1f);
        text(fBody, kind.blurb, W / 2f, H - 215f, Align.center, Color.WHITE, 0.85f);
        text(fSmall, "How long do you want to plank?", W / 2f, 490f, Align.center, Color.WHITE, 0.65f);
        text(fSmall, "Drop off the board, tap, or press Back any time to pause.", W / 2f, 100f, Align.center, Color.WHITE, 0.5f);
        batch.end();
        drawBrush(Color.WHITE);
    }

    private void drawButtons() {
        blend();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        Color c = tc;
        for (Dwell d : buttons) {
            boolean over = d.over(b) && d.armed;
            sr.setColor(0f, 0f, 0f, over ? 0.75f : 0.6f);
            sr.rect(d.x, d.y, d.w, d.h);
            c.fromHsv(d.hue, d.hue == 0f ? 0f : 0.55f, 1f);
            if (d.fill > 0f) {
                sr.setColor(c.r, c.g, c.b, 0.55f);
                sr.rect(d.x, d.y, d.w * d.fill, d.h);
            }
            sr.setColor(c.r, c.g, c.b, over ? 1f : 0.6f);
            float bw = over ? 6f : 4f;
            sr.rect(d.x, d.y, d.w, bw);
            sr.rect(d.x, d.y + d.h - bw, d.w, bw);
            sr.rect(d.x, d.y, bw, d.h);
            sr.rect(d.x + d.w - bw, d.y, bw, d.h);
            if (d.icon >= 0) icon(d.icon, d.x + d.w / 2f, d.y + d.h - 62f, c);
        }
        sr.end();
        batch.begin();
        for (Dwell d : buttons) {
            if (d.small) {
                text(fSmall, d.label, d.x + d.w / 2f, d.y + d.h / 2f + 13f, Align.center, Color.WHITE, 0.95f);
            } else if (d.icon >= 0) {
                text(fMid, d.label, d.x + d.w / 2f, d.y + 110f, Align.center, Color.WHITE, 1f);
                text(fSmall, d.sub, d.x + d.w / 2f, d.y + 50f, Align.center, Color.WHITE, 0.7f);
            } else {
                text(fBig, d.label, d.x + d.w / 2f, d.y + d.h - (d.sub == null ? d.h / 2f - 28f : 36f), Align.center, Color.WHITE, 1f);
                if (d.sub != null) {
                    layout.setText(fSmall, d.sub, Color.WHITE, d.w, Align.center, false);
                    fSmall.setColor(1f, 1f, 1f, 0.75f);
                    fSmall.draw(batch, d.sub, d.x, d.y + 92f, d.w, Align.center, false);
                }
            }
        }
        batch.end();
    }

    /** Little pictures on the game tiles. Called inside a Filled ShapeRenderer batch. */
    private void icon(int which, float x, float y, Color c) {
        sr.setColor(c);
        switch (which) {
            case 0: // workout: a target ring with a brush dot
                Move.ring(sr, x, y, 34f, 6f);
                sr.circle(x + 12f, y + 6f, 9f, 16);
                break;
            case 1: // garden: a flower
                for (int i = 0; i < 6; i++) sr.circle(x + MathUtils.cosDeg(i * 60f + time * 20f) * 22f, y + MathUtils.sinDeg(i * 60f + time * 20f) * 22f, 13f, 16);
                sr.setColor(1f, 0.9f, 0.4f, 1f);
                sr.circle(x, y, 11f, 16);
                break;
            case 2: // maze: a little spiral of walls and a marble
                sr.rect(x - 40f, y - 34f, 80f, 6f);
                sr.rect(x - 40f, y + 28f, 80f, 6f);
                sr.rect(x - 40f, y - 34f, 6f, 62f);
                sr.rect(x + 34f, y - 14f, 6f, 48f);
                sr.rect(x - 20f, y - 14f, 54f, 6f);
                sr.setColor(1f, 1f, 1f, 1f);
                sr.circle(x - 14f + 6f * MathUtils.sin(time * 2f), y + 12f, 9f, 16);
                break;
            case 3: // race: a road vanishing into the distance
                sr.triangle(x - 46f, y - 34f, x + 46f, y - 34f, x, y + 34f);
                sr.setColor(0.1f, 0.1f, 0.15f, 1f);
                sr.triangle(x - 34f, y - 34f, x + 34f, y - 34f, x, y + 30f);
                sr.setColor(c);
                for (int i = 0; i < 3; i++) {
                    float f = ((time * 0.8f + i / 3f) % 1f);
                    sr.rect(x - 2f, y - 34f + f * 56f, 4f, 10f * (1f - f) + 2f);
                }
                break;
            default: // free paint: a swirl of dabs
                for (int i = 0; i < 12; i++) {
                    tc.fromHsv((i * 30f + time * 60f) % 360f, 0.6f, 1f);
                    sr.setColor(tc);
                    float a = i * 40f;
                    sr.circle(x + MathUtils.cosDeg(a) * (6f + i * 3f), y + MathUtils.sinDeg(a) * (6f + i * 2.5f), 6f + i * 0.5f, 12);
                }
        }
    }

    private void drawGetSet() {
        blend();
        float p = MathUtils.clamp(stillT / 2f, 0f, 1f);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.35f);
        sr.rect(0, 0, W, H);
        if (countdown < 0f) {
            sr.setColor(1f, 1f, 1f, 0.15f);
            Move.ring(sr, cx, cy, 150f, 10f);
            sr.setColor(0.4f, 1f, 0.8f, 0.95f);
            int seg = Math.round(64 * p);
            for (int i = 0; i < seg; i++) {
                float a0 = 90f - i * 360f / 64, a1 = 90f - (i + 1) * 360f / 64;
                sr.rectLine(cx + MathUtils.cosDeg(a0) * 150f, cy + MathUtils.sinDeg(a0) * 150f,
                        cx + MathUtils.cosDeg(a1) * 150f, cy + MathUtils.sinDeg(a1) * 150f, 10f);
            }
            float dx = MathUtils.clamp(tilt.smX / 25f, -1f, 1f) * 130f, dy = MathUtils.clamp(tilt.smY / 25f, -1f, 1f) * 130f;
            sr.setColor(1f, 1f, 1f, 0.9f);
            sr.circle(cx + dx, cy + dy, 14f, 20);
        }
        sr.end();
        batch.begin();
        if (countdown < 0f) {
            text(fBig, "GET INTO PLANK", W / 2f, H - 90f, Align.center, Color.WHITE, 1f);
            text(fBody, "Hold the board level and still. Wherever you settle becomes the center.", W / 2f, H - 190f, Align.center, Color.WHITE, 0.85f);
            text(fSmall, kind.label + "  ·  " + clock(length.seconds), W / 2f, 140f, Align.center, Color.WHITE, 0.7f);
            text(fSmall, "Back button: go back", W / 2f, 90f, Align.center, Color.WHITE, 0.45f);
        } else {
            int n = Math.max(1, (int) Math.ceil(countdown));
            float f = countdown - (float) Math.floor(countdown);
            text(fTitle, String.valueOf(n), W / 2f, cy + 70f, Align.center, Color.WHITE, 0.4f + 0.6f * f);
            text(fBody, kind.label, W / 2f, H - 120f, Align.center, Color.WHITE, 0.85f);
        }
        batch.end();
        if (countdown >= 0f) drawBrush(Color.WHITE);
    }

    private void drawPlay() {
        Session s = session;
        Play p = s.play;
        p.draw(sr);

        blend();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.5f);
        sr.rect(0, H - HUD_H, W, HUD_H);
        p.drawProgress(sr, 40f, H - HUD_H + 6f, W - 80f);
        sr.end();

        batch.begin();
        if (!s.paused) {
        tc.set(p.titleColor());
        text(fBig, p.title(), 40f, H - 24f, Align.left, tc, 1f);
        text(fSmall, p.sub(), 40f, H - 96f, Align.left, Color.WHITE, 0.8f);
        text(fBig, clock(Math.max(0f, p.duration - p.t)), W - 40f, H - 24f, Align.right, Color.WHITE, 1f);
        text(fSmall, "tap or Back to pause", W - 40f, H - 96f, Align.right, Color.WHITE, 0.45f);
        text(fBody, p.counter(), W / 2f, H - HUD_H - 26f, Align.center, Color.WHITE, 0.85f);
        p.drawOverlay();
        }
        batch.end();
        if (p.usesBrush() && !s.paused) drawBrush(p.brushColor());

        if (s.resumeCount >= 0f) {
            dim(0.35f);
            batch.begin();
            int n = Math.max(1, (int) Math.ceil(s.resumeCount));
            text(fTitle, String.valueOf(n), W / 2f, cy + 70f, Align.center, Color.WHITE, 1f);
            text(fBody, "Get set...", W / 2f, cy - 90f, Align.center, Color.WHITE, 0.8f);
            batch.end();
        }

        if (s.paused) {
            dim(0.72f);
            batch.begin();
            text(fTitle, "PAUSED", W / 2f, H - 50f, Align.center, Color.WHITE, 1f);
            text(fBody, s.pauseReason.equals("Paused") ? "Take a breather." : s.pauseReason + ". Take a breather.", W / 2f, H - 200f, Align.center, Color.WHITE, 0.9f);
            text(fSmall, "Get back in plank: the brush lands on Resume. Or tilt it onto Restart or End & Save.", W / 2f, H - 260f, Align.center, Color.WHITE, 0.6f);
            int left = (int) Math.ceil(Session.GIVE_UP - s.pausedT);
            text(fSmall, "Ends and saves on its own in " + left + "s", W / 2f, 70f, Align.center, Color.WHITE, 0.45f);
            batch.end();
            drawButtons();
            drawBrush(Color.WHITE);
        }
    }

    private float resultsColumnX() { return 60f + W * 0.58f + 70f; }

    private void drawResults() {
        Session s = session;
        Play p = s.play;
        if (p.paints()) drawPaintingResults(s);
        else drawScoreResults(s);
        drawButtons();
        drawBrush(Color.WHITE);
    }

    private void drawPaintingResults(Session s) {
        Play p = s.play;
        float fw = W * 0.58f, fh = fw * paint.h / paint.w, fx = 60f, fy = (H - fh) / 2f + 20f;
        blend();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.36f, 0.24f, 0.14f, 1f);
        sr.rect(fx - 40f, fy - 40f, fw + 80f, fh + 80f);
        sr.setColor(0.95f, 0.93f, 0.88f, 1f);
        sr.rect(fx - 22f, fy - 22f, fw + 44f, fh + 44f);
        sr.end();
        batch.begin();
        batch.disableBlending();
        batch.draw(paint.texture(), fx, fy, fw, fh, 0, 0, paint.w, paint.h, false, true);
        batch.enableBlending();

        float x0 = resultsColumnX(), colW = W - x0 - 50f, y = H - 70f;
        layout.setText(fBig, artTitle, Color.WHITE, colW, Align.left, true);
        fBig.draw(batch, layout, x0, y);
        y -= layout.height + 30f;
        text(fSmall, p.kind().label + "  ·  " + clock(p.t) + " of plank" + (s.endedEarly ? " (ended early)" : ""), x0, y, Align.left, Color.WHITE, 0.75f);
        y -= 70f;
        text(fBig, p.scoreText(), x0, y, Align.left, Color.WHITE, 1f);
        y -= 72f;
        String g = p.kind() == Play.Kind.WORKOUT ? grade(score) : "";
        if (newBest) g += (g.isEmpty() ? "" : "  ·  ") + "new best!";
        if (!g.isEmpty()) {
            text(fSmall, g, x0, y, Align.left, Color.WHITE, 0.85f);
            y -= 66f;
        }
        y = rows(p, x0, colW, y);
        y -= 14f;
        text(fSmall, savedWhere != null ? "Saved to " + savedWhere : "Saved in the app", x0, y, Align.left, Color.WHITE, 0.55f);
        batch.end();
    }

    private void drawScoreResults(Session s) {
        Play p = s.play;
        tc.fromHsv(p.kind().hue, 0.5f, 1f);
        batch.begin();
        float y = H - 60f;
        text(fBig, p.kind().label.toUpperCase(), W / 2f, y, Align.center, tc, 1f);
        y -= 90f;
        text(fTitle, p.scoreText(), W / 2f, y, Align.center, Color.WHITE, 1f);
        y -= 170f;
        text(fSmall, clock(p.t) + " of plank" + (s.endedEarly ? " (ended early)" : "") + (newBest ? "  ·  new best!" : ""), W / 2f, y, Align.center, Color.WHITE, 0.8f);
        y -= 70f;
        float colW = Math.min(900f, W * 0.5f);
        rows(p, W / 2f - colW / 2f, colW, y);
        batch.end();
    }

    private float rows(Play p, float x0, float colW, float y) {
        for (String[] row : p.summary()) {
            text(fSmall, row[0], x0, y, Align.left, Color.WHITE, 0.85f);
            text(fSmall, row[1], x0 + colW, y, Align.right, Color.WHITE, 0.85f);
            y -= 42f;
        }
        return y;
    }

    void text(BitmapFont f, String s, float x, float y, int align, Color c, float a) {
        f.setColor(c.r, c.g, c.b, a);
        layout.setText(f, s);
        float dx = align == Align.center ? -layout.width / 2f : align == Align.right ? -layout.width : 0f;
        f.draw(batch, s, x + dx, y);
    }

    // ---------------------------------------------------------------------------------------------- results

    private static final String[] ADJ = {"Trembling", "Oblique", "Unbothered", "Quivering", "Resolute", "Sweaty", "Defiant",
            "Wobbly", "Stubborn", "Midwestern", "Furious", "Serene", "Load-Bearing", "Molten", "Untamed", "Gritted", "Shaky"};
    private static final String[] NOUN = {"Sunrise", "Abdomen", "Nocturne", "Composition", "Self-Portrait", "Landscape",
            "Study in Core", "Plank", "Situation", "Constellation", "Bloom", "Tremor", "Horizon", "Weather", "Garden"};

    static String makeArtTitle(int n) {
        return ADJ[MathUtils.random(ADJ.length - 1)] + " " + NOUN[MathUtils.random(NOUN.length - 1)] + " No. " + n;
    }

    static String grade(int score) {
        if (score >= 90) return "Gallery-worthy";
        if (score >= 75) return "Museum quality";
        if (score >= 55) return "Solid work";
        if (score >= 35) return "Abstract";
        return "Very abstract";
    }

    static String clock(float seconds) {
        int s = Math.round(seconds);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** Frames the painting with a little placard, saves it as a PNG, and hands it to the gallery saver. */
    private String exportPainting(Session s) {
        int cw = paint.w, ch = paint.h, strip = 200, eh = ch + strip;
        FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, cw, eh, false);
        fb.begin();
        Gdx.gl.glClearColor(0.95f, 0.93f, 0.88f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, cw, eh));
        batch.begin();
        batch.disableBlending();
        batch.draw(paint.texture(), 0, strip, cw, ch, 0, 0, cw, ch, false, true);
        batch.enableBlending();
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
        text(fBig, artTitle, 50f, strip - 40f, Align.left, INK, 1f);
        text(fSmall, "Core on canvas  ·  " + clock(s.play.t) + " plank  ·  " + s.kind.label + "  ·  " + s.play.scoreText().toLowerCase() + "  ·  " + date,
                50f, strip - 125f, Align.left, INK, 0.75f);
        text(fSmall, "CORE CANVAS", cw - 50f, strip - 52f, Align.right, INK, 0.5f);
        batch.end();
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, cw, eh);
        fb.end();
        fb.dispose();
        batch.setProjectionMatrix(cam.combined);
        flipOpaque(pm);

        String name = "core-canvas-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png";
        FileHandle file = Gdx.files.local("paintings/" + name);
        String where = null;
        try {
            PixmapIO.writePNG(file, pm);
            file.copyTo(Gdx.files.local("paintings/last.png"));
            if (lastPainting != null) lastPainting.dispose();
            lastPainting = new Texture(pm);
            lastPainting.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            if (saver != null) where = saver.save(file, name);
        } catch (Exception e) {
            Gdx.app.error("CoreCanvas", "Couldn't save painting", e);
        }
        pm.dispose();
        return where;
    }

    /** glReadPixels comes back upside down, and the alpha channel isn't meaningful for a PNG. */
    private static void flipOpaque(Pixmap pm) {
        ByteBuffer px = pm.getPixels();
        int w = pm.getWidth(), h = pm.getHeight(), row = w * 4;
        byte[] a = new byte[row], c = new byte[row];
        for (int y = 0; y < h / 2; y++) {
            px.position(y * row); px.get(a);
            px.position((h - 1 - y) * row); px.get(c);
            px.position(y * row); px.put(c);
            px.position((h - 1 - y) * row); px.put(a);
        }
        for (int i = 3; i < row * h; i += 4) px.put(i, (byte) 255);
        px.position(0);
    }

    private void screenshots() {
        if (time < nextShot) return;
        nextShot = time + 4f;
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, w, h);
        flipOpaque(pm);
        String tag = state == State.PLAY && session.paused ? "PAUSED" : state.toString();
        PixmapIO.writePNG(Gdx.files.absolute(shotsDir + "/shot-" + String.format("%03d", shotN++) + "-" + tag + ".png"), pm);
        pm.dispose();
        if (autopilot && state == State.RESULTS && stateT > 3f) Gdx.app.exit();
    }

    @Override public void dispose() {
        batch.dispose();
        sr.dispose();
        paint.dispose();
        brush.dispose();
        if (lastPainting != null) lastPainting.dispose();
        fTitle.dispose(); fBig.dispose(); fMid.dispose(); fBody.dispose(); fSmall.dispose();
    }

    /** A button you pick by holding the brush over it (or by tapping it). */
    static class Dwell {
        final float x, y, w, h, hue;
        String label;
        final String sub;
        Runnable action;
        float fill;
        boolean small, armed;
        int icon = -1;
        Play.Kind kind;
        Play.Length length;

        Dwell(float x, float y, float w, float h, String label, String sub, float hue, Runnable action) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label; this.sub = sub; this.hue = hue; this.action = action;
        }

        boolean over(Vector2 p) { return p.x >= x && p.x <= x + w && p.y >= y && p.y <= y + h; }
    }
}
