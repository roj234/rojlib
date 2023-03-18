package roj.archive.qz;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveWriter;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.zip.CRC32;

import static roj.archive.qz.BlockId.*;
import static roj.archive.qz.QZArchive.invertBits;

/**
 * @author Roj234
 * @since 2023/3/14 0014 22:08
 */
public class QZFileWriter extends OutputStream implements ArchiveWriter {
    private final Source s;

    private final List<WordBlock4W> blocks = new SimpleList<>();
    /**
     * 0: 空项目
     * 1: 空文件(而不是文件夹)
     * 2: anti
     * 3: 包含创建时间
     * 4: 访问时间
     * 5: 修改时间
     * 6: windows属性
     *
     * 7: WB uCrc32
     * 8: WB cCrc32
     * 9: Entry crc32
     */
    private final int[] flagSum = new int[10];

    private final List<QZEntry> files = new SimpleList<>();
    private final List<QZEntry> emptyFiles = new SimpleList<>();

    private QZEntry currentEntry;
    private long entryUSize;
    private OutputStream out;

    private final CRC32 crc32 = new CRC32();
    private final CRC32 blockCrc32 = new CRC32();

    private boolean finished;

    private int compressHeaderMin = 10;
    private boolean storeStreamCrc;
    private long solidSize, exceptFileSize;

    private QZCoder[] coders = {new LZMA2()};
    public static final QZCoder[] NO_COMPRESS = {Copy.INSTANCE};

    public QZFileWriter(String path) throws IOException {
        this(new FileSource(path));
    }
    public QZFileWriter(File file) throws IOException {
        this(new FileSource(file));
    }
    public QZFileWriter(Source s) throws IOException {
        this.s = s;
        s.seek(32);
    }

    public void setCodec(QZCoder... methods) {
        if (methods.length == 0) {
            coders = NO_COMPRESS;
            return;
        }
        coders = new QZCoder[methods.length];
        for (int i = 0; i < methods.length; i++) {
            coders[methods.length-i-1] = methods[i];
        }
    }
    public void setCodec(List<QZCoder> methods) {
        if (methods == null || methods.isEmpty()) {
            coders = NO_COMPRESS;
            return;
        }
        coders = new QZCoder[methods.size()];
        for (int i = 0; i < methods.size(); i++) {
            coders[methods.size()-i-1] = methods.get(i);
        }
    }

    /**
     * 保存压缩流的CRC32数据
     * 没必要
     * 至少7-zip和QZArchive都不会去验证
     */
    public void setSaveCompressedCrc(boolean b) {
        this.storeStreamCrc = b;
    }

    /**
     * 是否压缩头（使用最后设置的codec）
     * 0: 是
     * -1: 否
     * 大于零: 文件数量大于时压缩 (默认10)
     * 头大小正比于文件数量,但是头大小没法预先计算
     *
     * 注意: 如果不需要加密文件名请设置它为false 或者调用finish前删除加密的codec
     * 反之，需要则设为true
     * 否则可能会出现该加密没加密或相反
     */
    public void setCompressedHeader(int i) {
        this.compressHeaderMin = i;
    }

    /**
     * 固实大小
     * 0: 是
     * -1: 否 (一个文件一个字块)
     * 大于零: 按此大小
     * 注意: 7-zip已知输入文件大小。所以模式为<=固实大小时分块
     * 而write方法无法确定文件大小(可以使用beginEntry的重载)
     * 进而只能在输入大小>固实大小时分块
     */
    public void setSolidSize(long l) {
        this.solidSize = l;
    }

