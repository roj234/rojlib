package roj.archive.qz;

import roj.reflect.Unsafe;

import java.nio.file.attribute.FileTime;

import static roj.archive.ArchiveUtils.*;

/**
 * @author Roj234
 * @since 2023/3/14 7:56
 */
final class QZEntryA extends QZEntry {
    static final long[] SPARSE_ATTRIBUTE_OFFSET = new long[] {
        Unsafe.objectFieldOffset(QZEntryA.class, "createTime", long.class),
        Unsafe.objectFieldOffset(QZEntryA.class, "accessTime", long.class),
        Unsafe.objectFieldOffset(QZEntryA.class, "modifyTime", long.class),
        Unsafe.objectFieldOffset(QZEntryA.class, "attributes", int.class)
    };

    long accessTime, createTime, modifyTime;
    int attributes;

    QZEntryA() {}
    QZEntryA(String name) {super(name);}

    public final boolean hasAccessTime() {return (flag&AT) != 0;}
    public final boolean hasCreationTime() {return (flag&CT) != 0;}
    public final boolean hasModificationTime() {return (flag&MT) != 0;}

    public final long getAccessTime() {return winTime2JavaTime(accessTime);}
    public final long getCreationTime() {return winTime2JavaTime(createTime);}
    public final long getModificationTime() {return winTime2JavaTime(modifyTime);}
    public final int getWinAttributes() {return attributes;}

    public FileTime getPrecisionAccessTime() {return hasAccessTime() ? winTime2FileTime(accessTime) : null;}
    public FileTime getPrecisionCreationTime() {return hasCreationTime() ? winTime2FileTime(createTime) : null;}
    public FileTime getPrecisionModificationTime() {return hasModificationTime() ? winTime2FileTime(modifyTime) : null;}

    public final void setAccessTime(long t) {
        if (t == 0) flag &= ~AT;
        else {
            accessTime = java2WinTime(t);
            flag |= AT;
        }
    }
    public final void setCreationTime(long t) {
        if (t == 0) flag &= ~CT;
        else {
            createTime = java2WinTime(t);
            flag |= CT;
        }
    }
    public final void setModificationTime(long t) {
        if (t == 0) flag &= ~MT;
        else {
            modifyTime = java2WinTime(t);
            flag |= MT;
        }
    }
    public final void setPrecisionAccessTime(FileTime t) {
        if (t == null) flag &= ~AT;
        else {
            accessTime = fileTime2WinTime(t);
            flag |= AT;
        }
    }
    public final void setPrecisionCreationTime(FileTime t) {
        if (t == null) flag &= ~CT;
        else {
            createTime = fileTime2WinTime(t);
            flag |= CT;
        }
    }
    public final void setPrecisionModificationTime(FileTime t) {
        if (t == null) flag &= ~MT;
        else {
            modifyTime = fileTime2WinTime(t);
            flag |= MT;
        }
    }

    // int DIRECTORY = 16, READONLY = 1, HIDDEN = 2, ARCHIVE = 32;
    public final void setAttributes(int attr) {
        attributes = attr;
        flag |= ATTR;
    }

    @Override
    public QZEntryA clone() {return (QZEntryA) super.clone();}
}