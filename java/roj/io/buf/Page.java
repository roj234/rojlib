package roj.io.buf;

import roj.collect.IntList;
import roj.math.MathUtils;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.TimSortForEveryone;

import java.util.Arrays;

import static roj.reflect.Unaligned.U;
import static roj.text.TextUtil.scaledNumber1024;

/**
 * @author Roj234
 * @since 2023/11/7 11:32
 */
public sealed class Page {
	private static final byte LONG_SHIFT = 6, MINIMUM_SHIFT = 3;
	public static final long MINIMUM_MASK = (1L << MINIMUM_SHIFT) - 1;

	private static final char BITMAP_FREE = 'o', BITMAP_USED = '1', BITMAP_SUBPAGE = 'S', BITMAP_PREFIX = 'p';

	@FunctionalInterface
	public interface MemoryMover { void moveMemory(long oldPos, long newPos, long len); }

	// 不要用非Public的方法
	public static Page create(long capacity) {
		if (capacity < 1 << (MINIMUM_SHIFT+LONG_SHIFT))
			throw new IllegalArgumentException("Capacity("+capacity+") too small");

		int targetDepth = Long.numberOfTrailingZeros(MathUtils.getMin2PowerOf(capacity)) - LONG_SHIFT;
		int i = MINIMUM_SHIFT;
		while (i < targetDepth) i += LONG_SHIFT;

		return new Top(i, capacity);
	}

	// 节约内存
	// Bitset: 8byte / 512B
	// Page:  21byte / 512B :: 12 + 8 + 1

	final byte childId;
	long bitmap;

	Page(int childId) { this.childId = (byte) childId; }

	@Override
	public final String toString() {
		CharList sb = new CharList();
		return toString(sb, 0).toStringAndFree();
	}
	CharList toString(CharList sb, int depth) {
		sb.append("[ depth = "+MINIMUM_SHIFT+" (min)");
		if (bitmap == 0) return sb.append(" (empty) ]");
		sb.append('\n');

		sb.padEnd(' ', depth);
		scaledNumber1024(sb.append("  usage = "), usedSpace());
		scaledNumber1024(sb.append(" / "), totalSpace())
			.append('\n');

		sb.padEnd(' ', depth);
		CharList bin = new CharList(Long.toBinaryString(bitmap)).replace('0', BITMAP_FREE).replace('1', BITMAP_USED);
		sb.append("  mapping = ").padEnd(BITMAP_FREE, 64-bin.length()).append(bin).append('\n');
		bin._free();

		sb.padEnd(' ', depth);
		return sb.append(']');
	}

	public final long alloc(long len) {
		assert validOffLen(0, len);
		long s = malloc(align(len));
		assert validate();
		return s;
	}
	public final boolean alloc(long off, long len) {
		assert validOffLen(off, len);
		if (off+len > totalSpace()) return false;
		boolean ok = malloc(off, align(len));
		assert validate();
		return ok;
	}
	public final void free(long off, long len) {
		assert validOffLen(off, len);
		assert off+len <= totalSpace() : "mfree("+off+","+len+")";
		mfree(off, align(len));
		assert validate();
	}
	public final boolean allocBefore(long off, long len, long more) {
		assert validOffLen(off, len);
		boolean ok = malloc(align(off - more), align(more));
		validate();
		return ok;
	}
	public final boolean allocAfter(long off, long len, long more) {
		assert validOffLen(off, len);
		long realLen = align(len);

		more -= (realLen-len);
		if (more <= 0) return true;

		boolean ok = malloc(off + realLen, align(more));
		assert validate();
		return ok;
	}

	private static boolean validOffLen(long off, long len) {
		if (off < 0) throw new AssertionError("off < 0: " + off);
		if (off != align(off)) throw new AssertionError("un-aligned offset " + off);
		if (len <= 0) throw new AssertionError("len <= 0: " + len);
		return true;
	}

