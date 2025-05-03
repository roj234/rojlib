package roj.plugins.dns;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Roj234
 * @since 2023/3/4 18:00
 */
public final class RecordKey {
	String url;
	byte flag;

	public ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	public String getUrl() {
		return url;
	}
	public byte getFlag() {
		return flag;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		RecordKey that = (RecordKey) o;

		return url.equals(that.url);
	}

	@Override
	public int hashCode() {
		return url.hashCode();
	}

	@Override
	public String toString() {
		return url;
	}
}