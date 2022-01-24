/*
 * This file is a part of MoreItems
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
package roj.net.cross.server;

import roj.net.WrappedSocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:28
 */
final class HostLogin extends Stated {
    static final HostLogin HOST_LOGIN = new HostLogin();

    @Override
    Stated next(Client W) throws IOException {
        WrappedSocket ch = W.ch;
        if (System.currentTimeMillis() > W.timer) {
            syncPrint(W + ": 登录超时");
            write1(ch, (byte) PS_ERROR_TIMEOUT);
            return Logout.LOGOUT;
        }

        ByteBuffer rb = ch.buffer();
        int except;
        switch (W.st1) {
            case 0:
                except = 5;
                break;
            case 1:
                except = (rb.get(1) & 0xFF) + (rb.get(2) & 0xFF) +
                        (rb.get(3) & 0xFF) + ((rb.get(4) & 0xFF) << 1) + 5;
                break;
            default:
                // should not got here
                throw new IllegalStateException();
        }

        if (rb.position() < except) {
            int read = ch.read(except - rb.position());
            if (read < 0) return Logout.LOGOUT;
        }
        if (rb.position() < except) {
            return null;
        }

        int nameLen = rb.get(1) & 0xFF;
        int passLen = rb.get(2) & 0xFF;
        int motdLen = rb.get(3) & 0xFF;
        int portLen = rb.get(4) & 0xFF;
        if (rb.position() < (nameLen + passLen + motdLen + (portLen << 1) + 5)) {
            W.st1 = 1;
            return this;
        }
        rb.position(5);

        if (portLen < 1 || portLen > 64) {
            syncPrint(W + ": 端口映射表有误");
            return Logout.LOGOUT;
        }

        int code = AEServer.server.login(W,
                                         true,
                                         getUTF(rb, nameLen),
                                         getUTF(rb, passLen));
        if (code != -1) {
            syncPrint(W + ": 登录失败: " + ERROR_NAMES[code - 0x20]);
            write1(ch, (byte) code);
            return Logout.LOGOUT;
        }

        byte[] motd = new byte[motdLen];
        rb.get(motd);

        byte[] port = new byte[portLen << 1];
        rb.get(port);

        Room room = W.room;
        room.motd = motd;
        room.motdString = new String(motd, StandardCharsets.UTF_8);
        room.portMap = port;

        rb.clear();
        rb.put((byte) PC_LOGON_H)
          .put((byte) AEServer.server.info.length)
          .put(AEServer.server.info).flip();
        writeAndFlush(ch, rb, 500);

        StringBuilder pb = new StringBuilder();
        pb.append(W).append(": 登录成功, 端口映射表: ");
        for (int i = 0; i < port.length; i++) {
            pb.append(((port[i++] & 0xFF) << 8) | (port[i] & 0xFF)).append(", ");
        }
        pb.delete(pb.length() - 2, pb.length());
        syncPrint(pb.toString());
        rb.clear();
        return HostWork.HOST_WORK;
    }
}
