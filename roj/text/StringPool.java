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
package roj.text;

import roj.collect.IntBiMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class StringPool {
    final IntBiMap<String> list = new IntBiMap<>();
    final List<String> ordered;

    public StringPool() {
        this.ordered = new ArrayList<>();
    }

    public StringPool(ByteReader reader) {
        int length = reader.readVarInt(false);
        String[] array = new String[length];
        this.ordered = Arrays.asList(array);
        for (int i = 0; i < length; i++) {
            array[i] = reader.readVString();
        }
    }

    public ByteWriter writePool(ByteWriter data) {
        data.writeVarInt(ordered.size(), false);
        for (String s : ordered) {
            data.writeVString(s);
        }
        return data;
    }

    public ByteWriter writeString(ByteWriter w, String string) {
        int id = list.getInt(string);
        if (id == -1) {
            list.putByValue(id = list.size(), string);
            ordered.add(string);
        }
        return w.writeVarInt(id, false);
    }

    public String readString(ByteReader r) {
        return ordered.get(r.readVarInt(false));
    }

    public void writePool(OutputStream os) throws IOException {
        writePool(new ByteWriter(5 * ordered.size())).list.writeToStream(os);
    }

    public int size() {
        return ordered.size();
    }

    public int add(String string) {
        int id = list.getInt(string);
        if (id == -1) {
            list.putByValue(id = list.size(), string);
            ordered.add(string);
            return id;
        }
        return -1;
    }
}
