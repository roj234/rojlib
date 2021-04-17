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

package roj;

import java.io.File;
import java.io.IOException;

/**
 * Process hangup watchdog * require file updater to do so
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/7/9 11:45
 */
public final class HangupWatchdog {
    static final int CHECK_INTERVAL = 10 * 1000;

    static Process process;
    public static void main(String[] args) throws IOException {
        if(args.length < 3) {
            System.out.println("缺失参数, 参数: <文件检测路径> <程序工作目录> 程序参数(多个)...");
            return;
        }

        File path = new File(args[0]);
        String[] procArg = new String[args.length - 2];
        System.arraycopy(args, 2, procArg, 0, args.length - 2);

        Thread.currentThread().setName("HW-Watchdog");

        if((!path.isFile() && !path.createNewFile()) || !path.setLastModified(System.currentTimeMillis()))
            System.err.println("无法创建计时文件 " + path);

        startProcess(args[1], procArg);
        while (true) {
            long t = System.currentTimeMillis();
            if(t - path.lastModified() > CHECK_INTERVAL) {
                System.err.println("\n\n" + t + " 进程异常终止,正在重启\n\n");
                startProcess(args[1], procArg);
                path.setLastModified(t);
            } else if(!process.isAlive()) {
                System.err.println("\n\n" + t + " 进程正常退出,正在重启\n不需要请改源代码\n\n");
                startProcess(args[1], procArg);
                path.setLastModified(t);
            }

            try {
                // 自己改
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
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