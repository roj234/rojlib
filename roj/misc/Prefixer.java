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
package roj.misc;

import roj.io.FileUtil;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Auto append License and Prefix before class and fix older prefixes
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/6/16 2:39
 */
public class Prefixer {
    public static void main(String[] args) {
        if(args.length < 1) {
            System.out.println("Usage: Prefixer <path>\n not config available");
            return;
        }

        String license = ("/*\r\n" +
                " * This file is a part of MI\r\n" +
                " *\r\n" +
                " * The MIT License (MIT)\r\n" +
                " *\r\n" +
                " * Copyright (c) 2021 Roj234\r\n" +
                " *\r\n" +
                " * Permission is hereby granted, free of charge, to any person obtaining a copy\r\n" +
                " * of this software and associated documentation files (the \"Software\"), to deal\r\n" +
                " * in the Software without restriction, including without limitation the rights\r\n" +
                " * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\r\n" +
                " * copies of the Software, and to permit persons to whom the Software is\r\n" +
                " * furnished to do so, subject to the following conditions:\r\n" +
                " *\r\n" +
                " * The above copyright notice and this permission notice shall be included in\r\n" +
                " * all copies or substantial portions of the Software.\r\n" +
                " *\r\n" +
                " * THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\r\n" +
                " * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\r\n" +
                " * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\r\n" +
                " * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\r\n" +
                " * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\r\n" +
                " * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN\r\n" +
                " * THE SOFTWARE.\r\n" +
                " */\r\n");

        String common_prefix_v1 = "/**\r\n" +
                " * This file is a part of";

        String common_prefix_v2 = "/**\r\n" +
                " * This file is a part of MI <br>\r\n" +
                " * 版权没有";

        String common_new_prefix = "/**\n" +
                " * No description provided\n" +
                " *\n" +
                " * @author Roj234\n" +
                " * @version 0.1\n" +
                " * @since ";

        ACalendar calender = new ACalendar();

        ByteList bl = new ByteList();
        CharList cl = new CharList();
        CharList cl2 = new CharList();
        FileUtil.findAllFiles(new File(args[0]), file -> {
            if(!file.getName().endsWith(".java")) return false;

            long mtime = file.lastModified();
            try {
                final CharList cl1 = cl;
                cl1.clear();
                bl.clear();

                FileInputStream fis = new FileInputStream(file);
                ByteReader.decodeUTF(-1, cl1, bl.readStreamArrayFully(fis));
                fis.close();

                if(cl1.regionMatches(0, license)) {
                    System.out.println("[Info] Already Licensed " + file.getName());
                    return false;
                }

                int str = 0;
                while (str < cl1.length()) {
                    char c = cl1.charAt(str++);
                    if(c == '/' && str < cl1.length() && cl1.charAt(str) == '*') {
                        break;
                    }
                }
                int end = str;
                while (end < cl1.length()) {
                    char c = cl1.charAt(end++);
                    if(c == '*' && end < cl1.length() && cl1.charAt(end) == '/') {
                        break;
                    }
                }

                if(cl1.regionMatches(str - 1, common_prefix_v1)) {
                    cl2.append(common_new_prefix);
                    int[] date = calender.get(mtime);

                    int pos = str - 1;
                    String desc;
                    if(cl1.regionMatches(pos, common_prefix_v2)) {
                        //System.out.println("[Info] Prefix v2 detected");
                        while (pos < end) {
                            char c = cl1.charAt(pos++);
                            if(c == '@' && pos + 2 < end && cl1.charAt(pos) == 's' && cl1.charAt(pos + 1) == 'i' && cl1.charAt(pos + 2) == 'n') {
                                pos += 5;
                                break;
                            }
                        }
                        cl2.append(cl1, pos, end + 1 - pos);
                    } else {
                        while (pos < end) {
                            char c = cl1.charAt(pos++);
                            if(c == '.' && pos + 2 < end && cl1.charAt(pos) == 'j' && cl1.charAt(pos + 1) == 'a' && cl1.charAt(pos + 2) == 'v') {
                                pos += 5;
                                break;
                            }
                        }
                        if(pos >= end) {
                            System.err.println("[Warn]Non-standard mark in " + file.getAbsolutePath());
                            pos = end - 4; // \r\n*/
                        }

                        cl2.append(Integer.toString(date[ACalendar.YEAR])).append('/')
                           .append(Integer.toString(date[ACalendar.MONTH])).append('/')
                           .append(Integer.toString(date[ACalendar.DAY])).append(' ')
                           .append(Integer.toString(date[ACalendar.HOUR])).append(':')
                           .append(Integer.toString(date[ACalendar.MINUTE])).append(cl1, pos, end + 1 - pos);
                    }
                    if(str == 1) {
                        cl1.replace(0, end + 1, "");
                        pos = 0;
                        end = cl1.length();
                        while (pos + 6 < end) {
                            char c = cl1.charAt(pos++);
                            if(c == 'p' && cl1.charAt(pos) == 'u' && cl1.charAt(pos + 1) == 'b' && cl1.charAt(pos + 2) == 'l' && cl1.charAt(pos + 3) == 'i' && cl1.charAt(pos + 4) == 'c' && cl1.charAt(pos + 5) == ' ') {
                                pos -= 2;
                                break;
                            } else if(c == 'c' && cl1.charAt(pos) == 'l' && cl1.charAt(pos + 1) == 'a' && cl1.charAt(pos + 2) == 's' && cl1.charAt(pos + 3) == 's' && cl1.charAt(pos + 4) == ' ') {
                                pos -= 2;
                                break;
                            }
                        }
                        if(pos + 6 >= end) {
                            pos = 0;
                            System.err.println("[Warn] Tag not found in " + file.getAbsolutePath());
                        }

                        cl1.insert(pos, cl2);
                    } else {
                        cl1.replace(str - 1, end + 1, cl2);
                    }
                    cl2.clear();
                }

                cl1.insert(0, license);
                //System.out.println(cl1);

                bl.clear();
                ByteWriter.writeUTF(bl, cl1, -1);
                try(FileOutputStream fos = new FileOutputStream(file)) {
                    bl.writeToStream(fos);
                    bl.clear();
                }

                if(!file.setLastModified(mtime)) {
                    System.err.println("[Warn]Await set " + file.getAbsolutePath() + " to " + mtime);

                    while (!file.setLastModified(mtime)) {
                        System.gc();
                        System.runFinalization();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    System.out.println("[Info]Await done");
                }
            } catch (IOException e) {
                System.out.println("at " + file.getPath());
                e.printStackTrace();
            }
            return false;
        });

        System.out.println("Prefixer done");
    }
}
