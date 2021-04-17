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
 * Filename: EnumFacingPicker.java
 */
package ilib.gui.misc;

import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Mouse;
import roj.math.Vec3d;

public class SidePicker {
    public static class Hit {
        public final EnumFacing side;
        public final Vec3d coord;

        public Hit(EnumFacing side, Vec3d coord) {
            this.side = side;
            this.coord = coord;
        }
    }

    private final double minX, minY, minZ;
    private final double maxX, maxY, maxZ;

    public SidePicker(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public SidePicker(double halfSize) {
        minX = minY = minZ = -halfSize;
        maxX = maxY = maxZ = +halfSize;
    }

    private static Vec3d getMouseVector(float z) {
        return ProjectUtil.unproject(Mouse.getX(), Mouse.getY(), z);
    }

    private Vec3d calcX(Vec3d near, Vec3d diff, double x) {
        double p = (x - near.x) / diff.x;

        double y = near.y + diff.y * p;
        double z = near.z + diff.z * p;

        if (minY <= y && y <= maxY && minZ <= z && z <= maxZ) {
            return new Vec3d(x, y, z);
        }

        return null;
    }

    private Vec3d calcY(Vec3d near, Vec3d diff, double y) {
        double p = (y - near.y) / diff.y;

        double x = near.x + diff.x * p;
        double z = near.z + diff.z * p;

        if (minX <= x && x <= maxX && minZ <= z && z <= maxZ) {
            return new Vec3d(x, y, z);
        }

        return null;
    }

    private Vec3d calcZ(Vec3d near, Vec3d diff, double z) {
        double p = (z - near.z) / diff.z;

        double x = near.x + diff.x * p;
        double y = near.y + diff.y * p;

        if (minX <= x && x <= maxX && minY <= y && y <= maxY) {
            return new Vec3d(x, y, z);
        }

        return null;
    }

    public Vec3d[] getHits() {
        ProjectUtil.updateMatrices();
        Vec3d near = getMouseVector(0);
        Vec3d diff = getMouseVector(1).sub(near);

        return new Vec3d[] {
                calcY(near, diff, minY),
                calcY(near, diff, maxY),

                calcZ(near, diff, minZ),
                calcZ(near, diff, maxZ),

                calcX(near, diff, minX),
                calcX(near, diff, maxX)
        };
    }

    public Hit getNearestHit() {
        ProjectUtil.updateMatrices();
        Vec3d near = getMouseVector(0);
        Vec3d diff = getMouseVector(1).sub(near);

        minDist = Double.MAX_VALUE;
        face = -1;
        this.near = near;

        compute0(calcY(near, diff, minY), 0);
        compute0(calcY(near, diff, maxY), 1);

        compute0(calcZ(near, diff, minZ), 2);
        compute0(calcZ(near, diff, maxZ), 3);

        compute0(calcX(near, diff, minX), 4);
        compute0(calcX(near, diff, maxX), 5);

        this.near = null;

        return face == -1 ? null : new Hit(EnumFacing.VALUES[face], vec);
    }


    int face;
    Vec3d near, vec;
    double minDist;

    private void compute0(Vec3d vec, int i) {
        if(vec == null) return;
        // yeah, I know there are two entries maxBlock, but... meh
        double dist = vec.sub(near).len2();
        if (dist < minDist) {
            minDist = dist;
            face = i;
            this.vec = vec.add(near);
        }
    }
}
