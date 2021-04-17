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
package roj.asm.util;

import roj.asm.cst.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.collect.IntList;
import roj.collect.MyHashSet;
import roj.io.MutableZipFile;
import roj.io.MutableZipFile.EFile;
import roj.math.MutableInt;
import roj.util.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static roj.asm.cst.CstType.*;

/**
 * Packer 125
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/10/17 0:07
 */
public class Pack125 {
    public static void main(String[] args) throws Exception {
        MutableZipFile mzf = new MutableZipFile(new File(args[0]));
        Pack125 pack125 = new Pack125();
        IntList a = new IntList();
        long t = System.currentTimeMillis();
        for (EFile eFile : mzf.getEntries().values()) {
            String name = eFile.getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("assets/")) {
                ByteList in = new ByteList(mzf.getFileData(eFile));
                pack125.pack(in);
                a.add(hashCode(in));
            }
        }
        System.out.println("Pack " + (System.currentTimeMillis() - t));

        ByteList data = pack125.toByteArray();
        try (OutputStream out = new DeflaterOutputStream(new FileOutputStream("pack125.pak"), new Deflater(Deflater.DEFAULT_COMPRESSION, true))) {
            data.writeToStream(out);
        }

        t = System.currentTimeMillis();
        MutableInt i = new MutableInt();
        unpack(new ByteReader(data), (name, in) -> {
            if (a.get(i.getAndIncrement()) != hashCode(in)) throw new IllegalStateException("At " + name);
        });
        System.out.println("Unpack " + (System.currentTimeMillis() - t));
    }

    private static int hashCode(ByteList in) {
        int hash = 1;
        for (int i = 0; i < in.pos(); i++) {
            hash = hash * 31 + in.get(i);
        }
        return hash;
    }

    private static class LongerCst {
        int    index;
        Object data;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LongerCst cst = (LongerCst) o;

            return data.equals(cst.data);
        }

        @Override
        public int hashCode() {
            return data.hashCode();
        }

        public static LongerCst one() {
            return new LongerCst();
        }

        @Override
        public String toString() {
            return "#" + index + " => " + data;
        }
    }

    private final ByteWriter w1, w2, w3;
    private int cLen, cpLen;
    private final MyHashSet<LongerCst>[] cw;

    static final int HEADER = ('P' << 24) | ('k' << 16) | ('3' << 8) | '0';

    public static void unpack(ByteReader r, BiConsumer<String, ByteList> con) {
        if (r.readInt() != HEADER) throw new IllegalArgumentException("Header mismatch");
        int len = r.readInt() + 1;
        Object[] cp = new Object[len];

        List<Type> tmp = new ArrayList<>();
        for (int i = 1; i < len; i++) {
            Object u;
            byte b = r.readByte();
            switch (b) {
                case _TOP_:
                    u = CstTop.TOP;
                    break;
                case TYPES:
                    int len1 = r.readVarInt(false);
                    if (len1 == 0) {
                        u = new CstUTF(ParamHelper.getField((Type) cp[r.readVarInt(false)]));
                    } else {
                        for (int j = len1 - 1; j >= 0; j--) {
                            tmp.add((Type) cp[r.readVarInt(false)]);
                        }
                        u = new CstUTF(ParamHelper.getMethod(tmp));
                        tmp.clear();
                    }
                    break;
                case TYP:
                    Type type = new Type((char) r.readByte());
                    type.array = r.readVarInt(false);
                    if (type.type == 'L') {
                        type.owner = ((CstUTF) cp[r.readVarInt(false)]).getString();
                    }
                    u = type;
                    break;
                case NAME_AND_TYPE:
                    CstNameAndType nat = new CstNameAndType();
                    nat.setName((CstUTF) cp[r.readVarInt(false)]);
                    nat.setType((CstUTF) cp[r.readVarInt(false)]);
                    u = nat;
                    break;
                case UTF:
                    u = new CstUTF(r.readVString());
                    break;
                case INT:
                    u = new CstInt(r.readInt());
                    break;
                case FLOAT:
                    u = new CstFloat(r.readFloat());
                    break;
                case LONG:
                    u = new CstLong(r.readLong());
                    break;
                case DOUBLE:
                    u = new CstDouble(r.readDouble());
                    break;
                default:
                    throw new IllegalStateException("Unexpected " + (b & 0xFF));
            }
            cp[i] = u;
        }

        ByteWriter w = new ByteWriter();
        Constant[] map = new Constant[512];
        IntList empty = new IntList();
        len = r.readInt();
        for (int i = 0; i < len; i++) {
            int version = r.readInt();
            if (version == 0) {
                con.accept(r.readVString(), null);
                continue;
            }

            w.writeInt(0xcafebabe).writeInt(version);
            int mapLen = r.readVarInt(false);
            w.writeShort(mapLen + 1);
            if (map.length < mapLen) {
                map = new Constant[mapLen];
            }
            int j = mapLen;
            empty.clear();
            while (j > 0) {
                int k = r.readVarInt(false);
                if (k > 0) {
                    Constant c = (Constant) cp[k];
                    c.setIndex(j--);
                    map[j] = c;
                } else {
                    empty.add(--j);
                }
            }
            for (j = empty.size(); j > 0; ) {
                int k = empty.get(--j);
                (map[k++] = ConstantPool.readConstant(r)).setIndex(k);
            }
            for (; j < mapLen; j++) {
                map[j].write(w);
            }
            mapLen = r.readVarInt(false);
            w.writeBytes(r.getBytes().list, r.index + r.getBytes().offset(), mapLen);
            r.index += 2;
            j = ((CstClass) map[r.readUnsignedShort() - 1]).getValueIndex();
            con.accept(((CstUTF) map[j - 1]).getString(), w.list);
            r.index += mapLen - 4;
            w.list.clear();
        }
    }

    public Pack125(ByteList oa, ByteList ob) {
        oa.clear();
        ob.clear();
        this.w1 = new ByteWriter(oa);
        this.w1.writeInt(HEADER).writeInt(0);
        this.w2 = new ByteWriter(ob);
        this.w3 = new ByteWriter(4096);
        this.cw = Helpers.cast(new MyHashSet<?>[4]);
        for (int i = 0; i < 4; i++) {
            cw[i] = new MyHashSet<>();
        }
        this.map = EmptyArrays.INTS;
    }

    public Pack125() {
        this(new ByteList(65536), new ByteList(65536));
    }

    public Pack125 section(CharSequence commentOrAnyYouWant) {
        w2.writeInt(0).writeVString(commentOrAnyYouWant);
        cLen++;
        return this;
    }

    public Pack125 pack(ByteList in) {
        if (in == null) throw new NullPointerException("Bytecode is null!");

        ByteReader r = new ByteReader(in);

        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }

        int version = r.readInt();
        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        try {
            pool.read(r);
        } catch (Exception e) {
            throw new RuntimeException("Corrupted constant pool: ", e);
        }
        // original id to cp id
        ByteWriter w = this.w2;
        w.writeInt(version).writeVarInt(pool.index - 1, false);
        int[] map = storeConstantPool(pool);
        for (int i = pool.index - 2; i >= 0; i--) {
            w.writeVarInt(map[i], false);
        }
        w2.writeBytes(w3);

        int len = in.limit() - r.index;
        w.writeVarInt(len, false).writeBytes(in.list, r.index + in.offset(), len);
        this.cLen++;
        return this;
    }

    public ByteList toByteArray() {
        ByteList wl = w1.list;
        int pos = wl.pos();
        wl.pos(4);
        w1.writeInt(cpLen);
        wl.pos(pos);

        ByteList list = w1.writeInt(cLen).writeBytes(w2).list;

        reset();

        return list;
    }

    public byte[] compressedByteArray() throws IOException {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            writeToStream(new DeflaterOutputStream(bo, def));
        } finally {
            def.end();
        }
        return bo.toByteArray();
    }

    public void compressedByteArray(OutputStream out) throws IOException {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            writeToStream(new DeflaterOutputStream(out, def));
        } finally {
            def.end();
        }
    }

    public void writeCompressed(OutputStream out) throws IOException {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            writeToStream(new DeflaterOutputStream(out, def));
        } finally {
            def.end();
        }
    }

    public void writeToStream(OutputStream out) throws IOException {
        ByteList wl = w1.list;
        int pos = wl.pos();
        wl.pos(4);
        w1.writeInt(cpLen);
        wl.pos(pos);

        wl.writeToStream(out);
        wl.clear();
        w1.writeInt(cLen).list.writeToStream(out);

        w2.list.writeToStream(out);

        reset();
    }

    public void reset() {
        for (int i = 0; i < 4; i++) {
            cw[i].clear();
        }
        cpLen = cLen = 0;
        w1.list.clear();
        w1.writeInt(HEADER).writeInt(0);
        w2.list.clear();
    }

    static final int STR = 0, NUM = 1, TYP = 2, NAT = 3, TYPES = 13;

    private int[] map;

    private int[] storeConstantPool(ConstantPool pool) {
        List<Constant> array = pool.array();

        LongerCst f = new LongerCst(), f1;
        int[] map = this.map;
        if (map.length < array.size()) {
            map = this.map = new int[array.size()];
        } else {
            for (int i = 0; i < array.size(); i++) {
                map[i] = 0;
            }
        }

        List<Type> tmp = new ArrayList<>();
        ByteWriter w3 = this.w3;
        for (int i = 0; i < array.size(); i++) {
            Constant c = array.get(i);
            if (c.type() == NAME_AND_TYPE) {
                CstNameAndType nat = (CstNameAndType) c;
                f.data = nat.getName().getString() + nat.getType().getString();
                if (f == (f1 = cw[NAT].intern(f))) {
                    f = LongerCst.one();

                    w3.list.clear();
                    w3.writeByte(NAME_AND_TYPE);

                    LongerCst f2;
                    String name = nat.getName().getString();
                    f.data = name;
                    if (f == (f2 = cw[STR].intern(f))) {
                        w1.writeByte(UTF).writeVString(name);
                        f.index = ++cpLen;
                        f = LongerCst.one();
                    }
                    w3.writeVarInt(f2.index, false);

                    String type = nat.getType().getString();
                    f.data = type;
                    if (f == (f2 = cw[STR].intern(f))) {
                        f = LongerCst.one();

                        int p = w3.list.pos();
                        w3.writeByte((byte) TYPES);

                        if (type.charAt(0) == '(') {
                            tmp.clear();
                            ParamHelper.parseMethod(type, tmp);
                            w3.writeVarInt(tmp.size(), false);
                            for (int j = 0; j < tmp.size(); j++) {
                                f = writeType(f, tmp.get(j), w3);
                            }
                        } else {
                            Type t = ParamHelper.parseField(type);
                            w3.writeByte((byte) 0);
                            f = writeType(f, t, w3);
                        }
                        w1.writeBytes(w3.list.list, p, w3.list.pos() - p);
                        w3.list.pos(p);
                        f2.index = ++cpLen;
                    }
                    w3.writeVarInt(f2.index, false);
                    w1.writeBytes(w3);
                    f1.index = ++cpLen;
                }

                map[i] = f1.index;
            }
        }

        w3.list.clear();
        for (int i = 0; i < array.size(); i++) {
            Constant c = array.get(i);
            if (map[i] != 0) continue;
            switch (c.type()) {
                case _TOP_:
                    f.data = new Object();
                    if (f == (f1 = cw[STR].intern(f))) {
                        w1.writeByte(_TOP_);
                        f.index = ++cpLen;
                        f = LongerCst.one();
                    }
                    map[i] = f1.index;
                    break;
                case UTF: {
                    CstUTF u = (CstUTF) c;
                    f.data = u.getString();
                    if (f == (f1 = cw[STR].intern(f))) {
                        w1.writeByte(UTF).writeVString(u.getString());
                        f.index = ++cpLen;
                        f = LongerCst.one();
                    }
                    map[i] = f1.index;
                }
                break;
                case INT:
                case FLOAT:
                case LONG:
                case DOUBLE:
                    f.data = c;
                    if (f == (f1 = cw[NUM].intern(f))) {
                        c.write(w1);
                        f.index = ++cpLen;
                        f = LongerCst.one();
                    }
                    map[i] = f1.index;
                    break;
                case CLASS:
                case STRING:
                case FIELD:
                case METHOD:
                case INTERFACE:
                case METHOD_HANDLE:
                case METHOD_TYPE:
                case DYNAMIC:
                case INVOKE_DYNAMIC:
                case MODULE:
                case PACKAGE: // they are 'ref' and there is no need to shrink u2 => u1
                    c.write(w3);
                    break;
            }
        }
        return map;
    }

    private LongerCst writeType(LongerCst f, Type t, ByteWriter tmp) {
        LongerCst f1;
        f.data = t;
        if (f == (f1 = cw[TYP].intern(f))) {
            f = LongerCst.one();

            int p = tmp.list.pos();
            tmp.writeByte((byte) TYP).writeByte(t.type).writeVarInt(t.array, false);

            if (t.owner != null) {
                f.data = t.owner;
                LongerCst f2;
                if (f == (f2 = cw[STR].intern(f))) {
                    w1.writeByte(UTF).writeVString(t.owner);
                    f.index = ++cpLen;
                    f = LongerCst.one();
                }
                tmp.writeVarInt(f2.index, false);
            }
            w1.writeBytes(tmp.list.list, p, tmp.list.pos() - p);
            tmp.list.pos(p);
            f1.index = ++cpLen;
        }
        tmp.writeVarInt(f1.index, false);
        return f;
    }
}
