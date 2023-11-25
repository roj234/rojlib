package roj.archive.qz;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveWriter;
import roj.collect.SimpleList;
import roj.io.source.Source;
import roj.math.MutableInt;
import roj.util.ArrayCache;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * @author Roj234
 * @since 2023/3/14 0014 22:08
 */
public abstract class QZWriter extends OutputStream implements ArchiveWriter {
    public Source s;

    final SimpleList<WordBlock> blocks = new SimpleList<>();
    /**
     * 0 : 空项目
     * 1 : 空文件(而不是文件夹)
     * 2 : anti
     * 3 : 包含创建时间
     * 4 : 访问时间
     * 5 : 修改时间
     * 6 : windows属性
     * 7 : crc32
     *
     * 8 : block uCrc32
     * 9: Complex coder
     */
    final int[] flagSum = new int[10];

    final SimpleList<QZEntry> files = new SimpleList<>();
    final SimpleList<QZEntry> emptyFiles = new SimpleList<>();

    private QZEntry currentEntry;
    private long entryUSize;
    OutputStream out;

    final CRC32 crc32 = new CRC32(), blockCrc32 = new CRC32();

    boolean finished;

    private long solidSize;

    private Object[] coders;
    public static final QZCoder[] NO_COMPRESS = {Copy.INSTANCE};

    private CoderInfo complexCoder;
    private int cOffsets, cOutSizes;

    QZWriter(Source s) {this.s = s;}
    QZWriter(Source s, QZWriter parent) {
        this.s = s;
        this.solidSize = parent.solidSize;
        this.coders = parent.coders;
    }

    public ArchiveEntry createEntry(String fileName) { return new QZEntry(fileName); }

    public final void setCodec(QZCoder... methods) {
        assert methods.length <= 32;
        complexCoder = null;

        if (methods.length == 0) {
            coders = NO_COMPRESS;
            return;
        }

        if (methods.length == 1) {
            coders = methods;
            return;
        }

        for (QZCoder m : methods) {
            if (m instanceof QZComplexCoder) {
                setComplexCodec(methods);
                return;
            }
        }

        coders = new QZCoder[methods.length];
        for (int i = 0; i < methods.length; i++) {
            coders[methods.length-i-1] = methods[i];
        }
    }

    /**
     * 语法: 后序(栈)直到null(文件流)
     * 举例: [BCJ2, LZMA2, null, LZMA, null, LZMA, null, null]
     * 举例: [一进二出, 二进二出, null, null]
     */
    private void setComplexCodec(QZCoder[] methods) {
        CoderInfo[] nodes = new CoderInfo[methods.length];
        SimpleList<CoderInfo> nonNull = new SimpleList<>();

        int provides = -1;
        for (int i = 0; i < methods.length; i++) {
            QZCoder m = methods[i];
            if (m == null) continue;

            CoderInfo info = new CoderInfo(m, provides);
            nodes[i] = info;
            nonNull.add(info);
            provides += info.provides;
        }

        CoderInfo root = nodes[0];
        if (root.provides > 1) throw new IllegalArgumentException("root只能有一个输入!");

        MutableInt blockId = new MutableInt();
        inflateTree(root, nodes, blockId, 1);

        coders = nonNull.toArray(new CoderInfo[nonNull.size()]);
        complexCoder = root;
        cOffsets = blockId.getValue()-1;
        cOutSizes = provides;
    }
    private int inflateTree(CoderInfo node, CoderInfo[] nodes, MutableInt blockId, int i) {
        for (int j = 0; j < node.uses.length; j++) {
            CoderInfo next = nodes[i++];
            if (next != null) {
                i = inflateTree(next, nodes, blockId, i);
                for (int k = 0; k < next.provides; k++) {
                    node.pipe(j, next, k);
                }
            } else {
                node.setFileInput(blockId.getAndIncrement(), j);
            }
        }
        return i;
    }

    /**
     * 固实大小
     * 0: 合计一个字块
     * -1: 否 (一个文件一个字块)
     * 大于零: 按此(输入)大小
     */
    public void setSolidSize(long l) {
        this.solidSize = l;
    }

    // do not remove!
    public SimpleList<QZEntry> getFiles() { return files; }
    public SimpleList<QZEntry> getEmptyFiles() { return emptyFiles; }

    public final void copy(ArchiveFile owner, ArchiveEntry entry) throws IOException {
        QZArchive archive = (QZArchive) owner;
        QZEntry entry1 = (QZEntry) entry;
        if (entry1.uSize == 0) {
            beginEntry(entry1);
            closeEntry();
            return;
        }
        WordBlock b = entry1.block;
        if (b.fileCount != 1) throw new IOException("写入固实7z字块请使用copy的重载");

        copy(archive, b);
    }
    public final void copy(QZArchive archive, WordBlock b) throws IOException {
        if (b == null) throw new NullPointerException("b");
        if (finished) throw new IOException("Stream closed");

        closeEntry();
        nextWordBlock();

        if (s.hasChannel() & archive.r.hasChannel()) {
            FileChannel myCh = s.channel();
            archive.r.channel().transferTo(b.offset, b.size(), myCh);
        } else {
            Source src = archive.r;
            src.seek(b.offset);

            byte[] bb = ArrayCache.getByteArray(1024, false);
            long len = b.size();
            while (len > 0) {
                int l = (int) Math.min(bb.length, len);
                src.readFully(bb, 0, l);
                s.write(bb, 0, l);
                len -= l;
            }
            ArrayCache.putArray(bb);
        }

        blocks.add(b);
        countBlockFlag(b);

        QZEntry entry = b.firstEntry;
        while (entry != null) {
            files.add(entry);
            countFlag(entry.flag);
            entry = entry.next;
        }
    }

