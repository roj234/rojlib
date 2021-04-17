/*
 * This file is a part of MoreItems
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
package roj.sound;

import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.FileUtil;
import roj.io.source.RandomAccessFileSource;
import roj.sound.mp3.Player;
import roj.sound.util.JavaAudio;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ArrayUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/18 13:35
 */
public class MP3Player {
    static final int PLAY_SINGLE = 1, PLAY_REPEAT = 2, WAITING = 4, CUT_SONG = 8;

    static class Play extends Thread {
        int flags;
        int playIndex;
        File[] playList, playListBackup;
        Random rng;
        Player player;

        Play(File[] playList) {
            setName("Player");
            setDaemon(true);
            this.playList = playList.clone();
            this.playListBackup = playList;
            this.player = new Player(new JavaAudio());
            this.rng = new Random();
        }

        @Override
        public void run() {
            flags |= WAITING;
            LockSupport.park();
            while ((flags & WAITING) == 0) {
                File file = playList[playIndex];
                System.out.println("正在播放 " + file.getName());
                try (RandomAccessFileSource in = new RandomAccessFileSource(file)) {
                    player.open(in);
                    player.decode();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // stop
                if((flags & WAITING) != 0) {
                    LockSupport.park();
                    continue;
                }

                // cut
                if((flags & CUT_SONG) != 0) {
                    flags &= ~CUT_SONG;
                    continue;
                }

                // auto next
                if((flags & PLAY_SINGLE) != 0) {
                    if((flags & PLAY_REPEAT) == 0) {
                        flags |= WAITING;
                        LockSupport.park();
                    }
                } else {
                    if(++playIndex == playList.length) {
                        playIndex = 0;
                        if((flags & PLAY_REPEAT) == 0) {
                            flags |= WAITING;
                            LockSupport.park();
                        }
                    }
                }
            }
        }

        public void play(int song) {
            File f = playList[song];
            playIndex = song;
            flags |= CUT_SONG;
            player.stop();
            mayNotify();
        }

        public void stopPlaying() {
            player.stop();
            mayNotify();
        }

        public void mayNotify() {
            if((flags & WAITING) != 0) {
                flags &= ~WAITING;
                LockSupport.unpark(this);
            }
        }

        public void dumpInfo() {
            System.out.println("MP3头: " + player.getHeader());
            System.out.println("文件信息: " + player.getID3Tag());
            System.out.println("已播放 " + player.getHeader().getElapse() + "s");
            System.out.println();
        }
    }

    public static float dbSound(double linear) {
        return (float) (Math.log10(linear == 0.0 ? 1e-4 : linear) * 20.0);
    }

    public static void main(String[] args) throws IOException {
        if(args.length < 1) {
            System.out.println("MP3Player <dir>");
            return;
        }

        List<File> files = FileUtil.findAllFiles(new File(args[0]), file -> file.getName().toLowerCase().endsWith(".mp3"));
        Play play = new Play(files.toArray(new File[files.size()]));
        play.start();

        if (args.length != 2 || !args[1].equals("-notip")) {
            System.out.println("Command: ");
            System.out.println("  mode: single <on/off> repeat <on/off>");
            System.out.println("        shuffle <on/off> volume <vol>");
            System.out.println("        mute");
            System.out.println("  control: cut <index> goto <time>");
            System.out.println("           speed <speed> pause play stop");
            System.out.println("           prev next info list");
        }

        Tokenizer tokenizer = new Tokenizer();
        ArrayList<String> tmp = new ArrayList<>(4);
        boolean muted = false;

        o:
        while (true) {
            tokenizer.init(UIUtil.userInput("> "));
            tmp.clear();
            try {
                while (tokenizer.hasNext()) {
                    Word w = tokenizer.readWord();
                    if(w.type() == WordPresets.EOF) {
                        CmdUtil.error("指令有误 " + tmp);
                        continue o;
                    }
                    tmp.add(w.val());
                    tokenizer.recycle(w);
                }
                switch (tmp.get(0)) {
                    case "single":
                        if(tmp.get(1).equals("on"))
                           play.flags |= PLAY_SINGLE;
                        else
                            play.flags &= ~PLAY_SINGLE;
                        break;
                    case "repeat":
                        if(tmp.get(1).equals("on"))
                            play.flags |= PLAY_REPEAT;
                        else
                            play.flags &= ~PLAY_REPEAT;
                        break;
                    case "shuffle":
                        if(tmp.get(1).equals("on"))
                            ArrayUtil.shuffle(play.playList, play.rng);
                        else
                            System.arraycopy(play.playListBackup, 0, play.playList, 0, play.playList.length);
                        break;
                    case "cut":
                        play.play(Integer.parseInt(tmp.get(1)) - 1);
                        break;
                    case "goto":
                        if((play.flags & WAITING) == 0)
                            play.player.cut(Double.parseDouble(tmp.get(1)));
                        break;
                    case "prev":
                        play.play(play.playIndex == 0 ? play.playList.length - 1 : play.playIndex - 1);
                        break;
                    case "next":
                        play.play(play.playIndex == play.playList.length - 1 ? 0 : play.playIndex + 1);
                        break;
                    case "pause":
                        play.player.pause();
                        break;
                    case "stop":
                        play.flags |= WAITING;
                        play.player.stop();
                        break;
                    case "play":
                        if(play.player.paused()) {
                            play.player.pause();
                        }
                        play.mayNotify();
                        break;
                    case "info":
                        play.dumpInfo();
                        break;
                    case "speed":
                        System.out.println("WIP");
                        break;
                    case "vol":
                        ((JavaAudio) play.player.audio).setVolume(dbSound(Double.parseDouble(tmp.get(1))));
                        break;
                    case "mute":
                        ((JavaAudio) play.player.audio).mute(muted = !muted);
                        break;
                    case "list":
                        System.out.println("===================================");
                        File[] list = play.playList;
                        for (int i = 0; i < list.length; i++) {
                            File f = list[i];
                            System.out.println("  " + (i + 1) + ". " + f.getPath());
                        }
                        System.out.println("===================================");
                        break;

                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
}
