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

package roj.mod;

import roj.asm.Parser;
import roj.io.IOUtil;
import roj.ui.UIUtil;
import roj.util.ByteList;

import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2021/5/30 19:59
 */
public final class HotReload {
    public static void main(String[] args) throws IOException {
        if(args.length < 1) {
            System.out.println("class重载工具: 参数缺失: port");
            return;
        }

        InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(args[0]));
        ByteList bw = new ByteList().put((byte) 0x66).putShort(0);
        int dataAmount = 0;

        while (true) {
            String ques = UIUtil.userInput("class路径, 留空转换, CLR 清除");

            if(ques.equals("CLR")) {
                dataAmount = 0;
                bw.clear();
                bw.put((byte) 0x66).putShort(0);
            } else if(ques.length() == 0) {
                int pos = bw.wIndex();
                bw.wIndex(1);
                bw.putShort(dataAmount);
                bw.wIndex(pos);

                try (Socket socket = new Socket()) {
                    socket.setSoTimeout(1000);
                    socket.connect(addr);
                    bw.writeToStream(socket.getOutputStream());
                    System.out.println("重载请求已发送...");
                } catch (IOException e) {
                    System.out.println("连接失败");
                    e.printStackTrace();
                }

                dataAmount = 0;
                bw.clear();
                bw.put((byte) 0x66).putShort(0);
            } else {
                File clz = new File(ques);
                if (!clz.isFile()) {
                    System.out.println("文件不存在");
                    continue;
                }

                byte[] buf = IOUtil.read(new FileInputStream(clz));
                String name;
                try {
                    name = Parser.simpleData(buf).get(0);
                } catch (Throwable e) {
                    System.out.println("不是class文件");
                    e.printStackTrace();
                    continue;
                }

                dataAmount++;
                byte[] nb = name.replace('/', '.').getBytes(StandardCharsets.UTF_8);
                bw.putShort(nb.length)
                  .put(nb)
                  .putInt(buf.length)
                  .put(buf);
            }
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        int port = Integer.parseInt(agentArgs);
        Thread th = new Runner(inst, port);
        th.start();
    }

    static final class Runner extends Thread {
        final Instrumentation inst;
        final int port;

         Runner(Instrumentation inst, int port) {
            this.inst = inst;
            this.port = port;
            setName("HotReload Listener");
            setDaemon(true);
        }

        @Override
        public void run() {
            try (ServerSocket ss = new ServerSocket()) {
                ss.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
                byte[] tmp = new byte[8192];
                main:
                while (true) {
                    try (Socket socket = ss.accept()) {
                        socket.setSoTimeout(500);
                        InputStream in = socket.getInputStream();
                        if (3 != in.read(tmp, 0, 3) || tmp[0] != 0x66)
                            continue;
                        int entryCount = ((tmp[1] & 0xFF) << 8) | (tmp[2] & 0xFF);

                        ClassDefinition[] classes = new ClassDefinition[entryCount];

                        while (entryCount-- > 0) {
                            if (8 != in.read(tmp, 0, 6))
                                continue main;
                            int nameLen = ((tmp[0] & 0xFF) << 8) | (tmp[1] & 0xFF);
                            int dataLen = ((tmp[2] & 0xFF) << 24) | ((tmp[3] & 0xFF) << 16) | ((tmp[4] & 0xFF) << 8) | (tmp[5] & 0xFF);
                            if (tmp.length < dataLen) {
                                tmp = new byte[dataLen];
                            }
                            if (nameLen != in.read(tmp, 0, nameLen)) {
                                continue main;
                            }
                            String name = new String(tmp, 0, nameLen, StandardCharsets.UTF_8);

                            if (dataLen != in.read(tmp, 0, dataLen)) {
                                continue main;
                            }

                            try {
                                Class<?> t = Class.forName(name, false, Launch.classLoader);
                                classes[entryCount] = new ClassDefinition(t, Arrays.copyOf(tmp, dataLen));
                            } catch (Throwable e) {
                                System.err.println("Failed to update class: ");
                                e.printStackTrace();
                            }
                        }

                        inst.redefineClasses(classes);
                        System.out.println("对以下对象的操作成功: " + java.util.Arrays.toString(classes));
                    } catch (Throwable e) {
                        System.err.println("Failed to update class: ");
                        e.printStackTrace();
                    }
                }
            } catch (IOException ignored) {}
        }
    }
}