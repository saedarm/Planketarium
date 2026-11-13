package com.saedarm.corecanvas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

/** Sounds, the music loop and phone vibration. All audio comes from tools/make_assets.py. */
public class Sfx {
    private static final float[] SCALE = {1f, 1.125f, 1.25f, 1.5f, 1.6667f, 2f};

    public boolean on = true;
    private final Sound tick, ding, bloom, star, beep, go, move, lap, reverse, pause, resume, done, select, crash, engine;
    private final Music music;

    public Sfx() {
        tick = load("tick"); ding = load("ding"); bloom = load("bloom"); star = load("star");
        beep = load("beep"); go = load("go"); move = load("move"); lap = load("lap");
        reverse = load("reverse"); pause = load("pause"); resume = load("resume"); done = load("done");
        select = load("select"); crash = load("crash"); engine = load("engine");
        music = Gdx.audio.newMusic(Gdx.files.internal("audio/music.ogg"));
        music.setLooping(true);
        music.setVolume(0.3f);
    }

    private static Sound load(String name) { return Gdx.audio.newSound(Gdx.files.internal("audio/" + name + ".wav")); }

    private void play(Sound s, float vol, float pitch) { if (on) s.play(vol, pitch, 0f); }

    public void setOn(boolean on) {
        this.on = on;
        if (on) music.play(); else music.pause();
    }

    public void startMusic() { if (on && !music.isPlaying()) music.play(); }

    public void tick() { play(tick, 0.5f, 1f); }
    /** Rings up the scale with each rep, so a good streak sounds like a little tune. */
    public void ding(int rep) { play(ding, 0.7f, SCALE[(rep - 1) % SCALE.length] * ((rep - 1) / SCALE.length % 2 == 0 ? 0.75f : 1f)); }
    public void bloom(float pitch) { play(bloom, 0.45f, Math.min(pitch, 2f)); }
    public void star(int n) { play(star, 0.65f, SCALE[(n - 1) % SCALE.length] * 0.8f); }
    public void beep() { play(beep, 0.7f, 1f); }
    public void go() { play(go, 0.8f, 1f); }
    public void move() { play(move, 0.75f, 1f); }
    public void lap() { play(lap, 0.75f, 1f); }
    public void reverse() { play(reverse, 0.7f, 1f); }
    public void pause() { play(pause, 0.7f, 1f); }
    public void resume() { play(resume, 0.7f, 1f); }
    public void done() { play(done, 0.85f, 1f); }
    public void select() { play(select, 0.6f, 1f); }

    public void crash() { play(crash, 0.8f, 1f); }

    /** Starts the looping engine sound. Returns an id for enginePitch/engineStop, or -1. */
    public long engineStart() { return on ? engine.loop(0.35f, 0.6f, 0f) : -1; }
    public void enginePitch(long id, float pitch) { if (id >= 0) engine.setPitch(id, Math.max(0.5f, Math.min(2f, pitch))); }
    public void engineStop(long id) { if (id >= 0) engine.stop(id); }

    public void buzz(int ms) { Gdx.input.vibrate(ms); }
}
