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
package roj.math;

/**
 * An axis-aligned (two-dimensional) {@code int}-based rectangle.
 *
 * @author Maximilian Luz
 */
public class Rect2i {
    public int xmin, ymin, xmax, ymax;

    public Rect2i() {}

    public Rect2i(int xmin, int ymin, int xmax, int ymax) {
        this.xmin = xmin;
        this.ymin = ymin;
        this.xmax = xmax;
        this.ymax = ymax;
    }

    public Rect2i(Vec2i min, Vec2i max) {
        this.xmin = min.x;
        this.ymin = min.y;
        this.xmax = max.x;
        this.ymax = max.y;
    }

    public Rect2i(Rect2i other) {
        set(other);
    }

    public Rect2i set(int xmin, int ymin, int xmax, int ymax) {
        this.xmin = xmin;
        this.ymin = ymin;
        this.xmax = xmax;
        this.ymax = ymax;
        return this;
    }

    public Rect2i set(Vec2i min, Vec2i max) {
        this.xmin = min.x;
        this.ymin = min.y;
        this.xmax = max.x;
        this.ymax = max.y;
        return this;
    }

    public Rect2i set(Rect2i other) {
        this.xmin = other.xmin;
        this.ymin = other.ymin;
        this.xmax = other.xmax;
        this.ymax = other.ymax;
        return this;
    }

    public Vec2i min() {
        return new Vec2i(xmin, ymin);
    }

    public Vec2i max() {
        return new Vec2i(xmax, ymax);
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Rect2i)) return false;

        Rect2i other = (Rect2i) obj;
        return this.xmin == other.xmin
                && this.ymin == other.ymin
                && this.xmax == other.xmax
                && this.ymax == other.ymax;
    }

    @Override
    public int hashCode() {
        int i = xmin;
        i = 31 * i + ymin;
        i = 31 * i + xmax;
        i = 31 * i + ymax;
        return i;
    }

    @Override
    public String toString() {
        return this.getClass() + " {" + xmin + ", " + ymin + ", " + xmax + ", " + ymax + "}";
    }

    public Rect2i fix() {
        int tmp;

        if (xmin > xmax) {
            tmp = xmax;
            xmax = xmin;
            xmin = tmp;
        }
        if (ymin > ymax) {
            tmp = ymax;
            ymax = ymin;
            ymin = tmp;
        }

        return this;
    }

    public int length() {
        return (xmax - xmin) * (ymax - ymin);
    }

    public Rect2i expandIf(Vec2i... points) {
        for (Vec2i v : points) {
            if (xmin > v.x) xmin = v.x;
            if (xmax < v.x) xmax = v.x;

            if (ymin > v.y) ymin = v.y;
            if (ymax < v.y) ymax = v.y;
        }

        return this;
    }

    public boolean contains(int x, int y) {
        return xmin <= x && x <= xmax && ymin <= y && y <= ymax;
    }

    public boolean contains(Vec2i p) {
        return xmin <= p.x && p.x <= xmax && ymin <= p.x && p.x <= ymax;
    }

    public boolean contains(Rect2i other) {
        return xmax >= other.xmax && xmin <= other.xmin && ymax >= other.ymax && ymin <= other.ymin;
    }

    public Rect2i grow(int i) {
        xmin -= i;
        ymin -= i;
        xmax += i;
        ymax += i;
        return this;
    }

    public void expandIf(int x, int y, int z) {
        if (x < xmin) {
            xmin = x;
        } else if (x > xmax) {
            xmax = x;
        }
        if (y < ymin) {
            ymin = y;
        } else if (y > ymax) {
            ymax = y;
        }
    }

    public int size() {
        return (xmax - xmin + 1) * (ymax - ymin + 1);
    }

    public Rect2i copy() {
        return new Rect2i(this);
    }
}
