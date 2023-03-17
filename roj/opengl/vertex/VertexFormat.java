package roj.opengl.vertex;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

/**
 * Vertex Format
 *
 * @author Roj233
 * @since 2021/9/18 13:23
 */
public final class VertexFormat {
	public static final int BYTE = 0;
	public static final int UBYTE = 1;
	public static final int SHORT = 2;
	public static final int USHORT = 3;
	public static final int INT = 4;
	public static final int UINT = 5;
	public static final int FLOAT = 6;

	public static final int POS = 0;
	public static final int NORMAL = 1;
	public static final int COLOR = 2;
	public static final int PADDING = 3;
	public static final int UV = 4;
	public static final int GENERIC = 5;

	public static final VertexFormat POSITION = builder().pos3f().build();
	public static final VertexFormat POSITION_TEX = builder().pos3f().uv2f().build();
	public static final VertexFormat POSITION_COLOR = builder().pos3f().color4ub().build();
	public static final VertexFormat POSITION_TEX_COLOR = builder().pos3f().uv2f().color4ub().build();

	VertexFormat() {}

	Entry[] entries;
	int sizeInBytes;

	public Entry[] entries() {
		return entries;
	}

	public Entry getEntry(int i) {
		return entries[i];
	}

	public int entryCount() {
		return entries.length;
	}

	public int getSize() {
		return sizeInBytes;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Entry {
		private final byte v;
		private final byte index;
		private int offset;

		Entry(byte v, byte index) {
			this.v = v;
			this.index = index;
		}

		public int usage() {
			return (v >> 3) & 7;
		}

		public int type() {
			return v & 7;
		}

		public int glType() {
			return (v & 7) + GL11.GL_BYTE;
		}

		public int typeSize() {
			int v = (((this.v + 2) & 7) >>> 1);
			// override for f32
			return v == 0 ? 4 : v;
		}

		public int elementCount() {
			return 1 + (0x3 & (v >>> 6));
		}

		public int totalSize() {
			return typeSize() * elementCount();
		}

		public int getOffset() {
			return offset;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Entry entry = (Entry) o;
			return v == entry.v && offset == entry.offset;
		}

		@Override
		public int hashCode() {
			return v | (offset << 8);
		}

		public int getIndex() {
			return index & 0xFF;
		}
	}

	public static final class Builder {
		private final VertexFormat format = new VertexFormat();
		private final ArrayList<Entry> entries = new ArrayList<>();
		private byte flags, count;

		public Builder() {}

		public Builder pos3f() {
			return put(POS, FLOAT, 3);
		}

		public Builder pos3(int type) {
			return put(POS, type, 3);
		}

		public Builder pos(int type, int count) {
			return put(POS, type, count);
		}

		public Builder uv2f() {
			return put(UV, FLOAT, 2);
		}

		public Builder uv2(int type) {
			return put(UV, type, 2);
		}

		public Builder uv(int type, int count) {
			return put(UV, type, count);
		}

		public Builder color4ub() {
			return put(COLOR, UBYTE, 4);
		}

		public Builder colorf() {
			return put(COLOR, FLOAT, 1);
		}

		public Builder normal3b() {
			return put(NORMAL, BYTE, 3);
		}

		public Builder pad1() {
			return put(PADDING, BYTE, 1);
		}

		public Builder pad(int count) {
			return put(PADDING, BYTE, count);
		}

		public Builder put(int usage, int type, int count) {
			if (count < 1 || count > 4) throw new IllegalArgumentException("Invalid count " + count);
			if (usage < 0 || type < 0) throw new IllegalArgumentException("Invalid usage or type");
			if (usage < UV) {
				if ((flags & (1 << usage)) != 0) {throw new IllegalArgumentException("Duplicate " + usage);} else flags |= (1 << usage);
			}
			byte v = (byte) (((count - 1) << 6) | ((usage & 7) << 3) | (type & 7));
			int index;
			if (usage == UV) {
				index = this.count++;
				if (index >= 32) throw new IndexOutOfBoundsException("At most 32 textures");
			} else {
				index = 0;
			}
			entries.add(new Entry(v, (byte) index));
			return this;
		}

		public VertexFormat build() {
			return build(true);
		}

		public VertexFormat build(boolean pad4) {
			VertexFormat f = format;

			if (pad4) {
				int size = 0;
				for (int i = 0; i < entries.size(); i++) {
					size += entries.get(i).totalSize();
				}
				if ((size & 3) != 0) pad(4 - (size & 3));
			}

			Entry[] entries = f.entries = this.entries.toArray(new Entry[this.entries.size()]);
			for (int i = 1; i < entries.length; i++) {
				Entry entry = entries[i - 1];
				entries[i].offset = entry.totalSize() + entry.offset;
			}
			f.sizeInBytes = entries[entries.length - 1].offset + entries[entries.length - 1].totalSize();
			return f;
		}
	}
}
