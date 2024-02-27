package roj.collect;

import roj.crypt.XXHash32;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.nio.charset.StandardCharsets;
import java.util.function.IntFunction;

import static java.lang.Integer.MAX_VALUE;

/**
 * @author Roj234
 * @since 2024/5/4 0004 9:15
 */
public class PerfectHashMap<E> {
	private final int seed;
	private final Entry<E>[] value;
	private final BitArray pilot, pl2;

	public PerfectHashMap(int seed, Entry<E>[] value, BitArray pilot, BitArray pl2) {
		this.seed = seed;
		this.pilot = pilot;
		this.value = value;
		this.pl2 = pl2;
	}

	public Entry<E> getEntry(String key) {
		int b = hash(key, seed);
		int p = pl2.get(pilot.get((b & MAX_VALUE) % value.length));
		Entry<E> entry = value[((hash(key, p) ^ b) & MAX_VALUE) % value.length];
		return entry.key.equals(key) ? entry : null;
	}

	public void encode(DynByteBuf out) {
		out.putIntLE(seed).putVUInt(value.length).put(pilot.bits());
		for (int i : pilot.getInternal()) out.putIntLE(i);
		out.put(pl2.bits());
		for (int i : pl2.getInternal()) out.putIntLE(i);
		for (Entry<E> entry : value) out.putVUIGB(entry.key);
	}

	public static class Builder<E> {
		private final MyHashMap<String, Entry<E>> map = new MyHashMap<>();

		private int seed;
		private int[] pilot;
		private Entry<E>[] value;
		private int count;

		public Entry<E> put(String key, E value) {return put(new Entry<>(key, value));}
		public Entry<E> put(Entry<E> entry) {return map.put(entry.key, entry);}

		public PerfectHashMap<E> build(int cmp) {
			count = map.size();
			if (count == 0) return null;

			seed = (int) System.nanoTime();

			int maxCount = 0;
			int min = 9999999;
			int[] minPilot = null;
			Entry<E>[] minValue = null;
			int minSeed = 0;

			while (true) {
				if (pilot == minPilot) pilot = new int[count];
				value = Helpers.cast(new Entry<?>[count]);

				var buckets = sortBuckets();

				retry: {
					int i = 0;
					int max = 0;
					for (; i < buckets.size(); i++) {
						var bucket = buckets.get(i);
						int v = displace(bucket, count, min);
						if (v < 0) break retry;
						if (v > max) max = v;
					}

					if (max < min) {
						maxCount = -1;
						min = max;
						minPilot = pilot;
						minValue = value;
						minSeed = seed;
					}
				}

				if (++maxCount > cmp) {
					// bitArray<BITS(pilot)>[count] pilot
					Int2IntMap order = new Int2IntMap();
					for (int i = 0; i < minPilot.length; i++)
						order.putIntIfAbsent(minPilot[i], order.size());

					BitArray pl1 = new BitArray(32 - Integer.numberOfLeadingZeros(order.size()), count);
					for (int i = 0; i < minPilot.length; i++)
						pl1.set(i, order.getEntry(minPilot[i]).v);

					int bits = 32 - Integer.numberOfLeadingZeros(min);
					BitArray pl2 = new BitArray(bits, order.size());
					for (Int2IntMap.Entry entry : order.selfEntrySet()) {
						pl2.set(entry.v, entry.getIntKey());
					}

					return new PerfectHashMap<>(minSeed, minValue, pl1, pl2);
				}

				seed++;
			}
		}

		private SimpleList<SimpleList<Entry<E>>> sortBuckets() {
			IntMap<SimpleList<Entry<E>>> buckets = new IntMap<>(count);
			IntFunction<SimpleList<Entry<E>>> fn = i -> new SimpleList<>();

			for (Entry<E> entry : map.values()) {
				int id = bHash(entry.key) % count;
				buckets.computeIfAbsentInt(id, fn).add(entry);
			}

			var sorted = new SimpleList<>(buckets.values());
			sorted.sort((l, r) -> r.size() - l.size());
			return sorted;
		}

		private final IntList used = new IntList();
		private int displace(SimpleList<Entry<E>> bucket, int count, int min) {
			IntList used = this.used;
			int seed = 0, retry = 0;

			retry:
			while (true) {
				for (Entry<E> entry : bucket) {
					int id = ((hash(entry.key, this.seed) ^ hash(entry.key, seed)) & MAX_VALUE) % count;

					if (value[id] != null) {
						int[] array = used.getRawArray();
						for (int i = 0; i < used.size(); i++)
							value[array[i]] = null;
						used.clear();

						if (++retry > min) return -1;

						seed++;
						continue retry;
					}

					value[id] = entry;
					used.add(id);
				}

				used.clear();
				pilot[bHash(bucket.get(0).key) % count] = seed;
				return seed;
			}
		}

		private int bHash(String s) {return hash(s, seed) & MAX_VALUE;}
	}
	static int hash(String s, int seed) {
		byte[] stringData = s.getBytes(StandardCharsets.UTF_8);
		return XXHash32.xxHash32(seed, stringData, 0, stringData.length);
	}

	public static class Entry<S> {
		String key;
		public S value;

		public Entry(String key, S value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {return key;}
	}
}