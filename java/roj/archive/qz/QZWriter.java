package roj.archive.qz;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveWriter;
import roj.collect.ArrayList;
import roj.config.data.CInt;
import roj.crypt.CRC32;
import roj.io.source.Source;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/3/14 22:08
 */
public abstract class QZWriter extends OutputStream implements ArchiveWriter {
    Source source;
    public final Source source() {return source;}

    final ArrayList<WordBlock> blocks = new ArrayList<>();
    /**
     * 文件条目标志统计数组，索引对应含义：
     * <ul>
     *   <li>[0]: 空项目数</li>
     *   <li>[1]: 空文件数（非目录）</li>
     *   <li>[2]: 反(在解压时应删除的)文件数</li>
     *   <li>[3]: 包含创建时间的条目数</li>
     *   <li>[4]: 包含访问时间的条目数</li>
     *   <li>[5]: 包含修改时间的条目数</li>
     *   <li>[6]: 包含Windows属性的条目数</li>
     *   <li>[7]: 包含CRC校验的条目数</li>
     *   <li>[8]: 包含块级CRC校验的块数</li>
     *   <li>[9]: 复杂编码器导致原始流增加的数量</li>
     * </ul>
     */
    final int[] flagSum = new int[10];

    final ArrayList<QZEntry> files = new ArrayList<>();
    final ArrayList<QZEntry> emptyFiles = new ArrayList<>();

    private QZEntry currentEntry;
    private long entryUSize;
    OutputStream out;

    int crc32 = CRC32.initial, blockCrc32 = CRC32.initial;

    boolean finished;

    public byte flag;
    public static final int IGNORE_CLOSE = 1, NO_TRIM_FILE = 2;

    /**
     * 设置是否忽略对{@link #close()}的调用
     */
    public final void setIgnoreClose(boolean ignoreClose) { if (ignoreClose) flag |= IGNORE_CLOSE; else flag &= ~IGNORE_CLOSE; }

    private long solidSize;

    QZCoder[] coders;
    public static final QZCoder[] NO_COMPRESS = {Copy.INSTANCE};

    private CoderInfo complexCoder;
    private int cOffsets, cOutSizes;

    QZWriter(Source source) {this.source = source;}
    QZWriter(Source source, QZWriter parent) {
        this.source = source;
        this.solidSize = parent.solidSize;
        this.coders = parent.coders;
    }

    /**
     * 设置压缩编码器链
     *
     * <p>复杂编码器执行顺序为后序（栈式）排列，例如：
     * <pre>
     * [BCJ2, LZMA2, null, LZMA, null, LZMA, null, null]
     * 表示：BCJ2 → [LZMA2, LZMA, LZMA] → 文件 的编码流程
     * </pre>
     *
     * @param methods 压缩方法链（仅在包含复杂编码器时，使用null表示原始流）
     * @throws IllegalStateException 当编码器链配置失败时抛出
     * @throws IllegalArgumentException 当使用复杂编码器且根节点有多个输入时抛出
     */
    public final void setCodec(QZCoder... methods) {
        try {
            flush();
        } catch (IOException e) {
            throw new IllegalStateException("nextWordBlock() failed", e);
        }

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
        // 翻转数组
        for (int i = 0; i < methods.length; i++) {
            coders[methods.length-i-1] = methods[i];
        }
    }
    private void setComplexCodec(QZCoder[] methods) {
        CoderInfo[] nodes = new CoderInfo[methods.length];
        ArrayList<CoderInfo> nonNull = new ArrayList<>();

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
        if (root.provides > 1) throw new IllegalArgumentException("根编码器只能有一个输入!");

        CInt blockId = new CInt();
        inflateTree(root, nodes, blockId, 1);

        coders = nonNull.toArray(new CoderInfo[nonNull.size()]);
        complexCoder = root;
		cOffsets = blockId.value-1;
        cOutSizes = provides;
    }
    private int inflateTree(CoderInfo node, CoderInfo[] nodes, CInt blockId, int i) {
        for (int j = 0; j < node.uses.length; j++) {
            CoderInfo next = nodes[i++];
            if (next != null) {
                i = inflateTree(next, nodes, blockId, i);
                for (int k = 0; k < next.provides; k++) {
                    node.pipe(j, next, k);
                }
            } else {
                node.setFileInput(blockId.value++, j);
            }
        }
        return i;
    }

    /**
     * 设置固实压缩策略
     *
     * <p>参数说明：
     * <ul>
     *   <li>0: 所有文件合并为单个固实块</li>
     *   <li>-1: 非固实模式（每个文件独立块）</li>
     *   <li>>0: 当累计输入大小达到指定值时创建新块</li>
     * </ul>
     *
     * @param size 固实大小（单位：字节）
     */
    public final void setSolidSize(long size) {solidSize = size;}
    public final long getSolidSize() {return solidSize;}

    // do not modify those list!
    public final ArrayList<QZEntry> getFiles() { return files; }
    public final ArrayList<QZEntry> getEmptyFiles() { return emptyFiles; }

