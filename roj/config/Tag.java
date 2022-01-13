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
package roj.config;

import ilib.util.NBTType;
import roj.collect.MyHashMap;
import roj.config.data.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NBT IO class
 *
 * @see <a href="https://github.com/udoprog/c10t/blob/master/docs/NBT.txt">Online NBT specification</a>
 */
public final class Tag {
    public static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11;

    public final byte type;
    public Object value;

    public Tag(byte type) {
        switch (type) {
            case LIST:
                value = new ArrayList<>();
                break;
            case COMPOUND:
                value = new MyHashMap<>();
                break;
        }
        this.type = type;
    }

    public Tag(byte type, Object value) {
        this.type = type;
        setValue(value);
    }

    public void setValue(Object v) {
        switch (type) {
            case END:
                return;
            case BYTE:
            case DOUBLE:
            case LONG:
            case FLOAT:
            case INT:
            case SHORT:
                if (!(v instanceof Number))
                    throw new IllegalArgumentException();
                break;
            case BYTE_ARRAY:
                if (!(v instanceof byte[]))
                    throw new IllegalArgumentException();
                break;
            case STRING:
                if (!(v instanceof CharSequence))
                    throw new IllegalArgumentException();
                break;
            case LIST:
                if (!(v instanceof List))
                    throw new IllegalArgumentException();
                break;
            case COMPOUND:
                if (!(v instanceof Map))
                    throw new IllegalArgumentException();
                break;
            case INT_ARRAY:
                if (!(v instanceof int[]))
                    throw new IllegalArgumentException();
                break;
        }
        this.value = v;
    }

    @SuppressWarnings("unchecked")
    public byte getListType() {
        List<Tag> list = (List<Tag>) value;
        byte type = 0;
        byte prev = 0;
        for (int i = 0; i < list.size(); i++) {
            type = list.get(i).type;
            if (type != prev) throw new IllegalStateException("Invalid list!");
            prev = type;
        }
        return type;
    }

    @SuppressWarnings("unchecked")
    public void append(Tag tag) {
        List<Tag> list = (List<Tag>) value;
        if (!list.isEmpty() && list.get(0).type != tag.type)
            throw new IllegalStateException("Invalid list!");
        list.add(tag);
    }

    @SuppressWarnings("unchecked")
    public Tag get(int index) {
        List<Tag> list = (List<Tag>) value;
        return list.get(index);
    }

    @SuppressWarnings("unchecked")
    public void remove(int index) {
        List<Tag> list = (List<Tag>) value;
        list.remove(index);
    }

    @SuppressWarnings("unchecked")
    public List<Tag> getList() {
        return (List<Tag>) value;
    }

    @SuppressWarnings("unchecked")
    public void put(String name, Tag tag) {
        Map<String, Tag> map = (Map<String, Tag>) value;
        map.put(name, tag);
    }

