package roj.asm.tree.attr;

import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
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
			if (handle.kind != 6 && handle.kind != 8) throw new IllegalStateException("The reference_kind item of the CONSTANT_MethodHandle_info structure should have the value 6 or 8 (§5.4.3.5).");
			// parsing method
			int argc = r.readUnsignedShort();
			List<Constant> list = new ArrayList<>(argc);
			for (int j = 0; j < argc; j++) {
				Constant c = pool.get(r);
				switch (c.type()) {
					case Constant.STRING:
					case Constant.CLASS:
					case Constant.INT:
					case Constant.LONG:
					case Constant.FLOAT:
					case Constant.DOUBLE:
					case Constant.METHOD_HANDLE:
					case Constant.METHOD_TYPE:
						break;
					default:
						throw new IllegalStateException("Only accept STRING CLASS INT LONG FLOAT DOUBLE METHOD_HANDLE or METHOD_TYPE, got " + c);
				}
				list.add(c);
			}
			CstRef ref = handle.getRef();
			methods.add(new Item(ref.className(), ref.desc().name().str(), ref.desc().getType().str(), handle.kind, ref.type(), list));
		}
	}

	public List<Item> methods;

	@Override
	public boolean isEmpty() { return methods.isEmpty(); }

	public static final class Kind {
		public static byte GETFIELD = 1, GETSTATIC = 2, PUTFIELD = 3, PUTSTATIC = 4, INVOKEVIRTUAL = 5, INVOKESTATIC = 6, INVOKESPECIAL = 7, NEW_INVOKESPECIAL = 8, INVOKEINTERFACE = 9;

		public static boolean verifyType(byte kind, byte type) {
			switch (kind) {
				case 1: case 2: case 3: case 4: return type == Constant.FIELD;
				case 5: case 8: return type == Constant.METHOD;
				case 6: case 7: return type == Constant.METHOD || type == Constant.INTERFACE;
				case 9: return type == Constant.INTERFACE;
			}
			return false;
		}

		static final byte[] toString = {Opcodes.GETFIELD, Opcodes.GETSTATIC, Opcodes.PUTFIELD, Opcodes.PUTSTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESTATIC, Opcodes.INVOKESPECIAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE};

		public static String toString(byte kind) { return Opcodes.showOpcode(toString[kind]); }

		public static byte validate(int kind) {
			if (kind < 1 || kind > 9) throw new IllegalArgumentException("Illegal kind "+kind+ ", Must in [1,9]");
			return (byte) kind;
		}
	}

	public static final class Item implements Cloneable {
		/**
		 * 仅比较
		 */
		public Item(String owner, String name, String desc, int kind) {
			this.owner = owner;
			this.name = name;
			this.rawDesc = desc;
			checkInvariant(desc);
			this.kind = Kind.validate(kind);
		}

		public Item(String owner, String name, String desc, byte kind, byte methodType, List<Constant> arguments) {
			this.owner = owner;
			this.name = name;
			this.rawDesc = desc;
			checkInvariant(desc);
			this.kind = Kind.validate(kind);
			this.methodType = methodType;
			this.arguments = arguments;
		}

		public String owner, name;

		private String rawDesc;
		private List<Type> params;
		private Type returnType;

		private void initPar() {
			if (params == null) {
				params = TypeHelper.parseMethod(rawDesc);
				returnType = params.remove(params.size() - 1);
			}
		}

		public final Type factoryReturnType() {
			initPar();
			return returnType;
		}

		public final List<Type> factoryParameters() {
			initPar();
			return params;
		}

		public final String factoryDesc() {
			return rawDesc;
		}

		public final void factoryDesc(String param) {
			checkInvariant(param);
			this.rawDesc = param;
			if (params != null) {
				params.clear();
				TypeHelper.parseMethod(param, params);
				returnType = params.remove(params.size() - 1);
			}
		}

		public List<Constant> arguments;

		public byte kind, methodType;

		public void toByteArray(ConstantPool pool, DynByteBuf w) {
			if (!Kind.verifyType(kind, methodType)) {
				throw new IllegalArgumentException("Method type " + methodType + " doesn't fit with lambda kind " + kind);
			}
			if (params != null) {
				params.add(returnType);
				rawDesc = TypeHelper.getMethod(params);
				params.remove(params.size() - 1);
			}
			w.putShort(pool.getMethodHandleId(owner, name, rawDesc, kind, methodType));

			w.putShort(arguments.size());
			for (int i = 0; i < arguments.size(); i++) {
				w.putShort(pool.reset(arguments.get(i)).getIndex());
			}
		}

		public String toString() {
			initPar();

			CharList sb = new CharList()
				.append("类型: ").append(Kind.toString(kind))
				.append('\n').append(returnType).append(": ").append(owner).append('.').append(name).append('(');

			if (params.size() > 3) {
				int i = 3;
				while (true) {
					sb.append(params.get(i));
					if (++i == params.size()) break;
					sb.append(", ");
				}
			}
			sb.append(")\n方法参数: ");

			if (isInvokeMethod()) {
				sb.append("(LambdaMetafactory)")
				  .append("\n  形参: ").append(((CstMethodType) arguments.get(0)).name().str())
				  .append("\n  实参: ").append(((CstMethodType) arguments.get(2)).name().str())
				  .append("\n  kind: ").append(((CstMethodHandle) arguments.get(1)).kind)
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

		static void checkInvariant(String desc) {
			if (!desc.startsWith("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/")) throw new IllegalArgumentException("Invalid descriptor: " + desc);
		}

		public boolean equals0(Item method) {
			return method.kind == this.kind && method.owner.equals(this.owner) && method.name.equals(this.name) && method.rawDesc.equals(this.rawDesc);
		}

		public String interfaceDesc() {
			CstMethodType mType = (CstMethodType) arguments.get(0);
			return mType.name().str();
		}

		public CstRef implementor() {
			CstMethodHandle handle = (CstMethodHandle) arguments.get(1);
			return handle.getRef();
		}

		public boolean isInvokeMethod() {
			return owner.equals("java/lang/invoke/LambdaMetafactory");
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
			if (params != null) {
				params.add(returnType);
				slf.rawDesc = TypeHelper.getMethod(params);
				slf.params = null;
				params.remove(params.size() - 1);
			}
			return slf;
		}
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
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