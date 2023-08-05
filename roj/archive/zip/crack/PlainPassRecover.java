package roj.archive.zip.crack;

import roj.RequireUpgrade;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.concurrent.task.ITask;
import roj.io.FastFailException;
import roj.ui.ProgressBar;
import roj.util.ByteList;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj234
 * @since 2022/11/13 0013 17:52
 */
@RequireUpgrade
public class PlainPassRecover implements Macros, ITask {
	final int[] xlist;
	final int[] ylist;
	final int[] zlist;

	// 256
	final MyBitSet zm1_24_32;
	final MyBitSet z0_16_32;
	private int x0;

	private final List<Cipher> tmp = new SimpleList<>();
	private int cipherId = -1;

	final byte[] charset;
	final ByteList pass = new ByteList(6);

	PlainPassRecover(Cipher keys, byte[] charset) {
		this.charset = charset;

		int[] x = new int[7], y = new int[7], z = new int[7];
		zm1_24_32 = new MyBitSet(1<<8);
		z0_16_32 = new MyBitSet(1<<16);

		// initialize target X, Y and Z values
		x[6] = keys.x;
		y[6] = keys.y;
		z[6] = keys.z;

		// derive Y5
		y[5] = (y[6] - 1) * MulTable.MULTINV - (x[6] & 0xFF);

		// derive more Z bytes
		for(int i = 6; 1 < i; i--)
			z[i-1] = CrcTable.crc32inv(z[i], y[i] >>> 24);

		// precompute possible Z0[16,32) and Z{-1}[24,32)
		for(byte c5 : charset) {
			x[5] = CrcTable.crc32inv(x[6], c5);
			y[4] = (y[5] - 1) * MulTable.MULTINV - (x[5] & 0xFF);
			z[3] = CrcTable.crc32inv(z[4], y[4] >>> 24);

			for(byte c4 : charset) {
				x[4] = CrcTable.crc32inv(x[5], c4);
				y[3] = (y[4] - 1) * MulTable.MULTINV - (x[4] & 0xFF);
				z[2] = CrcTable.crc32inv(z[3], y[3] >>> 24);
				z[1] = CrcTable.crc32inv(z[2], 0);
				z[0] = CrcTable.crc32inv(z[1], 0);

				z0_16_32.add(z[0] >>> 16);
				zm1_24_32.add(CrcTable.crc32inv(z[0], 0) >>> 24);
			}
		}

		this.xlist = x;
		this.ylist = y;
		this.zlist = z;
	}

	PlainPassRecover(Manager r) {
		this.charset = r.charset;
		this.concOwner = r;

		this.z0_16_32 = r.z0_16_32;
		this.zm1_24_32 = r.zm1_24_32;

		this.xlist = r.xlist.clone();
		this.ylist = r.ylist.clone();
		this.zlist = r.zlist.clone();
	}

	private Cipher getCipher() {
		if (++cipherId >= tmp.size()) {
			Cipher c;
			tmp.add(c = new Cipher());
			return c;
		}
		return tmp.get(cipherId);
	}

	boolean tryShort() {
		Cipher cip = getCipher().set();

		try {
			for (int i = 6; i >= 0; i--) {
				if (recover6(cip)) {
					pass.wIndex(i);
					return true;
				}

				cip.updateBackwardPlain(charset[0]);
			}
		} finally {
			cipherId--;
		}

		return false;
	}

	boolean tryLonger(Cipher c, int length) {
		if(concOwner != null && concOwner.found) throw new FastFailException("fin");

		Cipher tmp = getCipher();

		try {
			if(length == 7) {
				if(!zm1_24_32.contains(c.z >>> 24)) return false;

				for(byte pi : charset) {
					tmp.set(c.x,c.y,c.z).update(pi);

					if(recover6(tmp)) {
						pass.preInsert(0, 1);
						pass.put(0, pi);
						return true;
					}
				}
			} else {
				for(byte pi : charset) {
					tmp.set(c.x,c.y,c.z).update(pi);

					if(tryLonger(tmp, length-1)) {
						pass.preInsert(0, 1);
						pass.put(0, pi);
						return true;
					}
				}
			}
		} finally {
			cipherId--;
		}

		return false;
	}

