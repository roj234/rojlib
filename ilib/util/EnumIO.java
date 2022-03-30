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
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: EnumInputOutputMode.java
 */
package ilib.util;

import java.awt.*;

public enum EnumIO {
    DEFAULT(0),
    INPUT(0x960066FF),
    OUTPUT(0x96FF6600),
    //OUTPUT_A(0), OUTPUT_B(0), OUTPUT_C(0),
    ALL(0x96009600),
    @Deprecated
    DISABLED(0x96b4b4b4);

    public static final int TYPE_ITEM = 0;
    public static final int TYPE_ENERGY = 1;
    public static final int TYPE_FLUID = 2;

    public static final EnumIO[] VALUES = values();

    private final Color color;

    EnumIO(int c) {
        color = new Color(c, true);
    }

    @Deprecated
    public enum Tube implements net.minecraft.util.IStringSerializable {
        normal, none;

        public static Tube byIOMode(EnumIO mode) {
            return mode == DISABLED ? none : normal;
        }

        public String getName() {
            return name();
        }
    }

    public boolean canOutput() {
        return this == OUTPUT || this == ALL;
    }

    public boolean canInput() {
        return this == INPUT || this == ALL;
    }

    public static EnumIO byId(int id) {
        return byId(id, DEFAULT);
    }

    public static EnumIO byId(int id, EnumIO def) {
        if (id > -1 && id < VALUES.length) {
            return VALUES[id];
        }
        return def;
    }

    public Color getColor() {
        return color;
    }
}
