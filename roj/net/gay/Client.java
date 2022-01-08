/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.net.gay;

import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.MSSSocket;
import roj.net.NetworkUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

/**
 * @author solo6975
 * @version 0.1
 * @since 2022/1/3 17:25
 */
public class Client {
    public static void main(String[] args) throws IOException {
        System.out.println("线性版本管理系统 Gay 1.0 联网更新客户端");
        if (args.length < 3) {
            System.out.println("参数: Client <server> <version> <base path>");
            return;
        }
        ByteList sb = IOUtil.getSharedByteBuf();
        int lv = sb.readStreamFully(new FileInputStream(args[1])).readInt();

        String s = args[0];
        Socket socket = new Socket();
        socket.connect(InetSocketAddress.createUnresolved(s.substring(s.indexOf(':') + 1), Integer.parseInt(s.substring(0, s.indexOf(':')))), 15000);

        NetworkUtil.MSSLoadClientRSAKey(new File("g_client.key"));
        MSSSocket mss = new MSSSocket(socket, NIOUtil.fd(socket));
        int timeout = 5000;
        while (!mss.handShake()) {
            LockSupport.parkNanos(20);
            if (timeout-- == 0) {
                System.out.println("握手超时");
                return;
            }
        }
        System.out.println("已连接到服务器，正在等待响应");
        ByteBuffer rb = mss.buffer();
        rb.clear();
        rb.putInt(Server.IDENTIFIER).putInt(lv).flip();
        timeout = 5000;
        while (timeout-- > 0) {
            int wr = mss.write(rb);
            if (wr == 0) {
                if (!rb.hasRemaining()) break;
                LockSupport.parkNanos(20);
            }
            if (wr < 0) {
                System.out.println(mss + " 连接断开 " + wr);
                return;
            }
        }

        int req = 12;
        while (rb.position() < req) {
            int read = mss.read();
            if (read == 0) {
                LockSupport.parkNanos(20);
                if (timeout-- == 0) {
                    System.out.println(mss + " 读取超时");
                    return;
                }
                continue;
            }
            if (read < 0) {
                System.out.println(mss + " 连接断开 " + read);
                return;
            }
            if (rb.position() >= 12 && req == 12) {
                if (rb.getInt(0) != Server.IDENTIFIER) {
                    System.out.println(mss + " 无效服务端");
                    while (!mss.shutdown() && timeout-- > 0) ;
                    return;
                }
                req = 12 + rb.getInt(8);
            }
        }

        int tv = rb.getInt(4);
        System.out.println("开始更新，目标版本: " + tv);
        try {
            sb.clear();
            int len = rb.getInt(8);
            sb.ensureCapacity(len);

            rb.position(12);
            rb.get(sb.list, 0, len);
            sb.wIndex(len);

            Ver.applyPatch(sb, new File(args[2]));

            sb.clear();
            sb.putInt(tv).writeToStream(new FileOutputStream(args[1]));
            System.out.println("更新成功！");
        } finally {
            System.out.println("更新失败，请将错误复制并交给管理员！");
        }
    }
}
