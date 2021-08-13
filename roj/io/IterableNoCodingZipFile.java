package roj.io;

import roj.text.CharList;
import roj.util.ByteList;

import java.io.*;
import java.util.ArrayList;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import static roj.io.MutableZipFile.*;

/**
 * 你别指望这东西能打开分卷压缩文件，没有ZIP64，也没有EXTTag，别做梦了 <BR>
 * 我要做一个XIP压缩文件
 *
 * @author Roj233
 * @since 2021/7/10 17:09
 */
public class IterableNoCodingZipFile implements Closeable, AutoCloseable {
    File file;
    RandomAccessFile zip;
    ArrayList<EFile> entries = new ArrayList<>();

    ByteList buffer = new ByteList();
    Inflater inflater = new Inflater(true);

    public IterableNoCodingZipFile(File file) throws IOException {
        this.file = file;
        zip = new RandomAccessFile(file, "r");
        zip.seek(0);
        try {
            readInternal();
        } catch (EOFException e) {
            throw (ZipException) new ZipException("Unexpected EOF at " + zip.getFilePointer()).initCause(e);
        }
    }

    private void readInternal() throws IOException {
        buffer.ensureCapacity(64);
        CharList out = new CharList();

        long len = zip.length();
        cyl:
        while (zip.getFilePointer() < len) {
            int header = zip.readInt();
            switch (header) {
                case HEADER_EOF:
                    readEOF(out);
                    break cyl;
                case HEADER_ATTRIBUTE:
                    readAttr(out);
                    break;
                case HEADER_FILE:
                    readFile(out);
                    break;
                default:
                    throw new ZipException("Unexpected ZIP Header: " + Integer.toHexString(header));
            }
        }
    }

    static final byte[] EMPTY = new byte[0];

    private void readFile(CharList out) throws IOException {
        byte[] buf = buffer.list;
        zip.read(buf, 0, 26);
        EFile entry = new EFile();

        int cp = entry.compressMethod = (buf[4] & 0xFF) | (buf[5] & 0xFF) << 8;
        int cSize = entry.cSize = (buf[14] & 0xFF) | (buf[15] & 0xFF) << 8 | (buf[16] & 0xFF) << 16 | (buf[17] & 0xFF) << 24;
        entry.uSize = buf[18] | buf[19] << 8 | buf[20] << 16 | buf[21] << 24;

        int nameLen = (buf[22] & 0xFF) | (buf[23] & 0xFF) << 8;
        int extraLen = (buf[24] & 0xFF) | (buf[25] & 0xFF) << 8;

        buffer.ensureCapacity(nameLen);
        zip.read(buf = buffer.list, 0, nameLen);

        buffer.pos(nameLen);
        entry.name = buffer.toByteArray();

        entries.add(entry);

        zip.skipBytes(extraLen);

        long off = zip.getFilePointer();

        if(off >= zip.length())
            throw new EOFException();

        entry.offset = off;

        // ... skip method, really
        if(cSize == 0 && (cp & 8) != 0) {
            Inflater inflater = this.inflater;

            ByteList b = buffer;
            b.ensureCapacity(1024);
            byte[] buf1 = b.list;
            try {
                ByteList out11 = new ByteList();
                out11.ensureCapacity(1024);
                while (true) {
                    int done = inflater.inflate(buf1, out11.pos(), out11.list.length - out11.pos());
                    out11.pos(out11.pos() + done);
                    if(out11.remaining() < 512)
                        out11.ensureCapacity(out11.pos() + 1024);
                    if (done == 0) {
                        if (inflater.finished() || inflater.needsDictionary()) {
                            entry.data = out11.toByteArray();
                            zip.seek(off + inflater.getBytesRead());
                            zip.read(buf1, 0, 16);
                            int sig = (buf1[3] & 0xFF) | (buf1[2] & 0xFF) << 8 | (buf1[1] & 0xFF) << 16 | (buf1[0] & 0xFF) << 24;
                            if (sig != HEADER_EXT) {
                                zip.seek(off + inflater.getBytesRead() + 12);
                            }
                            break;
                        }
                        if (inflater.needsInput()) {
                            int read = zip.read(buf1, 0, 1024);
                            if (read <= 0)
                                throw new EOFException("Before entry decompression completed");

                            inflater.setInput(buf1, 0, read);
                        }
                    }
                }
            } catch (DataFormatException e) {
                ZipException err = new ZipException("Data format: " + e.getMessage());
                err.initCause(e);
                throw err;
            } finally {
                inflater.reset();
            }
        } else {
            zip.seek(off + cSize);
        }
    }

    private void readAttr(CharList out) throws IOException {
        byte[] buf = buffer.list;
        zip.read(buf, 0, 42);

        int nameLen = (buf[24] & 0xFF) | (buf[25] & 0xFF) << 8;
        int extraLen = (buf[26] & 0xFF) | (buf[27] & 0xFF) << 8;
        int commentLen = (buf[28] & 0xFF) | (buf[29] & 0xFF) << 8;

        zip.skipBytes(nameLen + extraLen + commentLen);

        long off = zip.getFilePointer();
        if(off >= zip.length())
            throw new EOFException();
    }

    private void readEOF(CharList cl) throws IOException {
        byte[] buf = buffer.list;
        if(zip.read(buf, 0, 18) < 18)
            throw new EOFException();

        int commentLen = (buf[16] & 0xFF) | (buf[17] & 0xFF) << 8;
        zip.skipBytes(commentLen);
    }

    public byte[] getFileData(EFile file) throws IOException {
        if(file.data != null)
            return file.data;

        zip.seek(file.offset);
        buffer.ensureCapacity(file.cSize);
        if(zip.read(buffer.list, 0, file.cSize) < file.cSize)
            throw new IOException("Reading error: not read " + file.cSize);

        switch (file.compressMethod) {
            case ZipEntry.DEFLATED:
                Inflater inflater = this.inflater;
                inflater.setInput(buffer.list, 0, file.cSize);
                byte[] dec = new byte[file.uSize];
                try {
                    if(inflater.inflate(dec, 0, dec.length) < dec.length)
                        throw new ZipException("Data error");
                } catch (DataFormatException e) {
                    ZipException err = new ZipException("Data format: " + e.getMessage());
                    err.initCause(e);
                    throw err;
                }
                inflater.reset();
                return file.data = dec;
            case ZipEntry.STORED:
                buffer.pos(file.cSize);
                return file.data = buffer.toByteArray();
            default:
                throw new ZipException("Unsupported compression method " + Integer.toHexString(file.compressMethod));
        }
    }

    @Override
    public void close() throws IOException {
        inflater.end();
        entries.clear();
        if(zip != null)
            zip.close();
        buffer.list = null;
    }

    public static class EFile {
        int compressMethod;
        int cSize, uSize;
        byte[] name, data;

        long offset;
    }
}