    /**
     * 复制已有归档条目到当前写入流
     *
     * @param owner 源归档文件对象
     * @param entry 要复制的条目
     * @throws IOException 当源块包含多个文件 (防止误操作, 如果确需复制多个文件, 使用{@link #copy(QZArchive, WordBlock) 显式重载})
     */
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

        flush();

        source.put(archive.r, b.offset, b.size());

        blocks.add(b);
        countBlockFlag(b);

        QZEntry entry = b.firstEntry;
        while (entry != null) {
            files.add(entry);
            countFlag(entry.flag, 1);
            entry = entry.next;
        }
    }

    public final void beginEntry(ArchiveEntry entry) throws IOException {
        QZEntry ent = ((QZEntry) entry);
        if (finished) throw new IOException("Stream closed");

        closeEntry();
        countFlag(ent.flag & ~QZEntry.CRC, 1);
        currentEntry = ent;
    }
    public final void write(int b) throws IOException {
        if (currentEntry == null) throw new IOException("No active entry");

        out().write(b);
        crc32 = CRC32.update(crc32, b);
        blockCrc32 = CRC32.update(blockCrc32, b);
        entryUSize++;
    }
    public final void write(byte[] b, int off, int len) throws IOException {
        if (currentEntry == null) throw new IOException("No active entry");

        if (len <= 0) return;
        out().write(b, off, len);
        crc32 = CRC32.update(crc32, b, off, len);
        blockCrc32 = CRC32.update(blockCrc32, b, off, len);
        entryUSize += len;
    }
    public final void closeEntry() throws IOException {
        if (currentEntry == null) return;

        QZEntry entry = currentEntry;
        currentEntry = null;

        entry.uSize = entryUSize;
        if (entryUSize == 0) {
            countFlagEmpty(entry.flag, 1);
            emptyFiles.add(entry);
            return;
        }

        WordBlock b = blocks.getLast();

        entry.block = b;
        entry.offset = b.uSize;

        b.fileCount++;
        b.uSize += entryUSize;

        files.add(entry);

        flagSum[7]++;
        entry.crc32 = CRC32.finish(crc32);
        entry.flag |= QZEntry.CRC;
        crc32 = CRC32.initial;

        if (solidSize != 0 && b.uSize >= solidSize) finishWordBlock();

        entryUSize = 0;
    }

    final void countFlagEmpty(int flag, int v) {
        flagSum[0] += v;

        if ((flag & QZEntry.DIRECTORY) == 0) flagSum[1] += v;
        if ((flag & QZEntry.ANTI     ) != 0) flagSum[2] += v;
    }
    final void countFlag(int flag, int v) {
        if ((flag & QZEntry.CT  ) != 0) flagSum[3] += v;
        if ((flag & QZEntry.AT  ) != 0) flagSum[4] += v;
        if ((flag & QZEntry.MT  ) != 0) flagSum[5] += v;
        if ((flag & QZEntry.ATTR) != 0) flagSum[6] += v;
        if ((flag & QZEntry.CRC ) != 0) flagSum[7] += v;
    }
    private void countBlockFlag(WordBlock b) {
        if ((b.hasCrc & 1) != 0) flagSum[8]++;
        flagSum[9] += b.extraSizes.length;
    }

    /**
     * 重新计数所有标志<p>
     * 外部代码修改QZEntry的属性后调用
     */
    public final void recountFlags() {
        Arrays.fill(flagSum, 0);

        for (WordBlock b : blocks) countBlockFlag(b);
        for (int i = 0; i < files.size(); i++) countFlag(files.get(i).flag, 1);
        for (int i = 0; i < emptyFiles.size(); i++) {
            byte flag = emptyFiles.get(i).flag;
            countFlag(flag, 1);
            countFlagEmpty(flag, 1);
        }
    }

    /**
     * 结束当前Entry和字块
     */
    public final void flush() throws IOException {
        closeEntry();
        finishWordBlock();
    }
    void finishWordBlock() throws IOException {
        if (out == null) return;

        WordBlock b = blocks.getLast();

        flagSum[8]++;
        b.hasCrc |= 1;
        b.crc = CRC32.finish(blockCrc32);
        blockCrc32 = CRC32.initial;

        out.close();
        out = null;
    }

    final OutputStream out() throws IOException {
        if (out != null) return out;

        var wb = new WordBlock();
        blocks.add(wb);

        wb.coder = coders;
        if (complexCoder != null) {
            flagSum[9] += cOffsets;
            wb.complexCoder = complexCoder;
            wb.extraSizes = new long[cOffsets];
            wb.outSizes = new long[cOutSizes];
            return this.out = complexCoder.getOutputStream(wb, source);
        }

        if (coders.length > 1)
            wb.outSizes = new long[coders.length-1];

        OutputStream out = source;
        for (int i = 0; i < wb.coder.length; i++) {
            out = wb.new Counter(out, i-1);
            out = wb.coder[i].encode(out);
        }
        return this.out = out;
    }

    @Override
    public void finish() throws IOException {
        if (finished) return;
        flush();
        finished = true;
    }

    @Override
    public final void close() throws IOException {
        if ((flag&IGNORE_CLOSE) != 0) return;

        try {
            finish();
        } finally {
            source.close();
        }
    }
}