	long malloc(long len) {
		int blocks = (int) ((len+((1L << MINIMUM_SHIFT) - 1)) >>> MINIMUM_SHIFT);
		assert blocks <= 64;
		long flag = blocks == 64 ? -1L : (1L << blocks)-1;

		int offset = 0;
		while ((bitmap & flag) != 0) {
			if (offset+blocks > 63) return -1;
			flag <<= 1;
			offset++;
		}

		bitmap |= flag;
		return (long) offset << MINIMUM_SHIFT;
	}
	boolean malloc(long off, long len) {
		int bitFrom = (int) (off >>> MINIMUM_SHIFT);
		int bitTo = (int) ((off+len+((1L << MINIMUM_SHIFT) - 1)) >>> MINIMUM_SHIFT);

		long flag = BIT(bitFrom, bitTo);
		if ((bitmap&flag) != 0) return false;

		bitmap |= flag;
		return true;
	}
	void mfree(long off, long len) {
		int bitFrom = (int) (off >>> MINIMUM_SHIFT);
		int bitTo = (int) ((off+len+((1L << MINIMUM_SHIFT) - 1)) >>> MINIMUM_SHIFT);

		long flag = BIT(bitFrom, bitTo);
		assert ((bitmap)&flag) == flag : Long.toBinaryString(bitmap)+" & ~BIT["+bitFrom+", "+bitTo+"): space not allocated";

		bitmap ^= flag;
	}

	public long usedSpace() { return (long) Long.bitCount(bitmap) << MINIMUM_SHIFT; }
	public long freeSpace() { return (long) (64 - Long.bitCount(bitmap)) << MINIMUM_SHIFT; }
	public long totalSpace() { return 1L << (MINIMUM_SHIFT+LONG_SHIFT); }

	long headEmpty() { return (long) Long.numberOfTrailingZeros(bitmap) << MINIMUM_SHIFT; }
	long tailEmpty() { return (long) Long.numberOfLeadingZeros(bitmap) << MINIMUM_SHIFT; }

	boolean validate() { return true; }

	public static long align(long n) { return 0 == (n&MINIMUM_MASK) ? n : (n|MINIMUM_MASK) + 1; }
	public static long BIT(int bitFrom, int bitTo) {
		assert bitTo <= 64 && bitFrom < 64 && bitFrom < bitTo : "param=["+bitFrom+","+bitTo+']';

		long bits = -1L << bitFrom;
		return bitTo >= 64 ? bits : bits & ((1L << bitTo)-1);
	}

	public void compress(MemoryMover mover) { throw new UnsupportedOperationException("use PageEx"); }
	void getBlocks(long off, IntList list) {
		int state = 0;
		int i = 0;
		long map = bitmap;
		while (map != 0) {
			if ((map & 1) != state) {
				addLong(list, off + ((long)i << MINIMUM_SHIFT));
				state ^= 1;
			}

			map >>>= 1;
			i++;
		}
		if (state == 1) addLong(list, off + ((long)i << MINIMUM_SHIFT));
	}
	static void addLong(IntList l, long v) {
		int size = l.size();
		l.ensureCapacity(2+size*2);
		U.putLong(l.getRawArray(), Unaligned.ARRAY_INT_BASE_OFFSET + 4L * size, v);
		l.setSize(size+2);
	}
	static long getLong(IntList l, int i) { return U.getLong(l.getRawArray(), Unaligned.ARRAY_INT_BASE_OFFSET + 4L * i); }

	// 60bytes
	private static sealed class Ext extends Page {
		final byte SHIFT;

		private static final Page[] EMPTY_PAGES = new Page[0];
		private Page[] child = EMPTY_PAGES;
		private int childCount;

		// false: 还能往后增长, true: 只能用到当前块的结尾
		private boolean prefixLocked;
		private long prefix;

		long free;

		Ext(int shift, int childId) {
			super(childId);
			SHIFT = (byte) shift;
			free = 1L << (shift+LONG_SHIFT);
		}