    public void copy(ArchiveFile owner, ArchiveEntry entry) throws IOException {
        QZArchive archive = (QZArchive) owner;
        QZEntry entry1 = (QZEntry) entry;
        if (entry1.uSize == 0) {
            beginEntry(entry1);
            closeEntry();
            return;
        }
        WordBlock b = entry1.block;
        if (b.fileCount != 1) throw new IOException("写入固实7z字块请使用writeWordBlock");

        copy(archive, b);
    }
    public void copy(ArchiveFile owner, WordBlock b) throws IOException {
        QZArchive archive = (QZArchive) owner;

        if (b == null) throw new NullPointerException("b");
        if (finished) throw new IOException("Stream closed");

        closeEntry();
        nextWordBlock();

        if (s.hasChannel() & archive.r.hasChannel()) {
            FileChannel myCh = s.channel();
            archive.r.channel().transferTo(b.offset, b.size, myCh);
        } else {
            if (b.size > Integer.MAX_VALUE) throw new IOException("不是文件...感觉不可能打得开啊");

            Source src = archive.r;
            src.seek(b.offset);

            byte[] list = new byte[1024];
            int len = (int) b.size;
            while (len > 0) {
                int min = Math.min(list.length, len);
                src.readFully(list, 0, min);
                s.write(list, 0, min);
                len -= min;
            }
        }

        blocks.add(b);
        if ((b.hasCrc & 1) != 0) flagSum[7]++;
        if ((b.hasCrc & 2) != 0) flagSum[8]++;

        QZEntry entry = b.firstEntry;
        while (entry != null) {
            files.add(entry);

            countFlag(entry);
            int flag = entry.flag;
            if (entry.uSize == 0) {
                flagSum[0]++;

                if ((flag & QZEntry.DIRECTORY) == 0) flagSum[1]++;
                if ((flag & QZEntry.ANTI     ) != 0) flagSum[2]++;
            } else {
                if ((flag & QZEntry.CRC      ) != 0) flagSum[9]++;
            }

            entry = entry.next;
        }
    }

    public void write(ArchiveEntry entry, DynByteBuf data) throws IOException {
        tryNextBlock(data.readableBytes());
        ArchiveWriter.super.write(entry, data);
    }
    public void write(ArchiveEntry entry, File data) throws IOException {
        tryNextBlock(data.length());
        ArchiveWriter.super.write(entry, data);
    }

    // todo parallel

    public void beginEntry(ArchiveEntry entry) throws IOException {
        beginEntry((QZEntry) entry, -1);
    }
    public void beginEntry(QZEntry entry, long fileSize) throws IOException {
        if (finished) throw new IOException("Stream closed");

        closeEntry();
        countFlag(currentEntry = entry);
        if (fileSize >= 0) {
            tryNextBlock(fileSize);
            exceptFileSize = fileSize;
        } else {
            exceptFileSize = -1;
        }
    }
    public void write(int b) throws IOException {
        if (currentEntry == null) throw new IOException("No active entry");

        out().write(b);
        crc32.update(b);
        blockCrc32.update(b);
        entryUSize++;
    }
    public void write(byte[] b, int off, int len) throws IOException {
        if (currentEntry == null) throw new IOException("No active entry");

        if (len <= 0) return;
        out().write(b, off, len);
        crc32.update(b, off, len);
        blockCrc32.update(b, off, len);
        entryUSize += len;
    }
    public void closeEntry() throws IOException {
        if (currentEntry == null) return;

        QZEntry entry = currentEntry;
        currentEntry = null;

        entry.uSize = entryUSize;
        if (entryUSize == 0) {
            flagSum[0]++;

            int flag = entry.flag;
            if ((flag & QZEntry.DIRECTORY) == 0) flagSum[1]++;
            if ((flag & QZEntry.ANTI     ) != 0) flagSum[2]++;

            emptyFiles.add(entry);
            return;
        }

        WordBlock4W b = blocks.get(blocks.size() - 1);
        b.fileCount++;
        b.uSize += entryUSize;

        files.add(entry);

        flagSum[9]++;
        entry.crc32 = (int) crc32.getValue();
        entry.flag |= QZEntry.CRC;

        crc32.reset();

        if (solidSize < 0) nextWordBlock();

        long actualSize = entryUSize;
        entryUSize = 0;

        long size = exceptFileSize;
        exceptFileSize = -1;

        // 因为不影响压缩文件，所以放在最后
        if (size >= 0 && actualSize != size)
            throw new IllegalArgumentException("提供的预期文件大小为"+size+",实际为"+actualSize);
    }

