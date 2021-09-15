package roj.mod;

import roj.asm.Parser;
import roj.asm.annotation.OpenAny;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.ui.CmdUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Predicate;

import static roj.mod.Shared.MAIN_CONFIG;

/**
 * Class Timer
 *
 * @author Roj233
 * @since 2021/7/11 14:23
 */
class FileFilter implements Predicate<File> {
    public static final int F_CLASS = 0, F_SRC = 1, F_TIME = 2, F_CLASS_TIME = 3, F_JAVA_OA = 4, F_JAVA_TIME = 5, F_ALL = 6, F_CLASS_TIME_REMOVE = 7;

    public static final FileFilter INST = new FileFilter();

    FileFilter() {}

    static final byte[] buffer = new byte[MAIN_CONFIG.getInteger("AT查找缓冲区大小")];

    long stamp;
    int mode;
    static MyHashSet<String> modified = new MyHashSet<>();
    static int state;

    @Override
    public boolean test(File file) {
        switch (mode) {
            case F_CLASS:
                return file.getName().endsWith(".class");
            case F_SRC:
                return file.getName().endsWith(".java");
            case F_TIME:
                return file.lastModified() > stamp;
            case F_CLASS_TIME: {
                if (file.getName().endsWith(".class") && file.lastModified() > stamp) {
                    try {
                        modified.add(Parser.simpleData(IOUtil.read(file)).get(0));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return true;
                }
                return false;
            }
            case F_CLASS_TIME_REMOVE: {
                boolean is = file.getName().endsWith(".class");
                if (is && file.lastModified() <= stamp) {
                    if (!file.delete()) {
                        CmdUtil.warning("无法删除过时的文件 " + file.getPath());
                    }
                    modified.add(file.getAbsolutePath());
                    state++;
                    return false;
                }
                return is;
            }
            case F_JAVA_TIME: {
                boolean is = file.getName().endsWith(".java");
                if (is && file.lastModified() > stamp) {
                    try {
                        modified.add(Parser.simpleData(IOUtil.read(file)).get(0));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return is;
            }
            case F_JAVA_OA: {
                boolean is = file.getName().endsWith(".java");
                if (is && state != 1) {
                    if(isOAMarked(file)) {
                        state = 1;
                    }
                }
                return is;
            }
            case F_ALL:
                return true;
        }

        return false;
    }

    public FileFilter reset(long stamp, int enable) {
        this.stamp = stamp;
        this.mode = enable;
        state = 0;
        modified.clear();
        return this;
    }


    // region OpenAny Finder

    public static boolean isOAMarked(File file) {
        if(buffer.length == 0)
            return false;
        try(FileInputStream fis = new FileInputStream(file)) {
            int len = fis.read(buffer, 0, Math.min((int) file.length() >> 1, buffer.length));
            for (int i = 0; i < len; i++) {
                if(buffer[i] == '@') {
                    i++;
                    if(regionMatches(buffer, i, OA_CLASS_NAME) || regionMatches(buffer, i, "OpenAny")) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    static String OA_CLASS_NAME = OpenAny.class.getName().replace('/', '.');

    private static boolean regionMatches(byte[] list, int index, CharSequence seq) {
        if (index + seq.length() > list.length)
            return false;

        int j = 0;
        for (int i = index; j < seq.length(); i++, j++) {
            if (list[i] != seq.charAt(j))
                return false;
        }

        return true;
    }

    // endregion
}
