package roj.util;

import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.word.ITokenizer;
import roj.io.CorruptedInputException;
import roj.io.buf.NativeArray;
import roj.text.Appender;
import roj.text.TextUtil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:08
 */
public class BsDiff {
	public static final class Diff {
		public static final byte SAME = 0, CHANGE = 1, INSERT = 2, DELETE = 3;
		public final byte type;
		public final int leftOff, rightOff, len;
		public int advance;

		public static Diff link(Diff a, Diff next) {
			a.next = next;
			next.prev = a;
			return next;
		}

		private Diff(byte type, int leftOff, int rightOff, int len) {
			this.type = type;
			this.leftOff = leftOff;
			this.rightOff = rightOff;
			this.len = len;
		}

		public static Diff same(int leftOff, int rightOff, int len) { return new Diff(SAME, leftOff, rightOff, len); }
		public static Diff change(int leftOff, int rightOff, int len) { return new Diff(CHANGE, leftOff, rightOff, len); }
		public static Diff insert(int rightOff, int len) { return new Diff(INSERT, -1, rightOff, len); }
		public static Diff delete(int leftOff, int len) { return new Diff(DELETE, leftOff, -1, len); }

		Diff prev, next;
	}

	public BsDiff() {}
	public BsDiff(BsDiff prev) { sfx = prev.sfx; left = prev.left; }

	private byte[] left;
	private int[] sfx;

	public void setLeft(byte[] left) {
		this.left = left;
		int size = left.length;

		int[] bucket = ArrayCache.getIntArray(256, 256);
		// count
		for (int i = 0; i < left.length; i++) bucket[toPositive(left[i])]++;
		// cumulative sum
		for (int i = 1; i < bucket.length; i++) bucket[i] += bucket[i-1];
		// move
		System.arraycopy(bucket, 0, bucket, 1, bucket.length-1);
		bucket[0] = 0;

		sfx = new int[size];
		for (int i = 0; i < size; i++) {
			int n = toPositive(left[i]);
			int v = bucket[n]++;
			sfx[v] = i;
		}

		for (int i = 1; i < bucket.length; ++i) {
			if (bucket[i] != bucket[i-1] + 1) continue;
			sfx[bucket[i]-1] = -1;
		}

		if (bucket[0] == 1) sfx[0] = -1;


		int[] V = ArrayCache.getIntArray(size, 0);
		for (int i = 0; i < size; ++i) V[i] = bucket[toPositive(left[i])] - 1;

		ArrayCache.putArray(bucket);
		bucket = null;

		int h = 1;
		while (sfx[0] != -size) {
			int j = 0, len = 0;
			while (j < size) {
				if (sfx[j] < 0) {
					len -= sfx[j];
					j -= sfx[j];
					continue;
				}

				if (len > 0) sfx[j - len] = -len;

				int groupLen = V[sfx[j]] - j + 1;
				split(sfx, V, j, groupLen, h);

				j += groupLen;
				len = 0;
			}

			if (len > 0) sfx[size - len] = -len;

			h <<= 1;
			if (h < 0) break;
		}

		for (int i = 0; i < size; ++i) sfx[V[i]] = i;

		ArrayCache.putArray(V);
	}
	private static void split(int[] I, int[] V, int start, int len, int h) {
		int temp;
		if (len < 16) {
			int i = start;
			int k;
			while (i < start + len) {
				int j;
				int X = getV(V, I[i] + h);
				k = i + 1;
				for (j = i + 1; j < start + len; ++j) {
					if (getV(V, I[j] + h) < X) {
						X = getV(V, I[j] + h);
						k = i;
					}
					if (getV(V, I[j] + h) != X) continue;
					temp = I[j];
					I[j] = I[k];
					I[k] = temp;
					++k;
				}
				for (j = i; j < k; ++j) {
					V[I[j]] = k - 1;
				}
				if (k == i + 1) {
					I[i] = -1;
				}
				i = k;
			}
			return;
		}
		int X = getV(V, I[start + len / 2] + h);
		int smallCount = 0;
		int equalCount = 0;
		for (int i = 0; i < len; ++i) {
			if (getV(V, I[start + i] + h) < X) {
				++smallCount;
				continue;
			}
			if (getV(V, I[start + i] + h) != X) continue;
			++equalCount;
		}

		int smallPos = start + smallCount;
		int equalPos = smallPos + equalCount;
		int i = start;
		int j = i + smallCount;
		int k = j + equalCount;
		while (i < smallPos) {
			if (getV(V, I[i] + h) < X) {
				++i;
				continue;
			}
			if (getV(V, I[i] + h) == X) {
				temp = I[i];
				I[i] = I[j];
				I[j] = temp;
				++j;
				continue;
			}
			temp = I[i];
			I[i] = I[k];
			I[k] = temp;
			++k;
		}
		while (j < equalPos) {
			if (getV(V, I[j] + h) == X) {
				++j;
				continue;
			}
			temp = I[j];
			I[j] = I[k];
			I[k] = temp;
			++k;
		}

		if (smallPos > start) split(I, V, start, smallPos - start, h);
		for (i = smallPos; i < equalPos; ++i) V[I[i]] = equalPos - 1;
		if (equalPos == smallPos + 1) I[smallPos] = -1;

		if (equalPos < start + len) split(I, V, equalPos, len - (equalPos - start), h);
	}
	private static int getV(int[] V, int pos) { return pos < V.length ? V[pos] : -1; }