	private boolean recover6(Cipher initial) {
		// check compatible Z0[16,32)
		if(!z0_16_32.contains(initial.z >>> 16)) return false;

		// initialize starting X, Y and Z values
		xlist[0] = x0 = initial.x;
		ylist[0] = initial.y;
		zlist[0] = initial.z;

		// complete Z values and derive Y[24,32) values
		for(int i = 1; i <= 4; i++) {
			ylist[i] = CrcTable.getYi_24_32(zlist[i], zlist[i-1]);
			zlist[i] = CrcTable.crc32(zlist[i-1], ylist[i] >>> 24);
		}

		// recursively complete Y values and derive password
		return searchY(5);
	}

	private boolean searchY(int i) {
		// the Y-list is not complete so generate Y{i-1} values
		if (i != 1) {
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

					if (searchY(i - 1)) return true;
				}
			}
		} else {
			// the Y-list is complete so check if the corresponding X-list is valid

			// only the X1 LSB was not set yet, so do it here
			xlist[1] = (ylist[1] - 1) * MulTable.MULTINV - ylist[0];
			if((xlist[1] & MASK_8_32) != 0) return false;

			byte[] p = pass.list;
			// complete X values and derive password
			for(int j = 5; j >= 0; j--) {
				int xi_xor_pi = CrcTable.crc32inv(xlist[j+1], 0);
				p[j] = (byte) (xi_xor_pi ^ xlist[j]);
				xlist[j] = xi_xor_pi ^ p[j];
			}

			// the password is successfully recovered
			if(xlist[0] == x0) {
				pass.wIndex(6);
				return true;
			}
		}
		return false;
	}

	/// Try to recover the password associated with the given keys
	static byte[] recoverPassword(Cipher c, byte[] charset, int minLength, int maxLength) {
		Manager w = new Manager(c, charset);

		for(int length = minLength; length <= maxLength; length++) {
			if(length <= 6) {
				if(w.tryShort()) return w.pass.toByteArray();
				length = 6;
			} else if (length < 11) {
				if (w.tryLonger(new Cipher().set(), length)) return w.pass.toByteArray();
			} else {
				// same as above, but in a parallel loop

				int charsetSize = charset.length;
				int total = charsetSize * charsetSize;

				w.ep.setName("长度 " + length);
				w.compute(total);

				// bruteforce two characters to have many tasks for each CPU thread and share work evenly
                for (int i = 0; i < total; i++) {
					Cipher init = new Cipher().set();
					init.update(charset[i / charsetSize]);
					init.update(charset[i % charsetSize]);

					PlainPassRecover copy = new PlainPassRecover(w);
					copy.concCip = init;
					copy.concI = i;
					copy.concLen = length;

					Main.pool.pushTask(copy);
				}
				Main.pool.awaitFinish();

				if (w.found) return w.kill();
			}
		}

		return w.kill();
	}

	Manager concOwner;
	Cipher concCip;
	int concI, concLen;

	@Override
	public void execute() throws Exception {
		try {
			if (tryLonger(concCip, concLen - 2)) {
				ByteList p = concOwner.pass;
				p.clear();
				p.put(charset[concI / charset.length])
				 .put(charset[concI % charset.length])
				 .put(pass);
				concOwner.found = true;
			}
		} catch (FastFailException ignored) {}
		concOwner.finished.increment();
	}

	@Override
	public boolean isCancelled() {
		return concOwner.found;
	}

	static final class Manager extends PlainPassRecover implements Runnable {
		LongAdder finished = new LongAdder();
		int total;
		boolean found, kill;
		Thread thread;
		ProgressBar ep = new ProgressBar("");

		Manager(Cipher keys, byte[] charset) {
			super(keys, charset);
			ep.setUnit("H");
		}

		@Override
		public void run() {
			while (!kill && !found) {
				long sum = 0;
				do {
					long delta = finished.sumThenReset();
					sum += delta;
					LockSupport.parkNanos(50_000_000);
					if (found) break;
					ep.setPrefix(sum + "/" + total);
					ep.update(sum / (double)total, (int) delta);
					if (Thread.interrupted()) {
						sum = 0;
					}
				} while (sum < total);

				finished.reset();
				ep.end("完成");
			}
		}

		public void compute(int total) {
			finished.reset();
			this.total = total;
			if (thread == null) {
				Thread t = thread = new Thread(this);
				t.setDaemon(true);
				t.start();
			} else {
				thread.interrupt();
			}
		}

		byte[] kill() {
			kill = true;
			ep.end("完成");
			if (thread != null) thread.interrupt();
			return found ? pass.toByteArray() : null;
		}
	}
}