    private void countFlag(QZEntry entry) {
        int flag = entry.flag;
        if ((flag & QZEntry.CT       ) != 0) flagSum[3]++;
        if ((flag & QZEntry.AT       ) != 0) flagSum[4]++;
        if ((flag & QZEntry.MT       ) != 0) flagSum[5]++;
        if ((flag & QZEntry.ATTR     ) != 0) flagSum[6]++;
    }

    private void tryNextBlock(long fileSize) throws IOException {
        if (solidSize > 0 && !blocks.isEmpty()) {
            WordBlock4W b = blocks.get(blocks.size()-1);
            if (b.fileCount > 0 && b.uSize + fileSize > solidSize) {
                nextWordBlock();
            }
        }
    }
    public void nextWordBlock() throws IOException {
        if (out == null) return;

        WordBlock4W b = blocks.get(blocks.size() - 1);

        flagSum[7]++;
        flagSum[8]++;
        b.hasCrc |= 3;
        b.crc = (int) blockCrc32.getValue();
        blockCrc32.reset();

        out.close();
        out = null;
    }

    private OutputStream out() throws IOException {
        if (out != null) return out;

        WordBlock4W wb = new WordBlock4W();
        blocks.add(wb);

        wb.sortedCoders = coders;

        if (coders.length > 1)
            wb.outSizes = new long[coders.length-1];

        OutputStream out = wb.new CRC(s);
        for (int i = 0; i < coders.length; i++) {
            QZCoder m = coders[i];
            if (i!=0) out = wb.new Counter(out, coders.length-i-1);

            out = m.encode(out);
        }

        return this.out = out;
    }

    private ByteList buf;
    public void finish() throws IOException {
        closeEntry();
        nextWordBlock();

        if (finished) return;
        finished = true;

        ByteList.WriteOut out = new ByteList.WriteOut(null) {
            public void flush() {
                blockCrc32.update(list, arrayOffset(), realWIndex());
                super.flush();
            }
        };
        buf = out;

        long hstart = s.position();
        try {
            if (compressHeaderMin < 0 || files.size()+emptyFiles.size() <= compressHeaderMin) {
                blockCrc32.reset();
                out.setOut(s);
                writeHeader();
            } else {
                out.setOut(out());
                WordBlock4W metadata = blocks.remove(blocks.size()-1);

                writeHeader();

                out.flush();
                blocks.add(metadata);
                nextWordBlock();
                metadata.uSize = out.wIndex();
                System.out.println(metadata);

                long pos1 = s.position();
                blockCrc32.reset();
                out.setOut(s);

                out.write(iPackedHeader);
                writeStreamInfo(hstart-32);
                writeWordBlocks();
                out.write(iEnd);

                hstart = pos1;
            }
        } finally {
            try {
                out.close();
            } finally {
                if (this.out != null) {
                    this.out.close();
                    this.out = null;
                }
            }
        }

        long hend = s.position();
        if (s.length() > hend) s.setLength(hend);

        // start header
        s.seek(0);
        // signature and version
        ByteList buf = IOUtil.getSharedByteBuf();
        buf.putLong(QZArchive.QZ_HEADER)
           .putIntLE(0)
           .putLongLE(hstart-32)
           .putLongLE(hend-hstart)
           .putIntLE((int) blockCrc32.getValue());

        blockCrc32.reset();
        blockCrc32.update(buf.list, 12, 20);
        buf.putIntLE(8, (int) blockCrc32.getValue());

        s.write(buf);
    }

    @Override
    public void close() throws IOException {
        try {
            finish();
        } finally {
            s.close();
        }
    }