	public void bsdiff(byte[] oldData, byte[] newData, DynByteBuf patch) {
		patch.putAscii("ENDSLEY/BSDIFF43").putIntLE(newData.length);
		setLeft(oldData);
		genPatch(newData, patch);
	}

	public void genPatch(byte[] right, DynByteBuf patch) {
		byte[] left = this.left;
		int leftLen = left.length, rightLen = right.length;

		int scan = 0, lastScan = 0;
		int pos = 0, lastPos = 0;
		int len = 0, lastOffset = 0;
		while (scan < rightLen) {
			int match = 0;
			int scsc = scan += len;
			while (scan < rightLen) {
				pos = search(right, scan, left, 0, leftLen);
				len = this.len;
				while (scsc < scan + len) {
					if (scsc + lastOffset < leftLen && left[scsc + lastOffset] == right[scsc]) {
						++match;
					}
					++scsc;
				}
				if (len == match && len != 0 || len > match + 8) break;
				if (scan + lastOffset < leftLen && left[scan + lastOffset] == right[scan]) {
					--match;
				}
				++scan;
			}
			if (len == match && scan != rightLen) continue;
			int f = 0;
			int F2 = 0;
			int lenF = 0;
			int i2 = 0;
			while (i2 < scan - lastScan && i2 < leftLen - lastPos) {
				if (right[lastScan + i2] == left[lastPos + i2]) {
					++f;
				}
				if (2 * f - ++i2 <= 2 * F2 - lenF) continue;
				F2 = f;
				lenF = i2;
			}
			int b = 0;
			int B = 0;
			int lenB = 0;
			if (scan < rightLen) {
				for (int i3 = 1; i3 < scan - lastScan + 1 && i3 < pos + 1; ++i3) {
					if (right[scan - i3] == left[pos - i3]) {
						++b;
					}
					if (2 * b - i3 <= 2 * B - lenB) continue;
					B = b;
					lenB = i3;
				}
			}
			int overlap = -1;
			if (lenF + lenB > scan - lastScan) {
				overlap = lastScan + lenF - (scan - lenB);
				int s = 0;
				int S = 0;
				int lenS = 0;
				for (int i4 = 0; i4 < overlap; ++i4) {
					if (left[lastPos + lenF - overlap + i4] == right[lastScan + lenF - overlap + i4]) {
						++s;
					}
					if (left[pos - lenB + i4] == right[scan - lenB + i4]) {
						--s;
					}
					if (s <= S) continue;
					S = s;
					lenS = i4;
				}
				lenF = lenF - overlap + lenS;
				lenB -= lenS;
			}

			patch.putIntLE(lenF)
				 .putIntLE(scan - lastScan - lenF - lenB)
				 .putIntLE(pos - lastPos - lenF - lenB);

			NativeArray range = patch.byteRangeW(lenF);
			for (int i = 0; i < lenF; ++i) range.set(i, toPositive(left[lastPos + i]) - toPositive(right[lastScan + i]));

			if (overlap == -1) patch.put(right, lastScan + lenF, scan - lastScan - lenF - lenB);

			lastPos = pos-lenB;
			lastScan = scan-lenB;
			lastOffset = pos-scan;
		}
	}
	public int getDiffLength(byte[] right, int maxDifference) {
		int diffBytes = 0;

		byte[] left = this.left;
		int leftLen = left.length, rightLen = right.length;

		int scan = 0, lastScan = 0;
		int pos = 0, lastPos = 0;
		int len = 0, lastOffset = 0;
		while (scan < rightLen) {
			int match = 0;
			int scsc = scan += len;
			while (scan < rightLen) {
				pos = search(right, scan, left, 0, leftLen);
				len = this.len;
				while (scsc < scan + len) {
					if (scsc + lastOffset < leftLen && left[scsc + lastOffset] == right[scsc]) {
						++match;
					}
					++scsc;
				}
				if (len == match && len != 0 || len > match + 8) break;
				if (scan + lastOffset < leftLen && left[scan + lastOffset] == right[scan]) {
					--match;
				}
				++scan;
			}
			if (len == match && scan != rightLen) continue;
			int f = 0;
			int F2 = 0;
			int lenF = 0;
			int i2 = 0;
			while (i2 < scan - lastScan && i2 < leftLen - lastPos) {
				if (right[lastScan + i2] == left[lastPos + i2]) {
					++f;
				}
				if (2 * f - ++i2 <= 2 * F2 - lenF) continue;
				F2 = f;
				lenF = i2;
			}
			int b = 0;
			int B = 0;
			int lenB = 0;
			if (scan < rightLen) {
				for (int i3 = 1; i3 < scan - lastScan + 1 && i3 < pos + 1; ++i3) {
					if (right[scan - i3] == left[pos - i3]) {
						++b;
					}
					if (2 * b - i3 <= 2 * B - lenB) continue;
					B = b;
					lenB = i3;
				}
			}
			int overlap = -1;
			if (lenF + lenB > scan - lastScan) {
				overlap = lastScan + lenF - (scan - lenB);
				int s = 0;
				int S = 0;
				int lenS = 0;
				for (int i4 = 0; i4 < overlap; ++i4) {
					if (left[lastPos + lenF - overlap + i4] == right[lastScan + lenF - overlap + i4]) {
						++s;
					}
					if (left[pos - lenB + i4] == right[scan - lenB + i4]) {
						--s;
					}
					if (s <= S) continue;
					S = s;
					lenS = i4;
				}
				lenF = lenF - overlap + lenS;
				lenB -= lenS;
			}

			for (int i = 0; i < lenF; ++i) {
				if (left[lastPos+i] != right[lastScan+i]) {
					diffBytes++;
				}
			}

			if (overlap == -1) diffBytes += scan - lastScan - lenF - lenB;

			if (diffBytes > maxDifference) return -1;

			lastPos = pos-lenB;
			lastScan = scan-lenB;
			lastOffset = pos-scan;
		}

		return diffBytes;
	}

