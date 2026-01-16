package roj.collect;

import roj.util.Helpers;

import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * 区间(Interval)划分(Partition)
 * 根据Range将数轴划分为多个Segment，用于文件IO合并以及寄存器分配
 *
 * @author Roj234
 * @since 2026/1/24 20:23
 */
public class SweepLine {
	/**
	 * 两个方法的调用最好能常数时间
	 */
	public interface Range {
		static <T extends Range> RangeExtractor<T> getExtractor() {return Helpers.cast(DefaultRangeExtractor.INSTANCE);}

		long startPos();
		long endPos();
	}
	public interface RangeExtractor<T> {
		long startPos(T type);
		long endPos(T type);
	}
	private static final class DefaultRangeExtractor implements RangeExtractor<Range> {
		public static final DefaultRangeExtractor INSTANCE = new DefaultRangeExtractor();
		private DefaultRangeExtractor() {}
		@Override public long startPos(Range type) {return type.startPos();}
		@Override public long endPos(Range type) {return type.endPos();}
	}

	public static final class SimpleRange<E> implements Range {
		public long startPos, endPos;
		public E obj;

		public SimpleRange(E obj, long startPos, long endPos) {
			this.obj = obj;
			this.startPos = startPos;
			this.endPos = endPos;
		}

		@Override public long startPos() {return startPos;}
		@Override public long endPos() {return endPos;}
		@Override public String toString() {return String.valueOf(obj);}
	}

	public static <T extends Range> void merge(List<T> input, ObjLongConsumer<List<T>> processor) {
		merge(input, Range.getExtractor(), processor);
	}

	public static <T> void merge(List<T> input, RangeExtractor<T> extractor, ObjLongConsumer<List<T>> processor) {
		if (input.isEmpty()) return;
		input.sort((a, b) -> Long.compare(extractor.startPos(a), extractor.startPos(b)));

		int startIdx = 0;
		long prevStart = extractor.startPos(input.get(0));
		long prevEnd = extractor.endPos(input.get(0));

		for (int i = 1; i < input.size(); i++) {
			T range = input.get(i);
			long start = extractor.startPos(range);

			if (start < prevEnd) throw new IllegalArgumentException("Overlapping");

			if (start > prevEnd) {
				processor.accept(input.subList(startIdx, i), prevEnd - prevStart);
				startIdx = i;
				prevStart = start;
			}
			prevEnd = extractor.endPos(range);
		}

		processor.accept(input.subList(startIdx, input.size()), prevEnd - prevStart);
	}

	public static final class Segment<T> {
		public T value;
		public boolean isEnd;

		public Segment(boolean isEnd, T range) {
			this.isEnd = isEnd;
			this.value = range;
		}
	}

	public static <T> List<Segment<T>> scan(List<T> input, RangeExtractor<T> extractor) {
		List<Segment<T>> segments = new ArrayList<>(input.size() * 2);
		for (T range : input) {
			segments.add(new Segment<>(false, range));
			segments.add(new Segment<>(true, range));
		}

		segments.sort((a, b) -> {
			long apos = a.isEnd ? extractor.endPos(a.value) : extractor.startPos(a.value);
			long bpos = b.isEnd ? extractor.endPos(b.value) : extractor.startPos(b.value);
			if (apos != bpos) return Long.compare(apos, bpos);
			return Boolean.compare(b.isEnd, a.isEnd);
		});

		return segments;
	}
}