    public final void beginEntry(ArchiveEntry entry) throws IOException {
        QZEntry ent = ((QZEntry) entry);
        if (finished) throw new IOException("Stream closed");

        closeEntry();
        countFlag(ent.flag & ~QZEntry.CRC);
        currentEntry = ent;
    }
    public final void write(int b) throws IOException {
        if (currentEntry == null) throw new IOException("No active entry");

        out().write(b);
        crc32.update(b);
        blockCrc32.update(b);
        entryUSize++;
    }
    public final void write(byte[] b, int off, int len) throws IOException {
        if (currentEntry == null) throw new IOException("No active entry");

        if (len <= 0) return;
        out().write(b, off, len);
        crc32.update(b, off, len);
        blockCrc32.update(b, off, len);
        entryUSize += len;
    }
    public final void closeEntry() throws IOException {
        if (currentEntry == null) return;

        QZEntry entry = currentEntry;
        currentEntry = null;

        entry.uSize = entryUSize;
        if (entryUSize == 0) {
            countFlagEmpty(entry.flag);
            emptyFiles.add(entry);
            return;
        }

        WordBlock b = blocks.get(blocks.size()-1);

        entry.block = b;
        entry.offset = b.uSize;

        b.fileCount++;
        b.uSize += entryUSize;

        files.add(entry);

        flagSum[7]++;
        entry.crc32 = (int) crc32.getValue();
        entry.flag |= QZEntry.CRC;

        crc32.reset();

        if (solidSize != 0 && b.uSize >= solidSize) nextWordBlock();

        entryUSize = 0;
    }

    private void countFlagEmpty(int flag) {
        flagSum[0]++;

        if ((flag & QZEntry.DIRECTORY) == 0) flagSum[1]++;
        if ((flag & QZEntry.ANTI     ) != 0) flagSum[2]++;
    }
    private void countFlag(int flag) {
        if ((flag & QZEntry.CT       ) != 0) flagSum[3]++;
        if ((flag & QZEntry.AT       ) != 0) flagSum[4]++;
        if ((flag & QZEntry.MT       ) != 0) flagSum[5]++;
        if ((flag & QZEntry.ATTR     ) != 0) flagSum[6]++;
        if ((flag & QZEntry.CRC      ) != 0) flagSum[7]++;
    }
    private void countBlockFlag(WordBlock b) {
        if ((b.hasCrc & 1) != 0) flagSum[8]++;
        flagSum[9] += b.extraSizes.length;
    }

    /**
     * 修改了QZEntry的属性之后,通知已修改
     */
    public final void countFlags() {
        Arrays.fill(flagSum, 0);

        for (WordBlock b : blocks) countBlockFlag(b);
        for (int i = 0; i < files.size(); i++) countFlag(files.get(i).flag);
        for (int i = 0; i < emptyFiles.size(); i++) {
            byte flag = emptyFiles.get(i).flag;
            countFlag(flag);
            countFlagEmpty(flag);
        }
    }

    public final void nextWordBlock() throws IOException {
        if (out == null) return;

        WordBlock b = blocks.get(blocks.size()-1);

        flagSum[8]++;
        b.hasCrc |= 1;
        b.crc = (int) blockCrc32.getValue();
        blockCrc32.reset();

        out.close();
        out = null;
    }

    final OutputStream out() throws IOException {
        if (out != null) return out;

        WordBlock wb = new WordBlock();
        blocks.add(wb);

        if (complexCoder != null) {
            wb.tmp = coders;
            wb.complexCoder = complexCoder;
            wb.extraSizes = new long[cOffsets];
            flagSum[9] += cOffsets;
            wb.outSizes = new long[cOutSizes];
            return this.out = complexCoder.getOutputStream(wb, s);
        }

        wb.coder = (QZCoder[]) coders;

        if (coders.length > 1)
            wb.outSizes = new long[coders.length-1];

        OutputStream out = wb.new CRC(s);
        for (int i = 0; i < wb.coder.length; i++) {
            QZCoder m = wb.coder[i];
            if (i!=0) out = wb.new Counter(out, wb.coder.length-i-1);

            out = m.encode(out);
        }

        return this.out = out;
    }

    @Override
    public void finish() throws IOException {
        if (finished) return;

        closeEntry();
        nextWordBlock();

        finished = true;
    }

    @Override
    public void close() throws IOException {
        try {
            finish();
        } finally {
            s.close();
        }
    }
}
