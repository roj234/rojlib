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
package ilib.animator.interpolate;

import roj.collect.MyHashMap;

import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/27 22:23
 */
public class TFRegistry {
    // Config key
    public static final int CONFIG_EASE = 0;

    // Config value
    public static final int EASE_IN = 0, EASE_OUT = 1, EASE_IN_OUT = 2;

    static final MyHashMap<String, TimeFunc.Factory> registry = new MyHashMap<>();

    public static Set<String> keys() {
        return registry.keySet();
    }

    public static TimeFunc.Factory get(String name) {
        return registry.get(name);
    }

    public static void register(TimeFunc.Factory func) {
        registry.put(func.name(), func);
    }

    //interpolate.const=瞬间
    public static final TimeFunc CONST = new Const();
    //interpolate.linear=线性
    public static final TimeFunc LINEAR = new Linear();
    //interpolate.quad=二次方
    public static final TimeFunc X2_IN = new XnIn(2), X2_OUT = new XnOut(2), X2_IN_OUT = new XnHalf(1);
    //interpolate.cubic=三次方
    public static final TimeFunc X3_IN = new XnIn(3), X3_OUT = new XnOut(3), X3_IN_OUT = new XnHalf(2);


    /*interpolate.exp=指数
    interpolate.bezier=贝塞尔
    interpolate.back=回弹
    interpolate.elastic=弹性
    hermite 插值
    interpolate.bounce=弹跳*/
    /*HERMITE("hermite")
    {
        @Override
        public double interpolate(Keyframe a, Keyframe b, float x)
        {
            double v0 = a.prev.value;
            double v1 = a.value;
            double v2 = b.value;
            double v3 = b.next.value;

            return Interpolations.cubicHermite(v0, v1, v2, v3, x);
        }
    },
    BEZIER("bezier")
    {
        @Override
        public double interpolate(Keyframe a, Keyframe b, float x)
        {
            if (x <= 0) return a.value;
            if (x >= 1) return b.value;

            *//* Transform input to 0..1 *//*
            double w = b.tick - a.tick;
            double h = b.value - a.value;

            *//* In case if there is no slope whatsoever *//*
            if (h == 0) h = 0.00001;

            double x1 = a.rx / w;
            double y1 = a.ry / h;
            double x2 = (w - b.lx) / w;
            double y2 = (h + b.ly) / h;
            double e = 0.0005;

            e = h == 0 ? e : Math.max(Math.min(e, 1 / h * e), 0.00001);
            x1 = MathUtils.clamp(x1, 0, 1);
            x2 = MathUtils.clamp(x2, 0, 1);

            return Interpolations.bezier(0, y1, y2, 1, Interpolations.bezierX(x1, x2, x, e)) * h + a.value;
        }
    },*/

    public static void init() {
        register(LINEAR.factory());
    }
}
