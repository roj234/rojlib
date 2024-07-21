package roj.archive.qz;

import roj.archive.ArchiveEntry;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.util.Helpers;

import java.nio.file.attribute.FileTime;

/**
 * @author Roj234
 * @since 2023/3/14 0014 7:56
 */
public sealed class QZEntry implements ArchiveEntry, Cloneable permits QZEntryA {
	String name;

    byte flag;
    static final int
        CT = 1, AT = 2, MT = 4, ATTR = 8,
        DIRECTORY = 16, CRC = 32, ANTI = 64;

    int crc32;
    long uSize;

    WordBlock block;
    long offset;

    QZEntry next;

    QZEntry() {}
    QZEntry(String name) {this.name = name;}

    public static QZEntry of(String name) {return new QZEntryA(name);}
    public static QZEntry ofNoAttribute(String name) {return new QZEntry(name);}

    public void _setSize(long size) {uSize = size;}
    public void _setCrc(int crc) {crc32 = crc;flag |= CRC;}

    public final String getName() {return name;}
    public final void setName(String s) {name = s;}

    public final long getSize() {return uSize;}
    public final long getCompressedSize() {return block == null ? 0 : block.fileCount == 1 ? block.size() : -1;}

    public final boolean hasCrc32() {return (flag&CRC) != 0;}
    public final int getCrc32() {return crc32;}
    public final WordBlock block() {return block;}
    public final long offset() {return offset;}
    public final boolean isEncrypted() {return block != null && block.hasProcessor(QzAES.class);}

    public long getAccessTime() {return 0;}
    public long getCreationTime() {return 0;}
    public long getModificationTime() {return 0;}
    public int getAttributes() {return 0;}

    public FileTime getPrecisionAccessTime() {return null;}
    public FileTime getPrecisionCreationTime() {return null;}
    public FileTime getPrecisionModificationTime() {return null;}

    public final boolean hasAccessTime() {return (flag&AT) != 0;}
    public final boolean hasCreationTime() {return (flag&CT) != 0;}
    public final boolean hasModificationTime() {return (flag&MT) != 0;}
    public final boolean hasAttributes() {return (flag&ATTR) != 0;}
    public final boolean isDirectory() {return (flag&DIRECTORY) != 0;}
    public final boolean isAntiItem() {return (flag & ANTI) != 0;}

    public void setAccessTime(long t) {throw getException();}
    public void setCreationTime(long t) {throw getException();}
    public void setModificationTime(long t) {throw getException();}
    public void setPrecisionAccessTime(FileTime t) {throw getException();}
    public void setPrecisionCreationTime(FileTime t) {throw getException();}
    public void setPrecisionModificationTime(FileTime t) {throw getException();}
    public void setAttributes(int attr) {throw getException();}
    private static UnsupportedOperationException getException() {return new UnsupportedOperationException("属性已被忽略");}
    public final void setIsDirectory(boolean directory) {
        if (directory) flag |= DIRECTORY;
        else flag &= ~DIRECTORY;
    }
    public final void setAntiItem(boolean anti) {
        if (anti) flag |= ANTI;
        else flag &= ~ANTI;
    }

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
        var attributes = getAttributes();
        if ((attributes&   1) != 0) sb.append("只读|");
        if ((attributes&   2) != 0) sb.append("隐藏|");
        if ((attributes&   4) != 0) sb.append("系统|");
        if ((attributes&  32) != 0) sb.append("存档|");
        if ((attributes&1024) != 0) sb.append("链接|");
        if (sb.length() > len) sb.setLength(sb.length()-1);
    }

    @Override
    public final int hashCode() {return name.hashCode();}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QZEntry entry = (QZEntry) o;

        if (offset != entry.offset) return false;
        if (!name.equals(entry.name)) return false;
        return block == entry.block;
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