package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.util.ArrayUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * 区间(Interval)划分(Partition)
 * 根据Range将数轴划分为多个Segment，用于文件IO合并以及寄存器分配
 *
 * @author Roj234
 * @since 2021/4/27 18:20
 */
public class IntervalPartition<T extends IntervalPartition.Range> implements Iterable<IntervalPartition.Segment> {
	private static final Segment[] EMPTY_DATA = new Segment[0];

	/**
	 * 两个方法的调用最好能常数时间
	 */
	public interface Range {
		long startPos();
		long endPos();
	}

	public static final class Wrap<T1> implements Range {
		public long s, e;
		public T1 sth;

		public Wrap(T1 sth, long s, long e) {
			this.sth = sth;
			this.s = s;
			this.e = e;
		}

		@Override
		public long startPos() {return s;}
		@Override
		public long endPos() {return e;}
		@Override
		public String toString() {return String.valueOf(sth);}
	}

	public IntervalPartition() {this(0, true, 15);}
	public IntervalPartition(int capacity) {this(capacity, true, 15);}
	public IntervalPartition(int capacity, boolean trackCoverage, int endpointCache) {
		segments = capacity == 0 ? EMPTY_DATA : new Segment[capacity << 1];
		this.trackCoverage = trackCoverage;
		endpointCacheSize = endpointCache;
	}

	private Segment[] segments;
	private int rangeCount, segmentCount;
	private boolean trackCoverage;

	private Endpoint endpointCache;
	private int endpointCacheSize;

	private Endpoint retain(T owner, boolean end) {
		Endpoint p = endpointCache;

		if (p != null) {
			p.interval = owner;
			p.end = end;

			endpointCache = p.next;
			p.next = null;
			endpointCacheSize++;
		} else {
			p = new Endpoint(owner, end);
		}
		return p;
	}
	private void free(Endpoint p) {
		if (endpointCacheSize == 0) return;

		p.interval = null;
		p.next = endpointCache;
		endpointCacheSize--;
		endpointCache = p;
	}

	/**
	 * 设置是否跟踪区间覆盖信息
	 *
	 * @param enabled true启用覆盖跟踪(增加内存开销)，false禁用
	 * @throws IllegalStateException 集合非空时调用
	 */
	public void setTrackCoverage(boolean enabled) {
		if (segmentCount != 0) throw new IllegalStateException("Not empty");
		this.trackCoverage = enabled;
	}

	public Segment[] getSegments() {return segments;}
	public int getRangeCount() {return rangeCount;}
	public int getSegmentCount() {return segmentCount;}

	/**
	 * 精确查找区间
	 *
	 * @param start 区间起始位置
	 * @param end 区间结束位置
	 * @return 匹配的区间对象，未找到返回null
	 */
	@SuppressWarnings("unchecked")
	public T getExact(long start, long end) {
		int begin = binarySearch(start);
		if (begin < 0) return null;

		Endpoint endpoint = segments[begin].anchor;
		while (endpoint != null) {
			if (endpoint.interval.endPos() == end) return (T) endpoint.interval;
			endpoint = endpoint.next;
		}
		return null;
	}

	/**
	 * 查找包含指定点的段
	 *
	 * @param point 要查询的点
	 * @return 包含该点的段对象，未找到返回null
	 */
	public Segment segmentAt(long point) {
		int pos = indexAt(point);
		if (pos < 0) return null;
		return segments[pos];
	}
	/**
	 * @return 点所在段的索引，若在第一个段前返回-1
	 */
	private int indexAt(long point) {
		int pos = binarySearch(point);
		return pos < 0 ? -pos - 2 : pos;
	}

	/**
	 * 获取指定位置的区间覆盖
	 *
	 * @param segmentIndex 段索引(通过search()获取)
	 * @return 该段覆盖的区间列表(请勿修改)
	 */
	@SuppressWarnings("unchecked")
	public List<T> getCoverage(int segmentIndex) {
		Segment segment = segmentAt(segmentIndex);
		return segment == null ? Collections.emptyList() : (List<T>) segment.coverage;
	}

	@SuppressWarnings("unchecked")
	public <C extends Collection<T>> C getCoverage(int pos, C target) {
		Segment segment = segmentAt(pos);
		if (segment != null) target.addAll((Collection<T>) segment.coverage);
		return target;
	}

