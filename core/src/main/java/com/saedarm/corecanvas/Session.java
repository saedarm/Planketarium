package com.saedarm.corecanvas;

/**
 * Runs one Play: the plank timer, pausing (drop off the board, tap, or Back), the pause menu,
 * and a 3-2-1 countdown before play picks back up.
 */
public class Session {
    /** Paused this long and the session ends, saving what you made. */
    static final float GIVE_UP = 60f;

    final CoreCanvas g;
    public final Play play;
    public final Play.Kind kind;
    public final Play.Length length;

    public boolean paused, finished, endedEarly;
    public float pausedT;
    public String pauseReason = "";
    /** Seconds left in the get-ready countdown after resuming; negative when not counting. */
    public float resumeCount = -1f;
    private int lastBeep;
    private float fallT, pinT;

    Session(CoreCanvas g, Play.Kind kind, Play.Length length) {
        this.g = g;
        this.kind = kind;
        this.length = length;
        this.play = kind.create(g, length);
        play.start();
    }

    void update(float dt) {
        if (finished) return;
        if (paused) {
            pausedT += dt;
            if (pausedT >= GIVE_UP) finish(true);
            return;
        }
        if (resumeCount >= 0f) {
            resumeCount -= dt;
            if (resumeCount > 0f && (int) Math.ceil(resumeCount) < lastBeep) {
                lastBeep = (int) Math.ceil(resumeCount);
                g.sfx.beep();
            }
            if (resumeCount < 0f) {
                g.sfx.go();
                play.onResume();
            }
            return;
        }

        // Dropped off the board? Either tilted way past the play area, or slumped against an edge and not moving.
        if (!g.autopilot) {
            Tilt tilt = g.tilt;
            fallT = tilt.deviation() > tilt.maxDeg * 1.7f ? fallT + dt : Math.max(0f, fallT - dt * 2f);
            boolean pinned = (Math.abs(tilt.nx()) >= 0.98f || Math.abs(tilt.ny()) >= 0.98f) && tilt.wobble() < 1.5f;
            pinT = pinned ? pinT + dt : 0f;
            if (fallT > 1.2f || pinT > 3f) {
                pause("Looks like you dropped");
                return;
            }
        } else if (g.pauseTestAt > 0f && play.t >= g.pauseTestAt) {
            g.pauseTestAt = -1f;
            pause("Paused");
            return;
        }

        play.t += dt;
        play.update(dt);
        if (play.t >= play.duration) finish(false);
    }

    void pause(String reason) {
        if (paused || finished) return;
        paused = true;
        pausedT = 0f;
        fallT = pinT = 0f;
        pauseReason = reason;
        resumeCount = -1f;
        play.onPause();
        g.sfx.pause();
        g.sfx.buzz(80);
        g.buildPauseMenu();
    }

    void resume() {
        paused = false;
        resumeCount = 3f;
        lastBeep = 3;
        play.breakTrail();
        g.sfx.beep();
        g.clearButtons();
    }

    void finish(boolean early) {
        if (finished) return;
        finished = true;
        endedEarly = early;
        play.end();
        g.onSessionFinished(this);
    }
}
