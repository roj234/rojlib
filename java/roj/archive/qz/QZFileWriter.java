package roj.archive.qz;

import org.jetbrains.annotations.NotNull;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.config.data.CInt;
import roj.crypt.CRC32;
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
import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2023/3/14 22:08
 */
public class QZFileWriter extends QZWriter {
    private int compressHeaderMin = 1;

    public QZFileWriter(String path) throws IOException {this(new FileSource(path));}
    public QZFileWriter(File file) throws IOException {this(new FileSource(file));}
    /**
     * 构建自定义数据源写入器（自动创建32字节头保留空间，并初始化为LZMA2 Level5编码器）
     * @param source 数据源对象
     * @throws IOException 当数据源初始化失败时抛出
     */
    public QZFileWriter(Source source) throws IOException {
        super(source);
        if (source.length() < 32) source.setLength(32);
        source.seek(32);
        setCodec(new LZMA2());
    }

    /**
     * 设置头部压缩策略
     *
     * <p>参数说明：
     * <ul>
     *   <li>0: 总是压缩头部</li>
     *   <li>-1: 永不压缩头部</li>
     *   <li>大于0: 当文件总数超过该值时压缩头部（默认1）</li>
     * </ul>
     *
     * <p>注意事项：
     * <ul>
     *   <li>头部大小与文件数量成正比，但无法预先计算</li>
     *   <li>如需禁用文件名加密，应在调用{@link #finish()}前设置此值为-1或通过{@link #setCodec(QZCoder...)}移除加密编码器</li>
     *   <li>错误设置可能导致文件内容和名称的加密策略不一致</li>
     * </ul>
     *
     * @param mode 压缩策略参数
     */
    public void setCompressHeader(int mode) { compressHeaderMin = mode; }

    private List<ParallelWriter> parallelWriter;

    /**
     * 创建基于内存缓存的多线程并行写入器
     *
     * @return 并行写入器实例
     * @throws IOException 当流已关闭或初始化失败时抛出
     * @throws IllegalStateException 如果当前存在未关闭的WordBlock
     *
     * @see #newParallelWriter(Source)
     */
    public final QZWriter newParallelWriter() throws IOException {
        return newParallelWriter(new MemorySource(DynByteBuf.allocateDirect()));
    }
    /**
     * 创建自定义缓存的多线程并行写入器
     *
     * <p>注意事项：
     * <ul>
     *   <li>主写入器与并行写入器不能同时操作</li>
     *   <li>进入多线程模式前必须{@link #flush() 关闭当前WordBlock}</li>
     * </ul>
     *
     * @param cache 并行写入数据的缓存
     * @return 并行写入器实例
     * @throws IOException 当流已关闭或初始化失败时抛出
     * @throws IllegalStateException 如果当前存在未关闭的WordBlock
     */
    public synchronized QZWriter newParallelWriter(Source cache) throws IOException {
        if (out != null) throw new IllegalStateException("进入多线程模式前需要结束QZFW的WordBlock，否则会导致文件数据损坏");
        if (finished) throw new IOException("Stream closed");
        if (parallelWriter == null)
            parallelWriter = new ArrayList<>();

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
        ParallelWriter(Source source) { super(source, QZFileWriter.this); }

        @Override
        void finishWordBlock() throws IOException {
            if (out == null) return;

            super.finishWordBlock();
            QZFileWriter that = QZFileWriter.this;
            synchronized (that) {
                that.source.put(source);
                source.seek(0);

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

                source.close();
            }

            if (!removed) throw new AsynchronousCloseException();
        }
    }

    /**
     * 移除最后一个写入的WordBlock及其关联文件条目
     *
     * @throws IOException 当流已关闭或操作失败时抛出
     * @throws IllegalStateException 当没有可移除的WordBlock时抛出
     */
    public void removeLastWordBlock() throws IOException {
        flush();

        WordBlock b = blocks.pop();
        if (b == null) throw new IllegalStateException("没有可移除的WordBlock");

        int i = files.size() - 1;
        for (; i >= 0; i--) {
            QZEntry entry = files.get(i);
            if (entry.block != b) break;

            countFlag(entry.flag, -1);
        }

        files.removeRange(i+1, files.size());

        if ((b.hasCrc & 1) != 0) flagSum[8]--;
        flagSum[9] -= b.extraSizes.length;

        if (blocks.isEmpty()) source.seek(32);
        else {
            b = blocks.get(blocks.size()-1);
            source.seek(b.offset+b.size());
        }
    }

