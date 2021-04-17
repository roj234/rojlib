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
package roj.sound;

import roj.io.source.RandomAccessFileSource;
import roj.sound.mp3.Player;
import roj.sound.util.JavaAudio;
import roj.sound.util.PCMBuffer;
import roj.util.ByteList;

import java.io.IOException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/19 15:12
 */
public class Test {
    public static void main(String[] args) throws IOException {
        Player player = new Player(args.length > 1 ? new PCMBuffer() : new JavaAudio());
        player.open(new RandomAccessFileSource(args[0]));
        System.out.println("文件头部信息: " + player.getHeader());
        System.out.println("文件附加信息: " + player.getID3Tag());
        System.out.println("比特率: " + player.getHeader().getBitrate() + "Kbps");
        System.out.println("采样率: " + player.getHeader().getSamplingRate() + "kHz");

        long t = System.currentTimeMillis();
        player.decode();
        System.out.println("解码时间： " + (System.currentTimeMillis() - t) + "ms");
        System.out.println("有多少帧： " + player.getHeader().getFrames());
        System.out.println("解码长度: " + player.getHeader().getElapse() + "s");

        System.out.println("开始测试");
        ByteList buffer = ((PCMBuffer) player.audio).buf;

    }
}
