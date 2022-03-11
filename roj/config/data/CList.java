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

import roj.collect.IntList;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.serial.Serializer;
import roj.config.serial.Serializers;
import roj.config.serial.StreamSerializer;
import roj.config.serial.Structs;
import roj.config.word.AbstLexer;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class CList extends CEntry implements Iterable<CEntry> {
    final List<CEntry> list;

    public CList() {
        this(new ArrayList<>());
    }

    public CList(int size) {
        this.list = new ArrayList<>(size);
    }

    @SuppressWarnings("unchecked")
    public CList(List<? extends CEntry> list) {
        this.list = (List<CEntry>) list;
    }

    public static CList of(Object... objects) {
        return CEntry.wrap(objects).asList();
    }

    static CList _fromBinary(int type, ByteList r, Structs s, Serializers ser) {
        int cap = r.readVarInt(false);
        List<CEntry> list = new ArrayList<>(cap);
        if (type == 0) {
            while (cap-- > 0)
                list.add(fromBinary(r, s, ser));
        } else {
            switch (Type.VALUES[--type]) {
                case BOOL:
                    while (cap-- > 0)
                        list.add(CBoolean.valueOf(r.readBit1() != 0));
                    if (r.bitIndex != 0) {
                        r.bitIndex = 0;
                        r.rIndex++;
                    }
                    break;
                case INTEGER:
                    while (cap-- > 0)
                        list.add(CInteger.valueOf(r.readInt()));
                    break;
                case NULL:
                    while (cap-- > 0)
                        list.add(CNull.NULL);
                    break;
                case OBJECT:
                case MAP:
                    while (cap-- > 0) {
                        if (s != null) {
                            int rid = r.readUnsignedByte();
                            if (rid != Type.MAP.ordinal()) {
                                CMapping m = s.fromBinary(rid, r, ser);
                                if (m == null)
                                    throw new IllegalArgumentException("Illegal struct descriptor");
                                list.add(m);
                                continue;
                            }
                        }

                        int cap1 = r.readVarInt(false);
                        Map<String, CEntry> map = new MyHashMap<>(cap1);
                        while (cap1-- > 0) {
                            map.put(r.readVIVIC(), fromBinary(r, s, ser));
                        }

                        CEntry x = map.get("==");
                        if (type == Type.OBJECT.ordinal() && x != null) {
                            Serializer<?> deser = Serializers.DEFAULT.find(x.asString());
                            if (deser != null) {
                                list.add(new CObject<>(map, deser));
                                continue;
                            }
                        }
                        list.add(new CMapping(map));
                    }
                    break;
                case LONG:
                    while (cap-- > 0)
                        list.add(CLong.valueOf(r.readLong()));
                    break;
                case DOUBLE:
                    while (cap-- > 0)
                        list.add(CDouble.valueOf(r.readDouble()));
                    break;
                case STRING:
                    while (cap-- > 0)
                        list.add(CString.valueOf(r.readVIVIC()));
                    break;
                case Int1:
                    while (cap-- > 0)
                        list.add(CByte.valueOf(r.readByte()));
                    break;
                case Int2:
                    while (cap-- > 0)
                        list.add(CShort.valueOf(r.readShort()));
                    break;
                case Float4:
                    while (cap-- > 0)
                        list.add(CFloat.valueOf(r.readFloat()));
                    break;
                case LIST:
                default:
                    throw new IllegalArgumentException("Unsupported type");
            }
        }
        return new CList(list);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public final int size() {
        return list.size();
    }

    @Nonnull
    public final Iterator<CEntry> iterator() {
        return list.iterator();
    }

    @Override
    public final void forEach(Consumer<? super CEntry> action) {
        list.forEach(action);
    }

    @Override
    public final Spliterator<CEntry> spliterator() {
        return list.spliterator();
    }

    public final CList add(CEntry entry) {
        list.add(entry == null ? CNull.NULL : entry);
        return this;
    }

    public final void add(String s) {
        list.add(CString.valueOf(s));
    }

    public final void add(int s) {
        list.add(CInteger.valueOf(s));
    }

    public final void add(double s) {
        list.add(CDouble.valueOf(s));
    }

    public final void add(long s) {
        list.add(CLong.valueOf(s));
    }

    public final void add(boolean b) {
        list.add(CBoolean.valueOf(b));
    }

    public final void set(int index, CEntry entry) {
        list.set(index, entry == null ? CNull.NULL : entry);
    }

    @Nonnull
    public final CEntry get(int index) {
        return list.get(index);
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.LIST;
    }

    public final MyHashSet<String> asStringSet() {
        MyHashSet<String> stringSet = new MyHashSet<>(list.size());
        for (CEntry entry : list) {
            try {
                String val = entry.asString();
                stringSet.add(val);
            } catch (ClassCastException ignored) {
            }
        }
        return stringSet;
    }

    public final SimpleList<String> asStringList() {
        SimpleList<String> stringList = new SimpleList<>(list.size());
        for (CEntry entry : list) {
            try {
                String val = entry.asString();
                stringList.add(val);
            } catch (ClassCastException ignored) {
            }
        }
        return stringList;
    }

    public final int[] asIntList() {
        IntList numberList = new IntList(list.size());
        for (CEntry entry : list) {
            try {
                int val = entry.asInteger();
                numberList.add(val);
            } catch (ClassCastException ignored) {
            }
        }
        return numberList.toArray();
    }

    public final void addAll(CList list) {
        this.list.addAll(list.list);
    }

    public final List<CEntry> raw() {
        return list;
    }

    public final void clear() {
        list.clear();
    }

    @Nonnull
    @Override
    public final CList asList() {
        return this;
    }

    @Override
    public final StringBuilder toYAML(StringBuilder sb, int depth) {
        if (!list.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < list.size(); i++) {
                CEntry entry = list.get(i);
                for (int j = 0; j < depth; j++) {
                    sb.append(' ');
                }
                sb.append('-').append(' ');
                entry.toYAML(sb, depth + 2).append('\n');
            }
            return sb.delete(sb.length() - 1, sb.length());
        }
        return sb.append("[]");
    }

    @Override
    public final StringBuilder toJSON(StringBuilder sb, int depth) {
        sb.append('[');
        if (!list.isEmpty()) {
            if (depth < 0) {
                for (int j = 0; j < list.size(); j++) {
                    list.get(j).toJSON(sb, -1).append(',');
                }
                sb.delete(sb.length() - 1, sb.length());
            } else {
                sb.append('\n');
                for (int j = 0; j < list.size(); j++) {
                    CEntry entry = list.get(j);
                    for (int i = 0; i < depth + 4; i++) {
                        sb.append(' ');
                    }
                    entry.toJSON(sb, depth + 4).append(",\n");
                }
                sb.delete(sb.length() - 2, sb.length() - 1);
                for (int i = 0; i < depth; i++) {
                    sb.append(' ');
                }
            }
        }
        return sb.append(']');
    }

    @Override
    public final StringBuilder toINI(StringBuilder sb, int depth) {
        throw new UnsupportedOperationException("INI file format does not support LIST");
    }

    @Override
    public StringBuilder toTOML(StringBuilder sb, int depth, CharSequence chain) {
        if (list.isEmpty()) {
            return sb.append("[]");
        } else if (depth != 3) {
            for (int i = 0; i < list.size(); i++) {
                sb.append("[[");
                if (!CString.rawSafe(chain)) {
                    AbstLexer.addSlashes(chain, sb);
                } else {
                    sb.append(chain);
                }
                sb.append("]]\n");
                list.get(i).toTOML(sb, 2, chain).append("\n");
            }
            return sb.delete(sb.length() - 1, sb.length());
        } else {
            if (!CString.rawSafe(chain)) {
                AbstLexer.addSlashes(chain, sb);
            } else {
                sb.append(chain);
            }
            sb.append(" = [");
            for (int j = 0; j < list.size(); j++) {
                CEntry entry = list.get(j);
                entry.toTOML(sb, 3, chain).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
            return sb.append(']');
        }
    }

    @Override
    public final Object unwrap() {
        List<Object> caster = Arrays.asList(new Object[list.size()]);
        for (int i = 0; i < list.size(); i++) {
            caster.set(i, list.get(i).unwrap());
        }
        return caster;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public final void toBinary(ByteList w, Structs struct) {
        int iType = -1;
        List<CEntry> list = this.list;
        for (int i = 0; i < list.size(); i++) {
            int type1 = list.get(i).getType().ordinal();
            if (iType == -1) {
                iType = type1;
            } else if (iType != type1 || type1 == /*Type.LIST.ordinal()*/0) {
                iType = -1;
                break;
            }
        }
        w.put((byte) (((1 + iType) << 4)/* | Type.LIST.ordinal()*/)).putVarInt(list.size(), false);
        Type type = iType >= 0 ? Type.VALUES[iType] : null;
        if (type == null) {
            for (int i = 0; i < list.size(); i++) {
                list.get(i).toBinary(w, struct);
            }
            return;
        } else if (type == Type.NULL) {
            return;
        }

        int bv = 0, bvi = 8;
        for (int i = 0; i < list.size(); i++) {
            CEntry el = list.get(i);
            switch (type) {
                case BOOL:
                    bv |= (el.asInteger() << --bvi);
                    if (bvi == 0) {
                        w.put((byte) bv);
                        bv = 0;
                        bvi = 8;
                    }
                    break;
                case INTEGER:
                    w.putInt(el.asInteger());
                    break;
                case OBJECT:
                    el.asObject(Object.class).serialize();
                case MAP:
                    if (struct == null || !struct.toBinary(el.asMap(), w)) {
                        if (struct != null) w.put((byte) el.getType().ordinal());

                        Map<String, CEntry> map = el.asMap().raw();
                        w.putVarInt(map.size(), false);
                        if (!map.isEmpty()) {
                            for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                                entry.getValue().toBinary(w.putVarIntUTF(entry.getKey()), struct);
                            }
                        }
                    }
                    break;
                case LONG:
                    w.putLong(el.asLong());
                    break;
                case DOUBLE:
                    w.putDouble(el.asDouble());
                    break;
                case STRING:
                    w.putVarIntUTF(el.asString());
                    break;
                case Int1:
                    w.put((byte) el.asInteger());
                    break;
                case Int2:
                    w.putShort(el.asInteger());
                    break;
                case Float4:
                    w.putFloat((float) el.asDouble());
                    break;
            }
        }
        if (bvi != 8) {
            w.put((byte) bv);
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CList that = (CList) o;

        return Objects.equals(list, that.list);
    }

    @Override
    public final int hashCode() {
        return list != null ? list.hashCode() : 0;
    }

    @Override
    public final void serialize(StreamSerializer ser) {
        ser.valueList();
        List<CEntry> l = this.list;
        for (int i = 0; i < l.size(); i++) {
            l.get(i).serialize(ser);
        }
        ser.pop();
    }
}
