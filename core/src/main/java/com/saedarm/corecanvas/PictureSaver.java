package com.saedarm.corecanvas;

import com.badlogic.gdx.files.FileHandle;

/** Copies a finished painting somewhere the player can find it (the phone's Gallery on Android). */
public interface PictureSaver {
    /** Returns a short description of where it went, or null if it couldn't be saved. */
    String save(FileHandle png, String displayName);
}
