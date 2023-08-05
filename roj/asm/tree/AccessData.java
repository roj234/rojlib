package roj.asm.tree;

import roj.util.DynByteBuf;

import java.util.List;

/**
 * Class LOD 1
 *
 * @author Roj234
 * @since 2021/5/12 0:23
 */
public final class AccessData implements IClass {
	public final String name, parent;
	public List<String> itf;
	public List<MOF> methods, fields;
	public char acc;

	private final int off;
	private final byte[] byteCode;

	public AccessData(byte[] byteCode, int off, String name, String parent) {
		this.byteCode = byteCode;
		this.off = off;
		this.name = name;
		this.parent = parent;
	}

	@Override
	public String name() { return name; }
	@Override
	public String parent() { return parent; }
	@Override
	public List<String> interfaces() { return itf; }
	@Override
	public List<MOF> methods() { return methods; }
	@Override
	public List<MOF> fields() { return fields; }

	public final class MOF implements RawNode {
		public final String name, desc;
		/**
		 * Read only
		 */
		public char acc;
		private final int off;

		public MOF(String name, String desc, int off) {
			this.name = name;
			this.desc = desc;
			this.off = off;
		}

		@Override
		public char modifier() { return acc; }
		@Override
		public void modifier(int flag) {
			acc = (char) flag;
			byteCode[off] = (byte) (flag >>> 8);
			byteCode[off+1] = (byte) flag;
		}
		@Override
		public String name() { return name; }
		@Override
		public String rawDesc() { return desc; }

		@Override
		public String toString() {
			return "AccessD{" + name + ' ' + desc + '}';
		}
	}

	public char modifier() { return acc; }
	public void modifier(int flag) {
		acc = (char) flag;
		byteCode[off] = (byte) (flag >>> 8);
		byteCode[off+1] = (byte) flag;
	}

	@Override
	public DynByteBuf getBytes(DynByteBuf buf) { return buf.put(byteCode); }

	public byte[] toByteArray() { return byteCode; }

	@Override
	public String toString() {
		return "AccessData{" + "name='" + name + '\'' + ", extend='" + parent + '\'' + ", impl=" + itf + ", methods=" + methods + ", fields=" + fields + '}';
	}
}