	//region WIP
	public List<Diff> getDiff(byte[] right) {
		Diff head = Diff.insert(0,0), tail = head;

		byte[] left = this.left;
		int leftLen = left.length, rightLen = right.length;

		int scan = 0, lastScan = 0;
		int pos = 0, lastPos = 0;
		int len = 0, lastOffset = 0;
		while (scan < rightLen) {
			int match = 0;
			int scsc = scan += len;
			while (scan < rightLen) {
				pos = search(right, scan, left, 0, leftLen);
				len = this.len;
				while (scsc < scan + len) {
					if (scsc + lastOffset < leftLen && left[scsc + lastOffset] == right[scsc]) {
						++match;
					}
					++scsc;
				}
				if (len == match && len != 0 || len > match + 8) break;
				if (scan + lastOffset < leftLen && left[scan + lastOffset] == right[scan]) {
					--match;
				}
				++scan;
			}
			if (len == match && scan != rightLen) continue;
			int f = 0;
			int F2 = 0;
			int lenF = 0;
			int i2 = 0;
			while (i2 < scan - lastScan && i2 < leftLen - lastPos) {
				if (right[lastScan + i2] == left[lastPos + i2]) {
					++f;
				}
				if (2 * f - ++i2 <= 2 * F2 - lenF) continue;
				F2 = f;
				lenF = i2;
			}
			int b = 0;
			int B = 0;
			int lenB = 0;
			if (scan < rightLen) {
				for (int i3 = 1; i3 < scan - lastScan + 1 && i3 < pos + 1; ++i3) {
					if (right[scan - i3] == left[pos - i3]) {
						++b;
					}
					if (2 * b - i3 <= 2 * B - lenB) continue;
					B = b;
					lenB = i3;
				}
			}
			int overlap = -1;
			if (lenF + lenB > scan - lastScan) {
				overlap = lastScan + lenF - (scan - lenB);
				int s = 0;
				int S = 0;
				int lenS = 0;
				for (int i4 = 0; i4 < overlap; ++i4) {
					if (left[lastPos + lenF - overlap + i4] == right[lastScan + lenF - overlap + i4]) {
						++s;
					}
					if (left[pos - lenB + i4] == right[scan - lenB + i4]) {
						--s;
					}
					if (s <= S) continue;
					S = s;
					lenS = i4;
				}
				lenF = lenF - overlap + lenS;
				lenB -= lenS;
			}

			tail = Diff.link(tail, Diff.same(lastPos, lastScan, lenF));

			if (overlap == -1) {
				int myoff = lastScan + lenF;
				int len1 = scan - lenB - myoff;
				if (len1 > 0) tail = Diff.link(tail, Diff.insert(myoff, len1));
			}

			lastPos = pos-lenB;
			lastScan = scan-lenB;
			lastOffset = pos-scan;
		}

		return toRealDiff(right, head.next);
	}
	private List<Diff> toRealDiff(byte[] right, Diff in) {
		final int maxSame = 3;

		MyBitSet array = new MyBitSet();
		array.fill(left.length);

		int size = 0;
		Diff outHead = Diff.insert(0,0), out = outHead;

		while (in != null) {
			if (in.type != Diff.SAME) {
				out = Diff.link(out, in);
				size++;
			} else {
				array.removeRange(in.leftOff, in.leftOff+in.len);

				int i = in.leftOff;
				int j = in.rightOff;
				int max = i+in.len;
				int prevI = i;

				while (i < max) {
					if (left[i] != right[j]) {
						int len = i - prevI;
						if (len > 0) {
							out = Diff.link(out, Diff.same(prevI, j-len, len));
							size++;
							prevI = i;
						}

						int same = 0;
						while (i < max) {
							if (left[i] == right[j]) {
								if (++same > maxSame) break;
							} else {
								same = 0;
							}

							i++;
							j++;
						}

						len = i - prevI - same;
						if (len > 0) {
							out = Diff.link(out, Diff.change(prevI, j-len, len));
							size++;
							prevI = i;
						}
					}

					i++;
					j++;
				}

				int len = i - prevI;
				if (len > 0) {
					out = Diff.link(out, Diff.same(prevI, j-len, len));
					size++;
				}
			}

			in = in.next;
		}

		IntMap<List<Diff>> byOffset = new IntMap<>(size);
		Diff d = outHead.next;
		while (d != null) {
			if (d.leftOff >= 0) {
				byOffset.computeIfAbsentIntS(d.leftOff, SimpleList::new).add(d);
			}
			d = d.next;
		}

		int i = 0;
		while (true) {
			i = array.nextTrue(i);
			if (i < 0) break;
			int j = array.nextFalse(i+1);

			List<Diff> id = null;
			for (int k = 0; k < 200; k++) {
				id = byOffset.get(i+k);
				if (id != null) break;
				id = byOffset.get(i-k);
				if (id != null) break;
			}

			if (id == null || id.isEmpty()) {
				size++;
				out = Diff.link(out, Diff.delete(i, j-i));
			} else {
				int min = 999;
				int bestI = -1;
				Diff r = null;
				for (int k = 0; k < id.size(); k++) {
					d = id.get(k);
					int dt = Math.abs(d.len-j+i);
					if (dt < min) {
						bestI = k;
						if (dt == 0) {
							assert d.type == Diff.INSERT;
							r = Diff.change(i, d.rightOff, d.len);
							break;
						}
						min = dt;
					}
				}

				if (bestI < 0) {
					System.err.println("cannot match "+i);
					i = j;
					continue;
				}
				if (r == null) r = Diff.delete(i, j-i);

				d = id.remove(bestI);

				r.next = d.next;
				r.prev = d.prev;
				if (d.prev != null) d.prev.next = r;
				else outHead = r;
				if (d.next != null) d.next.prev = r;
				else out = r;
			}

			i = j;
		}

		SimpleList<Diff> list = new SimpleList<>(size);
		d = outHead.next;
		while (d != null) {
			list.add(d);
			d = d.next;
		}
		return list;
	}
	public void toMarkdown(byte[] left, byte[] right, List<Diff> diffs, Appender sb) throws IOException {
		Charset cs = Charset.forName("GB18030");

		System.out.println(diffs.size());
		long l = 0;
		for (Diff diff : diffs) {
			l += diff.len;
		}
		System.out.println(TextUtil.scaledNumber(l)+"B");

		ByteList buf1 = new ByteList(), buf2 = new ByteList();
		int type = Diff.SAME;
		for (Diff diff : diffs) {
			if (diff.type != type) {
				finishBlock(sb, buf1, buf2, type, cs);
				type = diff.type;
			}

			switch (diff.type) {
				default: buf1.put(left, diff.leftOff, diff.len); break;
				case Diff.CHANGE:
					buf1.put(left, diff.leftOff, diff.len);
					buf2.put(right, diff.rightOff, diff.len);
				break;
				case Diff.INSERT: buf1.put(right, diff.rightOff, diff.len); break;
			}
		}

		finishBlock(sb, buf1, buf2, type, cs);
	}
	private static void finishBlock(Appender sb, ByteList buf1, ByteList buf2, int type, Charset cs) throws IOException {
		switch (type) {
			default: case Diff.SAME: sb.append(new String(buf1.list, 0, buf1.length(), cs)); break;
			case Diff.CHANGE: sb.append("<i title=\"").append(ITokenizer.addSlashes(new String(buf1.list, 0, buf1.length(), cs))).append("\">")
								.append(new String(buf2.list, 0, buf2.length(), cs)).append("</i>"); break;
			case Diff.INSERT: sb.append("<b>").append(new String(buf1.list, 0, buf1.length(), cs)).append("</b>"); break;
			case Diff.DELETE: sb.append("<del>").append(new String(buf1.list, 0, buf1.length(), cs)).append("</del>"); break;
		}
		buf1.clear();
		buf2.clear();
	}
	// endregion

