package roj.archive.qz;

import roj.archive.ArchiveEntry;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.util.Helpers;

import static roj.archive.zip.ZEntry.java2WinTime;
import static roj.archive.zip.ZEntry.winTime2JavaTime;

/**
 * @author Roj234
 * @since 2023/3/14 0014 7:56
 */
public class QZEntry implements ArchiveEntry, Cloneable {
	String name;

    byte flag;

    static final int
        EMPTY = 1, DIRECTORY = 2, ANTI = 4,
        CT = 8, AT = 16, MT = 32, ATTR = 64,
        CRC = 128;

    long accessTime, createTime, modifyTime;
    int attributes;

    int crc32;
    long uSize;

    WordBlock block;
    long offset;

    QZEntry next;

    QZEntry() {}
    public QZEntry(String name) {
        this.name = name;
    }
    public QZEntry(String name, long expectSize) {
        this.name = name;
        this.uSize = expectSize;
    }

    void setSize(long size) { uSize = size; }
    void setCrc(int crc) {
        crc32 = crc;
        flag |= CRC;
    }

    public final String getName() { return name; }
    public final void setName(String s) { name = s; }

    public final long getSize() { return uSize; }
    public final long getCompressedSize() { return block == null ? 0 : block.fileCount == 1 ? block.size() : -1; }

    public final long getAccessTime() { return winTime2JavaTime(accessTime); }
    public final long getCreationTime() { return winTime2JavaTime(createTime); }
    public final long getModificationTime() {  return winTime2JavaTime(modifyTime); }
    public final int getAttributes() { return attributes; }
    public final boolean hasAccessTime() { return (flag&AT) != 0; }
    public final boolean hasCreationTime() { return (flag&CT) != 0; }
    public final boolean hasModificationTime() { return (flag&MT) != 0; }
    public final boolean hasAttributes() { return (flag&ATTR) != 0; }
    public final boolean isDirectory() { return (flag&DIRECTORY) != 0; }
    public final boolean isAntiItem() { return (flag & ANTI) != 0; }

    public final void setAccessTime(long t) {
        accessTime = java2WinTime(t);
        flag |= AT;
    }
    public final void setCreationTime(long t) {
        createTime = java2WinTime(t);
        flag |= CT;
    }
    public final void setModificationTime(long t) {
        modifyTime = java2WinTime(t);
        flag |= MT;
    }
    // int DIRECTORY = 16, READONLY = 1, HIDDEN = 2, ARCHIVE = 32;
    public final void setAttributes(int attr) {
        attributes = attr;
        flag |= ATTR;
    }
    public final void setIsDirectory(boolean directory) {
        if (directory) flag |= DIRECTORY;
        else flag &= ~DIRECTORY;
    }
    public final void setAntiItem(boolean anti) {
        if (anti) flag |= ANTI;
        else flag &= ~ANTI;
    }

    public WordBlock getBlock() { return block; }
    public long getOffset() { return offset; }
    public boolean hasCrc32() { return (flag&CRC) != 0; }
    public int getCrc32() { return crc32; }

    @Override
    public String toString() {
        CharList sb = new CharList("{\n  ");

        if ((flag & ANTI) != 0) sb.append("删除");

        if ((flag & DIRECTORY) != 0) sb.append("文件夹");
        else if (uSize == 0 && (flag & ANTI) == 0) sb.append("空文件");
        else sb.append("文件");
        sb.append(": '").append(name).append('\'');

        if ((flag & AT) != 0) sb.append("\n  访问: ").append(ACalendar.toLocalTimeString(getAccessTime()));
        if ((flag & CT) != 0) sb.append("\n  创建: ").append(ACalendar.toLocalTimeString(getCreationTime()));
        if ((flag & MT) != 0) sb.append("\n  修改: ").append(ACalendar.toLocalTimeString(getModificationTime()));
        if ((flag & ATTR) != 0) appendWindowsAttribute(sb.append("\n  属性: "));
        if ((flag & CRC) != 0) sb.append("\n  校验: ").append(Integer.toHexString(crc32));

        if (uSize > 0) {
            sb.append("\n  大小: ").append(uSize).append('B')
              .append("\n  位置:").append(block);
            if (offset != 0) sb.append('+').append(offset);
        }
        return sb.append('}').toString();
    }

    private void appendWindowsAttribute(CharList sb) {
        int len = sb.length();
        if ((attributes&   1) != 0) sb.append("只读|");
        if ((attributes&   2) != 0) sb.append("隐藏|");
        if ((attributes&   4) != 0) sb.append("系统|");
        if ((attributes&  32) != 0) sb.append("存档|");
        if ((attributes&1024) != 0) sb.append("链接|");
        if (sb.length() > len) sb.setLength(sb.length()-1);
    }

    @Override
    public QZEntry clone() {
        try {
            QZEntry clone = (QZEntry) super.clone();
            clone.block = null;
            clone.offset = 0;
            clone.next = null;
            return clone;
        } catch (Exception E) {
            return Helpers.nonnull();
        }
    }
}