		CharList toString(CharList sb, int depth) {
			sb.append("[ depth = ").append(SHIFT).append('\n');

			sb.padEnd(' ', depth);
			scaledNumber1024(sb.append("  usage = "), usedSpace());
			scaledNumber1024(sb.append(" / "), totalSpace())
				.append(" (").append(usedSpace()).append(" / ").append(totalSpace()).append(")")
				.append("  ").append(TextUtil.toFixed((1 - (double) free / totalSpace()) * 100, 2)).append("%\n");

			if (prefix > 0) {
				sb.padEnd(' ', depth);
				sb.append("  prefix = ").append(prefix);
				sb.append('\n');
			}

			if ((bitmap) != 0) {
				int cap = bitmapCapacity();

				for (int i = 0; i < depth; i++) sb.append(' ');
				CharList bin = new CharList(cap).append(Long.toBinaryString(bitmap));
				bin.replace('0', BITMAP_FREE).replace('1', BITMAP_USED).padStart(BITMAP_FREE, cap-bin.length());

				int bitCount = (int) ((prefix+((1L << SHIFT) - 1)) >>> SHIFT);
				for (int i = 0; i < bitCount; i++) bin.set(i, BITMAP_PREFIX);

				for (int i = 0; i < childCount; i++) {
					int j = cap-child[i].childId-1;
					if (j < 0) j = 0;
					bin.set(j, bin.charAt(j) != BITMAP_USED ? 'E' : BITMAP_SUBPAGE);
				}

				sb.append("  mapping = ").append(bin).append('\n');
				bin._free();
			}

			for (int i = 0; i < childCount; i++) {
				sb.padEnd(' ', depth);
				sb.append("  child[").append(child[i].childId).append("] = ");
				child[i].toString(sb, depth+2);
				sb.append('\n');
			}

			sb.padEnd(' ', depth);
			return sb.append(']');
		}

