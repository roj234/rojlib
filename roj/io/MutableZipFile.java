package roj.io;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.UnionerL;
import roj.collect.UnionerL.Point;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.EmptyArrays;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Random;
import java.util.zip.*;

/**
 * 你别指望这东西能打开分卷压缩文件，没有ZIP64，也没有EXTTag，别做梦了 <BR>
 * 我要做一个XIP压缩文件
 *
 * @author Roj233
 * @since 2021/7/10 17:09
 */
public class MutableZipFile implements Closeable, AutoCloseable {
    public static void main(String[] args) throws IOException {
        MutableZipFile file = new MutableZipFile(new File(args[0]));
        byte[] data = file.getFileData("test.txt");
        if(data != null)
            System.out.println(ByteReader.readUTF(new ByteList(data)));
        int seed = Integer.parseInt(args[1]);
        Random rnd = new Random(seed);
        for (int i = 0; i < 10; i++) {
            file.setFileData("F" + (10 + rnd.nextInt(90)), ByteWriter.encodeUTF("测试数据" + i));
        }
        file.store();
    }

    File file;
    RandomAccessFile zip;
    MyHashMap<String, EFile> entries = new MyHashMap<>();
    MyHashSet<ModFile> modified = new MyHashSet<>();
    EEOF eof;

    ByteList buffer = new ByteList();
    Inflater inflater = new Inflater(true);
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    CRC32 crc = new CRC32();

    public static final int HEADER_EXT = 0x504b0708;
    public static final int HEADER_EOF = 0x504b0506;
    public static final int HEADER_FILE = 0x504b0304;
    public static final int HEADER_ATTRIBUTE = 0x504b0102;

    public MutableZipFile(File file) throws IOException {
        this.file = file;
        if(!file.isFile() && !file.createNewFile())
            throw new IOException("Unable to create a new file");
        eof = new EEOF();
        eof.comment = "";
        zip = new RandomAccessFile(file, "r");
        zip.seek(0);
        try {
            readInternal();
        } catch (EOFException e) {
            throw (ZipException) new ZipException("Unexpected EOF at " + zip.getFilePointer()).initCause(e);
        }
        verify();
    }

