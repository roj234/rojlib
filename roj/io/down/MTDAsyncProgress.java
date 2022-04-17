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
package roj.io.down;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Roj234
 * @since  2020/9/13 12:33
 */
public class MTDAsyncProgress extends STDProgress {
    protected final ConcurrentLinkedQueue<IDown> workers;

    public MTDAsyncProgress() {
        workers = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void onJoin(IDown dn) {
        workers.add(dn);
    }

    @Override
    public void onChange(IDown dn) {
        long t = System.currentTimeMillis();
        if(t - last < printInterval) return;
        last = t;

        long sumDown = 0, sumTot = 0, sumByte = 0;
        for (Iterator<IDown> itr = workers.iterator(); itr.hasNext(); ) {
            IDown d = itr.next();
            sumDown += d.getDownloaded();
            sumTot += d.getTotal();
            sumByte += d.getAverageSpeed();
            if (d.getRemain() <= 0) itr.remove();
        }

        print(100d * sumDown / sumTot, sumByte);
    }

    public IProgress subProgress() {
        return new STDProgress() {
            @Override
            public void onJoin(IDown dn) {
                MTDAsyncProgress.this.onJoin(dn);
            }

            @Override
            public void onChange(IDown dn) {
                MTDAsyncProgress.this.onChange(dn);
            }

            @Override
            public void onFinish() {
                for (Iterator<IDown> itr = workers.iterator(); itr.hasNext(); ) {
                    IDown d = itr.next();
                    if (d.getRemain() <= 0) itr.remove();
                }
                if (workers.isEmpty()) {
                    super.onFinish();
                }
            }
        };
    }
}
