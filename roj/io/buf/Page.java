package roj.io.buf;

import roj.collect.IntList;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.UIUtil;
import roj.util.TimSortForEveryone;
import sun.misc.Unsafe;

import java.util.Arrays;

import static roj.reflect.FieldAccessor.u;
import static roj.text.TextUtil.scaledNumber1024;

/**
 * @author Roj234
 * @since 2023/11/7 0007 11:32
 */
public class Page {
	private static final byte LONG_SHIFT = 6;
	private static final byte MINIMUM_SHIFT = 3;
	private static final long MINIMUM_MASK = (1L << MINIMUM_SHIFT) - 1;

	private static final char BITMAP_FREE = 'o', BITMAP_USED = '1', BITMAP_SUBPAGE = 'S', BITMAP_PREFIX = 'p';

	@FunctionalInterface
	public interface MemoryMover { void moveMemory(long oldPos, long newPos, long len); }

	public static void main(String[] args) throws Exception {
		long memory = u.allocateMemory(331157);
		Page page = Page.create(331157);

		while (true) {
			String s = UIUtil.in.readLine();
			switch (s.charAt(0)) {
				case 'a':
					int size = Integer.parseInt(s.substring(1)) + 8;
					long addr = page.alloc(size);
					if (addr < 0) {
						System.out.println("allocation failed");
						break;
					}
					u.putLong(addr + memory, size);
					addr += 8;
					System.out.println("addr="+addr+",size="+size);
				break;
				case 'f': free(page, null, memory, Integer.parseInt(s.substring(1))); break;
				case 'p': System.out.println(page); break;
			}
		}
	}
	public static void free(Page root, Object ref, long base, long offset) {
		long metadataBase = base+(offset -= 8);
		long length = u.getLong(metadataBase);
		root.free(offset, length);
	}

	// 不要用非Public的方法
	public static Page create(long memory) {
		int targetDepth = Long.numberOfTrailingZeros(MathUtils.getMin2PowerOf(memory)) - LONG_SHIFT;
		int i = MINIMUM_SHIFT;
		while (i < targetDepth) i += LONG_SHIFT;

		return new PageEx(i,  memory, false);
	}

	final byte SHIFT;
	final long MASK;
	final byte childId;

	long bitmap;
	long free;

	Page(int shift, int childId) {
		this.SHIFT = (byte) shift;
		this.MASK = (1L << shift) - 1;
		this.free = 1L << (shift+6);
		this.childId = (byte) childId;
	}

	@Override
	public final String toString() {
		CharList sb = new CharList();
		return toString(sb, 0).toStringAndFree();
	}
	CharList toString(CharList sb, int depth) {
		sb.append("[ depth = ").append(SHIFT).append(" (min)\n");

		for (int i = 0; i < depth; i++) sb.append(' ');
		scaledNumber1024(sb.append("  usage = "), usedSpace());
		scaledNumber1024(sb.append(" / "), totalSpace())
			.append('\n');

		for (int i = 0; i < depth; i++) sb.append(' ');
		CharList bin = new CharList(Long.toBinaryString(bitmap)).replace('0', BITMAP_FREE).replace('1', BITMAP_USED);
		sb.append("  mapping = ").padEnd(BITMAP_FREE, 64-bin.length()).append(bin).append('\n');

		for (int i = 0; i < depth; i++) sb.append(' ');
		return sb.append(']');
	}

	public final long alloc(long len) { return malloc(align(len)); }
	public final boolean alloc(long off, long len) {
		assert off == align(off) : "un-aligned offset "+off;
		return malloc(off, align(len));
	}
	public final void free(long off, long len) {
		assert off == align(off) : "un-aligned offset "+off;
		mfree(off, align(len));
	}
	public final boolean allocBefore(long rOff, long rLen, long more) { return malloc(align(rOff-more), align(more)); }
	public final boolean allocAfter(long rOff, long rLen, long more) { return malloc(align(rOff+rLen), align(more)); }

	long malloc(long len) {
		if (free < len) return -1;

		int blocks = (int) ((len+MASK) >>> SHIFT);
		long flag = blocks == 64 ? -1L : (1L << blocks)-1;

		int offset = 0, maxOffset = 64-blocks;
		while ((bitmap & flag) != 0) {
			if (offset == maxOffset) return -1;
			flag <<= 1;
			offset++;
		}

		bitmap |= flag;
		free -= len;
		return offset << SHIFT;
	}
	boolean malloc(long off, long len) {
		if (free < len) return false;

		int bitFrom = (int) (off >>> SHIFT);
		int bitTo = (int) ((off+len+MASK) >>> SHIFT);

		long flag = BIT(bitFrom, bitTo);
		if ((bitmap&flag) != 0) return false;

		bitmap |= flag;
		free -= len;
		return true;
	}
	void mfree(long off, long len) {
		int bitFrom = (int) (off >>> SHIFT);
		int bitTo = (int) ((off+len+MASK) >>> SHIFT);

		long flag = BIT(bitFrom, bitTo);
		assert ((bitmap)&flag) == flag : Long.toBinaryString(bitmap)+" & ~BIT["+bitFrom+", "+bitTo+"): space not allocated";

		bitmap ^= flag;
		free += len;
	}