    private void verify() throws IOException {
        for (EFile file : entries.values()) {
            if(file.attr == null)
                throw new IllegalArgumentException(file.name + " doesnt have a CDir definition!");
        }
        if(eof.cDirLen != 0) {
            zip.seek(eof.cDirOffset + eof.cDirLen);
            int v = zip.readInt();
            if(v != HEADER_EOF) {
                throw new IllegalArgumentException("Dir length error: got " + eof.cDirLen + " val " + Integer.toHexString(v));
            }
            zip.seek(eof.cDirOffset);
            v = zip.readInt();
            if (v != HEADER_ATTRIBUTE) {
                throw new IllegalArgumentException("Dir offset error: got " + eof.cDirOffset + " val " + Integer.toHexString(v));
            }
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
                    //if(eof != null)
                    //    throw new ZipException("Duplicate End_Of_Core_Directory entry found at " + zip.getFilePointer());
                    eof = readEOF(out);
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

    private void readFile(CharList out) throws IOException {
        byte[] buf = buffer.list;
        zip.read(buf, 0, 26);
        EFile entry = new EFile();

        //entry.minExtractVer = buf[0] | buf[1] << 8;
        int flags = (buf[2] & 0xFF) | (buf[3] & 0xFF) << 8;
        entry.flags = (char) flags;
        int cp = /*entry.compressMethod =*/ (buf[4] & 0xFF) | (buf[5] & 0xFF) << 8;
        //entry.lastModify = _date(buf[6] | buf[7] << 8, buf[8] | buf[9] << 8);
        //entry.CRC32 = buf[10] | buf[11] << 8 | buf[12] << 16 | buf[13] << 24;
        int cSize = /*entry.cSize =*/ (buf[14] & 0xFF) | (buf[15] & 0xFF) << 8 | (buf[16] & 0xFF) << 16 | (buf[17] & 0xFF) << 24;
        //entry.uSize = buf[18] | buf[19] << 8 | buf[20] << 16 | buf[21] << 24;

        int nameLen = (buf[22] & 0xFF) | (buf[23] & 0xFF) << 8;
        int extraLen = (buf[24] & 0xFF) | (buf[25] & 0xFF) << 8;

        buffer.ensureCapacity(nameLen);
        zip.read(buf = buffer.list, 0, nameLen);

        buffer.pos(nameLen);
        ByteReader.decodeUTF(-1, out, buffer);
        entry.name = out.toString();
        out.clear();

        entries.put(entry.name, entry);

        if(extraLen > 0) {
            zip.read(buf, 0, extraLen);
            buffer.pos(extraLen);
            entry.extra = buffer.toByteArray();
        } else {
            entry.extra = EmptyArrays.BYTES;
        }

        long off = zip.getFilePointer();

        if(off >= zip.length())
            throw new EOFException();

        entry.offset = off;

        // ... skip method, really
        if((flags & 8) != 0) {
            Inflater inflater = this.inflater;
            ByteList b = buffer;
            b.ensureCapacity(1024);
            byte[] buf1 = b.list;
            try {
                while (true) {
                    if (inflater.inflate(buf1, 512, 512) == 0) {
                        if (inflater.finished() || inflater.needsDictionary()) {
                            zip.seek(off + inflater.getBytesRead());
                            zip.read(buf1, 0, 16);
                            int sig = (buf1[3] & 0xFF) | (buf1[2] & 0xFF) << 8 | (buf1[1] & 0xFF) << 16 | (buf1[0] & 0xFF) << 24;
                            if (sig != HEADER_EXT) {
                                zip.seek(off + inflater.getBytesRead() + 12);
                                entry.ext = 12;
                            } else {
                                entry.ext = 16;
                            }
                            break;
                        }
                        if (inflater.needsInput()) {
                            int read = zip.read(buf1, 0, 512);
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
            zip.seek(off + /*entry.*/cSize);
        }
    }

    private Attr readAttr(CharList out) throws IOException {
        byte[] buf = buffer.list;
        zip.read(buf, 0, 42);
        Attr entry = new Attr();

        //entry.ver = buf[0] | buf[1] << 8;
        //entry.minExtractVer = buf[2] | buf[3] << 8;
        //entry.flags = buf[4] | buf[5] << 8;
        entry.compressMethod = (char) ((buf[6] & 0xFF) | (buf[7] & 0xFF) << 8);
        entry.modTime = (buf[8] & 0xFF) | (buf[9] & 0xFF) << 8 | (buf[10] & 0xFF) << 16 | (buf[11] & 0xFF) << 24;
        entry.CRC32 = (buf[12] & 0xFF) | (buf[13] & 0xFF) << 8 | (buf[14] & 0xFF) << 16 | (buf[15] & 0xFF) << 24;
        entry.cSize = (buf[16] & 0xFF) | (buf[17] & 0xFF) << 8 | (buf[18] & 0xFF) << 16 | (buf[19] & 0xFF) << 24;
        entry.uSize = (buf[20] & 0xFF) | (buf[21] & 0xFF) << 8 | (buf[22] & 0xFF) << 16 | (buf[23] & 0xFF) << 24;

        //entry.diskId = buf[30] | buf[31] << 8;
        //entry.attrIn = buf[32] | buf[33] << 8;
        //entry.attrEx = buf[34] | buf[35] << 8 | buf[36] << 16 | buf[37] << 24;
        int fileHeader = /*entry.fileHeader =*/ (buf[38] & 0xFF) | (buf[39] & 0xFF) << 8 | (buf[40] & 0xFF) << 16 | (buf[41] & 0xFF) << 24;

        int nameLen = (buf[24] & 0xFF) | (buf[25] & 0xFF) << 8;
        int extraLen = (buf[26] & 0xFF) | (buf[27] & 0xFF) << 8;
        int commentLen = (buf[28] & 0xFF) | (buf[29] & 0xFF) << 8;

        buffer.ensureCapacity(Math.max(nameLen, commentLen));
        zip.read(buf = buffer.list, 0, nameLen);

        buffer.pos(nameLen);
        ByteReader.decodeUTF(-1, out, buffer);
        String name/*entry.name*/ = out.toString();
        out.clear();

        if(commentLen > 0) {
            zip.read(buf, 0, commentLen);
            buffer.pos(commentLen);
            //ByteReader.decodeUTF(-1, out, buffer);
            //entry.comment = out.toString();
            //out.clear();
        }

        EFile file = entries.get(/*entry.name*/name);
        if(file == null)
            throw new IllegalArgumentException("FileNode " + name + " is null");
        file.attr = entry;

        if(extraLen > 0) {
            zip.skipBytes(extraLen);
            //zip.read(buf, 0, extraLen);
            //buffer.pos(extraLen);
            //entry.extra = buffer.toByteArray();
        }

        if(fileHeader != file.startPos()) {
            throw new IllegalArgumentException(file.name + " offset mismatch: req " + fileHeader + " computed " + file.startPos());
        }

        long off = zip.getFilePointer();

        if(off >= zip.length())
            throw new EOFException();
        //entry.offset = off;
        //zip.seek(off/* + entry.cSize*/);

        return entry;
    }

    private EEOF readEOF(CharList cl) throws IOException {
        byte[] buf = buffer.list;
        if(zip.read(buf, 0, 18) < 18)
            throw new EOFException();
        EEOF entry = this.eof;

        //entry.diskId = buf[0] | buf[1] << 8;
        //entry.cDirBegin = buf[2] | buf[3] << 8;
        //entry.cDirOnDisk = buf[4] | buf[5] << 8;
        //entry.cDirTotal = buf[6] | buf[7] << 8;
        entry.cDirLen = (buf[8] & 0xFF) | (buf[9] & 0xFF) << 8 | (buf[10] & 0xFF) << 16 | (buf[11] & 0xFF) << 24;
        entry.cDirOffset = (buf[12] & 0xFF) | (buf[13] & 0xFF) << 8 | (buf[14] & 0xFF) << 16 | (buf[15] & 0xFF) << 24;

        int commentLen = (buf[16] & 0xFF) | (buf[17] & 0xFF) << 8;

        if(commentLen > 0) {
            buffer.ensureCapacity(commentLen);
            if(zip.read(buffer.list, 0, commentLen) < commentLen)
                throw new EOFException();

            buffer.pos(commentLen);
            ByteReader.decodeUTF(-1, cl, buffer);
            entry.comment = cl.toString();
            cl.clear();
        } else {
            entry.comment = "";
        }

        //entry.offset = zip.getFilePointer();

        return entry;
    }

    public MyHashMap<String, EFile> getEntries() {
        return entries;
    }

    public EEOF getEOF() {
        return eof;
    }

    public byte[] getFileData(String entry) throws IOException {
        EFile file = entries.get(entry);
        if(file == null)
            return null;
        if(file.data != null)
            return file.data;

        zip.seek(file.offset);
        Attr attr = file.attr;
        buffer.ensureCapacity(attr.cSize);
        if(zip.read(buffer.list, 0, attr.cSize) < attr.cSize)
            throw new IOException("Reading error: not read " + attr.cSize);

        switch (attr.compressMethod) {
            case ZipEntry.DEFLATED:
                Inflater inflater = this.inflater;
                inflater.setInput(buffer.list, 0, attr.cSize);
                byte[] dec = new byte[attr.uSize];
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
                buffer.pos(attr.cSize);
                return file.data = buffer.toByteArray();
            default:
                throw new ZipException("Unsupported compression method " + Integer.toHexString(attr.compressMethod));
        }
    }

    // content == null : 删除
    public ModFile setFileData(String entry, ByteList content) {
        ModFile file = new ModFile();
        file.name = entry;
        if(file == (file = modified.find(file))) {
            file.file = entries.get(entry);
            modified.add(file);
        }
        file.compress = true;
        file.data = content;
        return file;
    }

    public ModFile setFileData(String entry, ByteList content, boolean requiredExistence) {
        ModFile file = new ModFile();
        file.name = entry;
        if(file == (file = modified.find(file))) {
            if(((file.file = entries.get(entry)) == null) == requiredExistence) {
                throw new AssertionError("(entries.get(entry) == null) == requiredExistence");
            }
            modified.add(file);
        }
        file.compress = true;
        file.data = content;
        return file;
    }

    public void setFileDataMore(Map<String, ByteList> content) {
        for (Map.Entry<String, ByteList> entry : content.entrySet()) {
            ModFile file = new ModFile();
            file.name = entry.getKey();
            if(file == (file = modified.find(file))) {
                file.file = entries.get(entry.getKey());
                modified.add(file);
            }
            file.compress = true;
            file.data = entry.getValue();
        }
    }

    public void store() throws IOException {
        if(modified.isEmpty()) return;

        EFile minFile = null;

        UnionerL<EFile> uFile = new UnionerL<>();

        for(ModFile file : modified) {
            EFile o = entries.get(file.name);
            if (o != null) {
                if (minFile == null || minFile.offset > o.offset) {
                    minFile = o;
                }
                if(file.data == null)
                    entries.remove(file.name);
                else
                    uFile.add(o);
            }
            file.file = o;
        }

        File tmpFile = new File(file.getAbsolutePath() + System.currentTimeMillis() +  ".tmp");

        FileChannel cf = zip.getChannel();
        FileChannel ct = FileChannel.open(tmpFile.toPath(), StandardOpenOption.CREATE_NEW,
                                          StandardOpenOption.WRITE, StandardOpenOption.APPEND)
                .truncate(0);

        // write linear EFile header
        if(minFile != null) {
            for (EFile file : entries.values()) {
                if (file.offset >= minFile.offset && !uFile.add(file)) { // ^=
                    uFile.remove(file);
                }
            }

            long offset = minFile.startPos();
            cf.transferTo(0, offset, ct);

            long begin = -1;
            long len = offset;
            long delta = 0;
            for (UnionerL.Region region : uFile) { // index modified
                if (region.node().next() != null) {
                    if (begin == -1)
                        throw new IllegalStateException("Unexpected -1 at " + region.node().pos());
                    // req: 两个, id: 不能是第一个
                    UnionerL.Point point = region.node();
                    if (point.end())
                        point = point.next(); // 找到Start

                    EFile file1 = point.owner();
                    file1.offset += delta;

                    continue; // find intersection regions
                }
                if (begin == -1) { // node k
                    if (region.node().end())
                        throw new IllegalStateException("Unexpected value");
                    EFile file1 = region.node().owner();
                    begin = file1.startPos();

                    delta += (len - begin);

                    file1.offset += delta;
                } else { // node k + 1
                    EFile file1 = region.node().owner();

                    if (!region.node().end())
                        throw new IllegalStateException("Unexpected value");

                    len = file1.endPos() - begin - delta;
                    cf.transferTo(begin, len, ct);

                    len += begin;
                    begin = -1;
                }
            }
        } else {
            cf.transferTo(0, eof.cDirOffset, ct);
        }
        ct.close();
        cf.close();

        FileOutputStream appender = new FileOutputStream(tmpFile, true);

        ByteWriter writer = new ByteWriter();

        // write modified EFile header
        for(ModFile file : modified) {
            if(file.data == null) continue;
            if(file.file == null) {
                EFile ef = file.file = new EFile();
                ef.attr = new Attr();
                entries.put(ef.name = file.name, ef);
            } else {
                file.file.ext = 0;
            }

            EFile file1 = file.file;
            // flag 8: 后面包含 EXT_SIGN (stream)
            file1.flags = (char) ((file1.flags & ~8) | 2048);
            file1.offset = tmpFile.length() + 30 + file1.name.length() + file1.extra.length;

            Attr attr = file1.attr;
            attr.compressMethod = (char) (file.compress ? ZipEntry.DEFLATED : ZipEntry.STORED);

            crc.reset();
            crc.update(file.data.list, 0, file.data.pos());
            attr.CRC32 = (int) crc.getValue();
            crc.reset();
            attr.uSize = file.data.pos();

            ByteList t;
            if (file.compress) {
                Deflater deflater = this.deflater;
                deflater.setInput(file.data.list, 0, file.data.pos());
                deflater.finish();

                ByteList buffer = this.buffer;
                buffer.clear();
                buffer.ensureCapacity(1024);
                int off = 0;
                while (!deflater.finished()) {
                    off += deflater.deflate(buffer.list, off, 1024);
                    buffer.pos(off);
                    buffer.ensureCapacity(off + 1024);
                }
                attr.cSize = (int) deflater.getBytesWritten();
                deflater.reset();

                t = buffer;
            } else {
                attr.cSize = attr.uSize;
                t = file.data;
            }

            writeFile(appender, writer, file.file);
            appender.write(t.list, 0, t.pos());
        }

        modified.clear();
        buffer.clear();

        // write ALL CDir header
        eof.cDirOffset = tmpFile.length();
        ByteList bl = writer.list;

        // 排序CDir属性
        uFile.reuseClear();
        for (EFile file : entries.values()) {
            uFile.add(file);
        }
        for (UnionerL.Region region : uFile) {
            Point node = region.node();
            if (node.next() != null) {
                // 不用再做验证，做过一次了
                if (node.end())
                    node = node.next(); // 找到Start

                writeAttr(writer, node.owner());
            } else if(!node.end())
                writeAttr(writer, node.owner());

            if(bl.pos() > 1024) {
                bl.writeToStream(appender);
                bl.clear();
            }
        }

        if(bl.pos() > 0) {
            bl.writeToStream(appender);
        }
        bl.clear();

        eof.cDirLen = tmpFile.length() - eof.cDirOffset;

        writeEOF(writer);

        bl.writeToStream(appender);
        bl.clear();

        appender.close();
        buffer.clear();

        zip.close();
        if(!file.delete() || !tmpFile.renameTo(file)) {
            FileChannel t = FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE).truncate(0);
            FileChannel f = FileChannel.open(tmpFile.toPath(), StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
            f.transferTo(0, tmpFile.length(), t);

            f.close();
            t.close();
        }
        zip = new RandomAccessFile(file, "r");
    }

    /**
     * 你见过关了还可以在打开的么？现在有了
     */
    public void open() throws IOException {
        if(zip == null)
            zip = new RandomAccessFile(file, "r");
    }

    public void tClose() throws IOException {
        if(zip == null)
            return;
        zip.close();
        zip = null;
    }

    @Override
    public void close() throws IOException {
        inflater.end();
        deflater.end();
        entries.clear();
        modified.clear();
        if(zip != null)
            zip.close();
        buffer.list = null;
    }

    static void writeFile(FileOutputStream appender, ByteWriter util, EFile file) throws IOException {
        /*int elen = 0;
        int elenEXTT = 0;
        int flagEXTT = 0;
        if (e.mtime != null) {
            elenEXTT += 4;
            flagEXTT |= EXTT_FLAG_LMT;
        }
        if (e.atime != null) {
            elenEXTT += 4;
            flagEXTT |= EXTT_FLAG_LAT;
        }
        if (e.ctime != null) {
            elenEXTT += 4;
            flagEXTT |= EXTT_FLAT_CT;
        }
        if (flagEXTT != 0)
            elen += (elenEXTT + 5);    // headid(2) + size(2) + flag(1) + data*/

        Attr attr = file.attr;
        util.writeInt(HEADER_FILE)
            .writeShortR(20)
            .writeShortR(file.flags)
            .writeShortR(attr.compressMethod)
            .writeIntR(attr.modTime)
            .writeIntR(attr.CRC32)
            .writeIntR(attr.cSize)
            .writeIntR(attr.uSize)
            .writeShortR(ByteWriter.byteCountUTF8(file.name))
            .writeShortR(/*elen*/0)
            .writeAllUTF(file.name);
        /*if (flagEXTT != 0) {
            util.writeShortR(EXTID_EXTT)
            .writeShortR(elenEXTT + 1)      // flag + data
            .writeByteR(flagEXTT);
            if (e.mtime > 0)
                util.writeInt(e.mtime / 1000);
            if (e.atime > 0)
                util.writeIntR(e.atime / 1000);
            if (e.ctime > 0)
                util.writeIntR(e.ctime / 1000);
        }*/
        util.list.writeToStream(appender);
        util.list.clear();
    }

    static void writeAttr(ByteWriter util, EFile file) {
        Attr attr = file.attr;

        util.writeInt(HEADER_ATTRIBUTE)
            .writeShortR(20)
            .writeShortR(20)
            .writeShortR(file.flags)
            .writeShortR(attr.compressMethod)
            .writeIntR(attr.modTime)
            .writeIntR(attr.CRC32)
            .writeIntR(attr.cSize)
            .writeIntR(attr.uSize)
            .writeShortR(ByteWriter.byteCountUTF8(file.name))
            .writeShortR(0) // ext
            .writeShortR(0) // comment
            .writeShortR(0) // disk
            .writeShortR(0) // attrIn
            .writeIntR(0) // attrEx
            .writeIntR((int) file.startPos())
            .writeAllUTF(file.name);
    }

    void writeEOF(ByteWriter util) {
        EEOF eof = this.eof;

        util.writeInt(HEADER_EOF)
            .writeShortR(0)
            .writeShortR(0)
            .writeShortR(entries.size())
            .writeShortR(entries.size())
            .writeIntR((int) eof.cDirLen)
            .writeIntR((int) eof.cDirOffset)
            .writeShortR(ByteWriter.byteCountUTF8(eof.comment))
            .writeAllUTF(eof.comment);
    }

    /**
     * Converts DOS time to Java time (number of milliseconds since epoch).
     */
    public static long dos2JavaTime(int dtime) {
        long day = ACalendar.daySinceAD(((dtime >> 25) & 0x7f) + 1980, ((dtime >> 21) & 0x0f) - 1, (dtime >> 16) & 0x1f, null);
        return 86400000L * (day + 24L * (((dtime >> 11) & 0x1f) + 60L * (((dtime >> 5) & 0x3f) + 60L * ((dtime << 1) & 0x3e))));
    }

    /**
     * Converts Java time to DOS time.
     */
    public static long java2DosTime(int time) {
        int[] arr = ACalendar.get1(time);
        int year = arr[ACalendar.YEAR] + 1900;
        if (year < 1980) {
            return (1 << 21) | (1 << 16)/*ZipEntry.DOSTIME_BEFORE_1980*/;
        }
        return (year - 1980) << 25 | (arr[ACalendar.MONTH] + 1) << 21 |
                arr[ACalendar.DAY] << 16 | arr[ACalendar.HOUR] << 11 | arr[ACalendar.MINUTE] << 5 |
                arr[ACalendar.SECOND] >> 1;
    }

    public static class EFile implements UnionerL.Section {
        //int minExtractVer;
        char flags;
        //int compressMethod;
        //long lastModify;
        //int CRC32;
        //int cSize, uSize;
        String name;
        byte[] extra = EmptyArrays.BYTES;

        byte[] data;

        /**
         * 文件数据的offset 不要再记错了！
         */
        long offset;
        byte ext;

        Attr attr;

        @Override
        public String toString() {
            return "File{" + "'" + name + '\'' + ", [" + startPos() + ',' + (attr == null ? "?" : endPos()) + ']' /*+ ", " + attr */+ '}';
        }

        @Override
        public long startPos() {
            return offset - 30 - name.length() - extra.length;
        }

        @Override
        public long endPos() {
            return offset + attr.cSize + ext;
        }
    }

    public static final class Attr {
        //int ver;
        //int minExtractVer;
        //int flags;
        char compressMethod;
        int modTime;
        int CRC32;
        int cSize, uSize;
        //String name, comment;
        //int diskId;
        //int attrIn, attrEx;
        //int fileHeader;

        //long offset;

        //byte[] extra;

        @Override
        public String toString() {
            return "Attr{" + "cm=" + compressMethod + ", CRC=" + CRC32 + ", cSz=" + cSize + ", uSz" +
                    "=" + uSize + '}';
        }
    }

    public static final class EEOF {
        //int diskId;
        //int cDirBegin;
        //int cDirOnDisk;
        //int cDirTotal;
        long cDirLen, cDirOffset;
        //long offset;
        public String comment;

        @Override
        public String toString() {
            return "ZEnd{" + "cDirLen=" + cDirLen + ", cDirOff=" + cDirOffset + ", comment='" + comment + '\'' + '}';
        }
    }

    public static final class ModFile {
        EFile file;
        public boolean compress;
        public String name;
        public ByteList data;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ModFile file = (ModFile) o;
            return name.equals(file.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
