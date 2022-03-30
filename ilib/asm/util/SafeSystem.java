package ilib.asm.util;

import roj.asm.type.Type;
import roj.reflect.DirectAccessor;
import roj.util.Helpers;
import sun.security.util.SecurityConstants;

import net.minecraftforge.fml.relauncher.FMLSecurityManager;

import java.io.FileDescriptor;
import java.io.FilePermission;
import java.io.IOException;
import java.net.InetAddress;
import java.security.AccessControlContext;
import java.security.Permission;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since  2022/4/5 20:01
 */
public class SafeSystem extends FMLSecurityManager {
    private static Consumer<Object> SetSec;

    @SuppressWarnings("unchecked")
    public static void register() {
        if (SetSec == null) {
            SetSec = DirectAccessor
                .builder(Consumer.class)
                .i_access("java/lang/System", "security", new Type("java/lang/SecurityManager"), null, "accept", true)
                .build();
        }
        SetSec.accept(new SafeSystem());
    }

    public void checkExec(String cmd) {
        Helpers.athrow(new IOException("这并不是Minecraft运行所必须的权限"));
    }

    public void checkLink(String lib) {
        if (lib == null) {
            throw new NullPointerException("library can't be null");
        }
        for (int i = 0; i < lib.length(); i++) {
            char c = lib.charAt(i);
            if (c == '\\' || c == '/') {
                if (!lib.contains("jna")) Helpers.athrow(new UnsatisfiedLinkError("这并不是Minecraft运行所必须的权限"));
                break;
            }
        }
    }

    public void checkRead(String file) {
        checkPermission(new FilePermission(file, SecurityConstants.FILE_READ_ACTION));
    }

    public void checkRead(String file, Object context) {
        checkPermission(new FilePermission(file, SecurityConstants.FILE_READ_ACTION), context);
    }

    public void checkWrite(String file) {
        checkPermission(new FilePermission(file, SecurityConstants.FILE_WRITE_ACTION));
    }

    public void checkDelete(String file) {
        checkPermission(new FilePermission(file, SecurityConstants.FILE_DELETE_ACTION));
    }

    public void checkConnect(String host, int port) {
        //Helpers.athrow(new IOException("这并不是Minecraft运行所必须的权限"));
    }

    public void checkConnect(String host, int port, Object context) {
        //Helpers.athrow(new IOException("这并不是Minecraft运行所必须的权限"));
    }

    public void checkListen(int port) {
        //if (!ImpLib.isClient) Helpers.athrow(new IOException("这并不是Minecraft客户端运行所必须的权限"));
    }

    public void checkAccept(String host, int port) {
        //if (!ImpLib.isClient) Helpers.athrow(new IOException("这并不是Minecraft客户端运行所必须的权限"));
    }

    public void checkMulticast(InetAddress maddr) {
        //Helpers.athrow(new IOException("这并不是Minecraft运行所必须的权限"));
    }

    public void checkMulticast(InetAddress maddr, byte ttl) {
        //Helpers.athrow(new IOException("这并不是Minecraft运行所必须的权限"));
    }

    public void checkPrintJobAccess() {
        Helpers.athrow(new IOException("这并不是Minecraft运行所必须的权限"));
    }

    // 允许的权限

    public void checkPermission(Permission perm, Object context) {
        if (context instanceof AccessControlContext) {
            ((AccessControlContext)context).checkPermission(perm);
        } else {
            throw new SecurityException();
        }
    }

    public void checkRead(FileDescriptor fd) {
        if (fd == null) {
            throw new NullPointerException("file descriptor can't be null");
        }
        checkPermission(new RuntimePermission("readFileDescriptor"));
    }

    public void checkWrite(FileDescriptor fd) {
        if (fd == null) {
            throw new NullPointerException("file descriptor can't be null");
        }
        checkPermission(new RuntimePermission("writeFileDescriptor"));
    }

    public void checkPropertyAccess(String key) {}

    public void checkPropertiesAccess() {}

    public void checkSetFactory() {}

    public void checkSecurityAccess(String target) {}
}
