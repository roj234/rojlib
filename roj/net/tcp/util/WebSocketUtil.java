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
package roj.net.tcp.util;

import roj.collect.IBitSet;
import roj.io.FileUtil;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.response.HTTPResponse;
import roj.net.tcp.serv.util.Request;
import roj.text.CharList;
import roj.text.crypt.Base64;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/14 18:26
 */
public class WebSocketUtil {
    public static final byte
            /**
             * 延续帧。本次数据传输采用了数据分片，当前收到的数据帧为其中一个数据分片。
             */
            FRAME_CONTINUE = 0x0,
            FRAME_TEXT     = 0x1,
            FRAME_BINARY   = 0x2,
    // 3-7 non-control frame
            FRAME_DISCONN  = 0x8,
            FRAME_PING     = 0x9,
            FRAME_PONG     = 0xA;
    // B-F control frame

    //打包函数 返回帧处理

    /**
     * 编码数据帧
     * @param opcode FRAME_操作码
     */
    public static void encode(byte opcode, ByteList in, ByteBuffer out) {
        int $len = in.pos();
        out.put((byte) (256 | opcode));
        if ($len <= 125) {
            out.put((byte) $len);
        } else if ($len <= 65535) {
            out.put((byte) 126)
            .put((byte) ($len >> 8))
            .put((byte) $len);
        } else {
            out.put((byte) 127).putInt(0)
                    .put((byte) ($len >> 24))
                    .put((byte) ($len >> 16))
                    .put((byte) ($len >> 8))
                    .put((byte) $len);
        }
        in.putInto(out);
    }

    /**
     * 解析数据帧
     * @return 后面还有消息吗
     */
    public static boolean decode(ByteList in, IBitSet opcodes) {
        int $len;
        ByteReader r = new ByteReader(in);
        int id = r.readUnsignedByte();
        // 0

        if(!opcodes.contains(id & 15))
            throw new IllegalArgumentException("Not valid opcode.");
        int fin = id & 255;

        $len = r.readUnsignedByte(); // 1
        if(($len & 128) == 0)
            throw new IllegalArgumentException("Client data does not masked.");

        $len &= 127;
        int $masks = r.readInt();

        ByteList $data;
        if ($len == 126) {
            $data = r.readBytesDelegated(r.readUnsignedShort());
        } else if ($len == 127) {
            $data = r.readBytesDelegated((int) r.readLong());
        } else {
            $data = r.readBytesDelegated($len);
        }

        for (int i = 0; i < $data.pos(); i++) {
            in.set(i, (byte) ($data.get(i) ^ (($masks >>> ((i & 3) * 8)) & 255)));
        }

        return fin == 0;
    }

    public static byte[] calcKey(String key, CharList out) {
        FileUtil.SHA1.reset();
        String sec = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        int len = ByteWriter.byteCountUTF8(key);
        ByteList bl = new ByteList(len + sec.length());
        ByteWriter.writeUTF(bl, key, -1);
        for (int i = len; i < sec.length(); i++) {
            bl.list[i] = (byte) sec.charAt(i - len);
        }
        byte[] digest = FileUtil.SHA1.digest(bl.list);
        ByteList bl1 = new ByteList(digest);
        bl.clear();
        return Base64.encode(bl1, bl).toByteArray();
    }

    public static Response handShake(Request request, Set<String> validProtocol) {
        String key = request.headers("Sec-WebSocket-Key");
        String protocol = request.headers("Sec-WebSocket-Protocol");
        if(!validProtocol.contains(protocol))
            return null;

        return new Reply(Code.SWITCHING_PROTOCOL, new HTTPResponse() {
            @Override
            public void writeHeader(CharList list) {
                list.append("Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
                calcKey(key, list);
                list.append(CRLF).append(CRLF);
            }

            @Override
            public void prepare() {}

            @Override
            public boolean send(WrappedSocket channel) {
                return false;
            }

            @Override
            public void release() {}
        }, Action.HEAD);
    }
}
