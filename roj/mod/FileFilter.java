package roj.mod;

import roj.asm.Parser;
import roj.collect.MyHashSet;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.Type;
import roj.io.IOUtil;
import roj.io.PushbackInputStream;
import roj.io.StreamingChars;
import roj.ui.CmdUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static roj.mod.Shared.MAIN_CONFIG;

/**
 * Class Timer
 *
 * @author Roj233
 * @since 2021/7/11 14:23
 */
class FileFilter implements Predicate<File> {
    public static final int F_CLASS = 0, F_SRC = 1, F_TIME = 2, F_CLASS_TIME = 3, F_JAVA_ANNO = 4, F_JAVA_TIME = 5, F_ALL = 6, F_CLASS_TIME_REMOVE = 7;

    public static final FileFilter INST = new FileFilter();

    FileFilter() {}

    static final byte[]            buffer     = new byte[MAIN_CONFIG.getInteger("AT查找缓冲区大小")];
    static final List<CmtATEntry>  cmtEntries = new ArrayList<>();
    static final MyHashSet<String> modified   = new MyHashSet<>();
    static int state;

    long stamp;
    int mode;

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
            case F_JAVA_ANNO: {
                boolean is = file.getName().endsWith(".java");
                if (is) {
                    checkATComments(file);
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
        cmtEntries.clear();
        return this;
    }


    // region OpenAny Finder

    public static boolean checkATComments(File file) {
        if(buffer.length == 0)
            return false;
        try(FileInputStream in = new FileInputStream(file)) {
            int len = in.read(buffer, 0, Math.min((int) file.length(), buffer.length));
            for (int i = 0; i < len; i++) {
                if(buffer[i] == '/' && regionMatches(buffer, i, COMMENT_BEGIN)) {
                    PushbackInputStream in1 = new PushbackInputStream(in);
                    in1.setBuffer(buffer, i + COMMENT_BEGIN.length(), len);
                    StreamingChars reader = new StreamingChars(128) {
                        @Override
                        public void ensureRead(int required) {
                            int i = bufOff;
                            if(i < 0) return;
                            super.ensureRead(required);
                            for (; i < cl.length(); i++) {
                                if(cl.charAt(i) == '\n') {
                                    bufOff = -1;
                                    cl.setIndex(i + 1);
                                    break;
                                }
                            }
                        }
                    }.reset(in1);
                    CList list = JSONParser.parse(reader, JSONParser.NO_DUPLICATE_KEY | JSONParser.NO_EOF | JSONParser.UNESCAPED_SINGLE_YH).asList();
                    for (int j = 0; j < list.size(); j++) {
                        CEntry e1 = list.get(j);
                        if (e1.getType() != Type.LIST)
                            j = list.size();
                        CList list1 = e1.getType() == Type.LIST ? e1.asList() : list;
                        CmtATEntry entry = new CmtATEntry();
                        entry.clazz = list1.get(0).asString();
                        entry.value = list1.get(1).asList().asStringList();
                        entry.compile = list1.size() > 2 && list1.get(2).asBool();
                        cmtEntries.add(entry);
                    }
                    return true;
                }
            }
        } catch (IOException | ParseException e) {
            System.out.println("文件: " + file.getPath().substring(Compiler.BASE_PATH.length()));
            if(e instanceof ParseException) {
                System.out.println(e);
            } else {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static final String COMMENT_BEGIN = "//!!AT ";

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

    static final class CmtATEntry {
        String clazz;
        List<String> value;
        boolean compile;
    }

    // endregion
}
