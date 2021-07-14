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
package roj.config.data;

import roj.math.MathUtils;
import roj.text.ACalendar;

import javax.annotation.Nonnull;

/**
 * Type: YAML Date
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/7/7 0:43
 */
public final class CYamlDate extends CEntry {
    public long value;

    public CYamlDate(long value) {
        this.value = value;
    }

    /**
     * 2020-(0)1-10
     */
    public static CYamlDate valueOf(String val) {
        int[] buf = new int[3];
        int i = 0, off = 0, k = 0;
        while (i < val.length()) {
            if(val.charAt(i++) == '-') {
                buf[k++] = MathUtils.parseInt(val, off, i - off, 10);
                off = i;
            }
        }
        long timestamp = ACalendar.daySinceAD(buf[0], buf[1], buf[2], null) * 86400000;
        return new CYamlDate(timestamp);
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public int asInteger() {
        return (int) value;
    }

    @Override
    public long asLong() {
        return value;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.YAML_DATE;
    }

    @Nonnull
    @Override
    public String asString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CYamlDate that = (CYamlDate) o;
        return that.value == value;
    }

    @Override
    public int hashCode() {
        return (int) value;
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        int[] time = ACalendar.get1(value);
        return sb.append(time[ACalendar.YEAR]).append('-').append(time[ACalendar.MONTH]).append('-').append(time[ACalendar.DAY]);
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return sb.append("\"<YAML date ").append(toYAML(sb, 0)).append(">\"");
    }
}
