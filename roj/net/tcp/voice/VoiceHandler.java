package roj.net.tcp.voice;

import roj.concurrent.task.ITaskUncancelable;
import roj.net.tcp.util.WrappedSocket;
import roj.util.ByteList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/19 23:23
 */
public class VoiceHandler implements ITaskUncancelable {
    WrappedSocket socket;

    public VoiceHandler(WrappedSocket socket, VoiceServer server) {

    }

    @Override
    public void calculate(Thread thread) throws Exception {
        ByteList list = socket.buffer();
        socket.read();
    }

    @Override
    public boolean isDone() {
        return false;
    }
}
