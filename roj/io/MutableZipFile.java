package roj.io;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.Unioner;
import roj.collect.Unioner.Point;
import roj.collect.Unioner.Range;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.EmptyArrays;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.zip.*;

/**
 * 你别指望这东西能打开分卷压缩文件，没有ZIP64，也没有EXTTag <br>
 *     对于非UTF-8编码的压缩文件只读，毕竟java的charset写的垃圾的很
 *
 * @author Roj233
 * @since 2021/7/10 17:09
 */
public class MutableZipFile implements Closeable, AutoCloseable {
    public final File file;
    private RandomAccessFile zip;
    private final MyHashMap<String, EFile> entries;
    private final MyHashSet<ModFile> modified;
    private final EEOF eof;

    private final ByteList buffer;
    private final Inflater inflater;
    private final Deflater deflater;
    private final CRC32 crc;
    private final Charset charset;

    private final boolean killExtFlags;

    private static final int WHEN_USE_FILE_CACHE = 1048576;

    public static final int HEADER_EXT = 0x504b0708;
    public static final int HEADER_EOF = 0x504b0506;
    public static final int HEADER_FILE = 0x504b0304;
    public static final int HEADER_ATTRIBUTE = 0x504b0102;

    public static final int FLAG_KILL_EXT = 1;
    public static final int FLAG_VERIFY = 2;

    public MutableZipFile(File file) throws IOException {
        this(file, Deflater.DEFAULT_COMPRESSION, FLAG_KILL_EXT | FLAG_VERIFY, 0, StandardCharsets.UTF_8);
    }

    public MutableZipFile(File file, int flag) throws IOException {
        this(file, Deflater.DEFAULT_COMPRESSION, flag, 0, StandardCharsets.UTF_8);
    }

    public MutableZipFile(File file, int compressionLevel, int flag) throws IOException {
        this(file, compressionLevel, flag, 0, StandardCharsets.UTF_8);
    }

    public MutableZipFile(File file, int compressionLevel, int flag, long offset) throws IOException {
        this(file, compressionLevel, flag, 0, StandardCharsets.UTF_8);
    }

    public MutableZipFile(File file, int compressionLevel, int flag, long offset, Charset charset) throws IOException {
        this.file = file;
        if(!file.isFile() && !file.createNewFile())
            throw new IOException("Unable to create a new file");
        entries = new MyHashMap<>();
        modified = new MyHashSet<>();
        eof = new EEOF();
        eof.comment = "";
        buffer = new ByteList(1024);
        inflater = new Inflater(true);
        deflater = new Deflater(compressionLevel, true);
        crc = new CRC32();
        killExtFlags = (flag & FLAG_KILL_EXT) != 0;
        zip = new RandomAccessFile(file, "rw");
        zip.seek(offset);
        this.charset = charset;
        try {
            readInternal();
        } catch (EOFException e) {
            ZipException ze = (ZipException) new ZipException(
                    "Unexpected EOF at " + zip.getFilePointer()).initCause(e);
            zip.close();
            throw ze;
        } catch (IOException e) {
            zip.close();
        }
        if((flag & FLAG_VERIFY) != 0)
            verify();
    }

    public RandomAccessFile getFile() {
        return zip;
    }

    private void verify() throws IOException {
        for (EFile file : entries.values()) {
            if(file.attr == null)
                throw new ZipException(file.name + " doesn't have a CDir definition!");
        }
        if(eof.cDirLen != 0) {
            zip.seek(eof.cDirOffset + eof.cDirLen);
            int v = zip.readInt();
            if(v != HEADER_EOF) {
                throw new ZipException("Dir length error: got " + eof.cDirLen + " val " + Integer.toHexString(v));
            }
            zip.seek(eof.cDirOffset);
            v = zip.readInt();
            if (v != HEADER_ATTRIBUTE) {
                throw new ZipException("Dir offset error: got " + eof.cDirOffset + " val " + Integer.toHexString(v));
            }
        }
    }

