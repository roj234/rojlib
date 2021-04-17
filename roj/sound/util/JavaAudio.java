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
/*
 * Audio.java -- 音频输出
 */
package roj.sound.util;

import javax.sound.sampled.*;
import javax.sound.sampled.FloatControl.Type;

/**
 * 将解码得到的PCM数据写入音频设备（播放）。
 */
public class JavaAudio implements IAudio {
    private SourceDataLine out;

    @Override
    public boolean open(AudioConfig h) {
        AudioFormat af = new AudioFormat(h.getSamplingRate(), 16,
                h.channels(), true, false);
        try {
            if(out == null)
                out = AudioSystem.getSourceDataLine(af);
            if(out.isOpen())
                out.close();
            out.open(af, 8 * h.getPcmSize());
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            return false;
        }

        out.start();
        return true;
    }

    public void mute(boolean mute) {
        ((BooleanControl) out.getControl(BooleanControl.Type.MUTE)).setValue(mute);
    }

    public void setVolume(float vol) {
        ((FloatControl) out.getControl(Type.MASTER_GAIN)).setValue(vol);
    }

    public float getVolume() {
        return ((FloatControl) out.getControl(Type.MASTER_GAIN)).getValue();
    }

    @Override
    public int write(byte[] b, int size) {
        if (out == null)
            return -1;
        return out.write(b, 0, size);
    }

    public void start(boolean b) {
        if (out == null)
            return;
        if (b)
            out.start();
        else
            out.stop();
    }

    @Override
    public void drain() {
        if (out != null)
            out.drain();
    }

    @Override
    public void close() {
        if (out != null) {
            out.stop();
            out.close();
            out = null;
        }
    }
}