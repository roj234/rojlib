package roj.asm.nixim;

import roj.asm.tree.MoFNode;

/**
 * N/D Descriptor
 *
 * @author solo6975
 * @since 2021/10/3 20:59
 */
class DescEntry {
	String name, desc;

	public DescEntry() {}

	public DescEntry(MoFNode node) {
		name = node.name();
		desc = node.rawDesc();
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof DescEntry)) return false;

		DescEntry entry = (DescEntry) o;

		if (!name.equals(entry.name)) return false;
		return desc.equals(entry.desc);
	}

	@Override
	public final int hashCode() {
		int result = name.hashCode();
		result = 31 * result + desc.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return name + ' ' + desc;
	}
}
