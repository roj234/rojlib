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
package ilib.anim;

import roj.math.Mat4x3f;
import roj.math.MathUtils;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since  2021/5/27 21:58
 */
public final class Keyframe extends Mat4x3f {
    public int time;

    public Keyframe() {
        m00 = m11 = m22 = 1;
    }

    public Keyframe(int time) {
        this();
        this.time = time;
    }

    public Keyframe(Keyframe frame) {
        set(frame);
        time = frame.time;
    }

    public void set(byte type, float x, float y, float z) {
        float tx = m03, ty = m13, tz = m23;
        float rx = (float) Math.asin(m21), ry = (float) Math.asin(m02), rz = (float) Math.asin(m10);
        float cx = MathUtils.cos(rx), cy = MathUtils.cos(ry), cz = MathUtils.cos(rz);
        float sx = m00 / (cy * cz),
                sy = m11 / (cx * cz),
                sz = m22 / (cx * cy);

        switch (type) {
            case 0:
                tx = x;
                ty = y;
                tz = z;
                break;
            case 1:
                rx = x;
                ry = y;
                rz = z;
                break;
            case 2:
                if(x == 0 || y == 0 || z == 0) {
                    throw new IllegalArgumentException("scale(0)");
                }

                sx = x;
                sy = y;
                sz = z;
                break;
        }

        m00 = m11 = m22 = 1;
        m01 = m02 = m10 = m12 = m20 = m21 = 0;

        scale(sx, sy, sz).rotateX(rx).rotateY(ry).rotateZ(rz).translateAbs(tx, ty, tz);
    }

    public void toByteArray(ByteList w) {
        w.putVarInt(time, false)
         .putFloat(m00)
         .putFloat(m01)
         .putFloat(m02)
         .putFloat(m03)
         .putFloat(m10)
         .putFloat(m11)
         .putFloat(m12)
         .putFloat(m13)
         .putFloat(m20)
         .putFloat(m21)
         .putFloat(m22)
         .putFloat(m23);
    }

    public static Keyframe fromByteArray(ByteList r) {
        Keyframe kf = new Keyframe();
        kf.time = r.readVarInt(false);
        kf.m00 = r.readFloat();
        kf.m01 = r.readFloat();
        kf.m02 = r.readFloat();
        kf.m03 = r.readFloat();
        kf.m10 = r.readFloat();
        kf.m11 = r.readFloat();
        kf.m12 = r.readFloat();
        kf.m13 = r.readFloat();
        kf.m20 = r.readFloat();
        kf.m21 = r.readFloat();
        kf.m22 = r.readFloat();
        kf.m23 = r.readFloat();
        return kf;
    }
}
