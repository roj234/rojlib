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
import roj.util.ByteList;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;
import java.io.*;
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

    private final RandomAccessFile file;
    private final Deflater deflater;
    private final List<byte[]> attrList;
    private final ByteList buffer;
    private final ByteWriter bw;
    private final CRC32 crc;
    private final MyHashSet<String> duplicate;

    private boolean finish;
    private final EEOF eof;

    public ZipFileWriter(File file) throws IOException {
        this(file, Deflater.DEFAULT_COMPRESSION, true);
    }

    public ZipFileWriter(File file, boolean checkDuplicate) throws IOException {
        this(file, Deflater.DEFAULT_COMPRESSION, checkDuplicate);
    }

    public ZipFileWriter(File file, int compressionLevel, boolean checkDuplicate) throws IOException {
        this.file = new RandomAccessFile(file, "rw");
        this.file.seek(0);
        this.deflater = new Deflater(compressionLevel, true);
        this.attrList = new ArrayList<>();
        this.buffer = new ByteList();
        this.bw = new ByteWriter(buffer);
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

        crc.update(data.list, data.offset(), data.limit());

        int time = java2DosTime(System.currentTimeMillis());
        ByteList buf = this.buffer;
        buf.clear();
        bw.writeInt(HEADER_FILE)
          .writeShortR(20)
          .writeShortR(2048)
          .writeShortR(method)
          .writeIntR(time)
          .writeIntR((int) crc.getValue())
          .writeIntR(0) // cSize
          .writeIntR(data.pos())
          .writeShortR(ByteWriter.byteCountUTF8(name))
          .writeShortR(0)
          .writeAllUTF(name);
        long beginOffset = file.getFilePointer();
        file.write(buf.list, 0, buf.pos());
        buf.clear();
        long endOffset = file.getFilePointer();

        int cSize;
        if (method == ZipEntry.DEFLATED) {
            Deflater def = this.deflater;
            def.setInput(data.list, data.offset(), data.limit());
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
            cSize = data.pos();
            file.write(data.list, data.offset(), data.limit());
        }

        long curr = file.getFilePointer();
        file.seek(beginOffset + 18);
        file.writeInt(Integer.reverseBytes(cSize));
        file.seek(curr);

        boolean attrZip64 = beginOffset >= U32_MAX;
        bw.writeInt(HEADER_ATTRIBUTE)
          .writeShortR(attrZip64 ? 45 : 20)
          .writeShortR(attrZip64 ? 45 : 20)
          .writeShortR(2048)
          .writeShortR(method)
          .writeIntR(time)
          .writeIntR((int) crc.getValue())
          .writeIntR(cSize)
          .writeIntR(data.pos())
          .writeShortR(ByteWriter.byteCountUTF8(name))
          .writeShortR(attrZip64 ? 10 : 0)
          .writeShortR(0)
          .writeIntR(0) // 四个short 0
          .writeIntR(0)
          .writeIntR((int) (attrZip64 ? U32_MAX : beginOffset))
          .writeAllUTF(name);
        if (attrZip64) {
            bw.writeShortR(1).writeShortR(8).writeLongR(entryBeginOffset);
        }
        attrList.add(buf.toByteArray());
        buf.clear();

        crc.reset();
    }

    public void write(MutableZipFile owner, MutableZipFile.EFile entry) throws IOException {
        if (this.entry != null)
            closeEntry();
        long entryBeginOffset = file.getFilePointer();
        if (duplicate != null && !duplicate.add(entry.name))
            throw new ZipException("Duplicate entry " + entry.name);
        owner.getFile().getChannel().transferTo(entry.startPos(), entry.endPos() - entry.startPos(), file.getChannel());

        long delta = entryBeginOffset - entry.startPos();
        // zip64 supporting
        entry.offset += delta;
        MutableZipFile.writeAttr(bw, entry);
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

        bw.writeInt(HEADER_FILE)
            .writeShortR(20)
            .writeShortR(2048) // EFS
            .writeShortR(ze.getMethod())
            .writeIntR(MutableZipFile.java2DosTime(ze.getTime()))
            .writeIntR(0) // crc32
            .writeIntR(0) // csize
            .writeIntR(0) // usize
            .writeShortR(ByteWriter.byteCountUTF8(ze.getName()))
            .writeShortR(extra == null ? 0 : extra.length)
            .writeAllUTF(ze.getName());
        entryBeginOffset = file.getFilePointer();
        file.write(buffer.list, 0, buffer.pos());
        if (extra != null)
            file.write(extra);
        entryEndOffset = entryBeginOffset + buffer.pos();
        buffer.clear();
    }

    @Override
    public void write(int b) throws IOException {
        if (entry == null) throw new ZipException("Entry closed");
        crc.update(b);
        if (entry.getMethod() == ZipEntry.STORED) {
            file.write((byte) b);
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
                    buf.ensureCapacity(buf.pos() + 1024);
                    off = deflater.deflate(buf.list, buf.pos(), 1024);
                    buf.pos(buf.pos() + off);
                }
            } else {
                throw new ZipException("Entry asynchronously closed");
            }
        }
    }

    public void closeEntry() throws IOException {
        RandomAccessFile f = this.file;

        if (entry.getMethod() != ZipEntry.STORED) {
            f.seek(entryEndOffset);
            Deflater def = this.deflater;
            ByteList buf = this.buffer;
            f.write(buf.list, 0, buf.pos());
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

        long curr = f.getFilePointer();
        int cSize = (int) (curr - entryEndOffset);
        if (curr - entryEndOffset > U32_MAX)
            throw new ZipException("Zip64(of LOC header) is required, while ZFW not(want to) support it yet(for space reason)");
        int uSize = entry.getMethod() == ZipEntry.STORED ? cSize : (int) deflater.getBytesRead();
        deflater.reset();

        boolean attrZip64 = entryBeginOffset >= U32_MAX;
        bw.writeInt(HEADER_ATTRIBUTE)
          .writeShortR(attrZip64 ? 45 : 20)
          .writeShortR(attrZip64 ? 45 : 20)
          .writeShortR(2048) // EFS
          .writeShortR(entry.getMethod())
          .writeIntR(MutableZipFile.java2DosTime(entry.getTime()))
          .writeIntR((int) crc.getValue())
          .writeIntR(cSize)
          .writeIntR(uSize)
          .writeShortR(ByteWriter.byteCountUTF8(entry.getName()))
          .writeShortR(attrZip64 ? 12 : 0) // ext
          .writeShortR(0) // comment
          .writeShortR(0) // disk
          .writeShortR(0) // attrIn
          .writeIntR(0) // attrEx
          .writeIntR((int) (attrZip64 ? U32_MAX : entryBeginOffset))
          .writeAllUTF(entry.getName());
        if (attrZip64) {
            bw.writeShortR(1).writeShortR(8).writeLongR(entryBeginOffset);
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
        RandomAccessFile f = this.file;
        long cDirOffset = f.getFilePointer();
        List<byte[]> attrs = this.attrList;
        for (int i = 0; i < attrs.size(); i++) {
            f.write(attrs.get(i));
        }

        eof.cDirOffset = cDirOffset;
        eof.cDirLen = f.getFilePointer() - cDirOffset;
        MutableZipFile.writeEOF(bw, eof, attrs.size(), f.getFilePointer());
        f.write(buffer.list, 0, buffer.pos());

        attrList.clear();
        deflater.end();
        if (f.length() != f.getFilePointer()) // truncate too much
            f.setLength(f.getFilePointer());
        f.close();

        finish = true;
    }

    @Override
    public void close() throws IOException {
        finish();
    }
}
