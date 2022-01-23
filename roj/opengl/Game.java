/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.opengl;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.glu.Project;
import roj.collect.Int2IntMap;
import roj.math.Mat4f;
import roj.math.MathUtils;
import roj.opengl.text.FontTex;
import roj.opengl.text.TextRenderer;
import roj.opengl.vertex.VertexBuilder;
import roj.reflect.TraceUtil;
import roj.text.TextUtil;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author Roj233
 * @since 2021/9/17 23:15
 */
public abstract class Game {
    static final float SQRT_2 = (float) Math.sqrt(2);

    protected int width, height;
    int bakWidth, bakHeight;

    public void create() throws LWJGLException, IOException {
        try {
            Display.setDisplayMode(new DisplayMode(width = 666, height = 666));
        } catch (LWJGLException ignored) {}
        Display.setResizable(true);
        Display.setTitle("3D游戏？就这？");

        try {
            BufferedImage img = ImageIO.read(Game.class.getClassLoader().getResourceAsStream("META-INF/testgame.png"));
            byte[] array = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
            ByteBuffer buf = ByteBuffer.allocate((array.length * 3) >> 2);
            for (int i = 0; i < array.length; i += 4) {
                buf.put(array, i, 3);
            }
            buf.rewind();
            // should use 16x16 + 32x32
            Display.setIcon(new ByteBuffer[]{buf, buf});
        } catch (Throwable ignored) {}

        try {
            Display.create(new PixelFormat().withDepthBits(24));
        } catch (LWJGLException e) {
            e.printStackTrace();

            Display.create();
        }

        Mouse.create();
        Keyboard.create();

        fps = 40;
        vertexBuilder = new VertexBuilder(262144);
        textRenderer = new TextRenderer(new FontTex("黑体-20"), TextRenderer.COLOR_CODE, vertexBuilder);
        init();
    }

    public TextRenderer  textRenderer;
    public VertexBuilder vertexBuilder;
    public double        x, y, z,
            yaw, pitch,
            scale = 1;
    public double motionX, motionY, motionZ;
    private int moveForward, moveLeft, moveUp;
    public boolean cameraMode;
    public double mouseSensitive = .4, moveFactor = .03;
    public int fps;

    private int   frameCounter;
    private long  lastFrameTime;
    private float avgFrameTime;

    private boolean grabbed;
    private int     baseX, baseY;

    private boolean fullscreen;
    protected final Int2IntMap inputs = new Int2IntMap();

    public void processMove() {
        if(moveForward != 0 || moveLeft != 0 || moveUp != 0) {
            double sy = MathUtils.sin(yaw);
            double cy = MathUtils.cos(yaw);
            double sp = MathUtils.sin(pitch);

            motionX += moveFactor * (moveLeft * cy - moveForward * sy);
            motionY += moveFactor * (moveUp + (cameraMode ? moveForward * sp : 0));
            motionZ += moveFactor * (moveForward * cy + moveLeft * sy);

            moveForward = moveLeft = moveUp = 0;
        }

        x += motionX;
        y += motionY;
        z += motionZ;

        motionX *= 0.75;
        motionY *= 0.75;
        motionZ *= 0.75;
    }

    public void processInput() {
        // keyboard
        for (Int2IntMap.Entry e : inputs.entrySet()) {
            switch (e.getKey()) {
                case 1:
                    if(e.v == 1 && !grabbed)
                        System.exit(0);
                    grabbed = false;
                    Mouse.setGrabbed(false);
                    break;
                case 17:
                    moveForward--;
                    break;
                case 30:
                    moveLeft--;
                    break;
                case 31:
                    moveForward++;
                    break;
                case 32:
                    moveLeft++;
                    break;
                case 12:
                    if (scale > 0.25) scale -= 0.02;
                    break;
                case 13:
                    if (scale < 2.5) scale += 0.02;
                    break;
                case 57:
                    moveUp++;
                    break;
                case 42:
                    moveUp--;
                    break;
                case 87:
                    if(e.v == 1) {
                        try {
                            fullscreen(!fullscreen);
                        } catch (LWJGLException exception) {
                            exception.printStackTrace();
                        }
                    }
                    break;
            }
            e.v++;
        }
        // mouse
        if(grabbed) {
            int dx = Mouse.getX() - baseX;
            int dy = baseY - Mouse.getY();
            if (dx != 0 || dy != 0) {
                Mouse.setCursorPosition(width / 2, height / 2 - 20);
                double y = yaw + mouseSensitive * dx * Math.PI / 180;
                if(y < -Math.PI)
                    y += 2 * Math.PI;
                else if(y > Math.PI) // > 180 => to -180
                    y -= 2 * Math.PI;
                else if(y != y)
                    y = 0;
                yaw = y;
                pitch = MathUtils.clamp(pitch + mouseSensitive * dy * Math.PI / 180, -Math.PI / 2, Math.PI / 2);
            }
        }
    }

