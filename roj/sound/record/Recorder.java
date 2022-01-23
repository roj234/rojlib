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
package roj.sound.record;

import javax.sound.sampled.*;

/**
 * @author Roj234
 * @since  2020/12/19 22:36
 */
public class Recorder implements Runnable {
    public final AudioFormat format;
    protected final TargetDataLine line;
    protected final int once;
    protected final byte[] buf;
    protected final VoiceHandler handler;

    public Recorder(int once, VoiceHandler handler) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, this.format = getAudioFormat());
        line = (TargetDataLine) AudioSystem.getLine(info);
        this.once = once;
        this.handler = handler;
        this.buf = new byte[once];
    }

    protected AudioFormat getAudioFormat() {
        // 每秒播放和录制的 '样' 本数
        // 8000,11025,16000,22050,44100...
        float rate = 8000f;
        // 每个具有此格式的声音样本中的位数
        int sampleSize = 16;
        // 声道数
        int channels = 1;

        return new AudioFormat(rate, sampleSize, 2, true, true);
    }

    public void start() throws LineUnavailableException {
        line.open(format/*, buffer*/);
        line.start();
    }

    @Override
    public void run() {
        final Thread self = Thread.currentThread();
        final byte[] buf = this.buf;

        int got;
        do {
            //line.available();
            got = line.read(buf, 0, once);
            if (got <= 0) {
                break;
            }
            handler.handle(buf, got);
        } while (!self.isInterrupted() && line.isOpen());
    }

    public void close() {
        line.close();
    }
}
