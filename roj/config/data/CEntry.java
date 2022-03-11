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

import org.jetbrains.annotations.ApiStatus;
import roj.collect.MyHashMap;
import roj.config.serial.Serializer;
import roj.config.serial.Serializers;
import roj.config.serial.StreamSerializable;
import roj.config.serial.Structs;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Config Entry
 *
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public abstract class CEntry implements StreamSerializable {
    protected CEntry() {}

    public abstract Type getType();

    ////// easy caster

    public String asString() {
        throw new ClassCastException(getType() + " unable cast to 'string'");
    }

    public int asInteger() {
        throw new ClassCastException(getType() + " unable cast to 'int'");
    }

    public double asDouble() {
        throw new ClassCastException(getType() + " unable cast to 'double'");
    }

    public long asLong() {
        throw new ClassCastException(getType() + " unable cast to 'long'");
    }

    public CMapping asMap() {
        throw new ClassCastException(getType() + " unable cast to 'map'");
    }

    public CList asList() {
        throw new ClassCastException(getType() + " unable cast to 'list'");
    }

    public boolean asBool() {
        throw new ClassCastException(getType() + " unable cast to 'boolean'");
    }

    public <T> CObject<T> asObject(Class<T> clazz) {
        throw new ClassCastException(getType() + " unable cast to 'java_object'");
    }

    ////// toString methods

    @Override
    public String toString() {
        return toShortJSON();
    }

    protected abstract StringBuilder toJSON(StringBuilder sb, int depth);

    public final String toJSON() {
        return toJSON(new StringBuilder(), 0).toString();
    }

    public final StringBuilder toJSONb() {
        return toJSON(new StringBuilder(), 0);
    }

    public final String toShortJSON() {
        return toJSON(new StringBuilder(), -1).toString();
    }

    public final StringBuilder toShortJSONb() {
        return toJSON(new StringBuilder(), -1);
    }

    @ApiStatus.OverrideOnly
    protected StringBuilder toYAML(StringBuilder sb, int depth) {
        return toJSON(sb, 0);
    }

    public final String toYAML() {
        return toYAML(new StringBuilder(), 0).toString();
    }

    public final StringBuilder toYAMLb() {
        return toYAML(new StringBuilder(), 0);
    }

    @ApiStatus.OverrideOnly
    protected StringBuilder toINI(StringBuilder sb, int depth) {
        return toJSON(sb, 0);
    }

    public final String toINI() {
        return toINI(new StringBuilder(), 0).toString();
    }

    public final StringBuilder toINIb() {
        return toINI(new StringBuilder(), 0);
    }

    @ApiStatus.OverrideOnly
    protected StringBuilder toTOML(StringBuilder sb, int depth, CharSequence chain) {
        return toJSON(sb, 0);
    }

    public final String toTOML() {
        return toTOML(new StringBuilder(), 0, new CharList()).toString();
    }

    public final StringBuilder toTOMLb() {
        return toTOML(new StringBuilder(), 0, new CharList());
    }

    @ApiStatus.OverrideOnly
    protected StringBuilder toXML(StringBuilder sb, int depth) {
        return toJSON(sb, 0);
    }

    public final String toXML() {
        return toXML(new StringBuilder(), 0).toString();
    }

    public final StringBuilder toXMLb() {
        return toXML(new StringBuilder(), 0);
    }

    public final String toCompatXML() {
        return toXML(new StringBuilder(), -1).toString();
    }

    public final StringBuilder toCompatXMLb() {
        return toXML(new StringBuilder(), -1);
    }

    // Converting

    public abstract Object unwrap();

    public static CEntry wrap(Object o) {
        if (o == null) {
            return CNull.NULL;
        } else if (o instanceof Object[]) {
            Object[] arr = (Object[]) o;
            CList dst = new CList(arr.length);
            for (Object o1 : arr) {
                dst.add(wrap(o1));
            }
        } if (o.getClass().getComponentType() != null) {
            switch (o.getClass().getComponentType().getName()) {
                case "int":
                    return Serializers.wArray((int[]) o);
                case "byte":
                    return Serializers.wArray((byte[]) o);
                case "boolean":
                    return Serializers.wArray((boolean[]) o);
                case "char":
                    return Serializers.wArray((char[]) o);
                case "long":
                    return Serializers.wArray((long[]) o);
                case "short":
                    return Serializers.wArray((short[]) o);
                case "float":
                    return Serializers.wArray((float[]) o);
                case "double":
                    return Serializers.wArray((double[]) o);
                default:
                    throw new UnsupportedOperationException("void[] ???");
            }
        } if(o instanceof Map) {
            Map<String, Object> map = Helpers.cast(o);
            CMapping dst = new CMapping(map.size());
            try {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    dst.put(entry.getKey(), wrap(entry.getValue()));
                }
            } catch (ClassCastException e) {
                return new CObject<>(map);
                //throw new UnsupportedOperationException("序列化的map必须使用string做key!");
            }
            return dst;
        } else if (o instanceof List) {
            List<Object> list = Helpers.cast(o);
            CList dst = new CList(list.size());
            for (int i = 0; i < list.size(); i++) {
                dst.add(wrap(list.get(i)));
            }
            return dst;
        } else if (o instanceof Collection) {
            Collection<Object> list = Helpers.cast(o);
            CList dst = new CList(list.size());
            for (Object o1 : list) {
                dst.add(wrap(o1));
            }
            return dst;
        } else if (o instanceof CharSequence) {
            return CString.valueOf(o.toString());
        } else if (o instanceof Boolean) {
            return CBoolean.valueOf((Boolean) o);
        }else if (o instanceof Long) {
            return CLong.valueOf((Long) o);
        } else if (o instanceof Double || o instanceof Float) {
            return CDouble.valueOf(((Number) o).doubleValue());
        } else if (o instanceof Number) {
            return CInteger.valueOf(((Number) o).intValue());
        } else if (o instanceof Character) {
            return CInteger.valueOf((Character) o);
        } else if (o instanceof CEntry) {
            return (CEntry) o;
        } else {
            return new CObject<>(o);
        }
    }

    public final void toBinary(ByteList w) {
        toBinary(w, null);
    }
    public abstract void toBinary(ByteList w, Structs struct);

    public static CEntry fromBinary(ByteList r) {
        return fromBinary(r, null, null);
    }
    public static CEntry fromBinary(ByteList r, Structs struct, Serializers ser) {
        int b = r.readUnsignedByte();
        if (struct != null) {
            CMapping m = struct.fromBinary(b, r, ser);
            if (m != null) return m;
        }

        switch (Type.VALUES[b & 0xF]) {
            case BOOL:
                return CBoolean.valueOf(r.readBoolean());
            case INTEGER:
                return CInteger.valueOf(r.readInt());
            case NULL:
                return CNull.NULL;
            case OBJECT:
            case MAP:
                int cap = r.readVarInt(false);
                Map<String, CEntry> map = new MyHashMap<>(cap);
                while (cap-- > 0) {
                    map.put(r.readVIVIC(), fromBinary(r, struct, ser));
                }

                CEntry x = map.get("==");
                if (ser != null && (b & 0xF) == Type.OBJECT.ordinal() && x != null) {
                    Serializer<?> deser = ser.find(x.asString());
                    if (deser != null) {
                        return new CObject<>(map, deser);
                    }
                }
                return new CMapping(map);
            case LIST:
                return CList._fromBinary(b >>> 4, r, struct, ser);
            case LONG:
                return CLong.valueOf(r.readLong());
            case DOUBLE:
                return CDouble.valueOf(r.readDouble());
            case STRING:
                return CString.valueOf(r.readVIVIC());
            case Int1:
                return CByte.valueOf(r.readByte());
            case Int2:
                return CShort.valueOf(r.readShort());
            case Float4:
                return CFloat.valueOf(r.readFloat());
            default:
                throw new IllegalArgumentException("Unexpected id " + b);
        }
    }

    public boolean isSimilar(CEntry o) {
        return getType().isSimilar(o.getType());
    }
}
