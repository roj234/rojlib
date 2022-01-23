/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.net.gay;

import roj.collect.MyHashMap;
import roj.crypt.SM3;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * @author solo6975
 * @since 2022/1/2 23:04
 */
public final class Ver {
    static final int HEADER = 0xABDF18DB;
    static final FileRecord REMOVED = new FileRecord();

    static final class FileRecord implements Cloneable {
        long lastMod, size;
        byte[] data, hash;

        public FileRecord clone() {
            try {
                return (FileRecord) super.clone();
            } catch (CloneNotSupportedException e) {
                Helpers.athrow(e);
                return Helpers.nonnull();
            }
        }
    }

    private final Repository owner;
    private final MyHashMap<String, FileRecord> files = new MyHashMap<>();
    private boolean                             patch;

    Ver(Repository owner) {
        this.owner = owner;
    }

    public void readFresh() throws IOException {
        files.clear();

        int pathLen = owner.pathLen;

        SM3 sm3 = new SM3();
        for (File file : owner.getFiles()) {
            FileRecord fr = new FileRecord();
            fr.data = IOUtil.read(file);
            fr.hash = sm3.digest(fr.data);
            fr.lastMod = file.lastModified();
            fr.size = file.length();
            files.put(file.getAbsolutePath().substring(pathLen), fr);
        }
        patch = false;
    }

    public void save(File to) throws IOException {
        ByteList bl = new ByteList();

        try (OutputStream fos = new DeflaterOutputStream(new FileOutputStream(to))) {
            save(bl);
            bl.writeToStream(fos);
        }
    }

    public void save(ByteList w) {
        long t = System.currentTimeMillis();
        w.putInt(HEADER)
         .putBool(patch)
         .putLong(t).putVarInt(files.size(), false);

        List<String> removed = new ArrayList<>();
        for(Map.Entry<String, FileRecord> entry : files.entrySet()) {
            FileRecord v = entry.getValue();
            if (v.data == null) {
                removed.add(entry.getKey());
                continue;
            }
            w.putVarIntUTF(entry.getKey())
              .putVarLong(v.size, false)
              .putVarLong(v.lastMod - t)
              .put(v.hash)
              .putVarInt(v.data.length, false)
              .put(v.data);
        }
        if (removed.isEmpty()) return;
        if (!patch) System.err.println("Unexcepted delta: " + removed);
        w.put((byte) 0); // zero-length chars
        for (int i = 0; i < removed.size(); i++) {
            w.putVarIntUTF(removed.get(i));
        }
    }

    public void read(File file) throws IOException {
        ByteList r;
        try (InputStream fis = new InflaterInputStream(new FileInputStream(file))) {
            r = IOUtil.getSharedByteBuf().readStreamFully(fis);
        }

        r.rIndex = 0;
        read(r);
    }

    public void read(ByteList r) {
        files.clear();
        if (r.readInt() != HEADER) return;
        patch = r.readBoolean();
        long dt = r.readLong();
        int len = r.readVarInt(false);
        while (len-- > 0) {
            String k = r.readVarIntUTF();
            if (k.isEmpty()) break;
            FileRecord fr = new FileRecord();
            fr.size = r.readVarLong(false);
            fr.lastMod = r.readVarLong() + dt;
            r.readBytes(fr.hash = new byte[32]);
            r.readBytes(fr.data = new byte[r.readVarInt(false)]);
            files.put(k, fr);
        }
        if (len > 0) {
            if (!patch)
                throw new IllegalStateException("Unexpected DELTA identifier");
            while (len-- > 0) {
                String k = r.readVarIntUTF();
                if (k.isEmpty()) throw new IllegalStateException("Unexpected EMPTY string");
                files.put(k, REMOVED);
            }
        }
    }