    private void writeHeader() {
        try {
            int fileSize = files.size();

            buf.write(iHeader);

            buf.write(iArchiveInfo);
            if (fileSize > 0) {
                writeStreamInfo(0);
                writeWordBlocks();
                writeBlockFileMap();
            }
            buf.write(iEnd);

            files.addAll(emptyFiles);
            writeFilesInfo();

            buf.write(iEnd);
        } finally {
            blocks.clear();
            files.clear();
            emptyFiles.clear();
        }
    }

    private void writeStreamInfo(long offset) {
        buf.put(iStreamInfo)
         .putVULong(offset)
         .putVUInt(blocks.size())

         .write(iSize);
        for (int i = 0; i < blocks.size(); i++)
            buf.putVULong(blocks.get(i).size);

        if (storeStreamCrc) {
            buf.write(iCRC32);
            if (flagSum[8] == blocks.size()) {
                buf.write(1);
            } else {
                buf.write(0);
                writeBits(i -> (blocks.get(i).hasCrc&2) != 0, blocks.size(), buf);
            }
            for (int i = 0; i < blocks.size(); i++) {
                WordBlock4W b = blocks.get(i);
                if ((b.hasCrc & 2) != 0)
                    buf.putIntLE(b.cCrc);
            }
        }

        buf.write(iEnd);
    }
    private void writeWordBlocks() {
        buf.put(iWordBlockInfo)

         .put(iWordBlock)
         .putVUInt(blocks.size())
         .put(0);

        ByteList w = IOUtil.getSharedByteBuf();
        for (WordBlock4W b : blocks) {
            buf.putVUInt(b.sortedCoders.length);
            // always simple codec
            for (QZCoder c : b.sortedCoders) {
                w.clear();
                c.writeOptions(w);

                byte[] id = c.id();

                int flags = id.length;
                if (w.wIndex() > 0) flags |= 0x20;
                buf.put((byte) flags).put(id);

                if (w.wIndex() > 0) {
                    buf.putVUInt(w.wIndex()).put(w);
                }
            }

            // pipe
            for (int i = 0; i < b.sortedCoders.length-1; i++)
                buf.putVUInt(i+1).putVUInt(i);
        }

        buf.write(iWordBlockSizes);
        for (WordBlock4W b : blocks) {
            if (b.outSizes != null)
                for (long size : b.outSizes)
                    buf.putVULong(size);
            buf.putVULong(b.uSize);
        }

        buf.write(iCRC32);
        if (flagSum[7] == blocks.size()) {
            buf.write(1);
        } else {
            buf.write(0);
            writeBits(i -> (blocks.get(i).hasCrc&1) != 0, blocks.size(), buf);
        }
        for (int i = 0; i < blocks.size(); i++) {
            WordBlock4W b = blocks.get(i);
            if ((b.hasCrc & 1) != 0)
                buf.putIntLE(b.crc);
        }

        buf.write(iEnd);
    }
    private void writeBlockFileMap() {
        buf.write(iBlockFileMap);

        if (files.size() > blocks.size()) {
            buf.write(iFileCounts);
            for (int i = 0; i < blocks.size(); i++)
                buf.putVUInt(blocks.get(i).fileCount);

            buf.write(iSize);
            int j = 0;
            for (int i = 0; i < blocks.size(); i++) {
                int count = blocks.get(i).fileCount-1;
                while (count-- > 0)
                    buf.putVULong(files.get(j++).uSize);
                j++;
            }

            buf.write(iCRC32);
            j = 0;
            if (flagSum[9] == files.size()) {
                buf.write(1);
                for (int i = 0; i < blocks.size(); i++) {
                    WordBlock4W b = blocks.get(i);
                    if (b.fileCount != 1 || (b.hasCrc&1) == 0) {
                        for (int k = b.fileCount; k > 0; k--)
                            buf.putIntLE(files.get(j++).crc32);
                    } else {
                        j++;
                    }
                }
            } else {
                buf.write(0);

                ByteList w = IOUtil.getSharedByteBuf();
                MyBitSet set = new MyBitSet();
                int extraCount = 0;

                for (int i = 0; i < blocks.size(); i++) {
                    WordBlock4W b = blocks.get(i);
                    if (b.fileCount == 1 && (b.hasCrc & 1) != 0) continue;

                    for (int k = b.fileCount; k > 0; k--) {
                        QZEntry entry = files.get(j++);
                        if ((entry.flag & QZEntry.CRC) != 0) {
                            set.add(extraCount);
                            w.putIntLE(entry.crc32);
                        }

                        extraCount++;
                    }
                }

                writeBits(set);
                buf.put(w);
            }

        }

        buf.write(iEnd);
    }

