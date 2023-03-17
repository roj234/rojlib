package ilib.asm.util;

import ilib.Config;
import roj.asm.type.Type;
import roj.collect.TrieTreeSet;
import roj.reflect.DirectAccessor;
import roj.util.Helpers;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.relauncher.FMLSecurityManager;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.security.AccessControlContext;
import java.security.Permission;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/4/5 20:01
 */
public class SafeSystem extends FMLSecurityManager {
	private static Consumer<Object> SetSec;
	private static final String baseDir = new File("").getAbsolutePath();
	private static final TrieTreeSet forbidden = new TrieTreeSet("dll", "exe", "sh", "cmd", "bat", "scr", "lnk", "url", "so", "dyn", "sys");
	private static final String javaHome = System.getProperty("java.home");

	@SuppressWarnings("unchecked")
	public static void register() {
		if (SetSec == null) {
			SetSec = DirectAccessor.builder(Consumer.class).i_access("java/lang/System", "security", new Type("java/lang/SecurityManager"), null, "accept", true).build();
		}
		SetSec.accept(new SafeSystem());
	}

	public void checkExec(String cmd) {
		Helpers.athrow(new IOException("Access denied"));
	}

	public void checkLink(String lib) {
		if (lib == null) {
			throw new NullPointerException("library can't be null");
		}
		for (int i = 0; i < lib.length(); i++) {
			char c = lib.charAt(i);
			if (c == '\\' || c == '/') {
				if (!lib.contains("jna")) Helpers.athrow(new UnsatisfiedLinkError("Access Denied"));
				break;
			}
		}
	}

	public void checkRead(String file) {
		check(file, 0);
	}

	public void checkRead(String file, Object context) {
		check(file, 0);
	}

	public void checkWrite(String file) {
		check(file, 1);
	}

	public void checkDelete(String file) {
		check(file, 2);
	}

	private void check(String file, int type) {
		if (file.isEmpty()) return;
		switch (file.charAt(0)) {
			case '.':
			case '/':
			case '\\':
				if (!file.startsWith(baseDir)) {
					if (file.length() > 1 && (file.charAt(1) == '/' || file.charAt(1) == '\\')) return;
					onExternal(file, type);
				}
				return;
		}
		if (file.length() < 2) return;
		if (file.charAt(1) == ':' && !file.startsWith(baseDir)) onExternal(file, type);

	}

	private void onExternal(String file, int type) {
		if (file.startsWith(javaHome)) {
			if (type != 0) Helpers.athrow(new IOException("Access Denied"));
			return;
		}

		int sl = file.lastIndexOf('/');
		if (sl <= 0) sl = file.lastIndexOf('\\');
		if (file.startsWith("jna", sl + 1)) return;

		sl = file.lastIndexOf('.');
		if (sl < 0) return;
		if (forbidden.contains(file, sl + 1, file.length())) {
			Helpers.athrow(new IOException("Access Denied For " + file));
		}
	}

	public void checkConnect(String host, int port) {
		checkConnect(host, port, null);
	}

	public void checkConnect(String host, int port, Object context) {
		if (Loader.instance().hasReachedState(LoaderState.AVAILABLE)) return;

		if (host.equals("127.0.0.1") || host.equals("localhost")) return;
		if (Config.noUpdate != null && !Config.noUpdate.contains(host)) Helpers.athrow(new IOException("Access Denied - " + host + ":" + port));
	}

	public void checkPrintJobAccess() {
		Helpers.athrow(new IOException("Access Denied"));
	}

	// 允许的权限

	public void checkPermission(Permission perm, Object context) {
		if (context instanceof AccessControlContext) {
			((AccessControlContext) context).checkPermission(perm);
		} else {
			throw new SecurityException();
		}
	}

	public void checkListen(int port) {}

	public void checkAccept(String host, int port) {}

	public void checkMulticast(InetAddress maddr) {}

	public void checkMulticast(InetAddress maddr, byte ttl) {}

	public void checkRead(FileDescriptor fd) {}

	public void checkWrite(FileDescriptor fd) {}

	public void checkPropertyAccess(String key) {}

	public void checkPropertiesAccess() {}

	public void checkSetFactory() {}

	public void checkSecurityAccess(String target) {}
}