	private int len;
	private int search(byte[] right, int rightOff, byte[] left, int leftOff, int leftEnd) {
		loop:
		while (true) {
			int leftLen = leftEnd - leftOff;
			if (leftLen < 2) {
				int len1 = matchLen(left, sfx[leftOff], right, rightOff);
				if (leftLen > 0 && leftEnd < sfx.length) {
					int len2 = matchLen(left, sfx[leftEnd], right, rightOff);
					if (len2 >= len1) {
						len = len2;
						return sfx[leftEnd];
					}
				}

				len = len1;
				return sfx[leftOff];
			}

			// 二分查找 log2(n)
			int mid = leftLen/2 + leftOff;

			int i = sfx[mid], j = rightOff;
			int max = i + Math.min(left.length-i, right.length-rightOff);

			while (i < max) {
				if (left[i] < right[j]) {
					// 小于
					leftOff = mid;
					continue loop;
				}
				if (left[i] > right[j]) break;

				i++;
				j++;
			}

			// 大于和等于
			leftEnd = mid;
		}
	}
	private static int matchLen(byte[] lData, int lStart, byte[] rData, int rStart) {
		int i = lStart;
		while (i < lData.length && rStart < rData.length && lData[i] == rData[rStart]) {
			i++;
			rStart++;
		}
		return i - lStart;
	}

