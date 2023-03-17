package roj.asm.tree;

import roj.asm.Parser;
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

	private final int cao;
	private final byte[] byteCode;

	public AccessData(byte[] byteCode, int cao, String name, String parent) {
		this.byteCode = byteCode;
		this.cao = cao;
		this.name = name;
		this.parent = parent;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String parent() {
		return parent;
	}

	@Override
	public List<String> interfaces() {
		return itf;
	}

	@Override
	public List<? extends MoFNode> methods() {
		return methods;
	}

	@Override
	public List<? extends MoFNode> fields() {
		return fields;
	}

	@Override
	public int type() {
		return Parser.CTYPE_ACCESS;
	}

	public final class MOF implements MoFNode {
		public final String name, desc;
		/**
		 * Read only
		 */
		public char acc;
		private final int dao;

		public MOF(String name, String desc, int dao) {
			this.name = name;
			this.desc = desc;
			this.dao = dao;
		}

		@Override
		public void accessFlag(int flag) {
			acc = (char) flag;
			byteCode[dao] = (byte) (flag >>> 8);
			byteCode[dao + 1] = (byte) flag;
		}

		@Override
		public char accessFlag() {
			return acc;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public String rawDesc() {
			return desc;
		}

		@Override
		public int type() {
			return Parser.MFTYPE_LOD1;
		}

		@Override
		public String toString() {
			return "AccessD{" + name + ' ' + desc + '}';
		}
	}

	public char accessFlag() {
		return (char) ((byteCode[cao] & 0xff) << 8 | (byteCode[cao + 1] & 0xff));
	}
	public void accessFlag(int flag) {
		acc = (char) flag;
		byteCode[cao] = (byte) (flag >>> 8);
		byteCode[cao + 1] = (byte) flag;
	}

	@Override
	public DynByteBuf getBytes(DynByteBuf buf) {
		return buf.put(byteCode);
	}

	public byte[] toByteArray() {
		return this.byteCode;
	}

	@Override
	public String toString() {
		return "AccessData{" + "name='" + name + '\'' + ", extend='" + parent + '\'' + ", impl=" + itf + ", methods=" + methods + ", fields=" + fields + '}';
	}
}
