package roj;

import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.CstType;
import roj.asm.cst.CstUTF;
import roj.asm.mapper.Mapping;
import roj.asm.tree.AccessData;
import roj.asm.tree.ConstantData;
import roj.collect.MyHashMap;
import roj.io.FileUtil;
import roj.io.ZipUtil;
import roj.text.CharList;
import roj.ui.CmdUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/8 12:35
 */
public class BugHelper {
    public static void main(String[] args) throws Exception {
        System.out.println("检测IMessage的子类");

        MyHashMap<String, List<String>> librarySupers = new MyHashMap<>();
        MyHashMap<String, String[]> toFile = new MyHashMap<>();
        ByteList bl = new ByteList();
        String[] currZip = new String[1];

        ZipUtil.ICallback cb = (fileName, s) -> {
            bl.clear();
            bl.readStreamArrayFully(s);
            if(bl.pos() < 32)
                return;

            AccessData data;
            try {
                data = Parser.parseAccessDirect(bl.list);
            } catch (Throwable e) {
                CmdUtil.warning("Class " + fileName + " is unable to read", e);
                return;
            }

            ArrayList<String> list = new ArrayList<>();
            if(!"java/lang/Object".equals(data.superName)) {
                list.add(data.superName);
            }
            List<String> itf = data.itf;
            for (int i = 0; i < itf.size(); i++) {
                String name = itf.get(i);
                if (name.endsWith("_NMR$FAKEIMPL")) {
                    int ss = name.lastIndexOf('/') + 1;
                    int e = name.length() - "_NMR$FAKEIMPL".length();
                    name = new CharList(e - ss + 1).append(name, ss, e - ss).replace('_', '/').toString();
                    System.out.println("[NMR继承-Lib] " + data.name + " <= " + name);
                }
                list.add(name);
            }
            toFile.put(data.name, new String[] {currZip[0], fileName});

            // 构建lib一极继承表
            if(!list.isEmpty())
                librarySupers.put(data.name, list);
        };

        List<File> files = FileUtil.findAllFiles(new File(args[0]));

        for (int i = 0; i < files.size(); i++) {
            File fi = files.get(i);
            String f = fi.getName();
            if(f.endsWith(".zip") || f.endsWith(".jar")) {
                currZip[0] = fi.getAbsolutePath();
                ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
            }
        }

        Mapping.makeInheritMap(librarySupers, null);

        System.out.println("ClassInheritance load complete.");

        MyHashMap<String, ZipFile> zfs = new MyHashMap<>();

        for (Map.Entry<String, List<String>> key : librarySupers.entrySet()) {
            if(!key.getValue().contains("net/minecraftforge/fml/common/network/simpleimpl/IMessage")) {
                String[] data = toFile.get(key.getKey());
                ZipFile zf = zfs.get(data[0]);
                if(zf == null) {
                    zfs.put(data[0], zf = new ZipFile(data[0]));
                }
                InputStream in = zf.getInputStream(zf.getEntry(data[1]));
                bl.clear();
                bl.readStreamArrayFully(in);

                    ConstantData cd = Parser.parseConstants(bl);
                    List<Constant> cc = cd.cp.array();
                    for (int i = 0; i < cc.size(); i++) {
                        if(cc.get(i).type() == CstType.UTF) {
                            String cn = ((CstUTF) cc.get(i)).getString();
                            if(cn.endsWith("readItemStack")) {
                                System.out.println(">> In " + zf.getName());
                                System.out.println("> Inheritor '" + key.getKey() + "'");
                                System.out.println(" - !!Exact class usage found: " + cn);
                                try(FileOutputStream fos = new FileOutputStream(data[1].substring(data[1].lastIndexOf('/') + 1))) {
                                    bl.writeToStream(fos);
                                }
                                break;
                            }
                        }
                    }
                in.close();
            }
        }
    }

}
