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
