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
package roj.io;

import roj.collect.MyHashSet;
import roj.io.source.RandomAccessFileSource;
import roj.io.source.Source;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import static roj.io.MutableZipFile.*;

/**
 * Zip [File] Writer
 *
 * @author solo6975
 * @version 1.0
 * @since 2021/10/5 13:54
 */
public class ZipFileWriter extends OutputStream implements Closeable, AutoCloseable {
    static final byte[] DEFAULT_COMMENT = c2b("By Roj234's ZipFileWriter");
    private static byte[] c2b(String s) {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            b[i] = (byte) s.charAt(i);
        }
        return b;
    }

    private final Source file;
    private final Deflater deflater;
    private final List<byte[]> attrList;
    private final ByteList buffer;
    private final CRC32 crc;
    private final MyHashSet<String> duplicate;

    private boolean finish;
    private final EEOF eof;

    public ZipFileWriter(File file) throws IOException {
        this(new RandomAccessFileSource(file), Deflater.DEFAULT_COMPRESSION, true);
    }

    public ZipFileWriter(File file, boolean checkDuplicate) throws IOException {
        this(new RandomAccessFileSource(file), Deflater.DEFAULT_COMPRESSION, checkDuplicate);
    }

    public ZipFileWriter(Source file, int compressionLevel, boolean checkDuplicate) throws IOException {
        this.file = file;
        this.file.seek(0);
        this.deflater = new Deflater(compressionLevel, true);
        this.attrList = new ArrayList<>();
        this.buffer = new ByteList();
        this.crc = new CRC32();
        this.eof = new EEOF();
        this.eof.setComment(DEFAULT_COMMENT);
        this.duplicate = checkDuplicate ? new MyHashSet<>() : null;
    }

    public void setComment(String comment) {
        eof.setComment(comment);
    }

    public void setComment(byte[] comment) {
        eof.setComment(comment);
    }

    public void writeNamed(String name, ByteList data) throws IOException {
        writeNamed(name, data, ZipEntry.DEFLATED);
    }

    public void writeNamed(String name, ByteList data, int method) throws IOException {
        if (entry != null)
            closeEntry();
        if (name.endsWith("/"))
            throw new ZipException("ZipEntry couldn't be directory");
        if (duplicate != null && !duplicate.add(name))
            throw new ZipException("Duplicate entry " + name);

        crc.update(data.list, data.arrayOffset(), data.limit());

        int time = java2DosTime(System.currentTimeMillis());
        ByteList buf = this.buffer;
        buf.clear();
        buf.putInt(HEADER_FILE)
          .putShortLE(20)
          .putShortLE(2048)
          .putShortLE(method)
          .putIntLE(time)
          .putIntLE((int) crc.getValue())
          .putIntLE(0) // cSize
          .putIntLE(data.wIndex())
          .putShortLE(ByteList.byteCountUTF8(name))
          .putShortLE(0)
          .putUTFData(name);
        long beginOffset = file.position();
        file.write(buf.list, 0, buf.wIndex());
        buf.clear();
        long endOffset = file.position();

        int cSize;
        if (method == ZipEntry.DEFLATED) {
            Deflater def = this.deflater;
            def.setInput(data.list, data.arrayOffset(), data.limit());
            def.finish();
            buf.ensureCapacity(8192);
            while (!def.finished()) {
                int off = deflater.deflate(buf.list, 0, 8192);
                file.write(buf.list, 0, off);
            }

            // obviously < 2G
            cSize = (int) def.getBytesWritten();
            def.reset();
        } else {
            cSize = data.wIndex();
            file.write(data.list, data.arrayOffset(), data.limit());
        }

        long curr = file.position();
        file.seek(beginOffset + 18);
        buf.putIntLE(cSize);
        file.write(buf.list, 0, 4);
        buf.clear();
        file.seek(curr);

        boolean attrZip64 = beginOffset >= U32_MAX;
        buf.putInt(HEADER_ATTRIBUTE)
          .putShortLE(attrZip64 ? 45 : 20)
          .putShortLE(attrZip64 ? 45 : 20)
          .putShortLE(2048)
          .putShortLE(method)
          .putIntLE(time)
          .putIntLE((int) crc.getValue())
          .putIntLE(cSize)
          .putIntLE(data.wIndex())
          .putShortLE(ByteList.byteCountUTF8(name))
          .putShortLE(attrZip64 ? 10 : 0)
          .putShortLE(0)
          .putIntLE(0) // 四个short 0
          .putIntLE(0)
          .putIntLE((int) (attrZip64 ? U32_MAX : beginOffset))
          .putUTFData(name);
        if (attrZip64) {
            buf.putShortLE(1).putShortLE(8).putLongLE(entryBeginOffset);
        }
        attrList.add(buf.toByteArray());
        buf.clear();

        crc.reset();
    }

    public void write(MutableZipFile owner, MutableZipFile.EFile entry) throws IOException {
        if (this.entry != null) closeEntry();
        long entryBeginOffset = file.position();
        if (duplicate != null && !duplicate.add(entry.name))
            throw new ZipException("Duplicate entry " + entry.name);
        WritableByteChannel channel = file.channel();
        if (channel != null) {
            owner.getFile().getChannel().transferTo(entry.startPos(), entry.endPos() - entry.startPos(), channel);
        } else {
            RandomAccessFile src = owner.getFile();
            src.seek(entry.startPos());
            int max = Math.max(4096, buffer.list.length);
            buffer.ensureCapacity(max);

            byte[] list = buffer.list;
            int len = (int) (entry.endPos() - entry.startPos());
            while (len > 0) {
                src.readFully(list, 0, max);
                file.write(list, 0, max);
                len -= max;
            }
        }

        long delta = entryBeginOffset - entry.startPos();
        // zip64 supporting
        entry.offset += delta;
        MutableZipFile.writeAttr(buffer, entry);
        entry.offset -= delta;
        attrList.add(buffer.toByteArray());
        buffer.clear();
    }

    private ZipEntry entry;
    private long     entryBeginOffset, entryEndOffset;

    public void beginEntry(ZipEntry ze) throws IOException {
        if (entry != null)
            closeEntry();
        if (ze.isDirectory())
            throw new ZipException("Roj234: I don't want to support directories.");
        if (duplicate != null && !duplicate.add(ze.getName()))
            throw new ZipException("Duplicate entry " + ze.getName());
        entry = ze;
        // noinspection all
        if (ze.getMethod() == -1)
            ze.setMethod(ZipEntry.DEFLATED);

        byte[] extra = ze.getExtra();

        buffer.putInt(HEADER_FILE)
          .putShortLE(20)
          .putShortLE(2048) // EFS
          .putShortLE(ze.getMethod())
          .putIntLE(MutableZipFile.java2DosTime(ze.getTime()))
          .putIntLE(0) // crc32
          .putIntLE(0) // csize
          .putIntLE(0) // usize
          .putShortLE(ByteList.byteCountUTF8(ze.getName()))
          .putShortLE(extra == null ? 0 : extra.length)
          .putUTFData(ze.getName());
        entryBeginOffset = file.position();
        file.write(buffer.list, 0, buffer.wIndex());
        if (extra != null)
            file.write(extra);
        entryEndOffset = entryBeginOffset + buffer.wIndex();
        buffer.clear();
    }

    @Override
    @Deprecated
    public void write(int b) throws IOException {
        if (entry == null) throw new ZipException("Entry closed");
        crc.update(b);
        if (entry.getMethod() == ZipEntry.STORED) {
            file.write(new byte[] {(byte) b}, 0, 1);
        } else {
            throw new IOException("Are you **?");
        }
    }

    @Override
    public void write(@Nonnull byte[] b, int off, int len) throws IOException {
        if (entry == null) throw new ZipException("Entry closed");
        crc.update(b, off, len);
        if (entry.getMethod() == ZipEntry.STORED) {
            file.write(b, off, len);
        } else {
            Deflater def = this.deflater;

            if (!def.finished()) {
                def.setInput(b, off, len);
                ByteList buf = this.buffer;
                while (!def.needsInput()) {
                    buf.ensureCapacity(buf.wIndex() + 1024);
                    off = deflater.deflate(buf.list, buf.wIndex(), 1024);
                    buf.wIndex(buf.wIndex() + off);
                }
            } else {
                throw new ZipException("Entry asynchronously closed");
            }
        }
    }

    public void closeEntry() throws IOException {
        Source f = this.file;

        if (entry.getMethod() != ZipEntry.STORED) {
            f.seek(entryEndOffset);
            Deflater def = this.deflater;
            ByteList buf = this.buffer;
            f.write(buf.list, 0, buf.wIndex());
            buf.clear();
            if (!def.finished()) {
                def.finish();
                buf.ensureCapacity(1024);
                while (!def.finished()) {
                    int off = deflater.deflate(buf.list, 0, 1024);
                    f.write(buf.list, 0, off);
                }
            }
            // 没等于号，毕竟也可能正好是这个数
            if (def.getBytesRead() > U32_MAX)
                throw new ZipException("Zip64(of LOC header) is required, while ZFW not(want to) support it yet(for space reason)");
        }

        long curr = f.position();
        int cSize = (int) (curr - entryEndOffset);
        if (curr - entryEndOffset > U32_MAX)
            throw new ZipException("Zip64(of LOC header) is required, while ZFW not(want to) support it yet(for space reason)");
        int uSize = entry.getMethod() == ZipEntry.STORED ? cSize : (int) deflater.getBytesRead();
        deflater.reset();

        boolean attrZip64 = entryBeginOffset >= U32_MAX;
        buffer.putInt(HEADER_ATTRIBUTE)
          .putShortLE(attrZip64 ? 45 : 20)
          .putShortLE(attrZip64 ? 45 : 20)
          .putShortLE(2048) // EFS
          .putShortLE(entry.getMethod())
          .putIntLE(MutableZipFile.java2DosTime(entry.getTime()))
          .putIntLE((int) crc.getValue())
          .putIntLE(cSize)
          .putIntLE(uSize)
          .putShortLE(ByteList.byteCountUTF8(entry.getName()))
          .putShortLE(attrZip64 ? 12 : 0) // ext
          .putShortLE(0) // comment
          .putShortLE(0) // disk
          .putShortLE(0) // attrIn
          .putIntLE(0) // attrEx
          .putIntLE((int) (attrZip64 ? U32_MAX : entryBeginOffset))
          .putUTFData(entry.getName());
        if (attrZip64) {
            buffer.putShortLE(1).putShortLE(8).putLongLE(entryBeginOffset);
        }
        attrList.add(buffer.toByteArray());

        f.seek(entryBeginOffset + 14);
        f.write(buffer.list, 16, 12);
        f.seek(curr);
        buffer.clear();

        crc.reset();
        entry = null;
    }

    public void finish() throws IOException {
        if (finish) return;
        if (entry != null)
            closeEntry();
        Source f = this.file;
        long cDirOffset = f.position();
        List<byte[]> attrs = this.attrList;
        for (int i = 0; i < attrs.size(); i++) {
            f.write(attrs.get(i));
        }

        eof.cDirOffset = cDirOffset;
        eof.cDirLen = f.position() - cDirOffset;
        MutableZipFile.writeEOF(buffer, eof, attrs.size(), f.position());
        f.write(buffer.list, 0, buffer.wIndex());

        attrList.clear();
        deflater.end();
        if (f.length() != f.position()) // truncate too much
            f.setLength(f.position());
        f.close();

        finish = true;
    }

    @Override
    public void close() throws IOException {
        finish();
    }
}
