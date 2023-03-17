package roj.archive.zip;

/**
 * @author Roj234
 * @since 2023/3/14 0014 0:42
 */
public final class EntryMod {
	String name;
	ZEntry entry;

	public static final int E_COMPRESS = 8, E_UTF_NAME = 2, E_LARGE = 4, E_ORIGINAL_TIME = 1;
	public byte flag;

	public Object data;

	public byte[] pass;
	public int cryptType;

	public String getName() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		EntryMod file = (EntryMod) o;
		return name.equals(file.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public boolean large() {
		return (flag & E_LARGE) != 0;
	}
}
