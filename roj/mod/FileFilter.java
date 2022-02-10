package roj.mod;

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.Type;
import roj.io.PushbackInputStream;
import roj.io.StreamingChars;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static roj.mod.Shared.BASE;
import static roj.mod.Shared.MAIN_CONFIG;

/**
 * Class Timer
 *
 * @author Roj233
 * @since 2021/7/11 14:23
 */
class FileFilter implements Predicate<File> {
    public static final int F_RES_TIME = 0, F_SRC_ANNO = 1, F_SRC_TIME = 2, F_RES = 3;

    public static final FileFilter INST = new FileFilter();

    FileFilter() {}

    static final byte[]            buffer     = new byte[MAIN_CONFIG.getInteger("AT查找缓冲区大小")];
    static final List<CmtATEntry>  cmtEntries = new ArrayList<>();

    private long stamp;
    private int mode;

    @Override
    public boolean test(File file) {
        switch (mode) {
            case F_RES_TIME:
                return file.lastModified() > stamp;
            case F_SRC_TIME:
                return file.getName().endsWith(".java") && file.lastModified() > stamp;
            case F_SRC_ANNO: {
                boolean is = file.getName().endsWith(".java");
                if (is) checkATComments(file);
                return is;
            }
            case F_RES:
                return true;
        }

        return false;
    }

    public FileFilter reset(long stamp, int enable) {
        this.stamp = stamp;
        this.mode = enable;
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
                            int i = flag;
                            if((i & 1) != 0) return;
                            super.ensureRead(required);
                            for (; i < buf.length(); i++) {
                                if(buf.charAt(i) == '\n') {
                                    flag |= 1;
                                    buf.wIndex(i + 1);
                                    break;
                                }
                            }
                        }
                    }.reset(in1);
                    CList list = JSONParser.parse(reader, JSONParser.NO_DUPLICATE_KEY | JSONParser.NO_EOF | JSONParser.UNESCAPED_SINGLE_QUOTE).asList();
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
            System.out.println("文件: " + file.getPath().substring(BASE.getAbsolutePath().length()));
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