		final long malloc(final long size) {
			if (free < size) return -1;

			// 快速分配, remove时再拆分 (好消息：FIFO不会拆分)
			long len = prefix;
			if (!prefixLocked) {
				prefix = align(len+size);
				free -= size;
				return len;
			} else if (len > 0) {
				int bitCount = (int) ((prefix+((1L << SHIFT) - 1)) >>> SHIFT);
				assert !(childCount > 0 && child[0].childId < bitCount) : "invalid prefix state";

				long remain = prefix&((1L << SHIFT) - 1);
				if (remain > 0 && size <= ((1L << SHIFT) - 1)-remain) {
					prefix = align(len+size);
					free -= size;
					return len;
				}
			}

			int block = (int) (size >>> SHIFT);
			if (block > 0) { // 大于1个完整的块
				lockPrefix(); // splitBitmap |= BIT[0,bitCount] , mergeSplit也有检查

				assert block <= 64;
				long flag = block == 64 ? -1L : (1L << block)-1;

				int offset = 0, maxOffset = bitmapCapacity()-block;
				long subSize = size&((1L << SHIFT) - 1);

				while (true) {
					notFound11:
					if ((bitmap&flag) == 0) {
						if (subSize == 0) {
							if (isAllEmpty(flag)) break;
						} else if (offset+block < bitmapCapacity() && isAllEmpty(flag^Long.lowestOneBit(flag))) {
							Page p;
							// 尝试不完整的分配
							if (!isAllEmpty(Long.lowestOneBit(flag))) {
								int i = get(offset);
								// failed, next iter
								if (i < 0 || (p = child[i]).tailEmpty() < subSize) break notFound11;

								long myOffset = p.tailEmpty();
								boolean ok = p.malloc(p.totalSpace() - myOffset, myOffset);
								assert ok;

								ok = goc(offset + block).malloc(0, align(((1L << SHIFT) - 1) + 1 - (myOffset - subSize)));
								assert ok;

								subSize = p.totalSpace() - myOffset;
								flag ^= Long.lowestOneBit(flag);
								break;
							}

							p = goc(offset + block);
							if (p.headEmpty() >= subSize) {
								boolean ok = p.malloc(0, subSize);
								assert ok;

								subSize = 0;
								break;
							}
						}
					}

					if (offset == maxOffset) return -1;
					flag <<= 1;
					offset ++;
				}

				assert (bitmap&flag) == 0;
				bitmap |= flag;
				free -= size;
				// 前对齐 以后可以尝试移动到SubPage结尾
				return ((long) offset << SHIFT) + subSize;
			} else {
				for (int i = 0; i < childCount; i++) {
					Page p = child[i];
					long off = p.malloc(size);
					if (off >= 0) return success(size, p, off);

					// 跨区alloc(而且优先！)
					off = p.tailEmpty();
					if (off > 0 && (bitmap & (1L << p.childId)) == 0) {
						if (i+1 < childCount && off + child[i+1].headEmpty() < size) continue;
						else if (p.childId == bitmapCapacity()-1) break;

						long off1 = p.totalSpace() - off;
						boolean ok = p.malloc(off1, off);
						assert ok : "tailEmpty() error: "+p+" returns "+off+" bytes tail empty but cannot allocate";

						ok = goc(p.childId+1).malloc(0, size-off);
						assert ok : "tailEmpty() error: "+goc(p.childId+1)+" returns "+off+" bytes head empty but cannot allocate";

						return success(size, p, off1);
					}
				}

				long space = ~bitmap;
				// 还有可用空间
				if (space != 0) {
					// later 切断【最短】的连续空间 ?
					int id = Long.numberOfTrailingZeros(Long.lowestOneBit(space));
					if (id >= bitmapCapacity()) return -2;
					Page p = goc(id);
					boolean ok = p.malloc(0, size);
					assert ok;
					return success(size, p, 0);
				}
			}

			// 内存过于碎片 （主要是只整理一级）
			return -2;
		}
		final boolean malloc(long off, long len) {
			if (free < len) return false;

			if (!prefixLocked && prefix == off) {
				prefix = align(prefix+len);
				free -= len;
				return true;
			}

			int bitFrom = (int) (off >>> SHIFT); // 3
			int bitTo = (int) ((off+len) >>> SHIFT); // 5

			long before =  off     &((1L << SHIFT) - 1);
			long after  = (off+len)&((1L << SHIFT) - 1);

			if (bitTo >= bitmapCapacity()) {
				if (bitTo > bitmapCapacity() || after != 0)
					return false;
			}

			if ((prefix+((1L << SHIFT) - 1)) >>> SHIFT > bitFrom) return false;
			lockPrefix();

			if (bitFrom >= bitTo) return goc(bitFrom).malloc(off&((1L << SHIFT) - 1), len);

			if (before != 0 && goc(bitFrom++).tailEmpty() < before) return false;

			long flag;
			if (bitFrom < bitTo) {
				flag = BIT(bitFrom, bitTo);
				if (!isAllEmpty(flag)) return false;
			} else {
				flag = 0;
			}

			if (after != 0 && !goc(bitTo).malloc(0, after)) return false;
			if (before != 0) {
				boolean ok = child[get(bitFrom-1)].malloc(before, ((1L << SHIFT) - 1) + 1 - before);
				assert ok;
			}

			bitmap |= flag;
			free -= len;
			return true;
		}
		final void mfree(long off, long len) {
			free += len;

			if (prefix > 0) {
				if (!prefixLocked && align(off+len) == prefix) {
					prefix = (prefix - len) & ~MINIMUM_MASK;
					return;
				}
				splitPrefix();
			}

			int bitFrom = (int) (off >>> SHIFT); // 3
			int bitTo = (int) ((off+len) >>> SHIFT); // 5

			if (bitFrom >= bitTo) {
				goc(bitFrom).mfree(off&((1L << SHIFT) - 1), len);
				return;
			}

			long tmp;
			if ((tmp =  off     &((1L << SHIFT) - 1)) != 0) goc(bitFrom++).mfree(tmp, ((1L << SHIFT) - 1)+1 - tmp);
			if ((tmp = (off+len)&((1L << SHIFT) - 1)) != 0) goc(bitTo).mfree(0, tmp); // 128

			if (bitFrom < bitTo) {
				long flag = BIT(bitFrom, bitTo);
				assert ((bitmap) & flag) == flag : Long.toBinaryString(bitmap)+" & ~BIT["+bitFrom+", "+bitTo+"): space not allocated";
				bitmap &= ~flag;

				for (int blockId = bitFrom; blockId < bitTo; blockId++) {
					int i = get(blockId);
					if (i < 0) continue;

					var p = child[i];
					if (p.freeSpace() != 0) throw new AssertionError("Excepting 0, but "+p);
					remove(i);
				}
			}

			if (prefixLocked && bitmap == 0) { // 尝试解锁
				for (int i = childCount-1; i >= 0; i--) {
					if (!removeIfEmpty(i)) return;
				}
				prefixLocked = false;
			}
		}

