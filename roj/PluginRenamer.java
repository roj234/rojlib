package roj;

import roj.config.ParseException;
import roj.config.YAMLParser;
import roj.config.data.CMapping;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/2/26 10:52
 */
public class PluginRenamer {
    public static void main(String[] args) {
        if(args.length == 0)
            System.out.println("PR <path to plugins>");
        ByteList bl = new ByteList();
        CharList cl = new CharList();
        for (File file : new File(args[0]).listFiles()) {
            final String name = file.getName();
            if(file.isFile() && name.endsWith(".zip") || name.endsWith(".jar")) {
                try(ZipFile zf = new ZipFile(file)) {
                    ZipEntry ze = zf.getEntry("plugin.yml");
                    if(ze == null)
                        continue;

                    bl.clear();
                    bl.readStreamArrayFully(zf.getInputStream(ze));

                    cl.clear();
                    ByteReader.decodeUTF(-1, cl, bl);

                    CMapping map = YAMLParser.parse(cl);

                    String newFileName = map.getString("name") + '-' + map.getString("version") + name.substring(name.lastIndexOf('.'));

                    File newFile;
                    do {
                        newFile = new File(args[0], newFileName);
                        if(!newFile.isFile())
                            break;
                        newFileName += "_";
                    } while (true);

                    zf.close();

                    while(!file.renameTo(newFile)) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                        System.gc();
                        System.out.println("Waiting rename " + file.getName() + " to " + newFile.getName());
                    }
                } catch (IOException | ParseException e) {
                    System.out.println("In " + file.getName());
                    e.printStackTrace();
                }
            }
        }
    }
}