	@NotNull
	public Iterator<Segment> iterator() {return new ArrayIterator<>(segments, 0, segmentCount);}

	public boolean add(T range) {
		long sp = range.startPos();
		if (sp >= range.endPos()) throw new IndexOutOfBoundsException("start >= end: " + range);
		if (range.startPos() < 0) throw new IndexOutOfBoundsException("start < 0");

		int begin = binarySearch(sp);
		begin = addPoint(begin, range, false);
		if (begin == -1) return false;

		int end = binarySearch(range.endPos());
		end = addPoint(end, range, true);

		if (trackCoverage) {
			Segment[] d = segments;
			for (; begin < end; begin++) {
				d[begin].coverage.add(range);
			}
		}

		rangeCount++;

		return true;
	}
	public boolean remove(T range) {
		long startPos = range.startPos();
		if (startPos >= range.endPos()) throw new IndexOutOfBoundsException("start >= end");

		int begin = binarySearch(startPos);
		if (begin < 0 || !removePoint(begin, range)) return false;

		int endPos = binarySearch(range.endPos());
		if (endPos < 0 || !removePoint(endPos, range)) throw new AssertionError("数据结构损坏！(也许是错误的equals方法) "+range.startPos()+","+range.endPos()+": "+range);

		if (segments[endPos] == null) endPos--;

		if (trackCoverage) {
			for (; begin < endPos; begin++) {
				segments[begin].coverage.remove(range);
			}
		}

		rangeCount--;

		return true;
	}

	public void clear() {
		int i = 0;
		for (; i < segmentCount; i++) {
			Segment r = segments[i];

			r.coverage.clear();

			Endpoint anchor = r.anchor;
			r.anchor = null;

			while (anchor != null) {
				Endpoint next = anchor.next;
				free(anchor);
				anchor = next;
			}

			if (endpointCacheSize == 0) break;
		}
		for (; i < segmentCount; i++) {
			Segment r = segments[i];
			r.anchor = null;
			r.coverage.clear();
		}
		segmentCount = rangeCount = 0;
	}

	/**
	 * The <i>insertion point</i> is defined as the point at which the
	 * key would be inserted into the array: the index of the first
	 * element in the range greater than the key,
	 * or <tt>toIndex</tt> if all
	 * elements in the range are less than the specified key.
	 */
	private int binarySearch(long pos) {
		int low = 0;
		int high = segmentCount - 1;

		Segment[] a = segments;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			long midVal = a[mid].anchor.pos();

			if (midVal < pos) {
				low = mid + 1;
			} else if (midVal > pos) {
				high = mid - 1;
			} else {
				return mid;
			}
		}

