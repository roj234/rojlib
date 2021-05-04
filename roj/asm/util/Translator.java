package roj.asm.util;

import roj.asm.Parser;
import roj.asm.constant.Constant;
import roj.asm.constant.CstString;
import roj.asm.constant.CstType;
import roj.asm.struct.ConstantData;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.config.word.AbstLexer;
import roj.config.word.Lexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/18 14:46
 */
public class Translator {
    static boolean apply;
    static Map<String, IntMap<String>> value = new MyHashMap<>();

    public static void main(String[] args) throws IOException, ParseException {
        if (args.length < 2) {
            System.out.println("Translator [-apply] <file>... <dictionary>");
            return;
        }

        for (int i = 0; i < args.length - 1; i++) {
            String s = args[i];
            if (s.equals("-apply")) {
                apply = true;
                apply(args);
                return;
            }
            parse(new File(s));
        }

        String out = args[args.length - 1];

        StringBuilder sb = new StringBuilder(100000);

        for (Map.Entry<String, IntMap<String>> entry : value.entrySet()) {
            sb.append(entry.getKey()).append(':').append('\n');
            for (IntMap.Entry<String> entry1 : entry.getValue().entrySet()) {
                sb.append(' ').append(' ').append(entry1.getKey()).append('=').append('"').append(AbstLexer.addSlashes(entry1.getValue())).append('"').append('\n');
            }
        }

        try (FileOutputStream fos = new FileOutputStream(new File(out))) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }

    }

    private static void apply(String[] args) throws IOException, ParseException {
        String in = args[args.length - 1];
        Lexer wr = (Lexer) new Lexer() {
            @Override
            public Word readWord() throws ParseException {
                int index = this.index;
                final CharSequence input = this.input;

                while (index < input.length()) {
                    int c = input.charAt(index++);
                    switch (c) {
                        case '\'':
                        case '"':
                            this.index = index;
                            return readConstString((char) c);
                        case '/':
                            this.index = index;
                            Word word = ignoreStdNote();
                            if (word != null)
                                return word;
                            break;
                        case '=':
                        case ':':
                            this.index = index - 1;
                            return readSpecial();
                        default: {
                            if (!WHITESPACE.contains(c)) {
                                this.index = index - 1;
                                if (NUMBER.contains(c)) {
                                    return readDigit();
                                } else {
                                    return readAlphabet();
                                }
                            }
                        }
                    }
                }
                this.index = index;
                return eof();
            }


            /**
             * @return 标识符 or 变量
             */
            protected Word readAlphabet() throws ParseException {
                int index = this.index;
                final CharSequence input = this.input;

                CharList temp = this.found;
                temp.clear();

                while (index < input.length()) {
                    int c = input.charAt(index++);
                    if (c != ':' && c != '=') {
                        temp.append((char) c);
                    } else {
                        index--;
                        break;
                    }
                }
                this.index = index;
                if (temp.length() == 0) {
                    return eof();
                }

                return formAlphabetClip(temp);
            }
        }.init(IOUtil.readFile(new File(in)));

        while (wr.hasNext()) {
            Word w = wr.readWord();
            String name = w.val();
            if (wr.next() != ':') {
                throw wr.err("Delim is not :");
            }

            IntMap<String> im = new IntMap<>();
            while (wr.hasNext()) {
                Word w1 = wr.readWord();
                if (w1.type() != WordPresets.INTEGER) {
                    throw wr.err("Is not number");
                }
                Word w2 = wr.readWord();
                if (!w2.val().equals("=")) {
                    throw wr.err("Is not =");
                }
                w2 = wr.readWord();
                if (w2.type() != WordPresets.STRING) {
                    throw wr.err("Is not Slash_String");
                }
                im.put(MathUtils.parseInt(w1.val(), 10), w2.val());
                w2 = wr.nextWord();
                wr.retractWord();
                if (w2.type() != WordPresets.INTEGER) {
                    break;
                }
            }

            value.put(name, im);
        }

        for (int i = 1; i < args.length - 1; i++) {
            String s = args[i];
            if (s.equals("-apply")) {
                continue;
            }

            apply0(new File(s));
        }
    }

    public static void apply0(File f) throws IOException {
        if (f.isDirectory()) {
            for (File file : FileUtil.findAllFiles(f)) {
                apply0(file);
            }
        } else if (f.isFile()) {
            if (f.getName().endsWith(".class")) {
                byte[] bytes = applyClass("/", IOUtil.readFile(f));
                if (bytes != null) {
                    System.out.println("已修改的内容: " + f.getName());
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        fos.write(bytes);
                    }
                }
            } else {
                applyZipFile(f);
            }
        }
    }

    public static void applyZipFile(File f) throws IOException {
        ByteList bl = new ByteList((int) f.length());
        ZipOutputStream zos = new ZipOutputStream(bl.asOutputStream());
        zos.setComment("Powered by Roj-ASM class file translator");

        ZipFile zf = new ZipFile(f);
        String fn = f.getName() + '/';
        Enumeration<? extends ZipEntry> e = zf.entries();

        while (e.hasMoreElements()) {
            ZipEntry ze = e.nextElement();
            if (!ze.isDirectory() && ze.getName().endsWith(".class")) {

                final byte[] bc = IOUtil.readFully(zf.getInputStream(ze));
                byte[] bytes = applyClass(fn, bc);
                if (bytes != null)
                    System.out.println("已修改的内容: " + fn + ze.getName());

                zos.putNextEntry(new ZipEntry(ze.getName()));
                zos.write(bytes == null ? bc : bytes);
                zos.closeEntry();
            }
        }

        zf.close();
        ZipUtil.close(zos);

        try (FileOutputStream fos = new FileOutputStream(f)) {
            bl.writeToStream(fos);
        }
    }

    public static byte[] applyClass(String path, byte[] bc) {
        ConstantData data = Parser.parseConstants(bc);

        IntMap<String> map = value.get(path + data.name);
        if (map == null)
            return null;

        Constant[] constants = data.constants.array();

        for (IntMap.Entry<String> entry : map.entrySet()) {
            final CstString string = (CstString) constants[entry.getKey()];
            //System.out.println(string.getValue().getString() + " to " + entry.getValue());
            string.setValue(data.writer.getUtf(entry.getValue()));
        }

        return Parser.toByteArray(data);
    }

    public static void parse(File f) throws IOException {
        if (f.isDirectory()) {
            for (File file : FileUtil.findAllFiles(f)) {
                parse(file);
            }
        } else if (f.isFile()) {
            if (f.getName().endsWith(".class")) {
                addClass("/", IOUtil.readFile(f));
            } else {
                readZipFile(f);
            }
        }
    }

    public static void readZipFile(File f) throws IOException {
        ZipFile zf = new ZipFile(f);
        String fn = f.getName() + '/';
        Enumeration<? extends ZipEntry> e = zf.entries();
        while (e.hasMoreElements()) {
            ZipEntry ze = e.nextElement();
            if (!ze.isDirectory() && ze.getName().endsWith(".class")) {
                addClass(fn, IOUtil.readFully(zf.getInputStream(ze)));
            }
        }
    }

    public static void addClass(String path, byte[] bc) {
        ConstantData data = Parser.parseConstants(bc);

        IntMap<String> map = new IntMap<>();

        for (Constant s : data.constants.array()) {
            if (s != null && s.type == CstType.STRING) {
                map.put(s.getIndex(), ((CstString) s).getValue().getString());
            }
        }

        if (!map.isEmpty())
            value.put(path + data.name, map);
    }
}