    /**
     * 通过名称移除空文件条目
     *
     * @param name 要移除的空文件名称
     * @return 被移除的条目，未找到时返回null
     */
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
    /**
     * 通过索引移除空文件条目
     *
     * @param i 要移除的条目索引（基于插入顺序）
     * @throws IndexOutOfBoundsException 当索引越界时抛出
     */
    public void removeEmptyFile(int i) {
        QZEntry entry = emptyFiles.remove(i);
        countFlag(entry.flag, -1);
        countFlagEmpty(entry.flag, -1);
    }

    private ByteList buf;
    /**
     * 结束压缩包的写入操作
     *
     * <p>执行流程：
     * <ol>
     *   <li>等待所有并行写入任务完成</li>
     *   <li>根据压缩策略处理头部信息</li>
     *   <li>生成最终文件校验信息</li>
     *   <li>裁剪文件到实际数据大小（除非设置了{@link #NO_TRIM_FILE}标志）</li>
     * </ol>
     *
     * @throws IOException 当流已关闭或写入失败时抛出
     * @throws IllegalStateException 如果存在未关闭的WordBlock
     */
    public void finish() throws IOException {
        if (finished) return;

        super.finish();
        waitAsyncFinish();

        var crcOut = new OutputStream() {
            OutputStream out;
            public void write(int b) {}
            public void write(@NotNull byte[] b, int off, int len) throws IOException {
                blockCrc32 = CRC32.update(blockCrc32, b, off, len);
                out.write(b, off, len);
            }
            public void close() throws IOException {if (out != null) out.close();}
        };
        var out = buf = new ByteList.ToStream(crcOut);

        long hstart = source.position();
        try {
            if (compressHeaderMin == -1 ||
                files.size()+emptyFiles.size() <= compressHeaderMin ||
                coders[0] instanceof Copy) {

                crcOut.out = source;
                if ((files.size()|emptyFiles.size()) != 0)
                    writeHeader();
            } else {
                crcOut.out = out();
                WordBlock metadata = blocks.pop();

                writeHeader();

                out.flush();
                blocks.add(metadata);
                finishWordBlock();
                metadata.uSize = out.wIndex();

                long pos1 = source.position();
                blockCrc32 = CRC32.initial;
                crcOut.out = source;

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

        long hend = source.position();
        if ((flag&NO_TRIM_FILE) == 0 && source.length() > hend) source.setLength(hend);

        // start header
        source.seek(0);
        // signature and version
        ByteList buf = IOUtil.getSharedByteBuf();
        buf.putLong(QZArchive.QZ_HEADER)
           .putIntLE(0)
           .putLongLE(hstart-32)
           .putLongLE(hend-hstart)
           .putIntLE(CRC32.finish(blockCrc32))
           .putIntLE(8, CRC32.crc32(buf.list, 12, 20));

        source.write(buf);
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
            if (cc != null) {cc.writeCoders(b, buf);continue;}

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
                BitSet set = new BitSet();
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
            BitSet emptyFile = flagSum[1] > 0 ? new BitSet() : null;
            BitSet anti = flagSum[2] > 0 ? new BitSet() : null;

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
        if ((i = flagSum[3]) > 0) writeSparseTime(kCTime, QZEntry.CT, i);
        if ((i = flagSum[4]) > 0) writeSparseTime(kATime, QZEntry.AT, i);
        if ((i = flagSum[5]) > 0) writeSparseTime(kMTime, QZEntry.MT, i);
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
                buf.putIntLE(U.getInt(entry, offset));
        }
    }
    private void writeSparseTime(int id, int flag, int count) {
        DynByteBuf buf = writeSparseHeader(id, flag, count);

        long offset = QZEntryA.SPARSE_ATTRIBUTE_OFFSET[id-kCTime];
        for (int i = 0; i < files.size(); i++) {
            QZEntry entry = files.get(i);
            if ((entry.flag&flag) != 0)
                buf.putLongLE(U.getLong(entry, offset));
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