		return -(low + 1);
	}
	private void ensureCapacity(int cap) {
		if (segments.length < cap) {
			Segment[] newArr = new Segment[cap + 10];
			if (segmentCount > 0) System.arraycopy(segments, 0, newArr, 0, segmentCount);
			segments = newArr;
		}
	}

	private void addSegment(int index, Segment segment) {
		ensureCapacity(segmentCount + 1);
		final Segment[] data1 = this.segments;
		if (segmentCount - index > 0) System.arraycopy(data1, index, data1, index + 1, segmentCount - index);
		data1[index] = segment;
		segmentCount++;
	}
	private void removeSegment(int index) {
		Segment[] data1 = this.segments;
		if (segmentCount - index - 1 > 0) System.arraycopy(data1, index + 1, data1, index, segmentCount - index - 1);
		data1[--segmentCount] = null;
		if (segmentCount > 0) data1[segmentCount - 1].coverage.clear();
	}

	private int addPoint(int pos, T val, boolean end) {
		Endpoint point = retain(val, end);
		if (pos >= 0) {
			Endpoint anchor = segments[pos].anchor;
			while (true) {
				if (val.equals(anchor.interval)) return -1;
				if (anchor.next == null) {
					anchor.next = point;
					break;
				}
				anchor = anchor.next;
			}
		} else {
			// 上次clear剩下的
			Segment segment = segmentCount == segments.length ? null : segments[segmentCount];
			if (segment == null) segment = new Segment(trackCoverage);
			else segment.init(trackCoverage);
			segment.anchor = point;

			pos = -pos - 1;
			if (trackCoverage && pos > 0) {
				segment.coverage.addAll(segments[pos - 1].coverage);
			}
			addSegment(pos, segment);
		}
		return pos;
	}
	private boolean removePoint(int pos, T val) {
		Endpoint prev = null;
		Endpoint endpoint = segments[pos].anchor;
		while (endpoint != null) {
			if (val.equals(endpoint.interval)) {
				if (prev == null) {
					if (endpoint.next == null) removeSegment(pos);
					else segments[pos].anchor = endpoint.next;
				} else {
					prev.next = endpoint.next;
				}
				free(endpoint);
				return true;
			}
			prev = endpoint;
			endpoint = endpoint.next;
		}
		return false;
	}

	/**
	 * 合并连续区间并回调处理
	 *
	 * @param processor 接收(连续区间集合, 总长度)的回调
	 * @throws IllegalArgumentException 存在重叠或非连续区间时抛出
	 */
	public void mergeConnected(ObjLongConsumer<List<? extends Range>> processor) {
		long begin = -1;

		List<Range> list = new ArrayList<>();

		for (int i = 0; i < segmentCount; i++) {
			Segment r = segments[i];

			Endpoint p = r.anchor;
			Range owner = p.interval;

			if (p.next != null) {
				if (begin == -1) throw new IllegalArgumentException("不适合的Range "+r+": 重叠的区间");

				if (p.end) {
					p = p.next; // 找到Start
					if (p.next != null) throw new IllegalArgumentException("不适合的Range "+r+": 重叠的区间");
				}

				list.add(p.interval);
			} else if (begin == -1) {
				if (p.end) throw new IllegalArgumentException("不适合的Range "+r+": 长度为零");

				list.add(p.interval);

				begin = owner.startPos();
			} else {
				if (!p.end) throw new IllegalArgumentException("不适合的Range "+r+": 重叠的区间, 与列表中的至少一个 "+list);

				processor.accept(list, owner.endPos() - begin);

				list.clear();
				begin = -1;
			}
		}
	}

	public String toString() {
		return "IntervalPartition"+ArrayUtil.toString(segments, 0, segmentCount);
	}

	/**
	 * 表示数轴上的一个划分段
	 */
	public static final class Segment {
		Endpoint anchor;
		List<Range> coverage;

		public Segment(boolean care) {coverage = care ? new ArrayList<>() : Collections.emptyList();}

		void init(boolean care) {
			if ((coverage == Collections.EMPTY_LIST) == care) {
				coverage = care ? new ArrayList<>() : Collections.emptyList();
			}
		}

		/**
		 * @return 段起始位置(等于端点位置)
		 */
		@Contract(pure = true)
		public long pos() {return anchor.pos();}
		@NotNull
		@Contract(pure = true)
		public Endpoint anchor() {return anchor;}
		@SuppressWarnings("unchecked")
		@NotNull
		@Contract(pure = true)
		public <T extends Range> List<T> coverage() {return (List<T>) coverage;}

		@Override
		public String toString() {
			var sb = new StringBuilder().append('{').append(anchor);
			if (coverage != Collections.EMPTY_LIST) sb.append("\n  ").append(coverage);
			return sb.append("\n}").toString();
		}
	}

	/**
	 * 表示区间的端点(开始/结束)
	 */
	public static final class Endpoint {
		Range interval;
		boolean end;
		Endpoint next;

		public Endpoint(Range data, boolean end) {
			this.end = end;
			interval = data;
		}

		public long pos() {return end ? interval.endPos() : interval.startPos();}

		/**
		 * @return 关联的区间对象
		 */
		@SuppressWarnings("unchecked")
		@Contract(pure = true)
		public <T extends Range> T interval() {return (T) interval;}
		/**
		 * @return true表示结束端点，false表示开始端点
		 */
		@Contract(pure = true)
		public boolean isEnd() {return end;}
		/**
		 * 链表结构
		 */
		@Nullable
		@Contract(pure = true)
		public Endpoint next() {return next;}

		@Override
		public String toString() {
			var sb = new StringBuilder("\n  ").append(interval).append(end ? " end" : " start").append(" at ").append(pos());
			if (next != null) sb.append(next);
			return sb.toString();
		}
	}
}