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

package roj.asm.tree.anno;

import roj.asm.cst.CstUTF;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.collect.MyHashMap;
import roj.text.StringPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.Map;


/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class Annotation {
    public final String rawDesc;

    public Type type;
    public Map<String, AnnVal> values;

    public Annotation(String type, Map<String, AnnVal> values) {
        this.type = ParamHelper.parseField(this.rawDesc = type);
        this.values = values;
    }

    public Annotation(Annotation another) {
        this.type = another.type;
        this.rawDesc = another.rawDesc;
        this.values = another.values;
    }


    public static Annotation deserialize(StringPool pool, ByteReader r) {
        String name = pool.readString(r);
        int len = r.readVarInt(false);
        Map<String, AnnVal> params = new MyHashMap<>(len);
        for (int j = 0; j < len; j++) {
            String key = pool.readString(r);
            AnnVal value = AnnVal.deserialize(pool, r);
            params.put(key, value);
        }
        return new Annotation(name, params);
    }

    public void serialize(StringPool pool, ByteWriter w) {
        pool.writeString(w, ParamHelper.getField(type))
                .writeVarInt(values.size(), false);
        for (Map.Entry<String, AnnVal> e : values.entrySet()) {
            e.getValue().toByteArray(pool, pool.writeString(w, e.getKey()));
        }
    }

    public static Annotation deserialize(ConstantPool pool, ByteReader r) {
        String type = ((CstUTF) pool.get(r)).getString();
        int len = r.readUnsignedShort();
        Map<String, AnnVal> params = new MyHashMap<>(len);
        for (int j = 0; j < len; j++) {
            String key = ((CstUTF) pool.get(r)).getString();
            AnnVal value = AnnVal.parse(pool, r);
            params.put(key, value);
        }
        return new Annotation(type, params);
    }

    public void toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getUtfId(ParamHelper.getField(type)))
                .writeShort((short) values.size());
        for (Map.Entry<String, AnnVal> e : values.entrySet()) {
            e.getValue().toByteArray(pool, w.writeShort(pool.getUtfId(e.getKey())));
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("@");
        sb.append(type);
        if (!values.isEmpty()) {
            sb.append('(');
            for (Map.Entry<String, AnnVal> e : values.entrySet()) {
                sb.append(e.getKey()).append(" = ").append(e.getValue()).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length()).append(')');
        }
        return sb.toString();
    }
}