    public int deltaToCur(boolean force, boolean merge) throws IOException {
        int dt = 0;
        MyHashMap<String, FileRecord> removed = new MyHashMap<>(files);

        int pathLen = owner.pathLen;
        SM3 sm3 = new SM3();
        for (File file : owner.getFiles()) {
            FileRecord vf = new FileRecord();
            vf.size = file.length();
            vf.lastMod = file.lastModified();

            String rel = file.getAbsolutePath().substring(pathLen);

            FileRecord sf = removed.remove(rel);
            if (sf == null || sf.size != vf.size) {
                vf.data = IOUtil.read(file);
                vf.hash = sm3.digest(vf.data);
                files.put(rel, vf);
            } else if (sf.lastMod != vf.lastMod || force) {
                vf.data = IOUtil.read(file);
                vf.hash = sm3.digest(vf.data);
                if (!Arrays.equals(vf.hash, sf.hash)) {
                    files.put(rel, vf);
                } else if (!merge) {
                    files.remove(rel);
                }
            } else if (!merge) {
                files.remove(rel);
            }
        }
        if (!merge) {
            for(String key : removed.keySet()) {
                files.put(key, REMOVED);
            }
        }
        patch = !merge;
        return dt + removed.size();
    }

    /**
     * 如果我想变成v1我要怎么办
     */
    public int deltaTo(Ver v1, boolean merge) {
        int dt = 0;
        MyHashMap<String, FileRecord> removed = new MyHashMap<>(files);
        for(Map.Entry<String, FileRecord> entry : v1.files.entrySet()) {
            String rel = entry.getKey();
            FileRecord vf = entry.getValue();

            FileRecord sf = removed.remove(rel);
            if (sf == null || sf.size != vf.size ||
                    sf.lastMod != vf.lastMod || !Arrays.equals(vf.hash, sf.hash)) {
                if (vf.hash != null) {
                    files.put(rel, vf.clone());
                }
                dt++;
            } else if (!merge) {
                files.remove(rel);
            }
        }
        if (!merge) {
            for(String key : removed.keySet()) {
                files.put(key, REMOVED);
            }
        }
        patch = !merge;
        return dt + removed.size();
    }

    public boolean isPatch() {
        return patch;
    }

    public void apply() throws IOException {
        if (!patch) throw new IllegalStateException("Not at DELTA state");
        IOException ioe = null;

        List<String> entries = new ArrayList<>();
        for(Map.Entry<String, FileRecord> entry : files.entrySet()) {
            File file = new File(owner.basePath, entry.getKey());

            FileRecord v = entry.getValue();
            if (v.data == null) {
                if (!file.delete()) {
                    IOException e = new IOException("Failed to delete " + entry.getKey());
                    if (ioe == null) ioe = e;
                    else ioe.addSuppressed(e);
                }
            } else {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(v.data);
                } catch (IOException e) {
                    if (ioe == null) ioe = e;
                    else ioe.addSuppressed(e);
                }
            }
        }