    @SuppressWarnings("unchecked")
    public Tag get(String name) {
        Map<String, Tag> map = (Map<String, Tag>) value;
        return map.get(name);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Tag> getMap() {
        return (Map<String, Tag>) value;
    }

    @SuppressWarnings("unchecked")
    public void remove(String name) {
        Map<String, Tag> map = (Map<String, Tag>) value;
        map.remove(name);
    }

    @SuppressWarnings("unchecked")
    public void removeTag(Tag tag) {
        if (type == NBTType.LIST) {
            List<Tag> list = (List<Tag>) value;
            list.remove(tag);
        } else if (type == NBTType.COMPOUND) {
            Map<String, Tag> map = (Map<String, Tag>) value;
            map.values().remove(tag);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public static Tag read(InputStream is) throws IOException {
        return read((DataInput) new DataInputStream(is));
    }

    public static Tag read(DataInput in) throws IOException {
        byte flg = in.readByte();
        if (flg != COMPOUND) throw new IOException("Topmost entry must be a COMPOUND");
        char n = in.readChar();
        if (n != 0) throw new IOException("Topmost entry must not have name");
        //in.skipBytes(n);
        return new Tag(flg, readPayload(in, flg));
    }

    private static Object readPayload(DataInput in, byte type) throws IOException {
        switch (type) {
            case END:
            default:
                return null;
            case BYTE:
                return in.readByte();
            case SHORT:
                return in.readShort();
            case INT:
                return in.readInt();
            case LONG:
                return in.readLong();
            case FLOAT:
                return in.readFloat();
            case DOUBLE:
                return in.readDouble();
            case BYTE_ARRAY:
                byte[] ba = new byte[in.readInt()];
                in.readFully(ba);
                return ba;
            case STRING:
                return in.readUTF();
            case LIST:
                byte listType = in.readByte();
                int len = in.readInt();
                ArrayList<Tag> lo = new ArrayList<>(len);
                for (int i = 0; i < len; i++) {
                    lo.add(new Tag(listType, readPayload(in, listType)));
                }
                return lo;
            case COMPOUND:
                MyHashMap<String, Tag> tags = new MyHashMap<>();
                do {
                    byte flg = in.readByte();
                    if (flg == 0) break;
                    tags.put(in.readUTF(), new Tag(flg, readPayload(in, flg)));
                } while (true);
                return tags;
            case INT_ARRAY:
                int[] ia = new int[in.readInt()];
                for (int i = 0; i < ia.length; i++) ia[i] = in.readInt();
                return ia;
        }
    }

    public void write(DataOutput out) throws IOException {
        if (type != COMPOUND) throw new IOException("Topmost entry must be a COMPOUND");
        out.writeByte(COMPOUND);
        out.writeChar(0);
        writePayload(out);
    }

    @SuppressWarnings("unchecked")
    private void writePayload(DataOutput out) throws IOException {
        switch (type) {
            case END:
                throw new IOException("END should not appear");
            case BYTE:
                out.writeByte(((Number) value).byteValue());
                break;
            case SHORT:
                out.writeShort(((Number) value).shortValue());
                break;
            case INT:
                out.writeInt(((Number) value).intValue());
                break;
            case LONG:
                out.writeLong(((Number) value).longValue());
                break;
            case FLOAT:
                out.writeFloat(((Number) value).floatValue());
                break;
            case DOUBLE:
                out.writeDouble(((Number) value).doubleValue());
                break;
            case BYTE_ARRAY:
                byte[] arr = (byte[]) value;
                out.writeInt(arr.length);
                out.write(arr);
                break;
            case STRING:
                out.writeUTF((String) value);
                break;
            case LIST:
                List<Tag> list = (List<Tag>) value;
                out.writeByte(getListType());
                out.writeInt(list.size());
                for (int i = 0; i < list.size(); i++)
                    list.get(i).writePayload(out);
                break;
            case COMPOUND:
                Map<String, Tag> map = (Map<String, Tag>) value;
                for (Map.Entry<String, Tag> entry : map.entrySet()) {
                    Tag tag = entry.getValue();
                    out.writeByte(tag.type);
                    out.writeUTF(entry.getKey());
                    tag.writePayload(out);
                }
                out.write(END);
                break;
            case INT_ARRAY:
                int[] ia = (int[]) value;
                out.writeInt(ia.length);
                for (int i : ia) out.writeInt(i);
                break;
        }
    }

    public void print() {
        System.out.println(convert().toJSON());
    }

    @SuppressWarnings("unchecked")
    public CEntry convert() {
        switch (type) {
            case END:
                throw new IllegalStateException("END should not appear");
            case BYTE:
            case SHORT:
            case INT:
                return CInteger.valueOf(((Number) value).intValue());
            case LONG:
                return CLong.valueOf(((Number) value).longValue());
            case FLOAT:
            case DOUBLE:
                return CDouble.valueOf(((Number) value).doubleValue());
            case BYTE_ARRAY:
                byte[] arr = (byte[]) value;
                List<CEntry> list1 = new ArrayList<>(arr.length);
                for (byte b : arr) {
                    list1.add(CInteger.valueOf(b));
                }
                return new CList(list1);
            case STRING:
                return CString.valueOf((String) value);
            case LIST:
                List<Tag> list = (List<Tag>) value;
                list1 = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    list1.add(list.get(i).convert());
                }
                return new CList(list1);
            case COMPOUND:
                Map<String, Tag> map = (Map<String, Tag>) value;
                Map<String, CEntry> map1 = new MyHashMap<>(map.size());
                for (Map.Entry<String, Tag> entry : map.entrySet()) {
                    map1.put(entry.getKey(), entry.getValue().convert());
                }
                return new CMapping(map1);
            case INT_ARRAY:
                int[] ia = (int[]) value;
                list1 = new ArrayList<>(ia.length);
                for (int b : ia) {
                    list1.add(CInteger.valueOf(b));
                }
                return new CList(list1);
        }
        return null;
    }
}
