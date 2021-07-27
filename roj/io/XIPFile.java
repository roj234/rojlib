package roj.io;

import roj.collect.MyHashSet;
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.annotation.Nullable;
import java.io.*;
import java.util.Map;
import java.util.zip.*;

/**
 * XIP Compress file
 *
 * @author Roj233
 * @since 2021/7/21 13:13
 */
public class XIPFile implements Closeable, AutoCloseable {
    final RandomAccessFile f;

    MyHashSet<Entry> entries = new MyHashSet<>();
    Entry test = new Entry();

    long indexIndex;

    Inflater inflater = new Inflater(true);
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    ByteList buffer = new ByteList();

    static final int HEADER = ((int) 'X' << 24) | ((int) 'I' << 16) | ((int) 'P' << 8) | '1';

    public XIPFile(File f) throws IOException {
        this.f = new RandomAccessFile(f, "rw");
        if(f.length() > 12) {
            readHead();
        }
    }

    public Entry getEntry(String name) {
        Entry test = this.test;
        test.name = name;
        test = entries.find(test);
        return test == this.test ? null : test;
    }

    public void getData(Entry entry, ByteList data) throws IOException {
        if(entry == null)
            return;

        if(entry.data == null) {
            f.seek(entry.offset);
            if (f.length() - f.getFilePointer() < entry.cLen)
                throw new EOFException("Reading error: unable to read " + entry.cLen + " (Remain " + (f.length() - f.getFilePointer()) + " at" + f.getFilePointer() + ")");

            switch (entry.cp) {
                case ZipEntry.DEFLATED:
                    byte[] dec = new byte[entry.uLen];
                    int decOff = 0;

                    long off = f.getFilePointer();

                    Inflater unzip = this.inflater;
                    ByteList b = buffer;
                    b.ensureCapacity(8192);
                    byte[] buf = b.list;
                    try {
                        while (true) {
                            int i = unzip.inflate(buf, decOff, dec.length - decOff);
                            if (i == 0) {
                                if (unzip.finished() || unzip.needsDictionary()) {
                                    f.seek(off + unzip.getBytesRead());
                                    break;
                                }
                                if (unzip.needsInput()) {
                                    int read = f.read(buf, 0, 8192);
                                    if (read <= 0)
                                        throw new EOFException("Before entry decompression completed");

                                    unzip.setInput(buf, 0, read);
                                }
                            } else {
                                decOff += i;
                            }
                        }
                    } catch (DataFormatException e) {
                        ZipException err = new ZipException("Data format: " + e.getMessage());
                        err.initCause(e);
                        throw err;
                    } finally {
                        unzip.reset();
                    }

                    entry.data = dec;
                case ZipEntry.STORED:
                    byte[] eee = new byte[entry.uLen];
                    f.read(eee);
                    entry.data = eee;
                default:
                    throw new ZipException("Unsupported compression method " + Integer.toHexString(entry.cp));
            }
        }
        data.addAll(entry.data);
    }

    public void setEntry(String name, Entry entry) {

    }

    public void setEntryData(Entry entry, ByteList data) {

    }

    public void setEntries(Map<Entry, ByteList> entries, boolean clear) {
        if(clear) {
            entries.clear();
        }
    }

    public void store() throws IOException {
        if(f.length() < 12) {
            f.setLength(12);
            f.writeInt(HEADER);
            f.writeLong(0);
        }

        // todo
    }

    private void readHead() throws IOException {
        if(f.readInt() != HEADER) {
            throw new IllegalArgumentException("Not a XIP file");
        }
        readIndex(indexIndex = f.readLong());
    }

    private void readIndex(long index) throws IOException {
        f.seek(index);
        int len = f.readInt();
        ByteList bl = this.buffer;
        bl.ensureCapacity(36);
        ByteReader r = new ByteReader(bl);
        for (int i = 0; i < len; i++) {
            bl.pos(f.read(bl.list, 0, 36));

            Entry entry = new Entry();

            int nameLen = r.readVarInt(false);
            entry.offset = r.readVarLong(false);
            entry.cLen = r.readVarInt(false);
            entry.uLen = r.readVarInt(false);
            entry.modifyTime = r.readLong();
            entry.CRC32 = r.readInt();
            entry.cp = r.readByte();

            index += r.index;
            f.seek(index);
            bl.ensureCapacity(nameLen);
            if(f.read(bl.list, 0, nameLen) != nameLen) {
                throw new EOFException("offset " + index + ", len " + nameLen);
            }
            index += nameLen;
            bl.pos(nameLen);
            r.index = 0;
            entry.name = r.readUTF0(nameLen);

            entries.add(entry);

            r.index = 0;
        }

        int commentLen = f.readUnsignedShort();
        if(commentLen > 0) {
            bl.ensureCapacity(commentLen);
            if (f.read(bl.list, 0, commentLen) != commentLen) {
                throw new EOFException("offset " + index + ", len " + commentLen);
            }
            bl.pos(commentLen);
            r.index = 0;
            comment = r.readUTF0(commentLen);
        } else {
            comment = "";
        }
    }

    @Nullable
    public String comment;

    static class Entry {
        public String name = ""; // 128 * 128 => 2
        private long offset; // 9
        private int cLen, uLen; // 10
        public long modifyTime; // 8
        private int CRC32; // 4
        public byte cp; // 1

        @Nullable
        public byte[] data;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return name.equals(entry.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    @Override
    public void close() throws IOException {
        f.close();
        inflater.end();
        deflater.end();
    }
}
