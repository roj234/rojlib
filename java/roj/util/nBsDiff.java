package roj.util;

import roj.RojLib;
import roj.asmx.injector.Copy;
import roj.asmx.injector.Inject;
import roj.asmx.injector.Shadow;
import roj.asmx.injector.Weave;
import roj.asmx.launcher.Autoload;
import roj.reflect.litasm.FastJNI;

/**
 * @author Roj234
 * @since 2023/8/2 6:08
 */
@Autoload(value = Autoload.Target.NIXIM, intrinsic = RojLib.GENERIC)
@Weave(target = BsDiff.class)
final class nBsDiff {
	@Inject("<clinit>") static void __clinit() {RojLib.linkLibrary(BsDiff.class);}

	@Shadow private byte[] left;
	@Shadow private int[] sfx;

	@Inject(at = Inject.At.REPLACE)
	@FastJNI("IL_bsdiff_init")
	private static native void implSetLeft(final byte[] left, final int[] sfx, int size);
	@Inject(at = Inject.At.REMOVE)
	private static native void split(int[] I, int[] V, int start, int len, int h);
	@Inject(at = Inject.At.REMOVE)
	private static native int getV(int[] V, int pos);

	@Inject
	public void makePatch(byte[] right, DynByteBuf patch) {
		patch.putIntLE(right.length);
		var ctx = nCreateContext();
		try {
			while (true) {
				int length = patch.unsafeWritableBytes();
				int write = nMakePatch(sfx, left, ctx, right, patch.array(), patch._unsafeAddr(), length);
				if (write == -1) break;

				patch.wIndex(patch.wIndex() + write);
				patch.ensureWritable(1024);
			}
		} finally {
			nFreeContext(ctx);
		}
	}
	@Inject
	public int getDiffLength(byte[] right, int off, int end, int stopOn) {return nGetDiffLength(sfx, left, right, off, end, stopOn);}

	@Copy
	@FastJNI("IL_bsdiff_newCtx")
	private static native long nCreateContext();
	@Copy
	@FastJNI("IL_bsdiff_freeCtx")
	private static native void nFreeContext(long ctx);
	@Copy
	@FastJNI("IL_bsdiff_makePatch")
	private static native int nMakePatch(int[] sfx, byte[] left, long nativeContext, byte[] right, byte[] ip0, long ip1, int length);
	@Copy
	@FastJNI("IL_bsdiff_getDiffLength")
	private static native int nGetDiffLength(int[] sfx, byte[] left, byte[] right, int off, int end, int stopOn);
}