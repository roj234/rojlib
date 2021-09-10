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
package roj.util;

import org.jetbrains.annotations.ApiStatus.Internal;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

/**
 * 定长字符串 (填充)
 *
 * @author Roj234
 * @version 0.2
 * @since 2021/4/21 22:51
 */
@Internal
public class FixedString {
    public FixedString(int max) {
        this.max = max;
        if ((this.max & ~255) != 0) {
            this.len = 1;
        } else if ((this.max & ~65535) != 0) {
            this.len = 2;
        } else {
            this.len = 4;
        }
    }

    final int max;
    final byte len;

    @Nonnull
    public String read0(@Nonnull ByteReader reader) {
        int sLen = 0;
        String string;
        switch (len) {
            case 1:
                sLen = reader.readUByte();
                break;
            case 2:
                sLen = reader.readUnsignedShort();
                break;
            case 4:
                sLen = reader.readInt();
                break;
        }
        if (sLen <= 0) {
            string = "";
            sLen = 0;
        } else {
            byte[] bytes = reader.readBytes(sLen);
            string = new String(bytes, StandardCharsets.UTF_8);
        }
        reader.index += this.max - sLen;
        return string;
    }

    public void write(@Nonnull ByteWriter writer, @Nonnull String string) {
        int fullIndex = writer.list.pos() + this.len + this.max;
        byte[] arr = string.getBytes(StandardCharsets.UTF_8);
        if (arr.length == 0) {
            writer.list.pos(fullIndex);
            return;
        }
        if (arr.length > max) {
            throw new StringIndexOutOfBoundsException(arr.length);
        }
        switch (len) {
            case 1:
                writer.writeByte((byte) arr.length);
                break;
            case 2:
                writer.writeShort(arr.length);
                break;
            case 4:
                writer.writeInt(arr.length);
                break;
        }
        writer.writeBytes(arr);
        writer.list.pos(fullIndex);
    }

    public int getLength() {
        return this.len + this.max;
    }
}
