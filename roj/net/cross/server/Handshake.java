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
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:17
 */
final class Handshake extends Stated {
    static final Stated HANDSHAKE = new Handshake();

    private static byte handshake(Client worker) throws IOException {
        WrappedSocket ch = worker.ch;

        long wait = System.currentTimeMillis() + TIMEOUT;
        while (!ch.handShake()) {
            LockSupport.parkNanos(20);
            if (AEServer.server.shutdown) return HS_ERR_POLICY;
            if (System.currentTimeMillis() >= wait) {
                return HS_ERR_TIMEOUT;
            }
        }

        if (!readSome(ch, 6, wait)) {
            return HS_ERR_TIMEOUT;
        }

        ByteBuffer rb = ch.buffer();
        if (rb.getInt(0) != MAGIC) return HS_ERR_PROTOCOL;
        int v = rb.get(4) & 0xFF;
        if (v < PROTOCOL_VERSION) {
            return HS_ERR_VERSION_LOW;
        } else if (v > PROTOCOL_VERSION) {
            return HS_ERR_VERSION_HIGH;
        }

        return (byte) HS_OK;
    }

    @Override
    public Stated next(Client self) throws IOException {
        byte heart = handshake(self);
        WrappedSocket ch = self.ch;
        if (ch.handShake())
            write1(ch, heart);
        else
            write1Direct(ch, (byte) HS_ERR_TIMEOUT);
        if (heart != (byte) HS_OK) {
            return null;
        }
        ByteBuffer rb = ch.buffer();
        int channel_type = rb.get(5) & 0xFF;
        switch (channel_type) {
            case PCN_CONTROL:
                rb.clear();
                if (!readSome(ch, 1, System.currentTimeMillis() + TIMEOUT)) return null;
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
            case PCN_DATA:
                return PipeLogin.PIPE_LOGIN;
            default:
                syncPrint(self + ": 无效的频道类型");
                return null;
        }
    }
}
