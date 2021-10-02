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
package roj.sound.util;

/**
 * 音频输出缓冲区。
 */
public final class AudioBuffer {
    public byte[] pcm;
    final int[] off = new int[]{0, 2}; // 两个声道的缓冲区偏移量
    private final IAudio audio;

    /**
     * 音频输出缓冲区构建器。
     *
     * @param audio 音频输出对象。如果指定为null则调用 {@link #flush()} 不产生输出，仅清空缓冲区。
     * @param size  音频输出缓冲区长度，单位“字节”。
     */
    public AudioBuffer(IAudio audio, int size) {
        this.audio = audio;
        this.pcm = new byte[size];
    }

    /**
     * 音频输出缓冲区的内容刷向音频输出对象并将缓冲区偏移量复位。当缓冲区填满时才向音频输出对象写入，但调用者并不需要知道当前缓冲区是否已经填满。
     * 防止缓冲区溢出， 每解码完一帧应调用此方法一次。
     */
    public void flush() {
        if (audio != null)
            audio.write(pcm, off[0]);
        off[0] = 0;
        off[1] = 2;
    }

    /**
     * 音频输出缓冲区的全部内容刷向音频输出对象并将缓冲区偏移量复位。在解码完一个文件的最后一帧后调用此方法，将缓冲区剩余内容写向音频输出对象。
     */
    public void close() {
        if (off[0] > 0 && audio != null) {
            audio.write(pcm, off[0]);
            audio.drain();
        }
        off[0] = 0;
        off[1] = 2;
    }

    public final void offset(int ch, int val) {
        off[ch] = val;
    }

    public final int offsetOf(int ch) {
        return off[ch];
    }
}
