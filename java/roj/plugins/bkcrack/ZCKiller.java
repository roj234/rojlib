package roj.plugins.bkcrack;

import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.RSegmentTree;
import roj.collect.SimpleList;
import roj.concurrent.TaskExecutor;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.ui.EasyProgressBar;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/12 17:42
 */
final class ZCKiller {
	static final int ENCRYPTION_HEADER_SIZE = 12;

	ZCKiller(byte[] cipher, IntMap<byte[]> knownPlains) {
		RSegmentTree<RSegmentTree.Wrap<byte[]>> unioner = new RSegmentTree<>(10, false, 0);

		int total = 0;
		for (IntMap.Entry<byte[]> entry : knownPlains.selfEntrySet()) {
			int offset = entry.getIntKey();
			if (offset < -12) throw new IllegalArgumentException("offset < -12");

			byte[] plain = entry.getValue();

			offset += ENCRYPTION_HEADER_SIZE;
			if (offset + plain.length > cipher.length)
				throw new IllegalArgumentException("plains offset " + offset + " exceeds ciphertext boundary");

			total += plain.length;

			unioner.add(new RSegmentTree.Wrap<>(plain, offset, offset+plain.length));
		}

		if(total < Solver.CIPHER_KEY_SIZE) {
			throw new IllegalArgumentException("not enough plain (" + total + ", minimum " + Solver.CIPHER_KEY_SIZE + ") " +
				"there will be " + (1L << ((Solver.CIPHER_KEY_SIZE-total)<<3)) + " results");
		}

		List<IntMap.Entry<byte[]>> merged = new SimpleList<>();

		ByteList tmp = IOUtil.getSharedByteBuf();
		unioner.mergeConnected((list, length) -> {
			tmp.clear();
			for (int j = 0; j < list.size(); j++) {
				RSegmentTree.Wrap<byte[]> w = Helpers.cast(list.get(j));
				tmp.put(w.sth);
			}
			merged.add(new IntMap.Entry<>((int) list.get(0).startPos(), tmp.toByteArray()));
		});

		merged.sort((o1, o2) -> Integer.compare(o1.getValue().length, o2.getValue().length));
		// longest
		IntMap.Entry<byte[]> longest = merged.remove(merged.size() - 1);

		int plainOff = longest.getIntKey();
		byte[] plain = longest.getValue();

		if(plain.length < Solver.MIN_CONTIGUOUS_PLAIN_LENGTH)
			throw new IllegalArgumentException("not enough contiguous plain (" + (longest.getValue().length) + ", minimum " + Solver.MIN_CONTIGUOUS_PLAIN_LENGTH + ")");

		// shift offsets to absolute values
		this.plainOffset = plainOff;
		this.plain = plain;
		this.cipher = cipher;
		// compute keystream
		keystream = new byte[plain.length];
		for (int i = 0; i < plain.length; i++) {
			keystream[i] = (byte) (plain[i] ^ cipher[i+plainOffset]);
		}

		merged.sort((o1, o2) -> Integer.compare(o1.getIntKey(), o2.getIntKey()));

		for (int i = 0; i < merged.size(); i++) {
			if (merged.get(i).getIntKey() > plainOff) {
				if (i > 0) extraPlainsBefore = merged.subList(0, i);
				extraPlainsAfter = i == 0 ? merged : merged.subList(i, merged.size());
				break;
			}
		}
		if (extraPlainsAfter == null) extraPlainsBefore = merged;
		if (extraPlainsBefore != null) ArrayUtil.inverse(extraPlainsBefore);
	}

	byte[] cipher, plain, keystream;
	int plainOffset;

	List<IntMap.Entry<byte[]>> extraPlainsBefore, extraPlainsAfter;

	boolean stopOnFirstKey;
	boolean interrupt;
	EasyProgressBar progress;

	List<Cipher> solutions = new SimpleList<>();

	synchronized void solutionFound(Cipher cipher) {
		solutions.add(cipher.copy(new Cipher()));
		if (stopOnFirstKey) {
			interrupt = true;
			throw new FastFailException("First Key Found");
		} else {
			System.out.println(cipher);
		}
	}

	List<Cipher> find(TaskExecutor th) {
		var bar = progress = new EasyProgressBar("进度");

		bar.set("进度: Z-创建");
		Zreduction zr = new Zreduction(keystream);
		if(keystream.length > Solver.MIN_CONTIGUOUS_PLAIN_LENGTH) {
			bar.set("进度: Z-筛选");
			zr.filter();
		}

		bar.set("进度: Z-生成");
		zr.generate();

		int zIndex = zr.getIndex();
		IntList candidates = zr.getCandidates();
		bar.addTotal(candidates.size());

		solutions.clear();

		for (int i = 0; i < candidates.size(); i++) {
			th.submit(new Solver(this, zIndex, candidates.get(i)));
		}

		th.awaitFinish();

		bar.end("完成");
		progress = null;
		return solutions;
	}
}