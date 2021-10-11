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

import roj.collect.LinkedMyHashMap;
import roj.collect.Unioner.Range;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.*;
import java.util.Iterator;
import java.util.Set;

/**
 * Add, Remove, Modify 'Box' File
 *
 * @author Roj234
 * @version 1.0
 * @since  2021/4/5 18:14
 */
public class BoxFile implements Closeable {
    public static void main(String[] args) throws IOException {
        File file = new File("t.bf");
        BoxFile bf = new BoxFile(file);
        bf.append("ETag", ByteWriter.encodeUTF("11111"));
        bf.append("Last-Modified", ByteWriter.encodeUTF("ddddddddddddddd"));
        System.out.println(bf.getUTF("Last-Modified"));
        System.out.println(bf.getUTF("ETag"));
        System.out.println("===========");
        bf.load();
        bf.append("ETag", ByteWriter.encodeUTF("222"));
        bf.append("Last-Modified", ByteWriter.encodeUTF("dddddddddddddddddddddddddddddddddddddddd"));
        System.out.println(bf.getUTF("Last-Modified"));
        System.out.println(bf.getUTF("ETag"));
        System.out.println("===========");
        bf.load();
        bf.remove("ETag");
        System.out.println(bf.getUTF("Last-Modified"));
        System.out.println(bf.getUTF("ETag"));
        System.out.println("===========");
    }

    private final LinkedMyHashMap<String, F> infoMap = new LinkedMyHashMap<>();
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

        @Override
        public String toString() {
            return "F{" + "off=" + offset + ", len=" + length + '}';
        }
    }

    public BoxFile(File file) throws IOException {
        rf = new RandomAccessFile(file, "rw");
        if (rf.length() >= 12) {
            load();
        } else {
            rf.setLength(12);
            rf.seek(0);
            rf.writeInt(MAGIC);
            rf.writeLong(0);
        }
    }

    public BoxFile(File file, int initialFreeBytes) throws IOException {
        rf = new RandomAccessFile(file, "rw");
        if(rf.length() < initialFreeBytes) {
            rf.setLength(initialFreeBytes);
            rf.seek(0);
            rf.writeInt(MAGIC);
            rf.writeLong(freeBytes = initialFreeBytes - 4 - 8);
        } else {
            load();
        }
    }

    public void load() throws IOException {
        infoMap.clear();
        rf.seek(0);
        if (MAGIC != rf.readInt()) {
            throw new IOException("Wrong magic number");
        }
        long end = rf.length() - (freeBytes = rf.readLong());
        long pos = 12;

        CharList tmp = new CharList(100);
        ByteList bl = new ByteList(0);
        while (pos < end) {
            int slen = rf.readChar();
            bl.ensureCapacity(slen);
            if(rf.read(bl.list, 0, slen) < slen) {
                throw new EOFException("Truncated data #" + pos + " prev " + infoMap.lastEntry() + ", sl " + slen);
            }
            bl.pos(slen);
            ByteReader.decodeUTF(-1, tmp, bl);
            String key = tmp.toString();
            tmp.clear();

            pos += 2 + slen;
            slen = rf.readInt();
            F fe = new F(pos + 4, slen);
            infoMap.put(key, fe);

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
        rf.readFully(list.list, 0, arr.length);
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

    public long getOffset(String key) {
        F arr = infoMap.get(key);
        if(arr == null)
            return -1;
        return arr.offset;
    }

    public boolean remove(String name) throws IOException {
        F fe = infoMap.get(name);
        if(fe == null)
            return false;
        int nameBytes = ByteWriter.byteCountUTF8(name);

        int off = fe.length + nameBytes + 6;
        if (infoMap.lastEntry().v != fe) {
            long dataEnd = fe.offset + fe.length;
            FileUtil.transferFileSelf(rf.getChannel(), dataEnd,
                                      fe.offset - nameBytes - 6,
                                      rf.length() - freeBytes - dataEnd);

            Iterator<F> itr = infoMap.values().iterator();
            while (itr.hasNext()) {
                if (itr.next() == fe) {
                    itr.remove();
                    break;
                }
            }
            while (itr.hasNext()) {
                itr.next().offset -= off;
            }
        } else {
            infoMap.remove(name);
        }

        freeBytes += off;
        rf.seek(4);
        rf.writeLong(freeBytes);
        return true;
    }

    public boolean append(String name, ByteList list) throws IOException {
        if(name.length() > 65535) {
            throw new IOException("String length exceed limit 65535");
        }
        F fe = infoMap.get(name);
        if(fe == null) { // append
            long off = rf.length() - freeBytes;
            long dataLen = list.pos() + ByteWriter.byteCountUTF8(name) + 6;
            if(dataLen > freeBytes) {
                if(freeBytes != 0) {
                    rf.seek(4);
                    rf.writeLong(freeBytes = 0);
                }
            } else {
                freeBytes -= dataLen;
                rf.seek(4);
                rf.writeLong(freeBytes);
            }

            infoMap.put(name, new F(off + dataLen - list.pos(), list.pos()));
            rf.seek(off);

            rf.writeShort(name.length());
            ByteList li = ByteWriter.encodeUTF(name);
            rf.write(li.list, 0, li.pos());
            rf.writeInt(list.pos());
        } else { // replace
            int delta = list.pos() - fe.length;
            if(infoMap.lastEntry().v != fe) {
                long prevEnd = fe.offset + fe.length;

                FileUtil.transferFileSelf(rf.getChannel(), prevEnd,
                                          fe.offset + list.pos(),
                                          rf.length() - freeBytes - prevEnd);

                if (delta != 0) {
                    Iterator<F> itr = infoMap.values().iterator();
                    while (itr.hasNext()) {
                        if (itr.next() == fe) break;
                    }
                    while (itr.hasNext()) {
                        itr.next().offset += delta;
                    }
                }
            }
            rf.seek(4);
            if (freeBytes < delta) {
                if (freeBytes != 0) {
                    rf.writeLong(freeBytes = 0);
                }
            } else {
                freeBytes -= delta;
                rf.writeLong(freeBytes);
            }

            rf.seek(fe.offset - 4);
            rf.writeInt(fe.length = list.pos());
        }

        rf.write(list.list, list.offset(), list.limit());
        return fe == null;
    }

    public Set<String> keys() {
        return infoMap.keySet();
    }

    public void clear() throws IOException {
        rf.setLength(12);
        rf.seek(0);
        rf.writeInt(MAGIC);
        rf.writeLong(0);
        infoMap.clear();
    }

    @Override
    public void close() throws IOException {
        rf.close();
    }
}
