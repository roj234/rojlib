package roj.mod;

import roj.ui.CmdUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/30 11:32
 */
public abstract class IProcess implements Runnable, IntSupplier, BiConsumer<Integer, File> {
    File forgeJar, mcJar, mcServer;
    Thread thread;
    volatile int exitCode = 0x23232323;

    static Method addURL;

    public IProcess(File mcJar, File mcServer) {
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
        if(thread == null)
            throw new IllegalStateException("Not start yet");

        if (thread.isAlive() && exitCode == 0x23232323) {
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (Exception ignored) {
            }
        }
        return exitCode;
    }

    public abstract int run0();
}
