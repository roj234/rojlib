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
package roj.mod.fp;

import roj.mod.Shared;
import roj.ui.CmdUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;

/**
 * Abstraction of FMD File Processor
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/30 11:32
 */
public abstract class Processor implements Runnable, IntSupplier, BiConsumer<Integer, File> {
    File forgeJar, mcJar, mcServer;
    volatile int exitCode = 0x23232323;

    static Method addURL;

    public Processor(File mcJar, File mcServer) {
        this.mcJar = mcJar;
        this.mcServer = mcServer;
    }

    static {
        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            addURL = method;
        } catch (NoSuchMethodException e) {
            CmdUtil.error("Failed to get addURL method!", e);
        }
    }

    @Override
    public void run() {
        exitCode = run0();
        synchronized (this) {
            this.notifyAll();
        }
    }

    public int getAsInt() {
        Shared.parallel.waitUntilFinish();
        if(exitCode == 0x23232323) {
            CmdUtil.error("程序遇到了无法处理的异常，即将退出");
        }
        return exitCode;
    }

    public abstract int run0();
}
