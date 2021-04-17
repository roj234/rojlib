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
package roj.net.tcp.voice;

import roj.io.NonblockingUtil;
import roj.net.tcp.client.ClientSocket;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.sound.record.Recorder;
import roj.sound.record.VoiceHandler;
import roj.util.ByteList;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/19 13:21
 */
public class VoiceClient extends ClientSocket implements VoiceHandler, Runnable {
    final Recorder recorder;
    boolean started, ended;

    AtomicInteger ai = new AtomicInteger();

    public VoiceClient() {
        try {
            this.recorder = new Recorder(3072, 1024, this);
        } catch (LineUnavailableException e) {
            throw new UnsupportedOperationException("没有录音设备!", e);
        }
    }

    @Override
    protected WrappedSocket createChannel() {
        return new InsecureSocket(server.socket(), NonblockingUtil.fd(server));
    }

    public void connect(String address, int port) throws IOException {
        if (connected()) {
            disconnect();
        }
        super.createSocket(address, port, true);
    }

    public void run() {
        if (started)
            throw new IllegalStateException();
        try {
            recorder.start();
        } catch (LineUnavailableException e) {
            throw new UnsupportedOperationException("没有录音设备!", e);
        }
        started = true;
        ended = false;
        recorder.run();
        ended = true;
    }

    long t = System.currentTimeMillis();

    @Override
    public void handle(ByteList buffer) {
        //while (!ai.compareAndSet(0, -1)) {
        //    Thread.yield();
        //}

        //System.out.println("Handling " + buffer);

        //size += buffer.pos();
        /*try {
            buffer.writeToStream(gzos);
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        System.out.println(buffer.pos());
        if (System.currentTimeMillis() - t >= 2000) {
            System.exit(0);
        }

        //compress and output

        // do sth

        //ai.set(0);
    }
}
