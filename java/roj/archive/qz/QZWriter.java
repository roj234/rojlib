package roj.archive.qz;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveWriter;
import roj.collect.SimpleList;
import roj.config.data.CInt;
import roj.crypt.CRC32s;
import roj.io.source.Source;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

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

    int crc32 = CRC32s.INIT_CRC, blockCrc32 = CRC32s.INIT_CRC;

    boolean finished;

    public byte flag;
    public static final int IGNORE_CLOSE = 1, NO_TRIM_FILE = 2;
    public void setIgnoreClose(boolean ignoreClose) { if (ignoreClose) flag |= IGNORE_CLOSE; else flag &= ~IGNORE_CLOSE; }
    public int[] getFlagSum() {return flagSum;}

    private long solidSize;

    QZCoder[] coders;
    public static final QZCoder[] NO_COMPRESS = {Copy.INSTANCE};

    private CoderInfo complexCoder;
    private int cOffsets, cOutSizes;

    QZWriter(Source s) {this.s = s;}
    QZWriter(Source s, QZWriter parent) {
        this.s = s;
        this.solidSize = parent.solidSize;
        this.coders = parent.coders;
    }

    public ArchiveEntry createEntry(String fileName) { return QZEntry.of(fileName); }

    public final void setCodec(QZCoder... methods) {
        try {
            closeWordBlock();
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
     * 固实大小
     * 0: 固实 (合计一个字块)
     * -1: 非固实 (每文件一个字块)
     * 大于零: 按此(输入)大小
     */
    public void setSolidSize(long l) {solidSize = l;}
    public long getSolidSize() {return solidSize;}

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

        closeWordBlock();

        s.put(archive.r, b.offset, b.size());

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
        crc32 = CRC32s.update(crc32, b);
        blockCrc32 = CRC32s.update(blockCrc32, b);
        entryUSize++;
    }
    public final void write(byte[] b, int off, int len) throws IOException {
        if (currentEntry == null) throw new IOException("No active entry");

        if (len <= 0) return;
        out().write(b, off, len);
        crc32 = CRC32s.update(crc32, b, off, len);
        blockCrc32 = CRC32s.update(blockCrc32, b, off, len);
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
        entry.crc32 = CRC32s.retVal(crc32);
        entry.flag |= QZEntry.CRC;
        crc32 = CRC32s.INIT_CRC;

        if (solidSize != 0 && b.uSize >= solidSize) closeWordBlock0();

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
     * 修改了QZEntry的属性之后,通知已修改
     */
    public final void countFlags() {
        Arrays.fill(flagSum, 0);

        for (WordBlock b : blocks) countBlockFlag(b);
        for (int i = 0; i < files.size(); i++) countFlag(files.get(i).flag, 1);
        for (int i = 0; i < emptyFiles.size(); i++) {
            byte flag = emptyFiles.get(i).flag;
            countFlag(flag, 1);
            countFlagEmpty(flag, 1);
        }
    }

    public final void closeWordBlock() throws IOException {
        closeEntry();
        closeWordBlock0();
    }
    void closeWordBlock0() throws IOException {
        if (out == null) return;

        WordBlock b = blocks.getLast();

        flagSum[8]++;
        b.hasCrc |= 1;
        b.crc = CRC32s.retVal(blockCrc32);
        blockCrc32 = CRC32s.INIT_CRC;

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
            return this.out = complexCoder.getOutputStream(wb, s);
        }

        if (coders.length > 1)
            wb.outSizes = new long[coders.length-1];

        OutputStream out = s;
        for (int i = 0; i < wb.coder.length; i++) {
            out = wb.new Counter(out, i-1);
            out = wb.coder[i].encode(out);
        }
        return this.out = out;
    }

    @Override
    public void finish() throws IOException {
        if (finished) return;
        closeWordBlock();
        finished = true;
    }

    @Override
    public void close() throws IOException {
        if ((flag&IGNORE_CLOSE) != 0) return;

        try {
            finish();
        } finally {
            s.close();
        }
    }
}