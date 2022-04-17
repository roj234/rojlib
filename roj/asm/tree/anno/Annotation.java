/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and getsociated documentation files (the "Software"), to deal
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
import roj.asm.type.Type;
import roj.asm.util.ConstantPool;
import roj.collect.LinkedMyHashMap;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Annotation {
    public String clazz;
    public Map<String, AnnVal> values;

    public Annotation(String type, Map<String, AnnVal> values) {
        this.clazz = type.substring(1, type.length() - 1);
        this.values = values;
    }

    public int getInt(String name, int def) {
        AnnVal av = values.get(name);
        if (av == null) return def;
        return av.asInt();
    }
    public float getFloat(String name, float def) {
        AnnVal av = values.get(name);
        if (av == null) return def;
        return av.asInt();
    }
    public double getDouble(String name, double def) {
        AnnVal av = values.get(name);
        if (av == null) return def;
        return av.asInt();
    }
    public long getLong(String name, long def) {
        AnnVal av = values.get(name);
        if (av == null) return def;
        return av.asInt();
    }
    public String getString(String name) {
        AnnVal av = values.get(name);
        if (av == null) return Helpers.nonnull();
        return av.asString();
    }
    public String getString(String name, String def) {
        AnnVal av = values.get(name);
        if (av == null) return def;
        return av.asString();
    }
    public AnnValEnum getEnum(String name) {
        AnnVal av = values.get(name);
        if (av == null) return Helpers.nonnull();
        return av.asEnum();
    }
    public Type getClass(String name) {
        AnnVal av = values.get(name);
        if (av == null) return Helpers.nonnull();
        return av.asClass();
    }
    public Annotation getAnnotation(String name) {
        AnnVal av = values.get(name);
        if (av == null) return Helpers.nonnull();
        return av.asAnnotation();
    }
    public List<AnnVal> getArray(String name) {
        AnnVal av = values.get(name);
        if (av == null) return Helpers.nonnull();
        return av.asArray();
    }
    public boolean containsKey(String name) {
        return values.containsKey(name);
    }

    public void put(String name, AnnVal av) {
        values.put(name, av);
    }

    @Deprecated
    public static Annotation deserialize(ConstantPool pool, ByteReader r) {
        ByteList l = r.bytes;
        int ri = l.rIndex;
        l.rIndex = r.rIndex;
        try {
            return deserialize(pool, l);
        } finally {
            r.rIndex = l.rIndex;
            l.rIndex = ri;
        }
    }

    public static Annotation deserialize(ConstantPool pool, ByteList r) {
        String type = ((CstUTF) pool.get(r)).getString();
        int len = r.readUnsignedShort();

        Map<String, AnnVal> params;
        if (len > 0) {
            params = new LinkedMyHashMap<>(len);
            while (len-- > 0) {
                params.put(((CstUTF) pool.get(r)).getString(), AnnVal.parse(pool, r));
            }
        } else {
            params = Collections.emptyMap();
        }

        return new Annotation(type, params);
    }

    public void toByteArray(ConstantPool pool, ByteList w) {
        w.putShort(pool.getUtfId("L" + clazz + ';'))
         .putShort(values.size());
        for (Map.Entry<String, AnnVal> e : values.entrySet()) {
            e.getValue().toByteArray(pool, w.putShort(pool.getUtfId(e.getKey())));
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("@")
                .append(clazz.substring(clazz.lastIndexOf('/') + 1));
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