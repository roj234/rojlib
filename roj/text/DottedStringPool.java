/*
 * This file is a part of MoreItems
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

import java.util.ArrayList;
import java.util.List;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/9/30 22:42
 */
public class DottedStringPool extends StringPool {
    final char delimChar;
    final IntBiMap<String> dottedList = new IntBiMap<>();

    public DottedStringPool(char c) {
        this.delimChar = c;
    }

    public DottedStringPool(ByteReader r, char c) {
        super(r);
        this.delimChar = c;
    }

    private List<String> tmp;
    public ByteWriter writeDlm(ByteWriter w, String string) {
        if(tmp == null)
            tmp = new ArrayList<>();
        else
            tmp.clear();
        int id = dottedList.getByValue(string);
        if(id != -1) {
            return w.writeVarInt(id, false);
        }
        List<String> clipped = TextUtil.split(tmp, string, delimChar);
        w.writeByte((byte) 0).writeByte((byte) clipped.size());
        for (int i = 0; i < clipped.size() - 1; i++) {
            String string1;
            id = list.getByValue(string1 = clipped.get(i));
            if (id == -1) {
                list.putByValue(id = list.size(), string1);
                ordered.add(string1);
            }
            w.writeVarInt(id, false);
        }
        w.writeVString(clipped.get(clipped.size() - 1));
        dottedList.putByValue(dottedList.size() + 1, string);
        return w;
    }

    private CharList tmp2;
    public String readDlm(ByteReader r) {
        int blocks = r.readVarInt(false);
        if(blocks == 0) {
            blocks = r.readUByte() - 1;
            CharList cl = tmp2;
            if(cl == null)
                cl = tmp2 = new CharList();
            else
                cl.clear();
            for (int j = 0; j < blocks; j++) {
                cl.append(ordered.get(r.readVarInt(false))).append(delimChar);
            }
            cl.append(r.readVString());
            String e = cl.toString();
            dottedList.putByValue(dottedList.size() + 1, e);
            return e;
        } else {
            return dottedList.get(blocks);
        }
    }
}
