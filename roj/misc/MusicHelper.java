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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Music File Helper
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/7/13 17:43
 */
public class MusicHelper {
    public static void main(String[] args) {
        List<File> songs = new ArrayList<>();
        List<File> lyrics = new ArrayList<>();
        List<File> chineseLyrics = new ArrayList<>();

        FileUtil.findAllFiles(new File(args[0]), new Predicate<File>() {
            @Override
            public boolean test(File file) {
                String name = file.getName();
                if(name.endsWith(".lrc")) {
                    String abs = file.getAbsolutePath();
                    if(abs.contains(".mp3")) {
                        abs = abs.replace(".mp3", "");
                        System.out.println("Remove Mp3 " + name);
                        File x = new File(abs);
                        if(!file.renameTo(x)) {
                            long a = x.lastModified();
                            long b = file.lastModified();
                        }
                    }
                    if(abs.contains("..")) {
                        abs = abs.replace("..", ".");
                        System.out.println("Remove .. " + name);
                        File x = new File(abs);
                        if(x.isFile() || !file.renameTo(x)) {
                            long a = x.lastModified();
                            long b = file.lastModified();
                            int off = name.endsWith(".cn.lrc") ? 7 : 4;
                            String songFileName = abs.substring(0, abs.length() - off) + "mp3";
                            long music = new File(songFileName).lastModified();
                            if(Math.abs(music - a) < Math.abs(music - b)) {
                                // a latest
                                file.delete();
                            } else {
                                x.delete();
                            }
                        }
                    }
                    if(name.endsWith(".cn.lrc")) {
                        chineseLyrics.add(file);
                    } else {
                        lyrics.add(file);
                        String songFileName = abs.substring(0, abs.length() - 3) + "mp3";
                        if(!new File(songFileName).isFile()) {
                            int pos = abs.lastIndexOf('-');
                            if(pos != -1) {
                                if(abs.charAt(pos - 1) == ' ' && abs.charAt(pos + 1) == ' ') {
                                    File tg = new File(abs.substring(0, pos - 1) + ".mp3");
                                    if(tg.isFile()) {
                                        System.out.println("歌词多出专辑部分 " + name);
                                        if (!tg.renameTo(new File(songFileName))) {
                                            System.out.println("tg unable to song " + tg.getAbsolutePath() + " => " + songFileName);
                                        }
                                    } else {
                                        System.out.println("歌词缺歌 " + name);
                                        file.delete();
                                        new File(file.getAbsolutePath().substring(0, name.length() - 3) + "cn.lrc").delete();
                                    }
                                } else {
                                    System.out.println("InvlPosLrc " + name);
                                }
                            }
                        }
                    }
                } else {
                    int pos = name.lastIndexOf('-');
                    if(pos != -1) {
                        if(name.charAt(pos - 1) == ' ' && name.charAt(pos + 1) == ' ') {
                            int prev = name.length() - pos;
                            String abs = file.getAbsolutePath();
                            File tg = new File(abs.substring(0, abs.length() - prev - 1) + ".mp3");
                            if(tg.isFile()) {
                                if(tg.length() < file.length()) {
                                    tg.delete();
                                    System.out.println("重复,d1 " + tg.getName());
                                } else {
                                    file.delete();
                                    System.out.println(tg.renameTo(file));
                                    System.out.println("重复,d2 " + name);
                                }
                            }
                        } else {
                            System.out.println("InvlPos " + name);
                        }
                    } else {
                        System.out.println("无标题格式 " + name);
                        file.renameTo(new File( "[无标题格式]" + name));
                    }
                    songs.add(file);
                }
                return false;
            }
        });
    }
}
