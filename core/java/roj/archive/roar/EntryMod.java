package roj.archive.roar;

/**
 * @author Roj234
 * @since 2025/06/22 00:28
 */
public final class EntryMod {
	String name;
	RoarEntry entry;

	public boolean compress;

	public Object data;

	public String getName() { return name; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		EntryMod file = (EntryMod) o;
		return name.equals(file.name);
	}

	@Override
	public int hashCode() { return name.hashCode(); }
}