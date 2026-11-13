package com.saedarm.corecanvas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import java.time.LocalDate;

/** Saved totals, bests, streak and settings. */
public class Stats {
    private final Preferences p = Gdx.app.getPreferences("core-canvas");

    public int sessions() { return p.getInteger("sessions", 0); }
    public int paintings() { return p.getInteger("paintings", 0); }
    public float totalPlank() { return p.getFloat("plank", 0f); }
    public int best(Play.Kind k, Play.Length l) { return p.getInteger("best_" + k.name() + "_" + l.name(), 0); }
    public int sensitivity() { return p.getInteger("sensitivity", 1); }
    public boolean sound() { return p.getBoolean("sound", true); }

    /** Days in a row with at least a 30-second session. Shows 0 once a day has been missed. */
    public int streak() {
        long today = LocalDate.now().toEpochDay();
        long last = p.getLong("lastDay", -100);
        return today - last <= 1 ? p.getInteger("streak", 0) : 0;
    }

    public void setSensitivity(int s) { p.putInteger("sensitivity", s).flush(); }
    public void setSound(boolean on) { p.putBoolean("sound", on).flush(); }

    /** Records a finished session. Returns true if it beat the best score for its mode. */
    public boolean record(Session s, int score) {
        float plank = s.play.t;
        p.putInteger("sessions", sessions() + 1);
        if (s.play.paints()) p.putInteger("paintings", paintings() + 1);
        p.putFloat("plank", totalPlank() + plank);
        boolean best = false;
        if (!s.endedEarly && score > best(s.kind, s.length)) {
            p.putInteger("best_" + s.kind.name() + "_" + s.length.name(), score);
            best = true;
        }
        if (plank >= 30f) {
            long today = LocalDate.now().toEpochDay();
            long last = p.getLong("lastDay", -100);
            int streak = p.getInteger("streak", 0);
            if (today == last + 1) streak++;
            else if (today != last) streak = 1;
            p.putInteger("streak", Math.max(streak, 1));
            p.putLong("lastDay", today);
        }
        p.flush();
        return best;
    }
}
