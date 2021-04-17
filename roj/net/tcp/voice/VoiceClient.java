package roj.net.tcp.voice;

import roj.concurrent.TaskHandler;
import roj.concurrent.pool.TaskExecutor;
import roj.concurrent.task.ExecutionTask;
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
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/19 13:21
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
    protected WrappedSocket getChannel() throws IOException {
        return new InsecureSocket(server, NonblockingUtil.fd(server));
    }

    public void connect(String address, int port) throws IOException {
        if (connected()) {
            disconnect();
        }
        super.createSocket(address, port, true);
    }

    public final void start() {
        TaskExecutor executor = new TaskExecutor();
        executor.setDaemon(false);
        executor.start();
        start(executor);
        executor.interrupt();
    }

    public void start(TaskHandler handler) {
        if (started)
            throw new IllegalStateException();
        started = true;
        ended = false;
        try {
            recorder.start();
        } catch (LineUnavailableException e) {
            throw new UnsupportedOperationException("没有录音设备!", e);
        }
        handler.pushTask(new ExecutionTask(recorder));
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

    @Override
    public void run() {
        if (started && !ended)
            recorder.run();
    }
}
