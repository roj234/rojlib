package roj.asm;

import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * Class LOD 1
 *
 * @author Roj234
 * @since 2021/5/12 0:23
 */
public final class ClassView implements IClass {
	public final String name, parent;
	public List<String> interfaces;
	public List<MOF> methods, fields;
	public char modifier;

	private final int off;
	private final byte[] byteCode;

	public ClassView(byte[] byteCode, int off, String name, String parent) {
		this.byteCode = byteCode;
		this.off = off;
		this.name = name;
		this.parent = parent;
	}

	@Override public String name() { return name; }
	@Override public String parent() { return parent; }
	@Override public List<String> interfaces() { return interfaces; }
	@Override public List<MOF> fields() { return fields; }
	@Override public List<MOF> methods() { return methods; }

	public final class MOF implements RawNode {
		public final String name, desc;
		/**
		 * Read only
		 */
		public char modifier;
		private final int off;

		public MOF(CNode node) {
			this(node.name(), node.rawDesc(), -1);
			modifier = node.modifier();
		}
		public MOF(String name, String desc, int off) {
			this.name = name;
			this.desc = desc;
			this.off = off;
		}

		public ClassView owner() {return ClassView.this;}
		@Override public String ownerClass() {return ClassView.this.name;}
		@Override public String name() { return name; }
		@Override public String rawDesc() { return desc; }

		@Override public char modifier() { return modifier; }
		@Override public void modifier(int flag) {
			modifier = (char) flag;
			byteCode[off] = (byte) (flag >>> 8);
			byteCode[off+1] = (byte) flag;
		}

		@Override public String toString() {return toString(IOUtil.getSharedCharBuf()).toString();}
		public CharList toString(CharList sb) {
			Opcodes.showModifiers(modifier, Opcodes.ACC_SHOW_METHOD, sb).append(' ');
			TypeHelper.humanize(Type.methodDesc(desc), name, false, sb);
			return sb;
		}
	}

	public char modifier() { return modifier; }
	public void modifier(int flag) {
		modifier = (char) flag;
		byteCode[off] = (byte) (flag >>> 8);
		byteCode[off+1] = (byte) flag;
	}

	@Override
	public DynByteBuf toByteArray(DynByteBuf buf) { return buf.put(byteCode); }

	public byte[] toByteArray() { return byteCode; }

	@Override
	public String toString() {
		var sb = new CharList().append("<SimpleClassNode[ReadOnlyView]>\n");

		Opcodes.showModifiers(modifier, Opcodes.ACC_SHOW_CLASS, sb).append(' ');
		TypeHelper.toStringOptionalPackage(sb, name);

		String parent = parent();
		if (!"java/lang/Object".equals(parent) && parent != null) {
			TypeHelper.toStringOptionalPackage(sb.append(" extends "), parent);
		}

		var _list = interfaces;
		if (!_list.isEmpty()) {
			sb.append(" implements ");
			for (int j = 0; j < _list.size();) {
				String i = _list.get(j);
				TypeHelper.toStringOptionalPackage(sb, i);
				if (++j == _list.size()) break;
				sb.append(", ");
			}
		}

		sb.append(" {");
		if (!fields.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < fields.size(); i++) {
				fields.get(i).toString(sb).append("\n");
			}
		}
		if (!methods.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < methods.size(); i++) {
				methods.get(i).toString(sb).append('\n');
			}
		}

		return sb.append('}').toStringAndFree();
	}
}