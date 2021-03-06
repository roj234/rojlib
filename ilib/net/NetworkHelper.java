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
package ilib.net;

import ilib.net.packet.MsgSyncField;
import ilib.net.packet.MsgSyncFields;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since  2020/9/25 13:25
 */
public class NetworkHelper {
    public static final MyChannel IL = new MyChannel("IL");

    static {
        IL.registerMessage(null, MsgSyncFields.class, 0, null);
        IL.registerMessage(null, MsgSyncField.class, 1, null);
    }

    public static void writeStringList(ByteList buf, String[] list) {
        if (list == null) {
            buf.putVarInt(-1);
            return;
        }
        buf.putVarInt(list.length);
        for (String s : list) {
            buf.putIntUTF(s);
        }
    }

    public static String[] getStringList(ByteList buf) {
        int length = buf.readVarInt();
        if (length == -1) return null;
        String[] result = new String[length];
        for (int i = 0; i < length; i++) {
            result[i] = buf.readIntUTF();
        }
        return result;
    }
}
