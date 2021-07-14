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
package roj.fbt.tags;

import roj.fbt.result.TagResult;
import roj.fbt.result.TagResultString;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class TagFixedString implements Tag {
    public TagFixedString(int maxLength) {
        this.length = maxLength;
    }

    final int length;

    @Nonnull
    @Override
    public TagResult read(@Nonnull ByteReader reader) {
        return new TagResultString(new String(reader.readBytes(length), StandardCharsets.UTF_8));
    }

    @Override
    public void write(@Nonnull ByteWriter writer, @Nonnull TagResult result) {
        String string = result.getString();
        int fullIndex = writer.list.pos() + this.length;
        byte[] arr = string.getBytes(StandardCharsets.UTF_8);
        writer.writeBytes(arr);
        writer.list.pos(fullIndex);
    }

    @Nonnull
    @Override
    public TagType getType() {
        return TagType.STRING;
    }

    @Override
    public int getLength() {
        return this.length;
    }
}
