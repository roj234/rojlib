package roj.archive.qz;

import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.MemorySource;
import roj.io.source.Source;
import roj.math.MutableInt;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

import static roj.archive.qz.BlockId.*;

/**
 * @author Roj234
 * @since 2023/3/14 0014 22:08
 */
public class QZFileWriter extends QZWriter {
    private int compressHeaderMin = 1;

    public QZFileWriter(String path) throws IOException { this(new FileSource(path));  }
    public QZFileWriter(File file) throws IOException { this(new FileSource(file)); }
    public QZFileWriter(Source s) throws IOException { super(s); s.seek(32); setCodec(new LZMA2()); }

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

    public final QZWriter parallel() throws IOException { return parallel(new MemorySource()); }
    public synchronized QZWriter parallel(Source cache) throws IOException {
        if (finished) throw new IOException("Stream closed");

        closeWordBlock();

        if (parallelWriter == null)
            parallelWriter = new SimpleList<>();

        ParallelWriter pw = new ParallelWriter(cache);
        parallelWriter.add(pw);
        return pw;
    }

    private synchronized void waitAsyncFinish() {
        while (!parallelWriter.isEmpty()) {
            try {
                parallelWriter.wait();
            } catch (InterruptedException e) {
                Helpers.athrow(e);
            }
        }
    }

    private class ParallelWriter extends QZWriter {
        ParallelWriter(Source s) { super(s, QZFileWriter.this); }

        @Override
        public void finish() throws IOException {
            if (finished) return;
            super.finish();

            QZFileWriter that = QZFileWriter.this;
            synchronized (that) {
				if (!parallelWriter.remove(this)) throw new AsynchronousCloseException();

                if (s instanceof MemorySource) {
                    MemorySource s = (MemorySource) this.s;
                    that.s.write(s.buffer());
                    ((ByteList)s.buffer())._free();
                } else {
                    FileSource s = (FileSource) this.s;
                    try {
                        byte[] data = ArrayCache.getByteArray(4096, false);
                        s.seek(0);
                        while (true) {
                            int r = s.read(data);
                            if (r < 0) break;
                            that.s.write(data, 0, r);
                        }
                    } finally {
                        s.close();
                        Files.deleteIfExists(s.getFile().toPath());
                    }
                }

				that.blocks.addAll(blocks);
				that.files.addAll(files);
				that.emptyFiles.addAll(emptyFiles);

				int[] sum = that.flagSum;
				for (int i = 0; i < sum.length; i++) sum[i] += flagSum[i];

				if (parallelWriter.isEmpty()) that.notifyAll();
			}
        }
    }

    public void removeLastWordBlock() throws IOException {
        closeWordBlock();

        WordBlock b = blocks.remove(blocks.size() - 1);

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
                emptyFiles.remove(i);
                countFlag(entry.flag, -1);
                countFlagEmpty(entry.flag, -1);
                return entry;
			}
		}
        return null;
    }

    private ByteList buf;
    public void finish() throws IOException {
        if (finished) return;

        super.finish();
        waitAsyncFinish();

        ByteList.WriteOut out = new ByteList.WriteOut(null) {
            public void flush() {
                blockCrc32.update(list, arrayOffset(), realWIndex());
                super.flush();
            }
        };
        buf = out;

        long hstart = s.position();
        try {
            if (compressHeaderMin == -1 || files.size()+emptyFiles.size() < compressHeaderMin) {
                blockCrc32.reset();
                out.setOut(s);
                writeHeader();
            } else {
                out.setOut(out());
                WordBlock metadata = blocks.remove(blocks.size()-1);

                writeHeader();

                out.flush();
                blocks.add(metadata);
                closeWordBlock0();
                metadata.uSize = out.wIndex();

                long pos1 = s.position();
                blockCrc32.reset();
                out.setOut(s);

                out.write(kEncodedHeader);
                writeStreamInfo(hstart-32);
                writeWordBlocks();
                out.write(kEnd);

                hstart = pos1;
            }
        } finally {
            try {
                out.flush();
            } finally {
                if (this.out != null) {
                    this.out.close();
                    this.out = null;
                }

                out.setOut(null);
                out.close();
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

    private void writeHeader() {
        try {
            buf.write(kHeader);

            if (files.size() > 0) {
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
            if (b.complexCoder != null) {
                b.complexCoder.writeCoder(b, buf);
                continue;
            }

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

        buf.write(kCRC);
        if (flagSum[8] == blocks.size()) {
            buf.write(1);
        } else {
            buf.write(0);
            writeBits(i -> (blocks.get(i).hasCrc&1) != 0, blocks.size(), buf);
        }
        for (int i = 0; i < blocks.size(); i++) {
            WordBlock b = blocks.get(i);
            if ((b.hasCrc & 1) != 0)
                buf.putIntLE(b.crc);
        }

        buf.write(kEnd);
    }
    private void writeBlockFileMap() {
        buf.write(kSubStreamsInfo);

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
            MutableInt count = new MutableInt();
            MyBitSet emptyFile = flagSum[1] > 0 ? new MyBitSet() : null;
            MyBitSet anti = flagSum[2] > 0 ? new MyBitSet() : null;

            ByteList ob = IOUtil.getSharedByteBuf();
            writeBits(j -> {
                QZEntry e = files.get(j);
                if (e.uSize == 0) {
                    if ((e.flag&QZEntry.DIRECTORY) == 0) emptyFile.add(count.value);
                    if ((e.flag&QZEntry.ANTI) != 0) anti.add(count.value);
                    count.value++;
                    return true;
                }
                return false;
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
        i = flagSum[3];
        if (i > 0) writeSparseAttribute(kCTime, QZEntry.CT, i, (entry, buf) -> buf.putLongLE(entry.createTime));
        i = flagSum[4];
        if (i > 0) writeSparseAttribute(kATime, QZEntry.AT, i, (entry, buf) -> buf.putLongLE(entry.accessTime));
        i = flagSum[5];
        if (i > 0) writeSparseAttribute(kMTime, QZEntry.MT, i, (entry, buf) -> buf.putLongLE(entry.modifyTime));
        i = flagSum[6];
        if (i > 0) writeSparseAttribute(kWinAttributes, QZEntry.ATTR, i, (entry, buf) -> buf.putIntLE(entry.attributes));

        buf.write(kEnd);
    }

    private void writeFileNames() {
        buf.write(kName);

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
}