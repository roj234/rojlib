package roj.misc;

import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.io.MutableZipFile;
import roj.text.SimpleLineReader;
import roj.util.ByteList;
import roj.util.ByteWriter;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Vector;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/8 12:35
 */
public class CpFilter {
    public static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(CpFilter::genList));
    }

    private static void genList() {
        try {
            File dst = new File("classList.txt");
            // 取并集
            HashSet<String> dt = new HashSet<>();
            if(dst.isFile()) {
                dt.addAll(Files.readAllLines(dst.toPath()));
            }
            Field f = ClassLoader.class.getDeclaredField("classes");
            f.setAccessible(true);
            Vector<Class<?>> vector = Helpers.cast(f.get(CpFilter.class.getClassLoader()));
            for (int i = 0; i < vector.size(); i++) {
                Class<?> classes = vector.get(i);
                dt.add(classes.getName().replace('.', '/'));
            }
            ByteList b = new ByteList();
            try(FileOutputStream out = new FileOutputStream(dst)) {
                for (String name : dt) {
                    ByteWriter.writeUTF(b, name, -1);
                    b.add((byte) '\n');
                    b.writeToStream(out);
                    b.clear();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Usage: CpFilter <jar> <classList>");
        MyHashSet<String> dt = new MyHashSet<>();
        try (SimpleLineReader s = new SimpleLineReader(IOUtil.readUTF(new File(args[1])))) {
            for (String ss : s) {
                dt.add(ss);
            }
        }

        MutableZipFile zf = new MutableZipFile(new File(args[0]));

        for (String entry : zf.getEntries().keySet()) {
            if(entry.endsWith("/") || (!dt.isEmpty() && !dt.contains(entry.substring(0, entry.length() - 6)))) {
                zf.setFileData(entry, null);
            }
        }
        zf.store();
        System.out.println("OK");
    }
}
