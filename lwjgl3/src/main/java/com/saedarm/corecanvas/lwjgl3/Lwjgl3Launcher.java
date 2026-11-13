package com.saedarm.corecanvas.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.saedarm.corecanvas.CoreCanvas;
import com.saedarm.corecanvas.Play;

/** Desktop build for testing. The mouse stands in for the board. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        CoreCanvas game = new CoreCanvas((png, name) -> {
            FileHandle out = Gdx.files.external("Pictures/Core Canvas/" + name);
            png.copyTo(out);
            return "~/Pictures/Core Canvas";
        });
        // Testing: -Dauto=true plays by itself, -Dgame=MAZE and -Dlength=QUICK pick what it plays,
        // -Dspeed=4 runs time faster, -Dshots=/dir saves screenshots, -Dpause=20 pauses 20 s in.
        game.autopilot = Boolean.getBoolean("auto");
        game.speed = Float.parseFloat(System.getProperty("speed", "1"));
        game.shotsDir = System.getProperty("shots");
        game.autoKind = Play.Kind.valueOf(System.getProperty("game", "WORKOUT"));
        game.autoLength = Play.Length.valueOf(System.getProperty("length", "STANDARD"));
        game.pauseTestAt = Float.parseFloat(System.getProperty("pause", "-1"));

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Core Canvas");
        config.setWindowedMode(1560, 720); // the S23's 19.5:9 shape
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(game, config);
    }
}
