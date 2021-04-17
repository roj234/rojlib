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

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.TimeZone;
import java.util.jar.Manifest;
import java.util.zip.*;

/**
 * 不能打开分卷压缩文件（ZipFile也不可以好嘛啊sir，虽然我可以做，但是没需求又平添bug...）<br>
 * 对于非UTF-8编码的压缩文件只能以UTF-8（UFS bit）方式写入 （纯粹是我懒）
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
    private final LinkedList<ZipInflatedStream> inflaters;

    private final byte flags;

    private static final int WHEN_USE_FILE_CACHE = 1048576;

    public static final int HEADER_EXT = 0x504b0708;
    public static final int HEADER_ZIP64_EOF_LOCATOR = 0x504b0607;
    public static final int HEADER_ZIP64_EOF = 0x504b0606;
    public static final int HEADER_EOF = 0x504b0506;
    public static final int HEADER_FILE = 0x504b0304;
    public static final int HEADER_ATTRIBUTE = 0x504b0102;

    public static final long U32_MAX = 4294967295L;
    public static final int MAXIMUM_BYTE_ARRAY_LENGTH = 1 << 30;

    static final int MAX_INFLATER_SIZE = 16;

    public static final int FLAG_KILL_EXT = 1;
    public static final int FLAG_VERIFY = 2;
    public static final int FLAG_ONLY_READ_LOC = 4;

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
        inflaters = new LinkedList<>();
        entries = new MyHashMap<>();
        modified = new MyHashSet<>();
        eof = new EEOF();
        buffer = new ByteList(1024);
        inflater = new Inflater(true);
        deflater = new Deflater(compressionLevel, true);
        crc = new CRC32();
        flags = (byte) flag;
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
            throw e;
        }
        if((flag & (FLAG_VERIFY | FLAG_ONLY_READ_LOC)) == FLAG_VERIFY)
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
                    if ((flags & FLAG_ONLY_READ_LOC) != 0)
                        break cyl;
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
        zip.readFully(buf, 0, 26);
        EFile entry = new EFile();

        //entry.minExtractVer = buf[0] | buf[1] << 8;
        int flags = (buf[2] & 0xFF) | (buf[3] & 0xFF) << 8;
        entry.flags = (char) flags;
        int cp = /*entry.compressMethod =*/ (buf[4] & 0xFF) | (buf[5] & 0xFF) << 8;
        int cSize = /*entry.cSize =*/ (buf[14] & 0xFF) | (buf[15] & 0xFF) << 8 | (buf[16] & 0xFF) << 16 | (buf[17] & 0xFF) << 24;

        if ((flags & FLAG_ONLY_READ_LOC) != 0) {
            Attr attr = entry.attr = new Attr();
            attr.cSize = cSize;
            attr.uSize = buf[18] | buf[19] << 8 | buf[20] << 16 | buf[21] << 24;
            attr.compressMethod = (char) cp;
            attr.modTime = buf[6] | buf[7] << 8 | buf[8] << 16 | buf[9] << 24;
            attr.CRC32 = buf[10] | buf[11] << 8 | buf[12] << 16 | buf[13] << 24;
        }

        int nameLen = (buf[22] & 0xFF) | (buf[23] & 0xFF) << 8;
        int extraLen = (buf[24] & 0xFF) | (buf[25] & 0xFF) << 8;

        buffer.ensureCapacity(nameLen);
        zip.readFully(buf = buffer.list, 0, nameLen);

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
            zip.readFully(buf, 0, extraLen);
            buffer.pos(extraLen);
            entry.extraLength = (char) extraLen;
            entry.readExtra(buffer, cSize == (int)U32_MAX || (entry.attr != null && entry.attr.uSize == (int)U32_MAX));
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
                            if((flags & FLAG_KILL_EXT) != 0) {
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
            entry.extDesc = 12;
        } else {
            zip.skipBytes(12);
            entry.extDesc = 16;
        }
    }

    private Attr readAttr(CharList out) throws IOException {
        byte[] buf = buffer.list;
        zip.readFully(buf, 0, 42);
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
        long fileHeader = /*entry.fileHeader =*/ (buf[38] & 0xFF) | (buf[39] & 0xFF) << 8 | (buf[40] & 0xFF) << 16 | (buf[41] & 0xFF) << 24;

        int nameLen = (buf[24] & 0xFF) | (buf[25] & 0xFF) << 8;
        int extraLen = (buf[26] & 0xFF) | (buf[27] & 0xFF) << 8;
        int commentLen = (buf[28] & 0xFF) | (buf[29] & 0xFF) << 8;

        buffer.ensureCapacity(Math.max(nameLen, commentLen));
        zip.readFully(buf = buffer.list, 0, nameLen);

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
            //zip.readFully(buf, 0, commentLen);
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
            boolean checkZIP64 = entry.cSize == (int)U32_MAX || entry.uSize == (int)U32_MAX || fileHeader == (int)U32_MAX;
            if (!checkZIP64) {
                zip.skipBytes(extraLen);
            } else {
                zip.readFully(buf, 0, extraLen);
                buffer.pos(extraLen);
                long t = entry.readExtra(buffer, true, fileHeader);
                if (t >= 0)
                    fileHeader = t;
            }
        }

        if(fileHeader != file.startPos()) {
            throw new ZipException(file.name + " offset mismatch: req " + fileHeader + " computed " + file.startPos());
        }

        long off = zip.getFilePointer();

        if(off >= zip.length())
            throw new EOFException();

        return entry;
    }

    private void readEOF(CharList cl) throws IOException {
        byte[] buf = buffer.list;
        zip.readFully(buf, 0, 18);
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
            zip.readFully(buffer.list, 0, commentLen);
            buffer.pos(commentLen);
            entry.comment = buffer.toByteArray();
        } else {
            entry.comment = EmptyArrays.BYTES;
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
        if (file.data != null) return file.data;
        if (file.attr.uSize > MAXIMUM_BYTE_ARRAY_LENGTH)
            throw new ZipException("Compressed size >= 2Gbytes(1 << 30) limitation, streaming method is required!");

        return getFileData(file, new ByteList((int) file.attr.uSize)).toByteArray();
    }

    public ByteList getFileData(EFile file, ByteList buf) throws IOException {
        if (file.attr.uSize > MAXIMUM_BYTE_ARRAY_LENGTH)
            throw new ZipException("Compressed size >= 2Gbytes(1 << 30) limitation, streaming method is required!");
        if (buf.pos() + file.attr.uSize > MAXIMUM_BYTE_ARRAY_LENGTH)
            throw new ZipException("Uncompressed size >= 2Gbytes(1 << 30) limitation, streaming method is required!");

        zip.seek(file.offset);
        Attr attr = file.attr;
        buf.ensureCapacity((int) (buf.pos() + attr.uSize));
        int icSize = (int) attr.cSize;

        switch (attr.compressMethod) {
            case ZipEntry.DEFLATED:
                buffer.ensureCapacity(icSize);
                zip.readFully(buffer.list, 0, icSize);
                Inflater inf = this.inflater;
                inf.setInput(buffer.list, 0, icSize);
                try {
                    int gen;
                    do {
                        int pos = buf.pos();
                        gen = inf.inflate(buf.list, pos, buf.list.length - pos);
                        buf.pos(pos + gen);
                    } while (gen > 0);
                } catch (DataFormatException e) {
                    inf.reset();
                    ZipException err = new ZipException("Data format: " + e.getMessage());
                    err.initCause(e);
                    throw err;
                }
                if(inf.getBytesWritten() != attr.uSize)
                    throw new ZipException("Data error");
                inf.reset();
                return buf;
            case ZipEntry.STORED:
                zip.readFully(buf.list, buf.pos(), icSize);
                buf.pos(buf.pos() + icSize);
                return buf;
            default:
                throw new ZipException("Unsupported compression method " + Integer.toHexString(attr.compressMethod));
        }
    }

    public InputStream getFileDataStreaming(String entry) throws IOException {
        EFile file = entries.get(entry);
        if(file == null)
            return null;
        return getFileDataStreaming(file);
    }

    public InputStream getFileDataStreaming(EFile file) throws IOException {
        return file.attr.compressMethod == ZipEntry.STORED ?
                new ZipStoredStream(file, this.file) :
                inflaters.isEmpty() ? new ZipInflatedStream(file, this) : inflaters.removeLast();
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

    public ModFile setFileDataStreaming(String entry, InputStream content, boolean compress) {
        ModFile file = new ModFile();
        file.name = entry;
        if(file == (file = modified.find(file))) {
            file.file = entries.get(entry);
            modified.add(file);
        }
        file.compress = compress;
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
                        crc.update(bl.list, bl.offset(), bl.limit());
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

        ByteWriter bw = new ByteWriter();

        // write modified EFile header
        for(ModFile file : modified) {
            if(file.data == null) continue;
            if(file.file == null) {
                EFile ef = file.file = new EFile();
                ef.attr = new Attr();
                entries.put(ef.name = file.name, ef);
            } else {
                file.file.extDesc = 0;
                file.file.extraLength = 0;
                file.file.data = null;
            }

            EFile file1 = file.file;
            // flag 8: 后面包含 EXT_SIGN (stream)
            file1.flags = (char) ((file1.flags & ~8) | 2048);
            file1.offset = cf.position() + 30 + ByteWriter.byteCountUTF8(file1.name)/* + file1.extDesc*/;

            Attr attr = file1.attr;
            attr.compressMethod = (char) (file.compress ? ZipEntry.DEFLATED : ZipEntry.STORED);

            if (file.data instanceof ByteList) {
                ByteList data = (ByteList) file.data;
                crc.reset();
                crc.update(data.list, data.offset(), data.limit());
                attr.CRC32 = (int) crc.getValue();
                crc.reset();
                attr.uSize = data.pos();
                if (!file.compress) {
                    attr.cSize = attr.uSize;
                }

                long offset = zip.getFilePointer();
                writeFile(appender, bw, file.file);

                if (file.compress) {
                    Deflater def = this.deflater;
                    def.setInput(data.list, data.offset(), data.limit());
                    def.finish();

                    ByteList buffer = this.buffer;
                    buffer.clear();
                    buffer.ensureCapacity(1024);

                    while (!def.finished()) {
                        int len = def.deflate(buffer.list, 0, buffer.list.length);
                        if (len > 0)
                            appender.write(buffer.list, 0, len);
                    }
                    long curr = zip.getFilePointer();
                    // backward, 文件就是这点好
                    zip.seek(offset + 18);
                    zip.writeInt(Integer.reverseBytes((int) (attr.cSize = def.getBytesWritten())));
                    zip.seek(curr);
                    def.reset();
                } else {
                    appender.write(data.list, data.offset(), data.limit());
                }
            } else {
                InputStream in = (InputStream) file.data;

                // 可以用EXT,但是都是文件了,那么无聊浪费CPU干嘛
                long offset = zip.getFilePointer();
                // CONSIDER: 当使用Stream方式，多半是大文件，所以哦
                attr.cSize = U32_MAX;
                attr.uSize = U32_MAX;
                writeFile(appender, bw, file.file);

                crc.reset();

                ByteList buf = this.buffer;
                buf.clear();
                buf.ensureCapacity(2048);
                if (file.compress) {
                    Deflater def = this.deflater;
                    final int d2 = buf.list.length / 2;
                    int read;
                    while ((read = in.read(buf.list, 0, d2)) > 0) {
                        def.setInput(buf.list, 0, read);
                        crc.update(buf.list, 0, read);
                        while (!deflater.needsInput()) {
                            int len = def.deflate(buf.list, d2, buf.list.length - d2);
                            appender.write(buf.list, 0, len);
                        }
                    }
                    def.finish();

                    while (!def.finished()) {
                        int len = def.deflate(buf.list, 0, buf.list.length);
                        appender.write(buf.list, 0, len);
                    }

                    attr.uSize = def.getBytesRead();
                    attr.cSize = def.getBytesWritten();
                    def.reset();
                } else {
                    int read;
                    while ((read = in.read(buf.list, 0, buf.list.length)) > 0) {
                        appender.write(buf.list, 0, read);
                        crc.update(buf.list, 0, read);
                        attr.cSize += read;
                    }
                    attr.uSize = attr.cSize;
                }

                long curr = zip.getFilePointer();
                zip.seek(offset + 14);
                zip.writeShort(Integer.reverseBytes((int) crc.getValue()));
                boolean zip64 = attr.cSize > U32_MAX || attr.uSize > U32_MAX;
                if (!zip64) {
                    zip.writeInt(Integer.reverseBytes((int) (attr.uSize)));
                    zip.writeInt(Integer.reverseBytes((int) (attr.cSize)));
                    zip.seek(offset + 32 + ByteWriter.byteCountUTF8(file1.name));
                    zip.writeShort(0); // ignore Extra flag
                } else {
                    zip.seek(offset + 34 + ByteWriter.byteCountUTF8(file1.name));
                    zip.writeLong(Long.reverseBytes(attr.uSize));
                    zip.writeLong(Long.reverseBytes(attr.cSize));
                }
                zip.seek(curr);
            }

            file1.offset += file1.extraLength;
        }

        modified.clear();
        buffer.clear();

        // write ALL CDir header
        eof.cDirOffset = zip.getFilePointer();

        // 排序CDir属性
        uFile.reuseClear();
        for (EFile file : entries.values()) {
            uFile.add(file);
        }

        ByteList bl = bw.list;
        bl.ensureCapacity(1024);
        int v = Math.max((int) (bl.list.length * 0.8f), 1024);
        for (Unioner.Region region : uFile) {
            Point node = region.node();
            if (node.next() != null) {
                // 不用再做验证，做过一次了
                if (node.end())
                    node = node.next(); // 找到Start

                writeAttr(bw, node.owner());
            } else if(!node.end())
                writeAttr(bw, node.owner());

            if(bl.pos() > v) {
                bl.writeToStream(appender);
                bl.clear();
            }
        }

        if(bl.pos() > 0) {
            bl.writeToStream(appender);
        }
        bl.clear();

        eof.cDirLen = zip.getFilePointer() - eof.cDirOffset;

        writeEOF(bw, eof, entries.size(), zip.getFilePointer());

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
        for (ZipInflatedStream rs : inflaters) {
            rs.close();
        }
        inflaters.clear();
    }

    private static void writeFile(OutputStream appender, ByteWriter util, EFile file) throws IOException {
        Attr attr = file.attr;
        int ext = 4;

        int us;
        if (attr.uSize >= U32_MAX) {
            us = (int) U32_MAX;
            ext = 20;
        } else {
            us = (int) attr.uSize;
        }
        int cs;
        if (attr.cSize >= U32_MAX) {
            cs = (int) U32_MAX;
            ext = 20;
        } else {
            cs = (int) attr.cSize;
        }

        util.writeInt(HEADER_FILE)
            .writeShortR(ext == 20 ? 45 : 20)
            .writeShortR(file.flags)
            .writeShortR(attr.compressMethod)
            .writeIntR(attr.modTime)
            .writeIntR(attr.CRC32)
            .writeIntR(cs)
            .writeIntR(us)
            .writeShortR(ByteWriter.byteCountUTF8(file.name))
            .writeShortR(ext > 4 ? ext : 0)
            .writeAllUTF(file.name);
        if (ext > 4) {
            util.writeShortR(1).writeShortR(16)
                .writeLongR(attr.cSize).writeLongR(attr.uSize);
            file.extraLength = 20;
        } else {
            file.extraLength = 0;
        }
        util.list.writeToStream(appender);
        util.list.clear();
    }

    static void writeAttr(ByteWriter util, EFile file) {
        Attr attr = file.attr;
        int ext = 4;
        int off;
        if (file.startPos() >= U32_MAX) {
            off = (int) U32_MAX;
            ext += 8;
        } else {
            off = (int) file.startPos();
        }
        int us;
        if (attr.uSize >= U32_MAX) {
            us = (int) U32_MAX;
            ext += 8;
        } else {
            us = (int) attr.uSize;
        }
        int cs;
        if (attr.cSize >= U32_MAX) {
            cs = (int) U32_MAX;
            ext += 8;
        } else {
            cs = (int) attr.cSize;
        }

        util.writeInt(HEADER_ATTRIBUTE)
            .writeShortR(ext > 4 ? 45 : 20)
            .writeShortR(ext > 4 ? 45 : 20)
            .writeShortR(file.flags)
            .writeShortR(attr.compressMethod)
            .writeIntR(attr.modTime)
            .writeIntR(attr.CRC32)
            .writeIntR(cs)
            .writeIntR(us)
            .writeShortR(ByteWriter.byteCountUTF8(file.name))
            .writeShortR(ext > 4 ? ext : 0) // ext
            .writeShortR(0) // comment
            .writeShortR(0) // disk
            .writeShortR(0) // attrIn
            .writeIntR(0) // attrEx
            .writeIntR(off)
            .writeAllUTF(file.name);
        if (ext > 4) {
            util.writeShortR(1).writeShortR(ext - 4);
            if (cs == (int) U32_MAX) {
                util.writeLongR(attr.cSize);
            }
            if (us == (int) U32_MAX) {
                util.writeLongR(attr.uSize);
            }
            if (off == (int) U32_MAX) {
                util.writeLongR(file.startPos());
            }
        }
    }

    static void writeEOF(ByteWriter util, EEOF eof, int entryCount, long position) {
        boolean zip64 = false;
        int co;
        if (eof.cDirOffset >= U32_MAX) {
            co = (int) U32_MAX;
            zip64 = true;
        } else {
            co = (int) eof.cDirOffset;
        }
        int cl;
        if (eof.cDirLen >= U32_MAX) {
            cl = (int) U32_MAX;
            zip64 = true;
        } else {
            cl = (int) eof.cDirLen;
        }
        int count = entryCount;
        if (count >= 0xFFFF) {
            count = 0xFFFF;
            zip64 = true;
        }
        if (zip64) {
            util.writeInt(HEADER_ZIP64_EOF)
                .writeLongR(44).writeShortR(45).writeShortR(45) // size, ver, ver
                .writeIntR(0).writeIntR(0) // disk id, attr begin id
                .writeLongR(entryCount).writeLongR(entryCount) // disk entries, total entries
                .writeLongR(eof.cDirLen).writeLongR(eof.cDirOffset)

                .writeInt(HEADER_ZIP64_EOF_LOCATOR)
                .writeInt(0) // eof disk id
                .writeLongR(position)
                .writeIntR(1); // disk in total
        }

        util.writeInt(HEADER_EOF)
            .writeShortR(0)
            .writeShortR(0)
            .writeShortR(count)
            .writeShortR(count)
            .writeIntR(cl)
            .writeIntR(co)
            .writeShortR(eof.comment.length)
            .writeBytes(eof.comment);
    }

    /**
     * Converts DOS time to Java time (number of milliseconds since epoch).
     */
    public static long dos2JavaTime(int dtime) {
        long day = ACalendar.daySinceAD(((dtime >> 25) & 0x7f) + 1980, ((dtime >> 21) & 0x0f)/* - 1*/, (dtime >> 16) & 0x1f, null) - ACalendar.GREGORIAN_OFFSET_DAY;
        return 86400000L * day + 3600_000L * ((dtime >> 11) & 0x1f) + 60_000L * ((dtime >> 5) & 0x3f) + 1000L * ((dtime << 1) & 0x3e);
    }

    /**
     * Converts Java time to DOS time.
     */
    public static int java2DosTime(long time) {
        int[] arr = ACalendar.get1(time + TimeZone.getDefault().getOffset(time));
        int year = arr[ACalendar.YEAR] - 1980;
        if (year < 0) {
            return (1 << 21) | (1 << 16)/*ZipEntry.DOSTIME_BEFORE_1980*/;
        }
        return (year << 25) | (arr[ACalendar.MONTH] << 21) |
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

        byte[] data;

        /**
         * 文件数据的offset 不要再记错了！
         */
        long offset;
        char extraLength;
        byte extDesc;

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
            return offset - 30 - ByteWriter.byteCountUTF8(name) - extraLength;
        }

        @Override
        public long endPos() {
            return offset + attr.cSize + extDesc;
        }

        public long getUncompressedSize() {
            return attr.uSize;
        }

        @SuppressWarnings("fallthrough")
        void readExtra(ByteList extra, boolean checkZIP64) {
            ByteReader r = new ByteReader(extra);
            while (r.remain() > 4) {
                int flag = r.readUShortR();
                int length = r.readUShortR();
                switch (flag) {
                    case 1:
                        if (checkZIP64) {
                            // LOC extra zip64 entry MUST include BOTH original
                            // and compressed file size fields.
                            // If invalid zip64 extra fields, simply skip. Even
                            // it's rare, it's possible the entry size happens to
                            // be the magic value and it "accidently" has some
                            // bytes in extra match the id.
                            if (length >= 16) {
                                attr.uSize = r.readLongR();
                                attr.cSize = r.readLongR();
                            }
                            break;
                        }
                    default:
                        r.skipBytes(length);
                }
            }
        }
    }

    public static final class Attr {
        //int ver;
        //int minExtractVer;
        //int flags;
        char compressMethod;
        int modTime;
        int CRC32;
        long cSize, uSize;
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

        @SuppressWarnings("fallthrough")
        long readExtra(ByteList extra, boolean checkZIP64, long header) {
            ByteReader r = new ByteReader(extra);
            while (r.remain() > 4) {
                int flag = r.readUShortR();
                int length = r.readUShortR();
                switch (flag) {
                    case 1:
                        if (checkZIP64) {
                            if (uSize == (int) U32_MAX && length > 8) {
                                length -= 8;
                                uSize = r.readLongR();
                            }
                            if (cSize == (int) U32_MAX && length > 8) {
                                length -= 8;
                                cSize = r.readLongR();
                            }
                            if (header == (int) U32_MAX && length > 8) {
                                header = r.readLongR();
                            }
                            break;
                        }
                    default:
                        r.skipBytes(length);
                }
            }
            return header;
        }
    }

    public static final class EEOF {
        //int diskId;
        //int cDirBegin;
        //int cDirOnDisk;
        //int cDirTotal;
        long cDirLen, cDirOffset;
        //long offset;
        byte[] comment = EmptyArrays.BYTES;

        @Override
        public String toString() {
            return "ZEnd{" + "cDirLen=" + cDirLen + ", cDirOff=" + cDirOffset + ", comment='" + new String(comment) + '\'' + '}';
        }

        public void setComment(String comment) {
            this.comment = comment == null ? EmptyArrays.BYTES : ByteWriter.encodeUTF(comment).toByteArray();
            if (this.comment.length > 65535) {
                this.comment = Arrays.copyOf(this.comment, 65535);
                throw new IllegalArgumentException("Comment too long");
            }
        }

        public void setComment(byte[] comment) {
            this.comment = comment == null ? EmptyArrays.BYTES : comment;
        }
    }

    public static final class ModFile {
        EFile file;
        public boolean compress;
        public String name;
        public Object data;

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

    static class ZipStoredStream extends InputStream {
        ZipStoredStream(EFile eFile, File file) throws IOException {
            this.remain = eFile.attr.uSize;
            (this.file = new RandomAccessFile(file, "r"))
                    .seek(eFile.offset);
        }

        final RandomAccessFile file;
        long remain;

        @Override
        public int read() throws IOException {
            if (remain <= 0)
                return -1;
            return file.read();
        }

        @Override
        public int read(@Nonnull byte[] b, int off, int len) throws IOException {
            if (remain <= 0)
                return 0;
            len = Math.min(len, available());
            file.readFully(b, off, len);
            remain -= len;
            return len;
        }

        @Override
        public int available() throws IOException {
            file.getFilePointer();
            return remain > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remain;
        }

        @Override
        public long skip(long n) throws IOException {
            n = Math.min(n, remain);
            if (file.getFilePointer() + n > file.length())
                throw new ZipException("File size was externally changed.");
            file.seek(file.getFilePointer() + n);
            return n;
        }

        @Override
        public void close() throws IOException {
            file.close();
            remain = -1;
        }
    }

    static final class ZipInflatedStream extends ZipStoredStream {
        ZipInflatedStream(EFile eFile, MutableZipFile file) throws IOException {
            super(eFile, file.file);
            this.inf = new Inflater(true);
            this.buf = new byte[1024];
            this.owner = file;
        }

        final MutableZipFile owner;
        final Inflater inf;
        boolean eof;

        final byte[] buf;
        int bufPos;
        byte[] sb;

        @Override
        public int read() throws IOException {
            if (eof) return -1;
            if (sb == null)
                sb = new byte[1];
            if (read(sb, 0, 1) < 1) return -1;
            return sb[0];
        }

        @Override
        public int read(@Nonnull byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0 || eof) {
                return 0;
            }
            try {
                int n;
                while ((n = inf.inflate(b, off, len)) == 0) {
                    if (inf.finished() || inf.needsDictionary()) {
                        close();
                        return -1;
                    }
                    if (inf.needsInput()) {
                        int read = super.read(buf, 0, b.length);
                        if (read == 0) {
                            close();
                            throw new EOFException();
                        }
                        inf.setInput(buf, 0, read);
                    }
                }
                return n;
            } catch (DataFormatException e) {
                throw new ZipException(e.toString());
            }
        }

        @Override
        public void close() throws IOException {
            eof = true;
            if (owner.inflaters.size() < MAX_INFLATER_SIZE) {
                inf.reset();
                owner.inflaters.add(this);
            } else {
                inf.end();
                super.close();
            }
        }
    }
}
