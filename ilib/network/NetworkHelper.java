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
package ilib.network;

import roj.util.ByteReader;
import roj.util.ByteWriter;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/25 13:25
 */
public class NetworkHelper {
    // Utilities method
    public static void writeStringList(ByteWriter buf, String[] list) {
        if (list == null) {
            buf.writeVarInt(-1);
            return;
        }
        buf.writeVarInt(list.length);
        for (String s : list) {
            buf.writeString(s);
        }
    }

    public static String[] getStringList(ByteReader buf) {
        int length = buf.readVarInt();
        if (length == -1) return null;
        String[] result = new String[length];
        for (int i = 0; i < length; i++) {
            result[i] = buf.readString();
        }
        return result;
    }
}
