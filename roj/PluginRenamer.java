/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj;

import roj.config.ParseException;
import roj.config.YAMLParser;
import roj.config.data.CMapping;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/26 10:52
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
                    InputStream in = zf.getInputStream(ze);
                    bl.readStreamArrayFully(in);
                    //in.close();

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

                    while(!file.renameTo(newFile)) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                        System.gc();
                        System.runFinalization();
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
