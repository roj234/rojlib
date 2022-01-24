package roj.net.cross.server;

import roj.concurrent.task.AbstractCalcTask;
import roj.io.NIOUtil;
import roj.net.MSSSocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.P_UPNP_PING;
import static roj.net.cross.Util.P_UPNP_PONG;
import static roj.net.cross.server.AEServer.server;

/**
 * @author Roj233
 * @since 2022/1/24 14:12
 */
public class UPnPPingTask extends AbstractCalcTask<Byte> {
    final byte[] ip;
    final char port;
    final long sec;

    public UPnPPingTask(byte[] ip, char port, long sec) {
        this.ip = ip;
        this.port = port;
        this.sec = sec;
    }

    @Override
    public void calculate(Thread thread) throws Exception {
        executing = true;
        try {
            this.out = (byte) asyncPing(ip, port, sec);
        } catch (Throwable e) {
            exception = new ExecutionException(e);
        }
        executing = false;

        synchronized (this) {
            notifyAll();
        }
    }

    static int asyncPing(byte[] ip, char port, long sec) {
        try (Socket soc = new Socket()) {
            soc.setSoTimeout(200);
            soc.setReuseAddress(true);
            soc.connect(new InetSocketAddress(InetAddress.getByAddress(ip), port), 200);

            MSSSocket ch = new MSSSocket(soc, NIOUtil.fd(soc));
            long time = System.currentTimeMillis() + 500;
            while (!ch.handShake()) {
                LockSupport.parkNanos(10000);
                if (server.shutdown) return -4;
                if (System.currentTimeMillis() >= time) {
                    return -1;
                }
            }
            ip[0] = P_UPNP_PING;
            while (0 == ch.write(ByteBuffer.wrap(ip, 0, 1))) {
                LockSupport.parkNanos(10000);
                if (server.shutdown) return -4;
                if (System.currentTimeMillis() >= time) {
                    return -1;
                }
            }
            while (0 == ch.read()) {
                LockSupport.parkNanos(10000);
                if (server.shutdown) return -4;
                if (System.currentTimeMillis() >= time) {
                    return -1;
                }
            }
            ByteBuffer rb1 = ch.buffer();
            if (rb1.get(0) != P_UPNP_PONG) {
                return -2;
            }
            while (rb1.position() < 9) {
                ch.read();
                LockSupport.parkNanos(10000);
                if (server.shutdown) return -4;
                if (System.currentTimeMillis() >= time) {
                    return -1;
                }
            }
            return rb1.getLong(1) == sec ? 0 : -3;
        } catch (IOException ignored) {
            return -3;
        }
    }
}
