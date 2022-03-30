/*
 * This file is a part of MI
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
/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: TrackballMC.java
 */
package ilib.gui.util;

import ilib.client.RenderUtils;
import org.lwjgl.input.Mouse;
import roj.math.Mat4f;
import roj.math.Vec3f;

/**
 * @author solo6975
 * @since 2022/4/2 17:08
 */
public final class TrackballMC {
    private final int button;
    private final float radius;
    private float sensitive;

    private boolean dragging;

    private Vec3f begin;
    private Mat4f beginMat, mat = new Mat4f();

    public TrackballMC(int button, float radius, float sensitive) {
        this.button = button;
        this.radius = radius;
        this.sensitive = sensitive;
    }

    public void update(int mouseX, int mouseY) {
        float mx = mouseX / radius * sensitive;
        float my = mouseY / radius * sensitive;

        boolean pressed = Mouse.isButtonDown(button);
        if (!dragging) {
            if (pressed) {
                dragging = true;
                startDrag(mx, my);
            }
        } else if (!pressed) {
            dragging = false;
            endDrag(mx, my);
        }

        applyTransform(mx, my);
    }

    public void followMouse(int mouseX, int mouseY) {
        begin = spherePoint(0.5f, 0.5f);

        float mx = mouseX / radius * sensitive;
        float my = mouseY / radius * sensitive;
        mat.makeIdentity();
        mat = getTransform(mx, my);
    }

    public void setTransform(Mat4f transform) {
        mat = transform;
    }

    private static Vec3f spherePoint(float x, float y) {
        Vec3f result = new Vec3f(x, y, 0);

        float sqrZ = 1 - result.len2();
        if (sqrZ > 0) result.z = (float) Math.sqrt(sqrZ);
        else result.normalize();

        return result;
    }

    public Mat4f getTransform(float mouseX, float mouseY) {
        if (begin == null) return mat;

        Vec3f current = spherePoint(mouseX, mouseY);

        float dot = begin.dot(current);
        if (Math.abs(dot - 1) < 1e-4) return mat;

        if(current.cross(begin).len2() == 0) {
            return mat;
        }

        current.normalize();
        float angle = (float) (2 * Math.acos(dot));

        return mat.set(beginMat).rotate(current, angle);
    }

    public void applyTransform(float mouseX, float mouseY) {
        RenderUtils.loadMatrix(getTransform(mouseX, mouseY));
    }

    public void startDrag(float mouseX, float mouseY) {
        begin = spherePoint(mouseX, mouseY);
        beginMat = new Mat4f(mat);
    }

    public void endDrag(float mouseX, float mouseY) {
        mat = getTransform(mouseX, mouseY);
        begin = null;
    }

    public float getSensitive() {
        return sensitive;
    }

    public void setSensitive(float sensitive) {
        this.sensitive = sensitive;
    }
}
