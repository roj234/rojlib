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
package roj.util;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

import java.lang.reflect.Field;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/8/13 13:21
 */
public final class Pid {
    public static int getPid(Process process) {
        switch (OS.CURRENT) {
            case WINDOWS:
                return WinImpl.getPid(process);
            case MAC_OS:
                return MacImpl.getPid(process);
            case UNIX:
                return UnixImpl.getPid(process);
            default:
                return -1;
        }
    }

    private static final class WinImpl {
        public static int getPid(Process process) {
            try {
                Field field = process.getClass().getDeclaredField("handle");
                field.setAccessible(true);
                long handle = field.getLong(process);
                return Kernel32.INSTANCE.GetProcessId(new WinNT.HANDLE(new Pointer(handle)));
            } catch (Throwable e) {
                //e.printStackTrace();
            }
            return -1;
        }
    }

    private static final class MacImpl {
        public static int getPid(Process process) {
            return -1;
        }
    }

    private static final class UnixImpl {
        public static int getPid(Process process) {
            try {
                Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);
                return field.getInt(process);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                //e.printStackTrace();
            }
            return -1;
        }
    }
}
