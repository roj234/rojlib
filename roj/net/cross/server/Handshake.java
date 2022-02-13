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
import java.nio.channels.SelectionKey;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:17
 */
final class Handshake extends Stated {
    static final Stated HANDSHAKE = new Handshake();
    static final int NEXT_CYCLE = -1;

    @Override
    public Stated next(Client self) throws IOException {
        WrappedSocket ch = self.ch;
        if (System.currentTimeMillis() >= self.timer) {
            state((byte) HS_ERR_TIMEOUT, ch);
            return null;
        }

        if (!ch.handShake()) {
            self.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            return this;
        }
        self.key.interestOps(SelectionKey.OP_READ);

        ByteBuffer rb = ch.buffer();
        if (self.st1 == 0) {
            if (rb.position() < 6) {
                int read = ch.read(6 - rb.position());
                if (read < 0) return null;
            }
            if (rb.position() < 6) {
                return this;
            }

            if (rb.getInt(0) != MAGIC) {
                state((byte) HS_ERR_PROTOCOL, ch);
                return null;
            }

            int v = rb.get(4) & 0xFF;
            if (v < PROTOCOL_VERSION) {
                state((byte) HS_ERR_VERSION_LOW, ch);
                return null;
            } else if (v > PROTOCOL_VERSION) {
                state((byte) HS_ERR_VERSION_HIGH, ch);
                return null;
            }

            state((byte) HS_OK, ch);

            int channel_type = rb.get(5) & 0xFF;
            rb.clear();
            switch (channel_type) {
                case PCN_CONTROL:
                    self.st1 = 1;
                    break;
                case PCN_DATA:
                    return PipeLogin.PIPE_LOGIN;
                default:
                    syncPrint(self + ": 无效的频道类型");
                    return null;
            }
        }

        if (rb.position() < 1 && ch.read(1) == 0)
            return this;
        int role = rb.get(0) & 0xFF;
        switch (role) {
            case PS_LOGIN_C:
                return ClientLogin.CLIENT_LOGIN;
            case PS_LOGIN_H:
                return HostLogin.HOST_LOGIN;
            default:
                syncPrint(self + ": 无效的角色类型 " + role);
                return null;
        }
    }

    static void state(byte id, WrappedSocket ch) throws IOException {
        if (ch.handShake()) write1(ch, id);
        else write1Direct(ch, id);
    }
}