	public static long bspatch(DynByteBuf old, DynByteBuf patch, DynByteBuf out) throws IOException {
		if (!patch.readAscii(16).equals("ENDSLEY/BSDIFF43")) {
			throw new CorruptedInputException("header missing");
		}

		int outputSize = patch.readIntLE();
		long wrote = bspatch1(old, patch, out);
		if (wrote != outputSize) throw new IOException("patch: invalid output size");

		return wrote;
	}
	public static long bspatch1(DynByteBuf old, DynByteBuf patch, DynByteBuf out) throws IOException {
		long wrote = 0;

		while (patch.isReadable()) {
			int diffLen = patch.readIntLE();
			int extraLen = patch.readIntLE();
			int advance = patch.readIntLE();

			if (old.readableBytes() < diffLen) throw new IOException("in: no " + diffLen + " bytes readable");
			if (patch.readableBytes() < diffLen+extraLen) throw new CorruptedInputException("patch: no " + diffLen + " bytes readable");
			if (!out.ensureWritable(diffLen)) throw new IOException("out: no " + diffLen + " bytes writable");

			Object arIn = old.array();
			long adIn = old._unsafeAddr() + old.rIndex;

			Object arPat = patch.array();
			long adPat = patch._unsafeAddr() + patch.rIndex;

			Object arOut = out.array();
			long adOut = out._unsafeAddr() + out.wIndex();

			old.rIndex += diffLen;
			patch.rIndex += diffLen;
			out.wIndex(out.wIndex()+diffLen);

			wrote += diffLen;

			while (diffLen-- > 0) {
				u.putByte(arOut, adOut++,
					(byte)toNormal(
						toPositive(u.getByte(arIn, adIn++)) - u.getByte(arPat, adPat++)
								  )
						 );
			}

			out.put(patch, extraLen);
			patch.rIndex += extraLen;
			wrote += extraLen;

			old.rIndex += advance;
		}

		return wrote;
	}

	// (b & 0xFF) ^ 0x80
	private static int toPositive(byte b) { return b + 128; }
	private static int toNormal(int b) { return b - 128; }
}
