package roj.archive.qz;

import org.jetbrains.annotations.NotNull;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.data.CInt;
import roj.crypt.CRC32s;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.MemorySource;
import roj.io.source.Source;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.Int2IntFunction;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.AsynchronousCloseException;
import java.util.Arrays;
import java.util.List;

import static roj.archive.qz.BlockId.*;
import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/3/14 0014 22:08
 */
public class QZFileWriter extends QZWriter {
    private int compressHeaderMin = 1;

    public QZFileWriter(String path) throws IOException {this(new FileSource(path));}
    public QZFileWriter(File file) throws IOException {this(new FileSource(file));}
    public QZFileWriter(Source s) throws IOException {
        super(s);
        if (s.length() < 32) s.setLength(32);
        s.seek(32);
        setCodec(new LZMA2());
    }

    /**
     * 是否压缩头（使用最后设置的codec）
     *  0: 是
     * -1: 否
     * 大于零: 文件数量大于时压缩 (默认1)
     * 头大小正比于文件数量,但是头大小没法预先计算
     *
     * 注意: 如果不需要加密文件名请设置它为false 或者调用finish前删除加密的codec
     * 反之，需要则设为true
     * 否则可能会出现该加密没加密或相反
     */
    public void setCompressHeader(int i) { compressHeaderMin = i; }

    private List<ParallelWriter> parallelWriter;

    /**
     * 注意事项：本地的QZWriter不能和parallelWriter同时写入文件
     */
    public final QZWriter parallel() throws IOException { return parallel(new MemorySource(DynByteBuf.allocateDirect())); }
    public synchronized QZWriter parallel(Source cache) throws IOException {
        if (out != null) throw new IllegalStateException("进入多线程模式前需要结束QZFW的WordBlock，否则会导致文件数据损坏");
        if (finished) throw new IOException("Stream closed");
        if (parallelWriter == null)
            parallelWriter = new SimpleList<>();

        ParallelWriter pw = new ParallelWriter(cache);
        parallelWriter.add(pw);
        return pw;
    }

