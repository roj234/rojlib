package roj.io.session;

import roj.collect.LRUCache;
import roj.collect.MyHashMap;
import roj.concurrent.SegmentReadWriteLock;
import roj.config.NBTParser;
import roj.config.auto.Serializer;
import roj.config.auto.SerializerFactory;
import roj.config.serial.ToNBT;
import roj.io.source.BufferedSource;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.math.MathUtils;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2023/5/15 0015 14:13
 */
public class SimpleSessionProvider extends SessionProvider implements BiConsumer<String,Map<String,Object>> {
	private static final ThreadLocal<Serializer<?>> local = new ThreadLocal<>();
	private static volatile StandardCopyOption[] moveAction = {StandardCopyOption.ATOMIC_MOVE};

	private final File baseDir;
	private final LRUCache<String,Map<String,Object>>[] map;
	private final SegmentReadWriteLock lock = new SegmentReadWriteLock();
	private final int concurrency;
	private int multiplier;

	public SimpleSessionProvider(File baseDir, int concurrency, int LRUSizePerConcurrency) {
		this.baseDir = baseDir;

		if (concurrency <= 0) {
			this.concurrency = -1;
			this.map = null;
			return;
		}

		int tc = MathUtils.getMin2PowerOf(concurrency);
		if (tc > 32) throw new IllegalArgumentException("concurrency too large:"+concurrency);

		this.concurrency = tc-1;
		this.map = Helpers.cast(new LRUCache<?,?>[tc]);
		for (int i = 0; i < map.length; i++) {
			map[i] = new LRUCache<>(LRUSizePerConcurrency,1);
		}

		do {
			multiplier = rnd.nextInt();
		} while (multiplier == 0);
	}

	public SessionProvider saveOnClose() {
		if (concurrency >= 0) Runtime.getRuntime().addShutdownHook(new Thread(this::flush));
		return this;
	}

	public void flush() {
		if (concurrency < 0) return;

		lock.lockAll();
		try {
			for (LRUCache<String, Map<String, Object>> cache : map) {
				for (Map.Entry<String, Map<String, Object>> entry : cache.entrySet()) {
					accept(entry.getKey(),entry.getValue());
				}
				cache.clear();
			}
		} finally {
			lock.unlockAll();
		}
	}

	public void relock() {
		lock.lockAll();
		try {
			do {
				multiplier = rnd.nextInt();
			} while (multiplier == 0);
		} finally {
			lock.unlockAll();
		}
	}

	@Override
	public Map<String,Object> loadSession(String id) {
		if (!isValid(id)) return null;

		if (concurrency >= 0) {
			int _lock_ = (multiplier*id.hashCode()) & concurrency;
			lock.tryLock(_lock_);
			try {
				Map<String,Object> cached = Helpers.cast(map[_lock_].get(id));
				if (cached != null) return cached;
			} finally {
				lock.unlock(_lock_);
			}
		}

		File file = new File(baseDir, id);
		if (!file.isFile()) return new MyHashMap<>();

		try (Source in = BufferedSource.autoClose(new FileSource(file))) {
			Serializer<Map<String, Object>> des = adapter();
			des.reset();
			NBTParser.root(in.asDataInput(), des);
			return des.finished() ? des.get() : null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void saveSession(String id, Map<String,Object> value) {
		if (!isValid(id)) return;

		if (concurrency >= 0) {
			int _lock_ = (multiplier*id.hashCode()) & concurrency;
			lock.tryLock(_lock_);
			try {
				map[_lock_].put(id, value);
				// serialize handled by onEvict(accept) method
			} finally {
				lock.unlock(_lock_);
			}
		} else {
			accept(id, value);
		}
	}

	public void accept(String id, Map<String,Object> value) {
		File file = new File(baseDir, id);
		File tmpFile = new File(baseDir, id+".tmp");

		try {
			try (FileChannel fc = FileChannel.open(tmpFile.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				ByteList.WriteOut buf = new ByteList.WriteOut(Channels.newOutputStream(fc));
				adapter().write(new ToNBT(buf), value);
				buf.flush();
			}

			Files.deleteIfExists(file.toPath());
			try {
				Files.move(tmpFile.toPath(), file.toPath(), moveAction);
			} catch (AtomicMoveNotSupportedException e) {
				StandardCopyOption[] action = moveAction = new StandardCopyOption[0];
				Files.move(tmpFile.toPath(), file.toPath(), action);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			Files.deleteIfExists(tmpFile.toPath());
		} catch (IOException ignored) {}
	}

	@Override
	public void destroySession(String id) {
		if (!isValid(id)) return;

		if (concurrency >= 0) {
			int _lock_ = (multiplier*id.hashCode()) & concurrency;
			lock.tryLock(_lock_);
			try {
				boolean savedInMemory = null != map[_lock_].remove(id);
				if (savedInMemory) return;
			} finally {
				lock.unlock(_lock_);
			}
		}

		new File(baseDir, id).delete();
		new File(baseDir, id+".tmp").delete();
	}

	private static Serializer<Map<String, Object>> adapter() {
		Serializer<?> c = local.get();
		if (c == null) {
			c = SerializerFactory.POOLED.mapOf(Object.class);
			local.set(c);
		}
		return Helpers.cast(c);
	}
}