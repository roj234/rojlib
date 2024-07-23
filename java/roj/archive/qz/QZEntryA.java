package roj.archive.qz;

import roj.reflect.ReflectionUtils;

import java.nio.file.attribute.FileTime;

import static roj.archive.zip.ZEntry.*;

/**
 * @author Roj234
 * @since 2023/3/14 0014 7:56
 */
final class QZEntryA extends QZEntry {
    static final long[] SPARSE_ATTRIBUTE_OFFSET = new long[] {
        ReflectionUtils.fieldOffset(QZEntryA.class, "createTime"),
        ReflectionUtils.fieldOffset(QZEntryA.class, "accessTime"),
        ReflectionUtils.fieldOffset(QZEntryA.class, "modifyTime"),
        ReflectionUtils.fieldOffset(QZEntryA.class, "attributes")
    };

    long accessTime, createTime, modifyTime;
    int attributes;

    QZEntryA() {}
    QZEntryA(String name) {super(name);}

    public final long getAccessTime() {return winTime2JavaTime(accessTime);}
    public final long getCreationTime() {return winTime2JavaTime(createTime);}
    public final long getModificationTime() {return winTime2JavaTime(modifyTime);}
    public final int getAttributes() {return attributes;}

    public FileTime getPrecisionAccessTime() {return !hasAccessTime() ? null : winTime2FileTime(accessTime);}
    public FileTime getPrecisionCreationTime() {return !hasCreationTime() ? null : winTime2FileTime(createTime);}
    public FileTime getPrecisionModificationTime() {return !hasModificationTime() ? null : winTime2FileTime(modifyTime);}

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