    public void fullscreen(boolean full) throws LWJGLException {
        if(fullscreen != full) {
            fullscreen = full;
            if(full) {
                DisplayMode mode = Display.getDesktopDisplayMode();
                bakWidth = Display.getWidth();
                bakHeight = Display.getHeight();
                Display.setDisplayModeAndFullscreen(mode);
                width = mode.getWidth();
                height = mode.getHeight();
            } else {
                Display.setDisplayMode(new DisplayMode(width = bakWidth, height = bakHeight));
                Display.setFullscreen(false);
            }
            inputs.clear();
            Mouse.setCursorPosition(baseX = width / 2, baseY = height / 2 - 20);
        }
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public boolean isGrabbed() {
        return grabbed;
    }

    static float globalScale = 0.1f;
    static Mat4f m4 = new Mat4f();
    public void mainLoop() {
        long t = System.nanoTime();
        if(frameCounter > 100) {
            frameCounter = 0;
        }
        avgFrameTime = (avgFrameTime * frameCounter++ + (int) (t - lastFrameTime) / 1000000f) / frameCounter;
        lastFrameTime = t;

        if(Display.wasResized()) {
            width = Display.getWidth();
            height = Display.getHeight();
        }

        GL11.glPushMatrix();
        GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GL11.glViewport(0, 0, width, height);

        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();

        Project.gluPerspective(90, (float) width / height, 0.05F, 512 * SQRT_2);

        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glClearDepth(1.0);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthRange(0.05, 1);

        GL11.glEnable(GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL_GREATER, 0.1f);

        GL11.glEnable(GL_CULL_FACE);
        GL11.glCullFace(GL_FRONT);

        GL11.glRotated(Math.toDegrees(pitch), 1, 0, 0);
        GL11.glRotated(Math.toDegrees(yaw), 0, 1, 0);

        GL11.glPushMatrix();

        checkGLError("Render Sky Before");
        renderSky();
        checkGLError("Render Sky After");

        GL11.glPopMatrix();

        if(scale != 1) {
            GL11.glScaled(scale, scale, scale);
        }

        GL11.glTranslated(-x, -y, -z);

        GL11.glPushMatrix();
        textRenderer.scale = 1f / 100;
        checkGLError("Render 3D Before");
        render3D();
        checkGLError("Render 3D After");
        GL11.glPopMatrix();

        GL11.glDisable(GL_CULL_FACE);

        GL11.glPopMatrix();
        GL11.glPushMatrix();

        GL11.glClear(GL_DEPTH_BUFFER_BIT);

        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, 1000, 3000);

        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0, 42, -2000);
        GL11.glRotatef(180, 1, 0, 0);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        textRenderer.scale = 2f;

        checkGLError("Render 2D Before");
        renderUI();
        checkGLError("Render 2D After");

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();

        Display.update();

        boolean reqGrab = false;
        while (Mouse.next()) {
            if(Mouse.getEventButtonState()) {
                reqGrab = true;
                inputs.getEntryOrCreate(-Mouse.getEventButton() - 1, 1);
            } else {
                inputs.remove(-Mouse.getEventButton() - 1);
            }
        }
        if(reqGrab) {
            grabbed = true;
            Mouse.setGrabbed(true);
            Mouse.setCursorPosition(baseX = width / 2, baseY = height / 2 - 20);
        }
        while (Keyboard.next()) {
            if(Keyboard.getEventKeyState()) {
                inputs.getEntryOrCreate(Keyboard.getEventKey(), 1);
            } else {
                inputs.remove(Keyboard.getEventKey());
            }
        }

        processInput();
        processMove();

        if(fps >= 0)
            Display.sync(fps);
    }

    private final ArrayList<String> glErrors = new ArrayList<>();
    private int glErrorTimer;
    protected void checkGLError(String msg) {
        int id = GL11.glGetError();
        if (id != 0) {
            StackTraceElement[] es = TraceUtil.getTraces(new Throwable());
            String name = GLU.gluErrorString(id);
            String err = msg + ": " + name + ": " + es[es.length - 2];
            if(!glErrors.contains(err)) {
                glErrors.add(err);
            }
            System.out.println("########## GL ERROR ##########");
            System.out.println("@" + msg);
            System.out.println(id + ": " + name);
        }
    }

    protected abstract void init();

    protected void renderSky() {}

    protected void renderUI() {
        textRenderer.renderStringWithShadow("FPS: " + TextUtil.toFixed(1000 / avgFrameTime, 1) + '/' + fps, 4, 0);
        textRenderer.renderStringWithShadow("XYZ " + TextUtil.toFixed(x, 3) + ' ' + TextUtil.toFixed(y, 3) + ' ' + TextUtil.toFixed(z, 3), 4, -24);
        if(!glErrors.isEmpty()) {
            textRenderer.renderStringWithShadow("OpenGL Errors: ", 4, -48);
            int off = -64;
            for (int i = glErrors.size() - 1; i >= 0; i--) {
                textRenderer.renderStringWithShadow(glErrors.get(i), 4, off);
                off -= 16;
            }
            if(glErrorTimer++ == 60 || glErrors.size() > 50)
                glErrors.remove(0);
        } else {
            glErrorTimer = 0;
            textRenderer.renderStringWithShadow("Powered by Roj234", 4, -48);
        }
    }

    protected abstract void render3D();
}
