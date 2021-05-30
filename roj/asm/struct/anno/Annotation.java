/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: Annotation.java
 */
package roj.asm.struct.anno;

import roj.asm.cst.CstUTF;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;
import roj.collect.MyHashMap;
import roj.text.StringPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.Map;


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