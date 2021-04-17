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
package ilib.client.renderer.mirror.portal;

import javax.vecmath.Matrix3f;

//Simple Quaternion implementation. Just for what I need
public class Quaternion {
    public float x, y, z, w;

    public Quaternion() {
    }

    public Quaternion(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public Quaternion set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    public Quaternion conjugate() {
        Quaternion q = new Quaternion();
        q.x = -this.x;
        q.y = -this.y;
        q.z = -this.z;
        q.w = this.w;
        return q;
    }

    public Quaternion mul(Quaternion right) {
        Quaternion q = new Quaternion();
        q.set(this.x * right.w + this.w * right.x + this.y * right.z - this.z * right.y,
                this.y * right.w + this.w * right.y + this.z * right.x - this.x * right.z,
                this.z * right.w + this.w * right.z + this.x * right.y - this.y * right.x,
                this.w * right.w - this.x * right.x - this.y * right.y - this.z * right.z
        );
        return q;
    }

    public Quaternion setFromMatrix(Matrix3f m) {
        float mag;
        float tr = m.m00 + m.m11 + m.m22;
        if (tr >= 0.0) {
            mag = (float) Math.sqrt(tr + 1.0);
            w = mag * 0.5f;
            mag = 0.5f / mag;
            x = (m.m21 - m.m12) * mag;
            y = (m.m02 - m.m20) * mag;
            z = (m.m10 - m.m01) * mag;
        } else {
            float max = Math.max(Math.max(m.m00, m.m11), m.m22);
            if (max == m.m00) {
                mag = (float) Math.sqrt(m.m00 - (m.m11 + m.m22) + 1.0);
                x = mag * 0.5f;
                mag = 0.5f / mag;
                y = (m.m01 + m.m10) * mag;
                z = (m.m20 + m.m02) * mag;
                w = (m.m21 - m.m12) * mag;
            } else if (max == m.m11) {
                mag = (float) Math.sqrt(m.m11 - (m.m22 + m.m00) + 1.0);
                y = mag * 0.5f;
                mag = 0.5f / mag;
                z = (m.m12 + m.m21) * mag;
                x = (m.m01 + m.m10) * mag;
                w = (m.m02 - m.m20) * mag;
            } else {
                mag = (float) Math.sqrt(m.m22 - (m.m00 + m.m11) + 1.0);
                z = mag * 0.5f;
                mag = 0.5f / mag;
                x = (m.m20 + m.m02) * mag;
                y = (m.m12 + m.m21) * mag;
                w = (m.m10 - m.m01) * mag;
            }
        }
        return this;
    }
}
