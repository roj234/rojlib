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
 * ConcurrentSynthesis.java -- 一个声道多相频率倒置和多相合成滤波.
 */
package roj.sound.mp3;

import roj.concurrent.task.ITask;

/**
 * 由于大量浮点运算，多相合成滤波耗时最多，使用并发运算加速解码
 */
final class SynthesisTask implements ITask, Runnable {
    private final int ch;
    private final float[] samples;
    private float[][] bufA, bufB; // 调用者无锁双缓冲
    private final Layer3 owner;

    private volatile boolean done;

    public SynthesisTask(Layer3 owner, int ch) {
        this.owner = owner;
        this.ch = ch;
        this.done = true;
        samples = new float[32];
        bufA = new float[owner.granules][32 * 18];
        bufB = new float[owner.granules][32 * 18];
    }

    /**
     * 交换双缓冲
     */
    float[][] swapBuf() {
        // 1. 交换缓冲区
        float[][] p = bufA;
        bufA = bufB;
        bufB = p;

        done = false;

        // 3. 返回"空闲的"缓冲区，该缓冲区内的数据已被run()方法使用完毕
        return bufA;
    }

    float[][] getEmptyBuffer() {
        return bufA;
    }

    @Override
    public void calculate() {
        run();
    }

    public void run() {
        if (done) return;
        int granules = owner.granules;
        Synthesis syth = owner.synthesis;

        // 1. 干活
        float[][] bufB = this.bufB;
        float[] samples = this.samples;
        for (int gr = 0; gr < granules; gr++) {
            float[] xr = bufB[gr];
            for (int j = 0; j < 18; j += 2) {
                int sub;
                int i;
                for (i = j, sub = 0; sub < 32; sub++, i += 18)
                    samples[sub] = xr[i];
                syth.synthesisSubBand(samples, ch);

                for (i = j + 1, sub = 0; sub < 32; sub += 2, i += 36) {
                    samples[sub] = xr[i];

                    // 多相频率倒置(INVERSE QUANTIZE SAMPLES)
                    samples[sub + 1] = -xr[i + 18];
                }
                syth.synthesisSubBand(samples, ch);
            }
        }

        synchronized (this) {
            done = true;
            notify();
        }
    }

    void await() throws InterruptedException {
        synchronized (this) {
            if (!done) {
                wait();
            }
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }
}
