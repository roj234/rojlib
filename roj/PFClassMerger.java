package roj;

import roj.asm.Parser;
import roj.asm.struct.Clazz;
import roj.asm.struct.ConstantData;
import roj.asm.struct.Method;
import roj.asm.struct.simple.MethodSimple;
import roj.asm.struct.simple.MoFNode;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/8 12:35
 */
public class PFClassMerger {
    public static void main(String[] args) throws Exception {
        System.out.println("class方法替换者 参数 jar [class method...]... use '!' as methd name end");
        MyHashMap<String, ByteList> modified = new MyHashMap<>();

        ZipFile zf = new ZipFile(args[0]);

        int j = 0;

        while (j++ < args.length) {
            ConstantData now = Parser.parseConstants(IOUtil.readFile(new File(args[j++])));
            ZipEntry ze = zf.getEntry(now.name + ".class");
            if (ze == null) throw new FileNotFoundException(now.name + ".class");
            ConstantData orig = Parser.parseConstants(IOUtil.readFully(zf.getInputStream(ze)));

            MyHashMap<String, MoFNode> s = new MyHashMap<>();
            for (; j < args.length; j++) {
                if (args[j].equals("!")) break;
                s.put(args[j], null);
            }
            for (int i = 0; i < now.methods.size(); i++) {
                MethodSimple ms = now.methods.get(i);
                if (s.containsKey(ms.name.getString())) s.put(ms.name.getString(), new Method(now, ms));
            }
            for (int i = 0; i < orig.methods.size(); i++) {
                MethodSimple ms = orig.methods.get(i);
                if (s.containsKey(ms.name.getString())) orig.methods.remove(i--);
            }
            orig.methods.addAll(Helpers.cast(s.values()));
            Clazz compress = Parser.parse(orig.getBytes(), 0);
            modified.put(ze.getName(), compress.getBytes());
        }

        ByteList bl = new ByteList();
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(args[0] + ".rsl"));
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze1 = en.nextElement();
            if(ze1.isDirectory()) continue;
            ze1.setCompressedSize(-1);
            zos.putNextEntry(ze1);
            if(modified.containsKey(ze1.getName())) {
                modified.get(ze1.getName()).writeToStream(zos);
            } else {
                InputStream in = zf.getInputStream(ze1);
                bl.readStreamArrayFully(in).writeToStream(zos);
                in.close();
                bl.clear();
            }
            zos.closeEntry();
        }
        ZipUtil.close(zos);
        System.out.println("OK");
    }
}