    private void readInternal() throws IOException {
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

        if (charset == StandardCharsets.UTF_8) {
            buffer.pos(nameLen);
            ByteReader.decodeUTF(-1, out, buffer);
            entry.name = out.toString();
            out.clear();
        } else {
            entry.name = new String(buf, 0, nameLen, charset);
        }

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

        // ... skip method, PRETTY NOT WELL
        // cSize == 0: killExtFlags
        if((flags & 8) != 0 && cSize == 0) {
            Inflater inflater = this.inflater;
            buf = buffer.list;
            try {
                while (true) {
                    if (inflater.inflate(buf, 512, 512) == 0) {
                        if (inflater.finished() || inflater.needsDictionary()) {
                            // OVERWRITE FUCKING ZERO ENTRY (as we know actual size now)
                            // NOTE: attr knows size
                            if(killExtFlags) {
                                // top + header + offset (usize)
                                zip.seek(entry.startPos() + 4 + 14);
                                // not update EXT flag: not more I/O (moving)
                                buffer.clear();
                                new ByteWriter(buffer)
                                        // C(ompressed)Size
                                        .writeIntR((int) inflater.getBytesRead())
                                        // U(ncompressed)Size
                                        .writeIntR((int) inflater.getBytesWritten());
                                zip.write(buf, 0, 8);
                            }
                            zip.seek(off + inflater.getBytesRead());
                            skipExtHeader(entry);
                            break;
                        }
                        if (inflater.needsInput()) {
                            int read = zip.read(buf, 0, 512);
                            if (read <= 0)
                                throw new EOFException("Before entry decompression completed");

                            inflater.setInput(buf, 0, read);
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
            if((flags & 8) != 0)
                skipExtHeader(entry);
        }
    }

    private void skipExtHeader(EFile entry) throws IOException {
        if (zip.readInt() != HEADER_EXT) {
            zip.skipBytes(8);
            entry.ext = 12;
        } else {
            zip.skipBytes(12);
            entry.ext = 16;
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

        String name;
        if (charset == StandardCharsets.UTF_8) {
            buffer.pos(nameLen);
            ByteReader.decodeUTF(-1, out, buffer);
            name/*entry.name*/ = out.toString();
            out.clear();
        } else {
            name = new String(buf, 0, nameLen, charset);
        }

        zip.skipBytes(commentLen);
        //if(commentLen > 0) {
            //zip.read(buf, 0, commentLen);
            //buffer.pos(commentLen);
            //ByteReader.decodeUTF(-1, out, buffer);
            //entry.comment = out.toString();
            //out.clear();
        //}

        EFile file = entries.get(/*entry.name*/name);
        if(file == null)
            throw new ZipException("FileNode " + name + " is null");
        file.attr = entry;

        if(extraLen > 0) {
            zip.skipBytes(extraLen);
            //zip.read(buf, 0, extraLen);
            //buffer.pos(extraLen);
            //entry.extra = buffer.toByteArray();
        }

        if(fileHeader != file.startPos()) {
            throw new ZipException(file.name + " offset mismatch: req " + fileHeader + " computed " + file.startPos());
        }

        long off = zip.getFilePointer();

        if(off >= zip.length())
            throw new EOFException();
        //entry.offset = off;
        //zip.seek(off/* + entry.cSize*/);

        return entry;
    }

    private void readEOF(CharList cl) throws IOException {
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

            if (charset == StandardCharsets.UTF_8) {
                buffer.pos(commentLen);
                ByteReader.decodeUTF(-1, cl, buffer);
                entry.comment = cl.toString();
                cl.clear();
            } else {
                entry.comment = new String(buf, 0, commentLen, charset);
            }
        } else {
            entry.comment = "";
        }
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
        return getFileData(file);
    }

    public byte[] getFileData(EFile file) throws IOException {
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
        file.compress = content != null && content.pos() > 0;
        file.data = content;
        return file;
    }

    public ModFile setFileData(String entry, ByteList content, boolean requiredExistence) {
        ModFile file = new ModFile();
        file.name = entry;
        if(file == (file = modified.find(file))) {
            if(((file.file = entries.get(entry)) == null) == requiredExistence) {
                throw new AssertionError("(entries.get(entry) == null) != requiredExistence");
            }
            modified.add(file);
        }
        file.compress = content != null && content.pos() > 0;
        file.data = content;
        return file;
    }

    public void setFileDataMore(Map<String, ByteList> content) {
        for (Map.Entry<String, ByteList> entry : content.entrySet()) {
            ModFile file = new ModFile();
            file.name = entry.getKey();

            ByteList bl = entry.getValue();
            file.compress = bl != null && bl.pos() > 0;
            file.data = bl;
            if(file == (file = modified.find(file))) {
                if((file.file = entries.get(entry.getKey())) != null) {
                    Attr attr = file.file.attr;
                    if(bl.pos() == attr.uSize) {
                        crc.reset();
                        crc.update(bl.list, bl.offset(), bl.pos());
                        if ((int) crc.getValue() == attr.CRC32) {
                            // same length and checksum: same file, skip
                            continue;
                        }
                        crc.reset();
                    }
                }
                modified.add(file);
            }
        }
    }

    public MyHashSet<ModFile> getModified() {
        return modified;
    }

    public void store() throws IOException {
        if(modified.isEmpty()) return;

        EFile minFile = null;

        Unioner<EFile> uFile = new Unioner<>();

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

        FileChannel cf = zip.getChannel();

        // write linear EFile header
        if(minFile != null) {
            for (EFile file : entries.values()) {
                if (file.offset >= minFile.offset && !uFile.add(file)) { // ^=
                    uFile.remove(file);
                }
            }

            File tmp = new File("MZF~" + (System.nanoTime() % 100000) +  ".tmp");
            FileChannel ct = null;
            ByteBuffer direct = null;

            long len = minFile.startPos();
            cf.position(len);
            long delta = 0;
            long begin = -1;
            for (Unioner.Region region : uFile) { // index modified
                if (region.node().next() != null) {
                    if (begin == -1)
                        throw new ZipException("Unexpected -1 at " + region);
                    // req: 两个, id: 不能是第一个
                    Unioner.Point point = region.node();
                    if (point.end())
                        point = point.next(); // 找到Start

                    EFile file1 = point.owner();
                    file1.offset += delta;

                    continue; // find intersection regions
                }
                if (begin == -1) { // node k
                    if (region.node().end())
                        throw new ZipException("Unexpected value at " + region);
                    EFile file1 = region.node().owner();
                    begin = file1.startPos();

                    delta += (len - begin);

                    file1.offset += delta;
                } else { // node k + 1
                    EFile file1 = region.node().owner();

                    if (!region.node().end())
                        throw new ZipException("Unexpected value at " + region);

                    len = file1.endPos() - begin - delta;

                    while (true) {
                        if (len > WHEN_USE_FILE_CACHE || ct != null) {
                            if(ct == null)
                                ct = FileChannel.open(tmp.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                                                  StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
                            cf.transferTo(begin, len, ct.position(0));
                            ct.transferTo(0, len, cf);
                        } else {
                            if (direct == null) {
                                try {
                                    direct = ByteBuffer.allocateDirect(Math.min(WHEN_USE_FILE_CACHE, (int) cf.size()));
                                } catch (OutOfMemoryError toUseFileCache) {
                                    ct = FileChannel.open(tmp.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                                                          StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
                                    continue;
                                }
                            }
                            direct.position(0).limit((int) len);
                            cf.read(direct, begin);
                            direct.position(0);
                            cf.write(direct);
                        }
                        break;
                    }

                    len += begin;
                    begin = -1;
                }
            }
            if (ct != null)
                ct.close();
            if (direct != null)
                IOUtil.clean(direct);
        } else {
            cf.position(eof.cDirOffset);
        }

        ChannelOutputStream appender = new ChannelOutputStream(cf);

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
                file.file.data = null;
            }

            EFile file1 = file.file;
            // flag 8: 后面包含 EXT_SIGN (stream)
            file1.flags = (char) ((file1.flags & ~8) | 2048);
            file1.offset = cf.position() + 30 + ByteWriter.byteCountUTF8(file1.name) + file1.extra.length;

            Attr attr = file1.attr;
            attr.compressMethod = (char) (file.compress ? ZipEntry.DEFLATED : ZipEntry.STORED);

            crc.reset();
            crc.update(file.data.list, file.data.offset(), file.data.pos());
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
        eof.cDirOffset = cf.position();
        ByteList bl = writer.list;

        // 排序CDir属性
        uFile.reuseClear();
        for (EFile file : entries.values()) {
            uFile.add(file);
        }
        int v = Math.max((int) (bl.list.length * 0.8f), 1024);
        for (Unioner.Region region : uFile) {
            Point node = region.node();
            if (node.next() != null) {
                // 不用再做验证，做过一次了
                if (node.end())
                    node = node.next(); // 找到Start

                writeAttr(writer, node.owner());
            } else if(!node.end())
                writeAttr(writer, node.owner());

            if(bl.pos() > v) {
                bl.writeToStream(appender);
                bl.clear();
            }
        }

        if(bl.pos() > 0) {
            bl.writeToStream(appender);
        }
        bl.clear();

        eof.cDirLen = cf.position() - eof.cDirOffset;

        writeEOF(writer);

        bl.writeToStream(appender);
        bl.clear();

        if(zip.length() != zip.getFilePointer()) {
            zip.setLength(zip.getFilePointer());
        }
        buffer.clear();
    }

    public void clear() {
        entries.clear();
        modified.clear();
        eof.cDirLen = eof.cDirOffset = 0;
    }

    public void setManifest(Manifest mf) throws IOException {
        ByteList bl = new ByteList();
        mf.write(bl.asOutputStream());
        setFileData("META-INF/MANIFEST.MF", bl);
    }

    public void read() throws IOException {
        clear();
        zip.seek(0);
        readInternal();
    }

    public void reopen() throws IOException {
        if(zip == null)
            zip = new RandomAccessFile(file, "rw");
        verify();
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

    private static void writeFile(OutputStream appender, ByteWriter util, EFile file) throws IOException {
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

    private static void writeAttr(ByteWriter util, EFile file) {
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

    private void writeEOF(ByteWriter util) {
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
    public static int java2DosTime(long time) {
        int[] arr = ACalendar.get1(time);
        int year = arr[ACalendar.YEAR] + 1900;
        if (year < 1980) {
            return (1 << 21) | (1 << 16)/*ZipEntry.DOSTIME_BEFORE_1980*/;
        }
        return ((year - 1980) << 25) | ((arr[ACalendar.MONTH] + 1) << 21) |
                (arr[ACalendar.DAY] << 16) | (arr[ACalendar.HOUR] << 11) | (arr[ACalendar.MINUTE] << 5) |
                (arr[ACalendar.SECOND] >> 1);
    }

    public static class EFile implements Range {
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

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "File{" + "'" + name + '\'' + ", [" + startPos() + ',' + (attr == null ? "?" : endPos()) + ']' /*+ ", " + attr */+ '}';
        }

        @Override
        public long startPos() {
            return offset - 30 - ByteWriter.byteCountUTF8(name) - extra.length;
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
