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
package ilib.gui.misc;

import ilib.client.util.RenderUtils;
import org.lwjgl.input.Mouse;
import roj.math.Mat4f;
import roj.math.Vec3f;

public class TrackballMC {
    // Variables
    public int button, radius;
    public boolean isDragging = true;

    public Vec3f dragStart;
    public Mat4f lastTransform = new Mat4f();

    public TrackballMC(int button, int radius) {
        this.button = button;
        this.radius = radius;
    }

    public void update(int mouseX, int mouseY) {
        float mx = (float) mouseX / (float) radius;
        float my = (float) mouseY / (float) radius;

        boolean pressed = Mouse.isButtonDown(button);
        if (!isDragging && pressed) {
            isDragging = true;
            startDrag(mx, my);
        } else if (isDragging && !pressed) {
            isDragging = false;
            endDrag(mx, my);
        }

        applyTransform(mx, my, isDragging);
    }

    public void setTransform(Mat4f transform) {
        lastTransform = transform;
    }

    public static Vec3f spherePoint(float x, float y) {
        Vec3f result = new Vec3f(x, y, 0);

        float sqrZ = 1 - result.len2();
        if (sqrZ > 0)
            result.z = (float) Math.sqrt(sqrZ);
        else
            result.normalize();

        return result;
    }

    public Mat4f getTransform(float mouseX, float mouseY) {
        if (dragStart == null)
            return lastTransform;

        Vec3f current = spherePoint(mouseX, mouseY);

        float dot = dragStart.dot(current);

        if (Math.abs(dot - 1) < 0.0001)
            return lastTransform;

        if(current.cross(dragStart).len2() == 0) {
            return lastTransform;
        }
        current.normalize();

        float angle = (float) (2 * Math.acos(dot));

        return new Mat4f().rotate(current, angle).mul(lastTransform);
    }

    public void applyTransform(float mouseX, float mouseY, boolean isDragging) {
        RenderUtils.loadMatrix(isDragging ? getTransform(mouseX, mouseY) : lastTransform);
    }

    public void startDrag(float mouseX, float mouseY) {
        dragStart = spherePoint(mouseX, mouseY);
    }

    public void endDrag(float mouseX, float mouseY) {
        lastTransform = getTransform(mouseX, mouseY);
    }
}
