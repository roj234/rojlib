package roj.plugins.bkcrack;

import roj.collect.IntMap;
import roj.concurrent.Task;
import roj.io.FastFailException;

import static roj.plugins.bkcrack.Macros.*;

/**
 * @author Roj234
 * @since 2022/11/12 17:25
 */
final class Solver implements Task {
	static final int MIN_CONTIGUOUS_PLAIN_LENGTH = 8, CIPHER_KEY_SIZE = 12;

	private final ZCKiller ctx;

	private final Cipher cipA = new Cipher(), cipB = new Cipher();

	private final int index; // starting index of the used plaintext and keystream
	private final int[]
		zlist = new int[MIN_CONTIGUOUS_PLAIN_LENGTH],
		ylist = new int[MIN_CONTIGUOUS_PLAIN_LENGTH], // first two elements are not used
		xlist = new int[MIN_CONTIGUOUS_PLAIN_LENGTH]; // first four elements are not used

	Solver(ZCKiller ctx, int index, int candidateZ) {
		this.ctx = ctx;
		this.index = index + 1 - MIN_CONTIGUOUS_PLAIN_LENGTH;
		this.zlist[7] = candidateZ;
	}

	private void searchZ(int i) {
		if (i != 0) {
			// the Z-list is not complete so generate Z{i-1}[2,32) values

			// get Z{i-1}[10,32) from CRC32^-1
			int zim1_10_32 = CrcTable.getZim1_10_32(zlist[i]);

			// get Z{i-1}[2,16) values from keystream byte k{i-1} and Z{i-1}[10,16)
			for (int zim1_2_16 : KeyTable.getZi_2_16_vector(ctx.keystream[index + i - 1], zim1_10_32)) {
				// add Z{i-1}[2,32) to the Z-list
				zlist[i - 1] = zim1_10_32 | zim1_2_16;

				// find Zi[0,2) from CRC32^1
				zlist[i] &= MASK_2_32; // discard 2 least significant bits
				zlist[i] |= (CrcTable.crc32inv(zlist[i], 0) ^ zlist[i - 1]) >>> 8;

				// get Y{i+1}[24,32)
				if (i < 7) ylist[i + 1] = CrcTable.getYi_24_32(zlist[i + 1], zlist[i]);

				searchZ(i - 1);
			}
		} else {
			// the Z-list is complete so iterate over possible Y values

			// guess Y7[8,24) and keep prod == (Y7[8,32) - 1) * mult^-1
			for (int y7_8_24 = 0, prod = (MulTable.getMultinv(ylist[7] >>> 24) << 24) - MulTable.MULTINV;
				 y7_8_24 < 1 << 24;
				 y7_8_24 += 1 << 8, prod += MulTable.MULTINV << 8) {
				// get possible Y7[0,8) values
				for (byte y7_0_8 : MulTable.getMsbProdFiber3((ylist[6] >>> 24) - (prod >>> 24))) {
					// filter Y7[0,8) using Y6[24,32)
					if (Integer.compareUnsigned(prod + MulTable.getMultinv(y7_0_8) - (ylist[6] & MASK_24_32), MAXDIFF_0_24) <= 0) {
						ylist[7] = (y7_0_8&0xFF) | y7_8_24 | (ylist[7] & MASK_24_32);
						searchY(7);
					}
				}
			}
		}
	}

	private void searchY(int i) {
		// the Y-list is not complete so generate Y{i-1} values
		if (i != 3) {
			int fy = (ylist[i] - 1) * MulTable.MULTINV;
			int ffy = (fy - 1) * MulTable.MULTINV;

			// get possible LSB(Xi)
			for (byte xi_0_8 : MulTable.getMsbProdFiber2((ffy - (ylist[i - 2] & MASK_24_32)) >>> 24)) {
				// compute corresponding Y{i-1}
				int yim1 = fy - (xi_0_8&0xFF);

				// filter values with Y{i-2}[24,32)
				if (Integer.compareUnsigned(ffy - MulTable.getMultinv(xi_0_8) - (ylist[i - 2] & MASK_24_32), MAXDIFF_0_24) <= 0
					&& (yim1 >>> 24) == (ylist[i - 1] >>> 24)) {
					// add Y{i-1} to the Y-list
					ylist[i - 1] = yim1;

					// set Xi value
					xlist[i] = xi_0_8;

					searchY(i - 1);
				}
			}
		} else {
			// the Y-list is complete so check if the corresponding X-list is valid
			searchX();
		}
	}

