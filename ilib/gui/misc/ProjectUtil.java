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
 * Filename: ProjectUtil.java
 */
package ilib.gui.misc;

import net.minecraft.client.renderer.GLAllocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import roj.math.Vec3d;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class ProjectUtil {
    private static final IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);

    private static final FloatBuffer modelview = GLAllocation.createDirectFloatBuffer(16);

    private static final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);

    private static final FloatBuffer coords = GLAllocation.createDirectFloatBuffer(3);

    public static void updateMatrices() {
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
    }

    public static Vec3d unproject(float winX, float winY, float winZ) {
        Project.gluUnProject(winX, winY, winZ, modelview, projection, viewport, coords);

        float objectX = coords.get(0);
        float objectY = coords.get(1);
        float objectZ = coords.get(2);

        return new Vec3d(objectX, objectY, objectZ);
    }

    public static Vec3d project(float objX, float objY, float objZ) {
        Project.gluProject(objX, objY, objZ, modelview, projection, viewport, coords);

        float winX = coords.get(0);
        float winY = coords.get(1);
        float winZ = coords.get(2);

        return new Vec3d(winX, winY, winZ);
    }
}