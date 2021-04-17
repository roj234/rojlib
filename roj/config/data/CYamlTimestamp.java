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

import roj.text.ACalendar;

import javax.annotation.Nonnull;

/**
 * Type: YAML Timestamp
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/7/7 0:43
 */
public final class CYamlTimestamp extends CLong {
    public CYamlTimestamp(long number) {
        super(number);
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.YAML_TIMESTAMP;
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        int[] time = ACalendar.get1(value);
        return sb.append(time[ACalendar.YEAR]).append('-').append(time[ACalendar.MONTH]).append('-').append(time[ACalendar.DAY]).append('T').append(time[ACalendar.HOUR]).append(':').append(time[ACalendar.MINUTE]).append(':').append(time[ACalendar.SECOND]).append('.').append(time[ACalendar.MILLISECOND]).append('Z');
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return sb.append("new Date(").append(value).append(")");
    }
}
