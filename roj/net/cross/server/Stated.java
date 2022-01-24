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

import roj.io.NIOUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:18
 */
abstract class Stated {
    void enter(Client self) {
        self.timer = System.currentTimeMillis() + TIMEOUT;
        self.st1 = 0;
    }

    void tick(Client W) throws IOException {}

    abstract Stated next(Client self) throws IOException;

    static void unknownPacket(Client self, ByteBuffer rb) throws IOException {
        int bc = (rb.get(0) & 0xFF) - 0x20;
        if(rb.position() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
            syncPrint(self + ": 错误 " + ERROR_NAMES[bc]);
        } else {
            syncPrint(self + ": 未知数据包: " + NIOUtil.dumpBuffer(rb));
            write1(self.ch, (byte) PS_ERROR_UNKNOWN_PACKET);
        }
    }

    static boolean isInRoom(Client t) throws IOException {
        Room room = t.room;
        if (room != null) {
            if (room.master == null) {
                write1(t.ch, (byte) PS_ERROR_MASTER_DIE);
                return false;
            } else if (!room.clients.containsKey(t.clientId)) {
                write1(t.ch, (byte) PS_ERROR_KICKED);
                return false;
            }
        }
        return true;
    }

    static void openFail(ByteBuffer rb, String refused) {
        byte[] data = refused.getBytes(StandardCharsets.UTF_8);
        rb.clear();
        rb.put((byte) P_CHANNEL_OPEN_FAIL)
          .putInt(-1)
          .put((byte) data.length).put(data).flip();
    }

    static String getUTF(ByteBuffer buf, int len) {
        byte[] data = new byte[len];
        buf.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }
}