    private synchronized void waitAsyncFinish() {
        if (parallelWriter == null) return;
        while (!parallelWriter.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException e) {
                Helpers.athrow(e);
            }
        }
    }

    private class ParallelWriter extends QZWriter {
        ParallelWriter(Source s) { super(s, QZFileWriter.this); }

        @Override
        void closeWordBlock0() throws IOException {
            if (out == null) return;

            super.closeWordBlock0();
            QZFileWriter that = QZFileWriter.this;
            synchronized (that) {
                that.s.put(s);
                s.seek(0);

                that.blocks.addAll(blocks);
                that.files.addAll(files);
                that.emptyFiles.addAll(emptyFiles);

                blocks.clear();
                files.clear();
                emptyFiles.clear();

                int[] sum = that.flagSum;
                for (int i = 0; i < sum.length; i++) sum[i] += flagSum[i];

                Arrays.fill(flagSum, 0);
            }
        }

        @Override
        public void finish() throws IOException {
            if (finished) return;
            boolean removed;

            try {
                super.finish();
            } finally {
                QZFileWriter that = QZFileWriter.this;
                synchronized (that) {
                    removed = parallelWriter.remove(this);
                    if (parallelWriter.isEmpty()) that.notifyAll();
                }

                s.close();
            }

            if (!removed) throw new AsynchronousCloseException();
        }
    }

    public void removeLastWordBlock() throws IOException {
        closeWordBlock();

        WordBlock b = blocks.pop();

        int i = files.size() - 1;
        for (; i >= 0; i--) {
            QZEntry ent = files.get(i);
            if (ent.block != b) break;

            countFlag(ent.flag, -1);
        }

        files.removeRange(i+1, files.size());

        if ((b.hasCrc & 1) != 0) flagSum[8]--;
        flagSum[9] -= b.extraSizes.length;

        if (blocks.isEmpty()) s.seek(32);
        else {
            b = blocks.get(blocks.size()-1);
            s.seek(b.offset+b.size());
        }
    }

    public QZEntry removeEmptyFile(String name) {
		for (int i = 0; i < emptyFiles.size(); i++) {
			QZEntry entry = emptyFiles.get(i);
			if (entry.getName().equals(name)) {
                removeEmptyFile(i);
                return entry;
			}
		}
        return null;
    }
    public void removeEmptyFile(int i) {
        QZEntry entry = emptyFiles.remove(i);
        countFlag(entry.flag, -1);
        countFlagEmpty(entry.flag, -1);
    }

    private ByteList buf;
    public void finish() throws IOException {
        if (finished) return;

        super.finish();
        waitAsyncFinish();

        var crcOut = new OutputStream() {
            OutputStream out;
            public void write(int b) {}
            public void write(@NotNull byte[] b, int off, int len) throws IOException {
                blockCrc32 = CRC32s.update(blockCrc32, b, off, len);
                out.write(b, off, len);
            }
            public void close() throws IOException {if (out != null) out.close();}
        };
        var out = buf = new ByteList.ToStream(crcOut);

        long hstart = s.position();
        try {
            if (compressHeaderMin == -1 ||
                files.size()+emptyFiles.size() <= compressHeaderMin ||
                coders[0] instanceof Copy) {

                crcOut.out = s;
                if ((files.size()|emptyFiles.size()) != 0)
                    writeHeader();
            } else {
                crcOut.out = out();
                WordBlock metadata = blocks.pop();

                writeHeader();

                out.flush();
                blocks.add(metadata);
                closeWordBlock0();
                metadata.uSize = out.wIndex();

                long pos1 = s.position();
                blockCrc32 = CRC32s.INIT_CRC;
                crcOut.out = s;

                out.write(kEncodedHeader);
                writeStreamInfo(hstart-32);
                writeWordBlocks();
                out.write(kEnd);

                hstart = pos1;
            }

            out.flush();
            crcOut.out = null;
        } finally {
            // crcOut没写close, 并且如果出异常了也能关闭out()
            IOUtil.closeSilently(out);
        }

        long hend = s.position();
        if ((flag&NO_TRIM_FILE) == 0 && s.length() > hend) s.setLength(hend);

        // start header
        s.seek(0);
        // signature and version
        ByteList buf = IOUtil.getSharedByteBuf();
        buf.putLong(QZArchive.QZ_HEADER)
           .putIntLE(0)
           .putLongLE(hstart-32)
           .putLongLE(hend-hstart)
           .putIntLE(CRC32s.retVal(blockCrc32))
           .putIntLE(8, CRC32s.once(buf.list, 12, 20));

        s.write(buf);
    }

    private void writeHeader() {
        try {
            buf.write(kHeader);

            if (files.size() > 0) {
                assert !blocks.isEmpty();

                buf.write(kMainStreamsInfo);
                writeStreamInfo(0);
                writeWordBlocks();
                writeBlockFileMap();
                buf.write(kEnd);
            }

            files.addAll(emptyFiles);
            writeFilesInfo();

            buf.write(kEnd);
        } finally {
            blocks.clear();
            files.clear();
            emptyFiles.clear();
            flagSum[8] = flagSum[9] = 0;
        }
    }

    private void writeStreamInfo(long offset) {
        buf.put(kPackInfo)
         .putVULong(offset)
         .putVUInt(blocks.size()+flagSum[9])

         .write(kSize);
        for (int i = 0; i < blocks.size(); i++) {
            WordBlock b = blocks.get(i);
            buf.putVULong(b.size);
            for (long len : b.extraSizes) buf.putVULong(len);
        }

        buf.write(kEnd);
    }
    private void writeWordBlocks() {
        buf.put(kUnPackInfo)

         .put(kFolder)
         .putVUInt(blocks.size())
         .put(0);

        ByteList w = IOUtil.getSharedByteBuf();
        for (WordBlock b : blocks) {
            var cc = b.complexCoder();
            if (cc != null) {cc.writeCoder(b, buf);continue;}

            buf.putVUInt(b.coder.length);
            // always simple codec
            for (QZCoder c : b.coder) {
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
            for (int i = 0; i < b.coder.length-1; i++)
                buf.putVUInt(i+1).putVUInt(i);
        }

        buf.write(kCodersUnPackSize);
        for (WordBlock b : blocks) {
            if (b.outSizes != null)
                for (long size : b.outSizes)
                    buf.putVULong(size);
            buf.putVULong(b.uSize);
        }

        if (flagSum[8] > 0) {
            buf.write(kCRC);
            if (flagSum[8] == blocks.size()) {
                buf.write(1);
            } else {
                buf.write(0);
                writeBits(i -> blocks.get(i).hasCrc&1, blocks.size(), buf);
            }
            for (int i = 0; i < blocks.size(); i++) {
                WordBlock b = blocks.get(i);
                if ((b.hasCrc & 1) != 0)
                    buf.putIntLE(b.crc);
            }
        }

        buf.write(kEnd);
    }
    private void writeBlockFileMap() {
        buf.write(kSubStreamsInfo);

        block:
        if (files.size() > blocks.size()) {
            buf.write(kNumUnPackStream);
            for (int i = 0; i < blocks.size(); i++)
                buf.putVUInt(blocks.get(i).fileCount);

            buf.write(kSize);
            int j = 0;
            for (int i = 0; i < blocks.size(); i++) {
                int count = blocks.get(i).fileCount-1;
                while (count-- > 0)
                    buf.putVULong(files.get(j++).uSize);
                j++;
            }

            if (flagSum[7] == 0) break block;
            buf.write(kCRC);
            j = 0;
            if (flagSum[7] == files.size()) {
                buf.write(1);
                for (int i = 0; i < blocks.size(); i++) {
                    WordBlock b = blocks.get(i);
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
                    WordBlock b = blocks.get(i);
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

                set.writeBits(buf);
                buf.put(w);
            }

        }

        buf.write(kEnd);
    }

    private void writeFilesInfo() {
        buf.put(kFilesInfo).putVUInt(files.size());

        if (flagSum[0] > 0) {
            CInt count = new CInt();
            MyBitSet emptyFile = flagSum[1] > 0 ? new MyBitSet() : null;
            MyBitSet anti = flagSum[2] > 0 ? new MyBitSet() : null;

            ByteList ob = IOUtil.getSharedByteBuf();
            writeBits(j -> {
                QZEntry e = files.get(j);
                if (e.uSize == 0) {
                    if ((e.flag&QZEntry.DIRECTORY) == 0) emptyFile.add(count.value);
                    if ((e.flag&QZEntry.ANTI) != 0) anti.add(count.value);
                    count.value++;
                    return 1;
                }
                return 0;
            }, files.size(), ob);
            buf.put(kEmptyStream).putVUInt(ob.wIndex()).put(ob);

            int byteLen = (count.value+7)/8;
            // equals to flagSum[n] > 0
            if (emptyFile != null) {
                int extra = byteLen-emptyFile.byteLength();
                emptyFile.writeBits(buf.put(kEmptyFile).putVUInt(byteLen));
                while (extra > 0) {
                    buf.put(0);
                    extra--;
                }
            }

            if (anti != null) {
                int extra = byteLen-anti.byteLength();
                anti.writeBits(buf.put(kAnti).putVUInt(byteLen));
                while (extra > 0) {
                    buf.put(0);
                    extra--;
                }
            }
        }

        writeFileNames();

        int i;
        if ((i = flagSum[3]) > 0) writeSparseAttribute(kCTime, QZEntry.CT, i);
        if ((i = flagSum[4]) > 0) writeSparseAttribute(kATime, QZEntry.AT, i);
        if ((i = flagSum[5]) > 0) writeSparseAttribute(kMTime, QZEntry.MT, i);
        if ((i = flagSum[6]) > 0) writeSparseAttribute(i);

        buf.write(kEnd);
    }

    private void writeFileNames() {
        int len = 1 + (files.size()<<1); // external=0 + terminators
        for (int j = 0; j < files.size(); j++)
            len += files.get(j).getName().length() << 1;

        DynByteBuf buf = this.buf.put(kName).putVUInt(len).put(0);

        for (int j = 0; j < files.size(); j++) {
            String s = files.get(j).getName();
            for (int i = 0; i < s.length(); i++)
                buf.putShortLE(s.charAt(i));
            buf.putShortLE(0);
        }
    }
    private void writeSparseAttribute(int count) {
        DynByteBuf buf = writeSparseHeader(kWinAttributes, QZEntry.ATTR, count);

        long offset = QZEntryA.SPARSE_ATTRIBUTE_OFFSET[3];
        for (int i = 0; i < files.size(); i++) {
            QZEntry entry = files.get(i);
            if ((entry.flag&QZEntry.ATTR) != 0)
                buf.putIntLE(u.getInt(entry, offset));
        }
    }
    private void writeSparseAttribute(int id, int flag, int count) {
        DynByteBuf buf = writeSparseHeader(id, flag, count);

        long offset = QZEntryA.SPARSE_ATTRIBUTE_OFFSET[id-kCTime];
        for (int i = 0; i < files.size(); i++) {
            QZEntry entry = files.get(i);
            if ((entry.flag&flag) != 0)
                buf.putLongLE(u.getLong(entry, offset));
        }
    }
    private DynByteBuf writeSparseHeader(int id, int flag, int count) {
        int len = 2;
        // bitset size
        if (count < files.size()) len += (files.size()+7) >> 3;
        len += count << (id == kWinAttributes ? 2 : 3);

        DynByteBuf buf = this.buf.put(id).putVUInt(len);

        if (count < files.size()) {
            buf.write(0);
            writeBits(i -> (files.get(i).flag&flag) != 0 ? 1 : 0, files.size(), buf);
        } else {
            buf.write(1);
        }
        return buf.put(0);
    }

    private static void writeBits(Int2IntFunction fn, int len, DynByteBuf buf) {
        int v = 0;
        int shl = 7;
        for (int i = 0; i < len; i++) {
            v |= fn.apply(i) << shl;
            if (--shl < 0) {
                buf.write(v);
                shl = 7;
                v = 0;
            }
        }
        if (shl != 7) buf.write(v);
    }
}