package roj.misc;

import roj.asm.Parser;
import roj.asm.tree.*;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * @author Roj233
 * @since 2021/7/8 12:35
 */
public class PFClassMerger {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("PFClassMerger <jar> <class <method>... !>...");
            System.out.println("  用途：替换jar内指定class的指定方法");
            System.out.println("       使用'!'作为方法的终止");
            System.out.println("  eg: PFCR implib-0.4.0.jar PFClassMerger-replace.class main !");
            return;
        }
        MyHashMap<String, ByteList> modified = new MyHashMap<>();

        ZipFile zf = new ZipFile(args[0]);

        int j = 0;

        while (++j < args.length) {
            ConstantData now = Parser.parseConstants(IOUtil.read(new File(args[j])));
            ZipEntry ze = zf.getEntry(now.name + ".class");
            if (ze == null) throw new FileNotFoundException(now.name + ".class");
            ConstantData orig = Parser.parseConstants(IOUtil.read(zf.getInputStream(ze)));

            MyHashMap<String, List<MoFNode>> s = new MyHashMap<>();
            for (; j < args.length; j++) {
                if (args[j].equals("!")) break;
                s.put(args[j], new ArrayList<>());
            }
            for (int i = 0; i < now.methods.size(); i++) {
                MethodSimple ms = (MethodSimple) now.methods.get(i);
                List<MoFNode> nodes = s.get(ms.name.getString());
                if(nodes != null)
                    nodes.add(new Method(now, ms));
            }
            for (int i = orig.methods.size() - 1; i >= 0; i--) {
                MethodSimple ms = (MethodSimple) orig.methods.get(i);
                List<MoFNode> nodes = s.get(ms.name.getString());
                if(nodes != null) {
                    for (int k = 0; k < nodes.size(); k++) {
                        if(nodes.get(k).rawDesc().equals(ms.rawDesc())) {
                            orig.methods.remove(i);
                            System.out.println("Replace: " + ms);
                            break;
                        }
                    }
                }
            }
            for (List<MoFNode> nodes : s.values())
                orig.methods.addAll(Helpers.cast(nodes));
            Clazz compress = Parser.parse(Parser.toByteArrayShared(orig));
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
                bl.readStreamFully(in).writeToStream(zos);
                in.close();
                bl.clear();
            }
            zos.closeEntry();
        }
        ZipUtil.close(zos);
        System.out.println("OK");
    }
}
