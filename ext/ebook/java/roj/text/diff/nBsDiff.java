package roj.text.diff;

import roj.RojLib;
import roj.asmx.injector.Copy;
import roj.asmx.injector.Inject;
import roj.asmx.injector.Shadow;
import roj.asmx.injector.Weave;
import roj.asmx.launcher.Autoload;
import roj.util.DynByteBuf;
import roj.util.optimizer.IntrinsicCandidate;

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
	@IntrinsicCandidate("IL_bsdiff_init")
	private static native void initializeSuffixArray(final byte[] baseData, final int[] suffixArray, int size);
	@Inject(at = Inject.At.REMOVE)
	private static native void sortSuffixGroup(int[] suffixArray, int[] positionArray, int start, int length, int stepSize);
	@Inject(at = Inject.At.REMOVE)
	private static native int POS(int[] positionArray, int pos);

	@Inject
	public void makePatch(byte[] right, DynByteBuf patch) {
		patch.putIntLE(right.length);
		var ctx = newCtx();
		try {
			while (true) {
				int length = patch.unsafeWritableBytes();
				int write = makePatch(sfx, left, ctx, right, patch.array(), patch._unsafeAddr(), length);
				if (write == -1) break;

				patch.wIndex(patch.wIndex() + write);
				patch.ensureWritable(1024);
			}
		} finally {
			freeCtx(ctx);
		}
	}
	@Inject
	public int calculateDiffLength(byte[] right, int off, int end, int stopOn) {return getDiffLength(sfx, left, right, off, end, stopOn);}

	@Copy
	@IntrinsicCandidate("IL_bsdiff_newCtx")
	private static native long newCtx();
	@Copy
	@IntrinsicCandidate("IL_bsdiff_freeCtx")
	private static native void freeCtx(long ctx);
	@Copy
	@IntrinsicCandidate("IL_bsdiff_makePatch")
	private static native int makePatch(int[] sfx, byte[] left, long nativeContext, byte[] right, byte[] ip0, long ip1, int length);
	@Copy
	@IntrinsicCandidate("IL_bsdiff_getDiffLength")
	private static native int getDiffLength(int[] sfx, byte[] left, byte[] right, int off, int end, int stopOn);
}