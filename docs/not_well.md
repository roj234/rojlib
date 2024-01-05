```java
import sun.misc.Unsafe;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;

/**
 * @author Roj234
 * @since 2024/1/10 0010 1:47
 */
public class CalculateAverage_Roj234 {
	public static final Unsafe u = getUnsafe();
	private static Unsafe getUnsafe() {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return (Unsafe) f.get(null);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws Exception {
		long start = System.currentTimeMillis();
		File file = new File("D:\\Desktop\\dataset.csv");
		FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ);
		int core = Runtime.getRuntime().availableProcessors();
		long offset = 0;
		long length = file.length();
		long perTask = length / core / 2;
		Semaphore semaphore = new Semaphore(core);
		List<Task> tasks = new ArrayList<>();
		while (offset < length) {
			long len = Math.min(length-offset, perTask);
			tasks.add(new Task(fc, offset, len, semaphore));
			offset += len;
		}

		CalculateAverage_Roj234 inst = new CalculateAverage_Roj234();
		int running = tasks.size();
		while (tasks.size() > 0) {
			for (int i = tasks.size() - 1; i >= 0; i--) {
				Task task = tasks.get(i);
				if (task.finished) {
					running--;
					tasks.remove(i);
					task.merge(inst);
				}
			}
			Thread.sleep(1);
		}

		TreeMap<String, String> dump = inst.dump();
		System.out.println(dump);
		System.out.println(System.currentTimeMillis()-start);
	}

	private TreeMap<String, String> dump() throws Exception {
		TreeMap<String, String> kvpair = new TreeMap<>();
		StringBuilder sb = new StringBuilder();

		Field f = sb.getClass().getSuperclass().getDeclaredField("count");
		long countFieldOff = u.objectFieldOffset(f);

		byte[] dec = new byte[100];

		for (int i = 0; i < mask + 1; i++) {
			Station entry = entries[i];
			while (entry != null) {
				u.copyMemory(null, entry.nAddr, dec, Unsafe.ARRAY_BYTE_BASE_OFFSET, entry.nLen);
				String name = new String(dec, 0, entry.nLen, StandardCharsets.UTF_8);

				sb.append((float) entry.min/10).append('/');
				toFixed(sb, ((double) entry.avg / entry.cnt) / 10);
				sb.append('/').append((float) entry.max/10);

				kvpair.put(name, sb.toString());
				u.putInt(sb, countFieldOff, 0); // clear

				entry = entry.next;
			}
		}

		return kvpair;
	}

	private static StringBuilder toFixed(StringBuilder sb, double d) {
		int len = sb.length();
		sb.append(d);
		int dot = sb.lastIndexOf(".");
		assert dot > len;

		int ex = dot+2;
		if (sb.length() < ex) {
			while (sb.length() < ex) sb.append('0');
		} else {
			sb.setLength(ex);
		}
		return sb;
	}

	static final int mask = 8191;
	private final Station[] entries = new Station[8192];

	private static final class Station {
		Station next;
		int nHash;
		long nAddr;
		int nLen;

		int min, max;
		long avg;
		int cnt;
	}

	void incr(Station fromout) {
		int i = fromout.nHash & mask;
		Station entry = entries[i];
		if (entry == null) {
			fromout.next = null;
			entries[i] = fromout;
			return;
		}

		while (true) {
			block:
			if (entry.nLen == fromout.nLen) {
				for (int j = 0; j < fromout.nLen; j++) {
					if (u.getByte(entry.nAddr+j) != u.getByte(fromout.nAddr+j)) break block;
				}

				entry.avg += fromout.avg;
				entry.cnt += fromout.cnt;
				if (entry.min > fromout.min) entry.min = fromout.min;
				if (entry.max < fromout.max) entry.max = fromout.max;
				u.freeMemory(fromout.nAddr);
				return;
			}

			if (entry.next == null) {
				entry.next = fromout;
				fromout.next = null;
				return;
			}
			entry = entry.next;
		}
	}

	static class Task extends Thread {
		final ByteBuffer map;
		final long addr, end;
		final Semaphore lock;

		final Station[] entries = new Station[8192];
		volatile boolean finished;

		static final long f;
		//static final long f2;
		//static final long f3;

		static {
			try {
				f = u.objectFieldOffset(Buffer.class.getDeclaredField("address"));
				//Field f1 = ByteBuffer.allocateDirect(1).getClass().getDeclaredField("cleaner");
				//f2 = u.objectFieldOffset(f1);
				//f3 = f1.getType().getMethod("clean");
			} catch (NoSuchFieldException e) {
				throw new RuntimeException(e);
			}
		}

		public Task(FileChannel file, long offset, long len, Semaphore semaphore) throws Exception {
			map = file.map(FileChannel.MapMode.READ_ONLY, offset, len);
			addr = u.getLong(map, f);
			end = addr+len;
			lock = semaphore;
			start();
		}

		void merge(CalculateAverage_Roj234 inst) {
			for (int i = 0; i < mask + 1; i++) {
				Station entry = entries[i];
				while (entry != null) {
					Station next = entry.next;
					inst.incr(entry);
					entry = next;
				}
			}
		}

		@Override
		public void run() {
			try {
				lock.acquire();
			} catch (InterruptedException e) {}

			long pos = addr;
			long end = this.end;
			while (u.getByte(pos++) != '\n'); // align to line

			int num = 0;
			loop:
			while (true) {
				int myHash = 0;

				long i;
				for (i = pos; i < end;) {
					byte b = u.getByte(i++);
					if (b == ';') break;
					myHash = myHash * 31 + (b&0xFF);
				}

				if (end - i < 6) {
					lock.release();
					finished = true;
					return; // -99.9\n
				}
				long mypos = i-1;

				int sig;
				byte b = u.getByte(i);
				if (b == '-') {
					sig = -1;
					i++;
				} else {
					sig = 1;
				}

				num = sig * 100 * (u.getByte(i++)-'0');
				b = u.getByte(i++);
				if (b == '.') {
					// last
					num += sig * (u.getByte(i)-'0');
				} else {
					num += sig * 10 * (b-'0');
					// buf[i] must be .
					num += sig * (u.getByte(++i)-'0');
				}

				int len = (int) (mypos-pos);
				mypos = pos;

				pos = i+2;

				Station entry = entries[myHash &= mask];
				while (entry != null) {
					block:
					if (entry.nLen == len) {
						for (int j = 0; j < len; j++) {
							if (u.getByte(entry.nAddr +j) != u.getByte(mypos+j)) break block;
						}

						entry.avg += num;
						entry.cnt++;
						if (entry.min > num) entry.min = num;
						if (entry.max < num) entry.max = num;
						continue loop;
					}

					if (entry.next == null) {
						Station m = init(num);
						m.nAddr = u.allocateMemory(len);
						u.copyMemory(mypos, m.nAddr, len);
						m.nHash = myHash;
						m.nLen = len;
						entry.next = m;
						continue loop;
					}
					entry = entry.next;
				}

				Station m = init(num);
				m.nAddr = u.allocateMemory(len);
				u.copyMemory(mypos, m.nAddr, len);
				m.nHash = myHash;
				m.nLen = len;
				entries[myHash] = m;
			}
		}

		private static Station init(int num) {
			Station m = new Station();
			m.avg = m.max = m.min = num;
			m.cnt = 1;
			return m;
		}
	}
}
```