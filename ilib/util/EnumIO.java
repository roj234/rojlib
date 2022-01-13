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

import roj.util.Color;

public enum EnumIO {
    DEFAULT(255, 255, 255, 0),
    INPUT(0, 102, 255, 150),
    OUTPUT(255, 102, 0, 150),
    ALL(0, 150, 0, 150),
    DISABLED(180, 180, 180, 150);

    public static final byte DEFAULT_ID = 0;
    public static final byte INPUT_ID = 1;
    public static final byte OUTPUT_ID = 2;
    public static final byte ALL_ID = 3;
    public static final byte DISABLED_ID = 4;
    // Define of [getActualState FOUR_WAY patchClient] end;
    // Define of [ItemIO / EnergyIO / FluidIO] begin;
    public static final int TYPE_ITEM = 0;
    public static final int TYPE_ENERGY = 1;
    public static final int TYPE_FLUID = 2;

    // Color
    private final Color cachedColor;
    private final String lowerCase;

    EnumIO(int r, int g, int b, int a) {
        cachedColor = new Color(r, g, b, a);
        lowerCase = name().toLowerCase();
    }

    public enum Tube implements net.minecraft.util.IStringSerializable {
        normal, none;

        public static Tube byIOMode(EnumIO mode) {
            return mode == DISABLED ? none : normal;
        }

        public String getName() {
            return name();
        }
    }

    /**
     * The unlocalized name
     */
    public String getName() {
        return lowerCase;
    }

    /**
     * Used to get the name that should be displayed in GUI
     *
     * @return The display name
     */
    public String getDisplayName() {
        return TextHelper.translate("gui.mi.tabs.io." + lowerCase);
    }

    public Tube toTubeInfo() {
        return Tube.byIOMode(this);
    }

    /**
     * Used to get the correct mode from an integer
     *
     * @param id The value to convert
     * @return The mode for that value
     */
    public static EnumIO byId(int id) {
        if (id > -1 && id < values().length) {
            return values()[id];
        }
        return DEFAULT;
    }

    public static EnumIO byId(int id, EnumIO fallback) {
        if (id > -1 && id < values().length) {
            return values()[id];
        }
        return fallback;
    }

    /**
     * Used to get the integer value
     *
     * @return The value
     */
    public int getIndex() {
        return ordinal();
    }

    /**
     * Used to get the color to render on the Side Selector
     *
     * @return The color, with alpha attached
     */
    public Color getHighlightColor() {
        return cachedColor;
    }
}