    private void writeFilesInfo() {
        buf.put(iFilesInfo).putVUInt(files.size());

        if (flagSum[0] > 0) writeBitMap(iEmpty, j -> files.get(j).uSize == 0);
        if (flagSum[1] > 0) writeBitMap(iEmptyFile, j -> (files.get(j).flag&QZEntry.DIRECTORY) == 0);
        if (flagSum[2] > 0) writeBitMap(iDeleteFile, j -> (files.get(j).flag&QZEntry.ANTI) != 0);

        writeFileNames();

        int i;
        i = flagSum[3];
        if (i > 0) writeSparseAttribute(iCTime, QZEntry.CT, i, (entry, buf) -> buf.putLongLE(entry.createTime));
        i = flagSum[4];
        if (i > 0) writeSparseAttribute(iATime, QZEntry.AT, i, (entry, buf) -> buf.putLongLE(entry.accessTime));
        i = flagSum[5];
        if (i > 0) writeSparseAttribute(iMTime, QZEntry.MT, i, (entry, buf) -> buf.putLongLE(entry.modifyTime));
        i = flagSum[6];
        if (i > 0) writeSparseAttribute(iAttribute, QZEntry.ATTR, i, (entry, buf) -> buf.putIntLE(entry.attributes));

        buf.write(iEnd);
    }

    private void writeBitMap(int id, IntFunction<Boolean> fn) {
        ByteList buf = IOUtil.getSharedByteBuf();
        writeBits(fn, files.size(), buf);
        this.buf.put((byte) id).putVUInt(buf.wIndex()).put(buf);
    }
    private void writeFileNames() {
        buf.write(iFileName);

        ByteList buf = IOUtil.getSharedByteBuf().put(0);

        for (int j = 0; j < files.size(); j++) {
            String s = files.get(j).getName();
            for (int i = 0; i < s.length(); i++)
                buf.putShortLE(s.charAt(i));
            buf.putShortLE(0);
        }

        this.buf.putVUInt(buf.wIndex()).put(buf);
    }
    private void writeSparseAttribute(int id, int flag, int count, BiConsumer<QZEntry, DynByteBuf> fn) {
        ByteList buf = IOUtil.getSharedByteBuf();
        if (count < files.size()) {
            buf.write(0);
            writeBits(i -> (files.get(i).flag&flag) != 0, files.size(), buf);
        } else {
            buf.write(1);
        }
        buf.write(0);

        for (int i = 0; i < files.size(); i++) {
            QZEntry entry = files.get(i);
            if ((entry.flag&flag) != 0)
                fn.accept(entry, buf);
        }

        this.buf.put((byte) id).putVUInt(buf.wIndex()).put(buf);
    }

    private static void writeBits(IntFunction<Boolean> fn, int len, DynByteBuf buf) {
        int v = 0;
        int shl = 7;
        for (int i = 0; i < len; i++) {
            v |= ((fn.apply(i) ? 1 : 0) << shl);
            if (--shl < 0) {
                buf.write(v);
                shl = 7;
                v = 0;
            }
        }
        if (shl != 7) buf.write(v);
    }
    private void writeBits(MyBitSet set1) {
        long[] set = set1.array();
        int size = set1.last()+1;

        int i = 0;

        while (size >= 64) {
            buf.putLongLE(invertBits(set[i++]));
            size -= 64;
        }

        long fin = invertBits(set[i]);
        while (size > 0) {
            buf.put((byte) fin);
            fin >>>= 8;
            size -= 8;
        }
    }
}