		final long headEmpty() {
			if (prefix > 0) return 0;
			if (bitmap == 0) return totalSpace();

			int a = Long.numberOfTrailingZeros(bitmap);
			if (childCount > 0) {
				int id = child[0].childId;
				if (id <= a) return ((long) id << SHIFT) + child[0].headEmpty();
			}
			return (long) a << SHIFT;
		}
		final long tailEmpty() {
			if (bitmap == 0) return totalSpace() - prefix;

			int a = Long.numberOfLeadingZeros(bitmap);
			if (childCount > 0) {
				int i = childCount-1;
				int id = child[i].childId;
				if (id >= 63-a) return ((long) (63-id) << SHIFT) + child[i].tailEmpty();
			}
			return (long) a << SHIFT;
		}

		// 注意：按照语义，上面两个方法应该调用这个方法，但是它们不可能在TOP节点被调用，所以不改动
		// 如果改成public，就要做一些修改了
		int bitmapCapacity() { return 64; }
		public final long usedSpace() { return totalSpace() - free; }
		public final long freeSpace() { return free; }
		public long totalSpace() { return 1L << (SHIFT+LONG_SHIFT); }

		final boolean validate() {
			long myFree = (64-Long.bitCount(bitmap)) * (1L<<SHIFT);

			myFree -= prefix;

			for (int i = 0; i < childCount; i++) {
				Page page = child[i];
				try {
					page.validate();
				} catch (AssertionError e) {
					AssertionError error = new AssertionError("child["+page.childId+"] validate() failed: ("+e.getMessage()+")"+this);
					error.setStackTrace(e.getStackTrace());
					throw error;
				}
				myFree += page.freeSpace();
			}

			if (bitmapCapacity() == 64 && free != myFree) {
				throw new AssertionError(this+"Excepting free bytes="+myFree+" but actual="+free);
			}
			return true;
		}

		public final void compress(MemoryMover m) {
			IntList data = new IntList();
			getBlocks(0, data);

			TimSortForEveryone.sort(0, data.size() / 4, (refA, posA, posB) -> {
				long a = U.getLong(refA, posA); // [offset, length]
				long b = U.getLong(posB);
				return Long.compare(a, b);
			}, data.getRawArray(), Unaligned.ARRAY_INT_BASE_OFFSET, 16);

			long off = 0;
			for (int i = 0; i < data.size();) {
				long start = getLong(data, i);
				long end = getLong(data, i += 2);
				while (true) {
					i += 2;
					if (i >= data.size() || end != getLong(data, i)) break;
					end = getLong(data, i += 2);
				}

				long len = end - start;
				if (start != off) m.moveMemory(start, off, len);
				off += len;
			}

			bitmap = 0;
			child = EMPTY_PAGES;
			childCount = 0;
			prefixLocked = false;
			prefix = off;
		}
		final void getBlocks(long off, IntList list) {
			splitPrefix();
			super.getBlocks(off, list);

			for (int i = 0; i < childCount; i++) {
				Page p = child[i];
				p.getBlocks(off + ((long) p.childId << SHIFT), list);
			}
		}

