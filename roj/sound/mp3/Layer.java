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
package roj.sound.mp3;

import roj.sound.util.AudioBuffer;

/**
 * 抽象帧解码器
 *
 * @version 0.3
 */
public abstract class Layer {
    final Synthesis synthesis;
    final AudioBuffer buf;
    final Header header;
    final BitStream data;

    public Layer(Header h, AudioBuffer buf, BitStream data) {
        this.data = data;
        this.header = h;
        this.buf = buf;
        this.synthesis = new Synthesis(buf, h.channels());
    }

    /**
     * 从此位置开始解码一帧
     * @return 解码后的offset，用于计算解码下一帧数据的开始位置在源数据缓冲区的偏移量。
     */
    public abstract int decodeFrame(byte[] b, int off);

    /**
     * 完成一帧多相合成滤波后将输出的PCM数据写入音频输出
     *
     * @see AudioBuffer#flush()
     */
    public void flush() {
        buf.flush();
    }

    /**
     * 音频输出缓冲区的全部内容刷向音频输出并将缓冲区偏移量复位。
     *
     * @see AudioBuffer#close()
     */
    public void close() {
        buf.close();
    }
}
