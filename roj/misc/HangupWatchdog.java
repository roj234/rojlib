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

package roj.misc;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.locks.LockSupport;

/**
 * Process hangup watchdog * require file updater to do so
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/7/9 11:45
 */
public final class HangupWatchdog extends Thread {
    public static void HWClient(int port) {
        HangupWatchdog dog = new HangupWatchdog();
        dog.port = port;
        dog.setDaemon(true);
        dog.setName("Watchdog");
        dog.start();
    }

    int port;
    @Override
    public void run() {
        try(ServerSocket ss = new ServerSocket(port)) {
            byte[] temp = new byte[] {(byte) 233};
            while (true) {
                try (Socket socket = ss.accept()) {
                    while (true) {
                        socket.getOutputStream().write(temp);
                        LockSupport.parkNanos(500_000_000L);
                    }
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    static final int CHECK_INTERVAL = 10 * 1000;

    static Process process;
    public static void main(String[] args) throws IOException {
        if(args.length < 3) {
            System.out.println("缺失参数, 参数: <端口> <工作目录> <命令行>...");
            return;
        }

        String[] procArg = new String[args.length - 2];
        System.arraycopy(args, 2, procArg, 0, args.length - 2);

        startProcess(args[1], procArg);
        byte[] temp = new byte[1];

        while (true) {
            try (Socket socket = new Socket()) {
                socket.setSoTimeout(1000);
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(args[0])));
                int read;
                do {
                    read = socket.getInputStream().read(temp);
                } while (read > 0);
            } catch (IOException ignored) {}

            LockSupport.parkNanos(500_000_000L);
            long t1 = System.currentTimeMillis();
            if(!process.isAlive()) {
                System.err.println("\n\n" + t1 + " 进程正常退出,正在重启\n不需要请改源代码\n\n");
            } else {
                System.err.println("\n\n" + t1 + " 进程无响应,正在重启\n\n");
            }

            startProcess(args[1], procArg);
            LockSupport.parkNanos(1000_000_000L);
        }
    }

    static void startProcess(String env, String[] args) throws IOException {
        if(process != null) {
            if(process.isAlive())
                process.destroyForcibly();
        }
        process = new ProcessBuilder(args).directory(new File(env))
                // 将目标的标准错误，标准输出，标准输入重定向到自己
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
    }
}