		private long success(long size, Page p, long off) {
			free -= size;
			return ((long) p.childId << SHIFT) + off;
		}
		private int get(int blockId) {
			assert blockId >= 0 && blockId < bitmapCapacity() : blockId+" not in [0,"+bitmapCapacity()+"]";
			if (childCount == 64) return blockId;

			int low = 0;
			int high = childCount-1;

			while (low <= high) {
				int mid = (low + high) >>> 1;
				int midVal = child[mid].childId - blockId;

				if (midVal < 0) low = mid + 1;
				else if (midVal > 0) high = mid - 1;
				else return mid;
			}

			return -(low + 1);
		}
		private Page goc(int blockId) {
			int i = get(blockId);
			if (i >= 0) return child[i];

			lockPrefix();

			i = -i - 1;

			if (childCount == child.length) child = Arrays.copyOf(child, childCount+8);

			if (childCount > i) System.arraycopy(child, i, child, i+1, childCount-i);
			childCount++;

			int nextDepth = SHIFT-LONG_SHIFT;
			assert nextDepth >= MINIMUM_SHIFT : "invalid tree depth "+nextDepth;
			Page p = nextDepth > MINIMUM_SHIFT ? new Ext(nextDepth, blockId) : new Page(blockId);

			long myId = 1L << blockId;
			// auto split (目前只有一种可能: free when simple=DISABLED)
			if ((bitmap & myId) != 0) {
				p.malloc(p.freeSpace());
			}
			bitmap |= myId;

			return child[i] = p;
		}
		private boolean isAllEmpty(long bits) {
			bits &= bitmap;
			if (bits == 0) return true;

			int i = Long.numberOfTrailingZeros(bits);
			if (prefix > 0) {
				int bitCount = (int) ((prefix+((1L << SHIFT) - 1)) >>> SHIFT);
				if (i <= bitCount) return false;
			}

			i = get(i);
			if (i < 0) return false;

			int end = 64-Long.numberOfLeadingZeros(bits);
			while (i < childCount) {
				Page p = child[i];
				if (p.childId > end) break;
				if (!removeIfEmpty(i)) return false;
			}
			return (bits&bitmap) == 0;
		}
		private boolean removeIfEmpty(int arrayId) {
			Page p = child[arrayId];
			if (p.usedSpace() == 0) {
				remove(arrayId);
				bitmap ^= 1L << p.childId;
				return true;
			}
			return false;
		}
		private void remove(int arrayId) {
			System.arraycopy(child, arrayId +1, child, arrayId, childCount- arrayId -1);
			child[--childCount] = null;
		}

		private void lockPrefix() {
			if (prefixLocked) return;
			int bitCount = (int) ((prefix+((1L << SHIFT) - 1)) >>> SHIFT);
			if (bitCount > 0) {
				long mask = (1L << bitCount) - 1;
				assert (bitmap&mask) == 0;
				bitmap |= mask;
			}
			prefixLocked = true;
		}
		private void splitPrefix() {
			prefixLocked = true;
			if (prefix == 0) return;

			int bitCount = (int) (prefix >>> SHIFT);
			if (bitCount > 0) {
				long bit = BIT(0, bitCount);
				assert (bitmap & bit) == 0;
				bitmap |= bit;
				assert child.length == 0 || child[0].childId >= bitCount;
			}

			long len = prefix&((1L << SHIFT) - 1);
			if (len != 0) {
				Page p = goc(bitCount);
				boolean ok = p.malloc(0, len);
				assert ok;
			}

			prefix = 0;
		}
	}
	private static final class Top extends Ext {
		private final byte bmpCap;
		private final long totalSpace;

		Top(int shift, long totalSpace) {
			super(shift, 0);
			this.bmpCap = (byte) ((totalSpace+((1L << shift) - 1)) >>> shift);
			this.free = this.totalSpace = (((1L << shift) - 1)+1) * bmpCap;
		}

		final int bitmapCapacity() { return bmpCap; }
		public final long totalSpace() { return totalSpace; }
	}
}