	private void searchX() {
		// compute X7
		for (int i = 5; i <= 7; i++)
			xlist[i] = (CrcTable.crc32(xlist[i-1], ctx.plain[index+i-1])
                    & MASK_8_32) // discard the LSB
                    | (xlist[i] & 0xFF); // set the LSB

		// compute X3
		int x = xlist[7];
		for (int i = 6; i >= 3; i--)
			x = CrcTable.crc32inv(x, ctx.plain[index + i]);

		// check that X3 fits with Y1[26,32)
		int y1_26_32 = CrcTable.getYi_24_32(zlist[1], zlist[0]) & MASK_26_32;
		if (Integer.compareUnsigned(((ylist[3] - 1) * MulTable.MULTINV - (x&0xFF) - 1) * MulTable.MULTINV - y1_26_32, MAXDIFF_0_26) > 0) return;

		byte[] ks = ctx.keystream;

		// decipher and filter by comparing with remaining contiguous plaintext forward
		Cipher cForward = cipA.set(xlist[7], ylist[7], zlist[7]);
		cForward.update(ctx.plain[index+7]);

		byte[] b = ctx.plain;
		for (int i = index + 8; i < b.length; i++) {
			if (((ks[i] ^ cForward.keystream()) & 0xFF) != 0) return;
			cForward.update(b[i]);
		}

		int iBackward = ctx.plainOffset;

		// and backward
		Cipher cBackward = cipB.set(x, ylist[3], zlist[3]);
		b = ctx.cipher;
		for (int i = index + 2; i >= 0; i--) {
			cBackward.updateBackward(b[i + iBackward]);
			if (((ks[i] ^ cBackward.keystream()) & 0xFF) != 0) return;
		}

		// continue filtering with extra plaintext
		if (ctx.extraPlainsBefore != null) {
			// in [0, offset), reverse
			for (IntMap.Entry<byte[]> extra : ctx.extraPlainsBefore) {
				byte[] plains = extra.getValue();
				int off = extra.getIntKey() + plains.length;

				cBackward.updateBackward(b, iBackward, off);
				iBackward = off;

				for (int i = plains.length - 1; i >= 0; i--) {
					byte c = b[--iBackward];

					cBackward.updateBackward(c);
					int p = c ^ cBackward.keystream() ^ plains[i];

					if ((p & 0xFF) != 0) return;
				}
			}
		}
		if (ctx.extraPlainsAfter != null) {
			int iForward = ctx.plainOffset + ctx.plain.length;
			// in (offset, ∞)
			for (IntMap.Entry<byte[]> extra : ctx.extraPlainsAfter) {
				int off = extra.getIntKey();

				cForward.update(b, iForward, off);
				iForward = off;

				for (byte plain : extra.getValue()) {
					int p = b[iForward++] ^ cForward.keystream();
					cForward.update(p);

					if (((p ^ plain) &0xFF) != 0) return;
				}
			}
		}

		// all tests passed so the keys are (probably) found

		// get the keys associated with the initial state
		cBackward.updateBackward(ctx.cipher, iBackward, 0);

		ctx.solutionFound(cBackward);
	}

	@Override
	public void execute() {
		try {
			searchZ(7);
		} catch (FastFailException ignored) {}
		ctx.progress.increment(1);
	}

	@Override
	public boolean isCancelled() {
		return ctx.interrupt;
	}
}