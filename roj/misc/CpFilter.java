package roj.misc;

import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.io.MutableZipFile;
import roj.text.SimpleLineReader;

import java.io.File;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/8 12:35
 */
public class CpFilter {
    public static void main(String[] args) throws Exception {
        System.out.println("jar txt");
        MyHashSet<String> dt = new MyHashSet<>();
        try (SimpleLineReader s = new SimpleLineReader(IOUtil.readUTF(new File(args[1])))) {
            for (String ss : s) {
                dt.add(ss);
            }
        }
        System.out.println(dt);

        MutableZipFile zf = new MutableZipFile(new File(args[0]));

        for (String entry : zf.getEntries().keySet()) {
            if(entry.startsWith("roj/asm")||entry.startsWith("roj/collect"))
                if(!dt.contains(entry.substring(0, entry.length() - 6).replace('/', '.'))) {
                    zf.setFileData(entry, null);
                }
        }
        zf.store();
        System.out.println("OK");
    }
}