        if (ioe != null)
            throw ioe;
    }

    public static void genPatch(Ver a, Ver b, ByteList w) {
        MyHashMap<String, FileRecord> removed = new MyHashMap<>(a.files);

        int dt = 0;
        int pos = w.wIndex();
        w.putInt(0);
        for(Map.Entry<String, FileRecord> entry : b.files.entrySet()) {
            String rel = entry.getKey();
            FileRecord vf = entry.getValue();

            FileRecord sf = removed.remove(rel);
            if (sf == null || sf.size != vf.size ||
                    sf.lastMod != vf.lastMod || !Arrays.equals(vf.hash, sf.hash)) {
                if (vf.hash != null) {
                    w.putVarIntUTF(rel);
                    delta(sf, vf, w);
                    dt++;
                }
            }
        }

        if (!removed.isEmpty()) {
            w.put((byte) 0); // zero-length chars
            for (String key : removed.keySet()) {
                w.putVarIntUTF(key);
            }
        }
        int pos1 = w.wIndex();
        w.wIndex(pos);
        w.putInt(dt + removed.size())
         .wIndex(pos1);
    }

    private static void delta(FileRecord from, FileRecord to, ByteList w) {
        w.put((byte) 0);
        w.putVarInt(to.data.length, false)
         .put(to.data);
    }

    private static void unDelta(File basePath, String rel, ByteList r) throws IOException {
        File file = new File(basePath, rel);
        switch (r.readByte()) {
            case 0: // DIRECT COPY
                ByteList dg = r.slice(r.readVarInt(false));
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    dg.writeToStream(fos);
                }
                break;
            case 1: // DIFF PATCH
                ByteList src = IOUtil.getSharedByteBuf();
                src.clear();
                src.readStreamFully(new FileInputStream(file));
                ByteList patch = r.slice(r.readInt());
                patch(patch, src, new FileOutputStream(file));
                break;
                // More methods waiting for u
        }
    }

    private static void patch(ByteList r, ByteList src, OutputStream dst) throws IOException {
        while(true) {
            int cmd = r.readUnsignedByte();
            if (cmd == 0) {
                return;
            }

            if (cmd <= 246) {
                append(cmd, r, dst);
            } else {
                int len, off;
                switch(cmd) {
                    case 247:
                        append(r.readUnsignedShort(), r, dst);
                        break;
                    case 248:
                        append(r.readInt(), r, dst);
                        break;
                    case 249:
                        off = r.readUnsignedShort();
                        len = r.readUnsignedByte();
                        copy(off, len, src, dst);
                        break;
                    case 250:
                        off = r.readUnsignedShort();
                        len = r.readUnsignedShort();
                        copy(off, len, src, dst);
                        break;
                    case 251:
                        off = r.readUnsignedShort();
                        len = r.readInt();
                        copy(off, len, src, dst);
                        break;
                    case 252:
                        off = r.readInt();
                        len = r.readUnsignedByte();
                        copy(off, len, src, dst);
                        break;
                    case 253:
                        off = r.readInt();
                        len = r.readUnsignedShort();
                        copy(off, len, src, dst);
                        break;
                    case 254:
                        off = r.readInt();
                        len = r.readInt();
                        copy(off, len, src, dst);
                        break;
                    case 255:
                        long loffset = r.readLong();
                        if(loffset >= Integer.MAX_VALUE)
                            throw new ArrayIndexOutOfBoundsException("Long param 0xFF is not supported");
                        off = (int) loffset;
                        len = r.readInt();
                        copy(off, len, src, dst);
                        break;
                    default:
                        throw new IllegalStateException("command " + cmd);
                }
            }
        }
    }

    private static void copy(int off, int len, ByteList src, OutputStream dst) throws IOException {
        dst.write(src.list, off, len);
    }

    private static void append(int len, ByteList patch, OutputStream dst) throws IOException {
        dst.write(patch.list, patch.rIndex, len);
        patch.rIndex += len;
    }

    public static void applyPatch(ByteList r, File basePath) throws IOException {
        IOException ioe = null;

        int len = r.readInt();
        while (len-- > 0) {
            String rel = r.readVarIntUTF();
            if (rel.isEmpty()) break;
            unDelta(basePath, rel, r);
        }
        while (len-- > 0) {
            String rel = r.readVarIntUTF();
            if (!new File(basePath, rel).delete()) {
                IOException e = new IOException("Failed to delete " + rel);
                if (ioe == null) ioe = e;
                else ioe.addSuppressed(e);
            }
        }
        if (ioe != null)
            throw ioe;
    }

    public Ver copy() {
        Ver v1 = new Ver(owner);
        v1.patch = patch;
        v1.files.putAll(files);
        return v1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (patch) sb.append("补丁包\n");
        for(Map.Entry<String, FileRecord> entry : files.entrySet()) {
            FileRecord v = entry.getValue();
            sb.append(v.data == null ? " 删除 " : " 更新 ").append(entry.getKey()).append("\n");
        }
        return sb.toString();
    }
}
