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
package roj.io;

import roj.collect.MyHashMap;
import roj.collect.Unioner.Range;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Add, Remove, Modify 'Box' File
 *
 * @author Roj234
 * @version 1.0
 * @since  2021/4/5 18:14
 */
public class BoxFile implements Closeable {
    private final MyHashMap<String, F> infoMap = new MyHashMap<>();
    private final List<F> fsByOrder;
    private final RandomAccessFile rf;
    private long freeBytes;

    static final int MAGIC = 0x27462DE8;
    static final class F implements Range {
        long offset;
        int length;

        public F(long offset, int length) {
            this.offset = offset;
            this.length = length;
        }

        @Override
        public long startPos() {
            return offset;
        }

        @Override
        public long endPos() {
            return offset + length;
        }
    }

    public BoxFile(File file) throws IOException {
        rf = new RandomAccessFile(file, "rw");
        if(rf.length() < 2048) {
            rf.setLength(2048);
            rf.writeInt(MAGIC);
            rf.writeLong(2048 - 4 - 8);
        }
        fsByOrder = new ArrayList<>();
    }

    public void load() throws IOException {
        long len = rf.length();
        if(len < 2048) {
            rf.seek(0);
            rf.setLength(2048);
            rf.writeInt(MAGIC);
            rf.writeLong(2048 - 4 - 8);
        }
        rf.seek(12);
        freeBytes = rf.readLong();
        len -= freeBytes;
        long pos = 12;

        CharList tmp = new CharList(100);
        ByteList bl = new ByteList();
        while (pos < len) {
            int slen = rf.readChar();
            bl.ensureCapacity(slen);
            if(rf.read(bl.list, 0, slen) < slen) {
                throw new EOFException("Truncated data #" + pos);
            }
            bl.pos(slen);
            ByteReader.decodeUTF(-1, tmp, bl);
            String key = tmp.toString();
            tmp.clear();

            pos += 2 + slen;
            slen = rf.readInt();
            F fe = new F(pos + 4, slen);
            infoMap.put(key, fe);
            fsByOrder.add(fe);

            pos += 4 + slen;
            rf.seek(pos);
        }
    }

    public boolean contains(String name) {
        return infoMap.containsKey(name);
    }

    public ByteList get(String name, ByteList list) throws IOException {
        F arr = infoMap.get(name);
        if(arr == null)
            return null;
        rf.seek(arr.offset);
        list.ensureCapacity(arr.length);
        rf.readFully(list.list, list.offset(), arr.length);
        list.pos(arr.length);
        return list;
    }

    public byte[] getBytes(String name) throws IOException {
        ByteList bl = get(name, new ByteList());
        if(bl == null)
            return null;
        return bl.getByteArray();
    }

    public String getUTF(String name) throws IOException {
        ByteList bl = get(name, new ByteList());
        if(bl == null)
            return null;
        return ByteReader.readUTF(bl);
    }

    public boolean remove(String name) throws IOException {
        F fe = infoMap.remove(name);
        if(fe == null)
            return false;
        int i = fsByOrder.indexOf(fe);
        fsByOrder.remove(i);
        long dataEnd = fe.offset + fe.length;
        int nameBytes = ByteWriter.byteCountUTF8(name);
        if(rf.length() == dataEnd) {
            freeBytes += nameBytes + fe.length + 6;
            rf.seek(4);
            rf.writeLong(freeBytes);
        } else {
            FileChannel fc = rf.getChannel();
            // [dataEnd, rf.length] => dataBegin
            fc.position(dataEnd)
              .transferTo(fe.offset - nameBytes - 6, rf.length() - dataEnd, fc);
            int off = fe.length + nameBytes + 6;
            List<F> fs = fsByOrder;
            for (int j = i; j < fs.size(); j++) {
                fs.get(j).offset -= off;
            }
        }
        return true;
    }

    public boolean append(String name, ByteList list) throws IOException {
        if(name.length() > 65535) {
            throw new IOException("String length exceed limit 65535");
        }
        F fe = infoMap.get(name);
        if(fe == null) { // append
            long off = list.pos() + name.length() + 6 - freeBytes;
            if(off > 0) {
                rf.setLength(off + (off = rf.length()));
                if(freeBytes != 0) {
                    rf.seek(4);
                    rf.writeLong(freeBytes = 0);
                }
            } else {
                off = rf.length() - freeBytes;
                freeBytes -= off;
                rf.seek(4);
                rf.writeLong(freeBytes);
            }

            infoMap.put(name, fe = new F(off, list.pos()));
            fsByOrder.add(fe);
            rf.seek(off);
            fe = null;
        } else { // replace
            List<F> fs = fsByOrder;
            int delta = list.pos() - fe.length;
            if(fs.get(fs.size() -1) != fe) {
                rf.seek(4);
                if(freeBytes < delta) {
                    rf.setLength(rf.length() + delta - freeBytes);
                    if(freeBytes != 0) {
                        rf.writeLong(freeBytes = 0);
                    }
                } else {
                    freeBytes -= delta;
                    rf.writeLong(freeBytes);
                }

                FileChannel fc = rf.getChannel();
                long prevEnd = fe.offset + fe.length;
                fc.position(prevEnd)
                  .transferTo(fe.offset + list.pos(), rf.length() - prevEnd, fc);
                for (int j = fs.indexOf(fe) + 1; j < fs.size(); j++) {
                    fs.get(j).offset += delta;
                }
            } else {
                rf.seek(4);
                freeBytes -= delta;
                if(freeBytes < 0) {
                    rf.setLength(rf.length() - freeBytes);
                    freeBytes = 0;
                }
                rf.writeLong(freeBytes);
            }
            rf.seek(fe.offset - 4);
            rf.writeInt(fe.length = list.pos());
        }

        rf.writeShort(name.length());
        ByteList li = ByteWriter.encodeUTF(name);
        rf.write(li.list, li.offset(), li.pos());

        rf.writeInt(list.pos());
        rf.write(list.list, list.offset(), list.pos());
        return fe == null;
    }

    public Set<String> keys() {
        return infoMap.keySet();
    }

    public void clear() throws IOException {
        rf.setLength(4);
        rf.seek(0);
        rf.writeInt(MAGIC);
        infoMap.clear();
    }

    @Override
    public void close() throws IOException {
        rf.close();
    }
}
