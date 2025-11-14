package roj.io;

import roj.RojLib;
import roj.util.DynByteBuf;
import roj.util.JVM;

import java.util.Objects;

/**
 * @author Roj234
 * @since 2025/11/27 23:24
 */
public class SecurityDescriptor {
	static {RojLib.hasNative(1);}

	private static final boolean isRoot = JVM.isRoot();
	private static native byte[] get0(String path, boolean backupRestore);
	private static native boolean set0(String path, long handle, boolean backupRestore);

	public static byte[] get(String path) {
		return get0(Objects.requireNonNull(path), false);
	}

	public static boolean set(String path, DynByteBuf sd) {
		Objects.requireNonNull(path);
		if (sd.isDirect()) {
			return set0(path, sd.address(), isRoot);
		} else {
			var directBuf = BufferPool.localPool().allocate(true, sd.readableBytes());
			directBuf.put(sd);

			var result = set0(path, directBuf.address(), isRoot);

			directBuf.release();

			return result;
		}
	}
}
