package roj.asm.attr;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.AsmCache;
import roj.asm.cp.*;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class BootstrapMethods extends Attribute {
	public BootstrapMethods() { methods = new ArrayList<>(); }
	public BootstrapMethods(DynByteBuf r, ConstantPool pool) {
		int len = r.readUnsignedShort();
		List<Item> methods = this.methods = new ArrayList<>(len);
		while (len-- > 0) {
			CstMethodHandle handle = pool.get(r);

			// parsing arguments
			int argc = r.readUnsignedShort();
			var list = new ArrayList<Constant>(argc);
			for (int i = 0; i < argc; i++) {
				Constant c = pool.get(r);
				switch (c.type()) {
					case Constant.STRING, Constant.CLASS, Constant.INT, Constant.LONG, Constant.FLOAT, Constant.DOUBLE, Constant.METHOD_HANDLE, Constant.METHOD_TYPE -> {}
					default -> throw new IllegalStateException("Unexpected constant: "+c);
				}
				list.add(c);
			}
			methods.add(new Item(handle.kind, handle.getTarget(), list));
		}
	}

	public List<Item> methods;

	@Override
	public boolean writeIgnore() { return methods.isEmpty(); }

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

	public static final class Kind {
		public static final byte GETFIELD = 1, GETSTATIC = 2, PUTFIELD = 3, PUTSTATIC = 4, INVOKEVIRTUAL = 5, INVOKESTATIC = 6, INVOKESPECIAL = 7, NEW_INVOKESPECIAL = 8, INVOKEINTERFACE = 9;
		private static final String[] toString = {"GetField","GetStatic","PutField","PutStatic","InvokeVirtual","InvokeStatic","InvokeSpecial","NewObject","InvokeInterface"};
		public static String toString(byte kind) { return toString[kind-1]; }

		public static void validate(byte kind, CstRef linker) {
			var type = linker.type();
			if (switch (kind) {
				case GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC -> type != Constant.FIELD;
				case INVOKEVIRTUAL, NEW_INVOKESPECIAL -> type != Constant.METHOD;
				case INVOKESTATIC, INVOKESPECIAL -> type != Constant.METHOD && type != Constant.INTERFACE;
				case INVOKEINTERFACE -> type != Constant.INTERFACE;
				default -> true;
			}) {
				throw new IllegalArgumentException("链接器"+linker+"与操作类型"+Kind.toString(kind)+"不兼容");
			}
		}
	}

	public static final class Item {
		public Item(CstRef linker) {this(Kind.INVOKESTATIC, linker, Collections.emptyList());}
		public Item(CstRef linker, Constant argument) {this(Kind.INVOKESTATIC, linker, Collections.singletonList(argument));}
		public Item(CstRef linker, Constant... arguments) {this(Kind.INVOKESTATIC, linker, Arrays.asList(arguments));}
		public Item(@MagicConstant(intValues = {Kind.INVOKESTATIC, Kind.NEW_INVOKESPECIAL}) byte kind, CstRef linker, List<Constant> arguments) {
			if (kind != Kind.INVOKESTATIC && kind != Kind.NEW_INVOKESPECIAL)
				throw new IllegalStateException("引导方法调用类型非法 不允许"+Kind.toString(kind)+" (JVMS §5.4.3.5).");

			this.kind = kind;
			this.linker = linker;
			this.arguments = arguments;
		}

		@MagicConstant(intValues = {Kind.INVOKESTATIC, Kind.NEW_INVOKESPECIAL})
		public byte kind;
		public CstRef linker;
		public List<Constant> arguments;

		public boolean isLambda() {return linker.owner().equals("java/lang/invoke/LambdaMetafactory");}
		public String lambdaInterfaceDesc() {
			assert isLambda();
			CstMethodType mType = (CstMethodType) arguments.get(0);
			return mType.value().str();
		}

		private static void validate(String desc) {
			// 明确声明基本类型、String、Class、MethodHandle 或 MethodType参数，或使用Object[]接收任意个剩余参数
			if (desc.startsWith("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;")) return;
			if (desc.startsWith("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;")) return;
			throw new IllegalArgumentException("无效的方法签名，不是LoadDynamic也不是InvokeDynamic");
		}

		public void toByteArray(ConstantPool pool, DynByteBuf w) {
			//validate(linker.rawDesc());
			Kind.validate(kind, linker);
			w.putShort(pool.getMethodHandleId(kind, linker))
			 .putShort(arguments.size());
			for (int i = 0; i < arguments.size(); i++) {
				w.putShort(pool.fit(arguments.get(i)));
			}
		}

		public String toString() {
			ArrayList<Type> in = AsmCache.getInstance().methodTypeTmp();
			var out = Type.getArgumentTypes(linker.rawDesc(), in);

			CharList sb = new CharList()
					.append("类型: ").append(Kind.toString(kind))
					.append('\n').append(out).append(": ").append(linker.owner()).append('.').append(linker.name()).append('(');

			if (in.size() > 3) {
				int i = 3;
				while (true) {
					sb.append(in.get(i));
					if (++i == in.size()) break;
					sb.append(", ");
				}
			}
			sb.append(")\n方法参数: ");

			if (isLambda()) {
				sb.append("(Lambda)")
						.append("\n  形参: ").append(((CstMethodType) arguments.get(0)).value().str())
						.append("\n  实参: ").append(((CstMethodType) arguments.get(2)).value().str())
						.append("\n  类型: ").append(((CstMethodHandle) arguments.get(1)).kind)
						.append("\n  目标: ").append(((CstMethodHandle) arguments.get(1)).getTarget());
			} else {
				List<String> list = new ArrayList<>(arguments.size());
				for (int i = 0; i < arguments.size(); i++) {
					list.add(arguments.get(i).getClass().getSimpleName().substring(3));
				}
				sb.append(list);
			}
			return sb.append('\n').toStringAndFree();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Item item = (Item) o;

			if (kind != item.kind) return false;
			if (!linker.equals(item.linker)) return false;
			return arguments.equals(item.arguments);
		}

		@Override
		public int hashCode() {
			int result = linker.hashCode();
			result = 31 * result + kind;
			result = 31 * result + arguments.hashCode();
			return result;
		}
	}

}