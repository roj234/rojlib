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
package ilib.animator.keyframe;

import ilib.animator.interpolate.TFRegistry;
import ilib.animator.interpolate.TimeFunc;
import roj.math.Mat4x3f;
import roj.math.MathUtils;
import roj.text.StringPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/27 21:58
 */
public final class Keyframe extends Mat4x3f {
    public int tick;
    public TimeFunc fn;
    public byte flag;

    public Keyframe() {
        m00 = m11 = m22 = 1;
        fn = TFRegistry.LINEAR;
    }

    public Keyframe(Keyframe frame) {
        set(frame);
        tick = frame.tick;
        flag = frame.flag;
        fn = frame.fn;
    }

    public Mat4x3f interpolate(Keyframe next, double percent, Mat4x3f store) {
        return Mat4x3f.mix(next, this, (float) fn.interpolate(percent, flag), store);
    }

    /*
一个三维场景中的各个模型一般需要各自建模，再通过坐标变换放到一个统一的世界空间的指定位置上。 这个过程在 3D 图形学中称作“世界变换” 。 世界变换有三种，平移、旋转和缩放 (实际还有不常用的扭曲和镜像，它们不是affine变换)。 这三种变换按各种顺序执行，结果是不同的。 可是实际的应用中一般按照 缩放 -> 旋转 -> 平移的顺序进行。 这样做的原因是可以获得最符合常理的变换结果。

比方说，通过世界变换希望获得的结果可能是：
将一个放在原点的物体（比方说可乐罐）移动到（30，50），让它自身倾斜 45 度，再放大 2 倍。

而不希望的结果是：
和本地坐标轴成角度的缩放（会导致扭曲，像踩扁的可乐罐）。
绕自己几何中心以外位置的原点的旋转 （地球公转式） 和缩放。

而颠倒了上述变换顺序就会得到这样不自然的结果。
具体的说：
当缩放在旋转之后进行时，会发生现象1。
当缩放和旋转在平移之后进行时会发生现象2。

这时因为：
在物体刚刚放入世界坐标系的时候使用的是本地坐标，也就是本地和全局坐标系的原点和坐标轴都是重合的（当然两者分别使用了左右手坐标系时除外 - 那是BUG），此时所有物体都“把世界坐标系当做自己的本地坐标系”。
而经过了坐标变换之后：
缩放变换不改变坐标轴的走向，也不改变原点的位置，所以两个坐标系仍然重合。
旋转变换改变坐标轴的走向，但不改变原点的位置，所以两个坐标系坐标轴不再处于相同走向。
平移变换不改变坐标轴走向，但改变原点位置，两个坐标系原点不再重合。
     */
    public void applyTransform(byte type, float x, float y, float z) {
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

    public void toByteArray(ByteWriter w, StringPool pool) {
        w.writeVarInt(tick, false).writeByte(flag);

        w.writeFloat(m00)
                .writeFloat(m01)
                .writeFloat(m02)
                .writeFloat(m03)
                .writeFloat(m10)
                .writeFloat(m11)
                .writeFloat(m12)
                .writeFloat(m13)
                .writeFloat(m20)
                .writeFloat(m21)
                .writeFloat(m22)
                .writeFloat(m23);

        fn.toByteArray(w, pool);
    }

    public static Keyframe fromByteArray(ByteReader r, StringPool pool) {
        Keyframe kf = new Keyframe();
        kf.tick = r.readVarInt(false);
        kf.flag = r.readByte();

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

        kf.fn = TimeFunc.fromByteArray(r, pool);
        return kf;
    }
}
