package com.saedarm.corecanvas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;

/**
 * Reads how the board is tilted, in degrees, relative to the screen as the player sees it.
 * +x means the right edge of the screen is lower (brush goes right), +y means the top edge is lower (brush goes up).
 * On desktop the mouse stands in for the board.
 */
public class Tilt {
    public float smX, smY;
    public float neutralX, neutralY;
    public float maxDeg = 11f;

    private final boolean accel;
    private final float[] hx = new float[45], hy = new float[45]; // 1.5 s of history at 30 Hz
    private int head, filled;
    private float sampleAcc;
    private boolean first = true;

    public Tilt() {
        accel = Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer);
    }

    public boolean hasSensor() { return accel; }

    public void update(float dt) {
        float rawX, rawY;
        if (accel) {
            float ax = Gdx.input.getAccelerometerX(), ay = Gdx.input.getAccelerometerY(), az = Gdx.input.getAccelerometerZ();
            // Readings come in the device's native orientation; turn them into screen directions.
            float sx, sy;
            switch (Gdx.input.getRotation()) {
                case 90: sx = ay; sy = -ax; break;
                case 180: sx = ax; sy = ay; break;
                case 270: sx = -ay; sy = ax; break;
                default: sx = -ax; sy = -ay;
            }
            rawX = MathUtils.atan2(sx, (float) Math.sqrt(sy * sy + az * az)) * MathUtils.radDeg;
            rawY = MathUtils.atan2(sy, (float) Math.sqrt(sx * sx + az * az)) * MathUtils.radDeg;
        } else {
            float mx = Gdx.input.getX() / (float) Gdx.graphics.getWidth() * 2f - 1f;
            float my = 1f - Gdx.input.getY() / (float) Gdx.graphics.getHeight() * 2f;
            rawX = mx * maxDeg * 1.15f;
            rawY = my * maxDeg * 1.15f;
        }
        if (first) { smX = rawX; smY = rawY; first = false; }
        float k = 1f - (float) Math.exp(-dt * 14f);
        smX += (rawX - smX) * k;
        smY += (rawY - smY) * k;

        sampleAcc += dt;
        while (sampleAcc >= 1f / 30f) {
            sampleAcc -= 1f / 30f;
            hx[head] = smX;
            hy[head] = smY;
            head = (head + 1) % hx.length;
            if (filled < hx.length) filled++;
        }
    }

    /** Tilt relative to the calibrated level, scaled so ±1 is the edge of the play area. */
    public float nx() { return MathUtils.clamp((smX - neutralX) / maxDeg, -1f, 1f); }
    public float ny() { return MathUtils.clamp((smY - neutralY) / maxDeg, -1f, 1f); }

    /** Degrees away from the calibrated level. */
    public float deviation() { return (float) Math.hypot(smX - neutralX, smY - neutralY); }

    /** Degrees away from flat (phone lying face up), ignoring calibration. */
    public float fromFlat() { return (float) Math.hypot(smX, smY); }

    /** Largest swing on either axis over the recent history, in degrees. 99 until there is enough history. */
    public float wobble() {
        if (filled < 10) return 99f;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < filled; i++) {
            int j = (head - 1 - i + hx.length) % hx.length;
            minX = Math.min(minX, hx[j]); maxX = Math.max(maxX, hx[j]);
            minY = Math.min(minY, hy[j]); maxY = Math.max(maxY, hy[j]);
        }
        return Math.max(maxX - minX, maxY - minY);
    }

    /** Sets "level" to wherever the board has been sitting for the last moment. */
    public void calibrate() {
        if (filled == 0) { neutralX = smX; neutralY = smY; return; }
        float sx = 0, sy = 0;
        for (int i = 0; i < filled; i++) {
            int j = (head - 1 - i + hx.length) % hx.length;
            sx += hx[j];
            sy += hy[j];
        }
        neutralX = sx / filled;
        neutralY = sy / filled;
    }

    public void resetNeutral() { neutralX = neutralY = 0f; }

    public void clearHistory() { filled = 0; }
}
