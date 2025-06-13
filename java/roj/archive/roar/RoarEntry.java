package roj.archive.roar;

import roj.archive.ArchiveEntry;
import roj.collect.IntervalPartition;

import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2025/6/18 13:37
 */
public final class RoarEntry implements IntervalPartition.Range, ArchiveEntry, Cloneable {
	char flags;

	long offset;
	int size, compressedSize;

	private String _name;
	byte[] name;
	int nameLen;
	int hash;

	RoarEntry next;

	public RoarEntry(String name) {
		this._name = name;
		this.name = name.getBytes(StandardCharsets.UTF_8);
		this.hash = RoArchive.getHash(this);
		this.flags = 8;
	}

	RoarEntry() {}

	public String getName() { return name == null ? "<unknown>" : _name == null ? _name = new String(name, StandardCharsets.UTF_8) : _name; }
	public boolean isDirectory() { return false; }

	@Override public final long startPos() { return offset - nameLen; }
	@Override public final long endPos() { return offset + getCompressedSize(); }

	public long getOffset() { return offset; }
	public final long getSize() { return size & 0xFFFFFFFFL; }
	public final long getCompressedSize() { return compressedSize & 0xFFFFFFFFL; }

	public final long getAccessTime() { return 0; }
	public final long getCreationTime() { return 0; }
	public final long getModificationTime() { return 0; }

	public final FileTime getPrecisionAccessTime() { return null; }
	public final FileTime getPrecisionCreationTime() { return null; }
	public final FileTime getPrecisionModificationTime() { return null; }

	public final boolean hasAccessTime() { return false; }
	public final boolean hasCreationTime() { return false; }
	public final boolean hasModificationTime() { return false; }

	public int getWinAttributes() {return 0;}
	public final boolean isEncrypted() { return false; }

	@Override
	public String toString() {
		return "RoarEntry{" +
				"flags=" + flags +
				", offset=" + offset +
				", size=" + size +
				", compressedSize=" + compressedSize +
				", name=" + getName() +
				", nameLen=" + nameLen +
				", hash=" + hash +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		RoarEntry file = (RoarEntry) o;
		return name != null && Arrays.equals(name, file.name);
	}
	@Override
	public int hashCode() {return hash;}
}