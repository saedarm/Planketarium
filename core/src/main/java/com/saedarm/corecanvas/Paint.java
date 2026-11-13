package com.saedarm.corecanvas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;

/** The painting. Everything the brush leaves behind is stamped into this frame buffer and kept for the whole session. */
public class Paint {
    public static final Color BG = new Color(0.075f, 0.085f, 0.135f, 1f);

    public final int w, h;
    private final FrameBuffer fbo;
    private final SpriteBatch batch;
    private final Texture brush;
    private final Matrix4 proj = new Matrix4();
    private final Color tmp = new Color(Color.WHITE);

    public Paint(int w, int h, SpriteBatch batch, Texture brush) {
        this.w = w;
        this.h = h;
        this.batch = batch;
        this.brush = brush;
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        proj.setToOrtho2D(0, 0, w, h);
        clear();
    }

    public void clear() {
        fbo.begin();
        Gdx.gl.glClearColor(BG.r, BG.g, BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        fbo.end();
    }

    public void begin() {
        fbo.begin();
        batch.setProjectionMatrix(proj);
        // Keep the canvas fully opaque no matter how much paint piles up.
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
    }

    public void end() {
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(Color.WHITE);
        fbo.end();
    }

    public void stamp(float x, float y, float size, Color c, float alpha) {
        batch.setColor(tmp.set(c.r, c.g, c.b, alpha));
        batch.draw(brush, x - size / 2f, y - size / 2f, size, size);
    }

    /** A line of overlapping stamps from one point to another. */
    public void stroke(float x0, float y0, float x1, float y1, float size, Color c, float alpha) {
        float d = (float) Math.hypot(x1 - x0, y1 - y0);
        float spacing = Math.max(2f, size * 0.22f);
        int n = Math.max(1, (int) Math.ceil(d / spacing));
        for (int i = 1; i <= n; i++) {
            float f = i / (float) n;
            stamp(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, size, c, alpha);
        }
    }

    public Texture texture() { return fbo.getColorBufferTexture(); }

    public void dispose() { fbo.dispose(); }
}