	public final long usedSpace() { return totalSpace() - free; }
	public final long freeSpace() { return free; }
	public long totalSpace() { return 1L << (SHIFT+6); }

	long headEmpty() { return Long.numberOfTrailingZeros(bitmap) << SHIFT; }
	long tailEmpty() { return Long.numberOfLeadingZeros(bitmap) << SHIFT; }

	public static long align(long n) { return 0 == (n&MINIMUM_MASK) ? n : (n|MINIMUM_MASK) + 1; }
	public static long BIT(int bitFrom, int bitTo) {
		assert bitTo <= 64 : "illegal parameter";
		assert bitFrom < 64 && bitFrom <= bitTo : "illegal parameter";

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
				addLong(list, off + ((long)i << SHIFT));
				state ^= 1;
			}

			map >>>= 1;
			i++;
		}
		if (state == 1) addLong(list, off + ((long)i << SHIFT));
	}
	static void addLong(IntList l, long v) {
		int size = l.size();
		l.ensureCapacity(2+size*2);
		u.putLong(l.getRawArray(), (long)Unsafe.ARRAY_INT_BASE_OFFSET+4*size, v);
		l.setSize(size+2);
	}
	static long getLong(IntList l, int i) { return u.getLong(l.getRawArray(), (long)Unsafe.ARRAY_INT_BASE_OFFSET+4*i); }

	static final class PageEx extends Page {
		private long splitBitmap;

		private static final Page[] EMPTY_PAGES = new Page[0];
		private Page[] child = EMPTY_PAGES;
		private int childCount;

		// false: 还能往后增长, true: 只能用到当前块的结尾
		private boolean prefixLocked;
		private long prefix;

		// 管理TOP节点的真实大小
		private final byte bmpCap;

		PageEx(int shift, long free, boolean _distinguish) {
			super(shift, -1);
			this.free = free;
			this.bmpCap = (byte) ((free+MASK) >>> shift);
		}
		private PageEx(int shift, int childId) {
			super(shift, childId);
			bmpCap = 64;
		}

		CharList toString(CharList sb, int depth) {
			sb.append("[ depth = ").append(SHIFT).append('\n');

			for (int i = 0; i < depth; i++) sb.append(' ');
			scaledNumber1024(sb.append("  usage = "), usedSpace());
			scaledNumber1024(sb.append(" / "), totalSpace())
				.append(" (").append(usedSpace()).append(" / ").append(totalSpace()).append(")")
				.append("  ").append(TextUtil.toFixed((1 - (double) free / totalSpace()) * 100, 2)).append("%\n");

			if (prefix > 0) {
				for (int i = 0; i < depth; i++) sb.append(' ');
				sb.append("  prefix = ").append(prefix);
				sb.append('\n');
			}

			if ((bitmap|splitBitmap) != 0) {
				int cap = bitmapCapacity();

				for (int i = 0; i < depth; i++) sb.append(' ');
				CharList bin = new CharList(cap).append(Long.toBinaryString(bitmap));
				bin.replace('0', BITMAP_FREE).replace('1', BITMAP_USED).padStart(BITMAP_FREE, cap-bin.length());

				if (splitBitmap != 0) {
					long m = splitBitmap;
					int i = cap-1;
					while (m != 0) {
						if ((m & 1) != 0) bin.set(i, get(cap-1-i) >= 0 ? BITMAP_SUBPAGE : BITMAP_PREFIX);

						i--;
						m >>>= 1;
					}
				}

				sb.append("  mapping = ").append(bin).append('\n');
			}

			for (int i = 0; i < childCount; i++) {
				for (int j = 0; j < depth; j++) sb.append(' ');
				sb.append("  child[").append(child[i].childId).append("] = ");
				child[i].toString(sb, depth+2);
				sb.append('\n');
			}

			for (int i = 0; i < depth; i++) sb.append(' ');
			return sb.append(']');
		}

		final long malloc(long size) {
			if (free < size) return -1;

			int block = (int) (size >>> SHIFT);
			if (block > 0) { // 大于1个完整的块
				lockPrefix(); // splitBitmap |= BIT[0,bitCount] , mergeSplit也有检查

				long flag = block == 64 ? -1L : (1L << block)-1;

				int offset = 0, maxOffset = 64-block;
				while (true) {
					if ((bitmap&flag) == 0 && removeEmpties(flag)) {
						long subSize = size&MASK;
						if (subSize == 0) break;

						// 最后一个可以是不完整的, 但是连续长度要足够
						Page p = goc(offset+block);
						if (p.headEmpty() >= subSize) {
							boolean ok = p.malloc(0, subSize);
							assert ok;
							break;
						}
					}

					if (offset == maxOffset) return -1;
					flag <<= 1;
					offset ++;
				}

				bitmap |= flag;
				free -= size;
				// 前对齐 以后可以尝试移动到SubPage结尾
				return offset << SHIFT;
			} else {
				// 快速分配, remove时再拆分 (好消息：FIFO不会拆分)
				long len = prefix;
				if (!prefixLocked) {
					prefix = align(len+size);
					free -= size;
					return len;
				} else if (len > 0) {
					long remain = prefix&MASK;
					if (remain > 0 && size <= MASK-remain) {
						prefix = align(len+size);
						free -= size;
						return len;
					}
				}

				for (int i = 0; i < childCount; i++) {
					Page p = child[i];
					long off = p.malloc(size);
					if (off >= 0) return success(size, p, off);

					// 跨区alloc(而且优先！)
					off = p.tailEmpty();
					if (off > 0 && (bitmap & (1L << p.childId)) == 0) {
						if (i+1 < childCount && off + child[i+1].headEmpty() < size)
							continue;

						long off1 = p.totalSpace() - off;
						boolean ok = p.malloc(off1, off);
						assert ok : "tailEmpty() error";

						ok = goc(p.childId +1).malloc(0, size-off);
						assert ok : "headEmpty() error";

						return success(size, p, off1);
					}
				}

				long space = ~(bitmap|splitBitmap);
				// 还有可用空间
				if (space != 0) {
					// later 切断【最短】的连续空间 ?
					int id = Long.numberOfTrailingZeros(Long.lowestOneBit(space));
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

			int bitFrom = (int) (off >>> SHIFT),
				bitTo = (int) ((off+len) >>> SHIFT);

			if ((prefix+MASK) >>> SHIFT > bitFrom) return false;

			if (bitFrom == bitTo) {
				assert len < MASK;

				if (goc(bitFrom).malloc(off&MASK, len)) {
					free -= len;
					return true;
				}
				return false;
			}

			lockPrefix();
			// bitFrom is inclusive (bitFrom = off+MASK >>> SHIFT也许更好理解？)
			long ext = off&MASK;

			long flag = BIT(ext != 0 ? bitFrom+1 : bitFrom, bitTo);
			if ((bitmap&flag) != 0 || !removeEmpties(flag)) return false;

			// assert len >= MASK, so this is smaller
			if (ext > 0 && !goc(bitFrom).malloc(ext, MASK+1 - ext)) // BEFORE
				return false;

			long ext2 = (off+len)&MASK;
			if (ext2 > 0 && !goc(bitTo).malloc(0, ext2)) { // AFTER
				// ATOMIC
				if (ext > 0) goc(bitFrom).free(ext, MASK+1 - ext);
				return false;
			}

			bitmap |= flag;
			free -= len;
			return true;
		}
		final void mfree(long off, long len) {
			free += len;

			if (prefix > 0) {
				if (bitmap == 0 && align(off+len) == prefix) {
					prefix = (prefix - len) & ~MINIMUM_MASK;
					return;
				}
				splitPrefix();
			}

			int bitFrom = (int) (off >>> SHIFT),
				bitTo = (int) ((off+len) >>> SHIFT);

			if (bitFrom == bitTo) {
				assert len < MASK;
				goc(bitFrom).mfree(off&MASK, len);
				return;
			}

			long flag = BIT(bitFrom, bitTo);
			assert ((bitmap|splitBitmap) & flag) == flag : Long.toBinaryString(bitmap)+" & ~BIT["+bitFrom+", "+bitTo+"): space not allocated";

			long ext = off&MASK;
			// assert len >= MASK, so this is smaller
			if (ext > 0) goc(bitFrom).mfree(ext, MASK+1 - ext); // BEFORE

			ext = (off+len)&MASK;
			if (ext > 0) goc(bitTo).mfree(0, ext); // AFTER

			bitmap &= ~flag;

			if (prefixLocked && bitmap == 0) { // 尝试解锁
				for (int i = childCount-1; i >= 0; i--) {
					if (!removeIfEmpty(i)) return;
				}
				splitBitmap = 0;
				prefixLocked = false;
			}
		}

		final long headEmpty() {
			if (prefix > 0) return -1;
			if (bitmap == 0 && childCount == 0) return totalSpace();

			int a = Long.numberOfTrailingZeros(bitmap);
			long split = splitBitmap;
			while (true) {
				int b = Long.numberOfTrailingZeros(split);
				if (b > a) return a << SHIFT;

				// get(b) >= 0 because prefix == 0
				if (!removeIfEmpty(get(b)))
					return (b << SHIFT) + goc(b).headEmpty();

				split ^= 1L << b;
			}
		}
		final long tailEmpty() {
			if (bitmap == 0 && childCount == 0) return totalSpace() - prefix;

			int a = Long.numberOfLeadingZeros(bitmap);
			long split = splitBitmap;
			while (true) {
				int b = Long.numberOfLeadingZeros(split);
				if (b > a) return (long) a << SHIFT;

				b = 63-b;

				int i = get(b);
				if (i < 0 || !removeIfEmpty(i)) {
					return ((63L-b) << SHIFT) + goc(b).tailEmpty();
				}

				split ^= 1L << b;
			}
		}

		// 注意：按照语义，上面两个方法应该调用这个方法，但是它们不可能在TOP节点被调用，所以不改动
		// 如果改成public，就要做一些修改了
		private int bitmapCapacity() { return bmpCap; }
		public final long totalSpace() { return (1L << SHIFT) * bitmapCapacity(); }

		public final void compress(MemoryMover m) {
			IntList data = new IntList();
			getBlocks(0, data);

			TimSortForEveryone.sort(data.getRawArray(), Unsafe.ARRAY_INT_BASE_OFFSET, 0, data.size()/4, 16, (refA, posA, posB) -> {
				long a = u.getLong(refA, posA); // [offset, length]
				long b = u.getLong(posB);
				return Long.compare(a, b);
			});

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

			bitmap = splitBitmap = 0;
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
				p.getBlocks(off + (p.childId << SHIFT), list);
			}
		}

		private long success(long size, Page p, long off) {
			free -= size;
			return (p.childId << SHIFT) + off;
		}
		private int get(int blockId) {
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
			assert blockId <= 63;
			int i = get(blockId);
			if (i >= 0) return child[i];

			i = -i - 1;

			if (childCount == child.length) child = Arrays.copyOf(child, childCount+10);

			if (childCount > i) System.arraycopy(child, i, child, i+1, childCount-i);
			childCount++;

			int nextDepth = SHIFT-6;
			assert nextDepth >= MINIMUM_SHIFT : "invalid tree depth "+nextDepth;
			Page p = nextDepth > MINIMUM_SHIFT ? new PageEx(nextDepth, blockId) : new Page(nextDepth, blockId);

			long myId = 1L << blockId;
			// auto split (目前只有一种可能: free when simple=DISABLED)
			if ((bitmap & myId) != 0) {
				bitmap ^= myId;
				p.malloc(p.free);
			}
			splitBitmap |= myId;

			return child[i] = p;
		}
		private boolean removeEmpties(long bits) {
			bits &= splitBitmap;
			if (bits == 0) return true;

			int i = Long.numberOfTrailingZeros(bits);
			if (prefix > 0) {
				int bitCount = (int) ((prefix+MASK) >>> SHIFT);
				if (i <= bitCount) return false;
			}

			i = get(i);
			int len = i + Long.bitCount(bits);
			while (i < len) {
				if (!removeIfEmpty(i++)) return false;
			}
			return true;
		}
		private boolean removeIfEmpty(int arrayId) {
			Page p = child[arrayId];
			if (p.usedSpace() == 0) {
				System.arraycopy(child, arrayId+1, child, arrayId, childCount-arrayId-1);
				child[--childCount] = null;
				splitBitmap ^= 1L << p.childId;
				return true;
			}
			return false;
		}

		private void lockPrefix() {
			if (prefixLocked) return;
			int bitCount = (int) ((prefix+MASK) >>> SHIFT);
			if (bitCount > 0) {
				assert splitBitmap == 0;
				splitBitmap = (1L << bitCount) - 1;
			}
			prefixLocked = true;
		}
		private void splitPrefix() {
			prefixLocked = true;
			if (prefix == 0) return;

			int bitCount = (int) ((prefix+MASK) >>> SHIFT);
			if (child.length == 0) child = new Page[bitCount];
			long len = prefix;
			for (int i = 0; i < bitCount; i++) {
				Page p = goc(i);
				long decr = Math.min(p.free, len);
				p.malloc(decr);
				len -= decr;
			}
			assert len == 0;
			prefix = 0;
		}
	}
}