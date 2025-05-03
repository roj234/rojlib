package roj.asm.attr;

import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class BootstrapMethods extends Attribute {
	public BootstrapMethods() { methods = new ArrayList<>(); }
	public BootstrapMethods(DynByteBuf r, ConstantPool pool) {
		int len = r.readUnsignedShort();
		List<Item> methods = this.methods = new SimpleList<>(len);
		while (len-- > 0) {
			CstMethodHandle handle = (CstMethodHandle) pool.get(r);
			if (handle.kind != Kind.INVOKESTATIC && handle.kind != Kind.NEW_INVOKESPECIAL)
				throw new IllegalStateException("The reference_kind item of the CONSTANT_MethodHandle_info structure should have the value 6 or 8 (§5.4.3.5).");

			// parsing method
			int argc = r.readUnsignedShort();
			List<Constant> list = new ArrayList<>(argc);
			for (int j = 0; j < argc; j++) {
				Constant c = pool.get(r);
				switch (c.type()) {
					case Constant.STRING, Constant.CLASS, Constant.INT, Constant.LONG, Constant.FLOAT, Constant.DOUBLE, Constant.METHOD_HANDLE, Constant.METHOD_TYPE -> {}
					default -> throw new IllegalStateException("Unexpected constant: "+c);
				}
				list.add(c);
			}
			CstRef ref = handle.getRef();
			methods.add(new Item(handle.kind, ref, list));
		}
	}

	public List<Item> methods;

	@Override
	public boolean writeIgnore() { return methods.isEmpty(); }

	public static final class Kind {
		public static final byte GETFIELD = 1, GETSTATIC = 2, PUTFIELD = 3, PUTSTATIC = 4, INVOKEVIRTUAL = 5, INVOKESTATIC = 6, INVOKESPECIAL = 7, NEW_INVOKESPECIAL = 8, INVOKEINTERFACE = 9;

		public static boolean verifyType(byte kind, byte type) {
			return switch (kind) {
				case GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC -> type == Constant.FIELD;
				case INVOKEVIRTUAL, NEW_INVOKESPECIAL -> type == Constant.METHOD;
				case INVOKESTATIC, INVOKESPECIAL -> type == Constant.METHOD || type == Constant.INTERFACE;
				case INVOKEINTERFACE -> type == Constant.INTERFACE;
				default -> false;
			};
		}

		static final byte[] toString = {Opcodes.GETFIELD, Opcodes.GETSTATIC, Opcodes.PUTFIELD, Opcodes.PUTSTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESTATIC, Opcodes.INVOKESPECIAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE};

		public static String toString(byte kind) { return Opcodes.showOpcode(toString[kind]); }

		public static byte validate(int kind) {
			if (kind < 1 || kind > 9) throw new IllegalArgumentException("Illegal kind "+kind+ ", Must in [1,9]");
			return (byte) kind;
		}
	}

	public static final class Item implements Cloneable {
		Item(byte kind, CstRef ref, List<Constant> list) {
			this.kind = kind;
			this.methodType = ref.type();
			this.owner = ref.owner();
			this.name = ref.name();
			this.desc = ref.rawDesc();
			checkInvariant(desc);
			this.arguments = list;
		}

		/**
		 * 仅比较
		 */
		public Item(String owner, String name, String desc, int kind) {
			this.kind = Kind.validate(kind);
			this.owner = owner;
			this.name = name;
			this.desc = desc;
			checkInvariant(desc);
		}
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Item item)) return false;

			if (kind != item.kind) return false;
			if (methodType != item.methodType) return false;
			if (!owner.equals(item.owner)) return false;
			if (!name.equals(item.name)) return false;
			if (!arguments.equals(item.arguments)) return false;
			return rawDesc().equals(item.rawDesc());
		}
		@Override
		public int hashCode() {
			int result = owner.hashCode();
			result = 31 * result + name.hashCode();
			result = 31 * result + rawDesc().hashCode();
			result = 31 * result + kind;
			result = 31 * result + methodType;
			return result;
		}

		public Item(String owner, String name, String desc, byte kind, byte methodType, List<Constant> arguments) {
			checkInvariant(desc);
			this.kind = Kind.validate(kind);
			this.methodType = methodType;
			this.owner = owner;
			this.name = name;
			this.desc = desc;
			this.arguments = arguments;
		}

		public String owner, name;

		private String desc;
		private List<Type> in;
		private Type out;

		public byte kind, methodType;

		public List<Constant> arguments;

		static void checkInvariant(String desc) {
			//if (!desc.startsWith("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"))
			//	throw new IllegalArgumentException("invokedynamic的前三个参数应当固定: "+desc);
		}

		public String interfaceDesc() {
			CstMethodType mType = (CstMethodType) arguments.get(0);
			return mType.name().str();
		}

		public boolean isInvokeMethod() {
			return owner.equals("java/lang/invoke/LambdaMetafactory");
		}

		private void initPar() {
			if (in == null) {
				in = Type.methodDesc(desc);
				out = in.remove(in.size()-1);
			}
		}

		public final List<Type> parameters() {initPar();return in;}
		public final Type returnType() {initPar();return out;}

		public final String rawDesc() {
			if (in != null) {
				in.add(out);
				desc = Type.toMethodDesc(in, desc);
				in.remove(in.size()-1);
			}
			return desc;
		}
		public final void rawDesc(String param) {
			checkInvariant(param);
			desc = param;
			in = null;
			out = null;
		}

		public void toByteArray(ConstantPool pool, DynByteBuf w) {
			if (!Kind.verifyType(kind, methodType)) throw new IllegalArgumentException("Method type "+methodType+" doesn't fit with lambda kind " + kind);

			w.putShort(pool.getMethodHandleId(owner, name, rawDesc(), kind, methodType));

			w.putShort(arguments.size());
			for (int i = 0; i < arguments.size(); i++) {
				w.putShort(pool.fit(arguments.get(i)));
			}
		}

		public String toString() {
			initPar();

			CharList sb = new CharList()
				.append("类型: ").append(Kind.toString(kind))
				.append('\n').append(out).append(": ").append(owner).append('.').append(name).append('(');

			if (in.size() > 3) {
				int i = 3;
				while (true) {
					sb.append(in.get(i));
					if (++i == in.size()) break;
					sb.append(", ");
				}
			}
			sb.append(")\n方法参数: ");

			if (isInvokeMethod()) {
				sb.append("(LambdaMetafactory)")
				  .append("\n  形参: ").append(((CstMethodType) arguments.get(0)).name().str())
				  .append("\n  实参: ").append(((CstMethodType) arguments.get(2)).name().str())
				  .append("\n  类型: ").append(((CstMethodHandle) arguments.get(1)).kind)
				  .append("\n  目标: ").append(((CstMethodHandle) arguments.get(1)).getRef());
			} else {
				List<String> list = new SimpleList<>(arguments.size());
				for (int i = 0; i < arguments.size(); i++) {
					list.add(arguments.get(i).getClass().getSimpleName().substring(3));
				}
				sb.append(list);
			}
			return sb.append('\n').toStringAndFree();
		}

		@Override
		public Item clone() {
			Item slf;
			try {
				slf = (Item) super.clone();
			} catch (CloneNotSupportedException e) {
				return Helpers.nonnull();
			}
			List<Constant> args = slf.arguments = new ArrayList<>(slf.arguments);
			for (int i = 0; i < args.size(); i++) {
				args.set(i, args.get(i).clone());
			}
			if (in != null) {
				in.add(out);
				slf.desc = Type.toMethodDesc(in);
				slf.in = null;
				in.remove(in.size() - 1);
			}
			return slf;
		}
	}

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.putShort(methods.size());
		for (int i = 0; i < methods.size(); i++) {
			methods.get(i).toByteArray(pool, w);
		}
	}

	@Override
	public String name() { return "BootstrapMethods"; }
	public String toString() {
		StringBuilder sb = new StringBuilder("BootstrapMethods: \n");
		int i = 0;
		for (Item method : methods) {
			sb.append("         #").append(i++).append(": ").append(method).append('\n');
		}
		sb.delete(sb.length() - 2, sb.length());
		return sb.toString();
	}
}