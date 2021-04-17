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
package ilib.util;

import net.minecraft.util.math.Vec3d;

/**
 * 然而自从FMD支持AT起，它唯一的命运就只是测试DirectAccessor了...
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/22 19:45
 */
//!!AT [ "net.minecraft.util.math.Vec3d", ["field_72450_a", "field_72448_b", "field_72449_c"]]
public final class MutableVec {
    public final Vec3d vec = new Vec3d(0, 0, 0);

    public Vec3d getVector() {
        return vec;
    }

    public MutableVec set(double x, double y, double z) {
        Reflection.HELPER.setVecX(vec, x);
        Reflection.HELPER.setVecY(vec, y);
        Reflection.HELPER.setVecZ(vec, z);
        return this;
    }
}
