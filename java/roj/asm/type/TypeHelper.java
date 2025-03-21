package roj.asm.type;

import roj.asm.insn.AbstractCodeWriter;
import roj.collect.MyHashMap;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;

import static roj.asm.type.Type.MAP;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class TypeHelper {
	/** 这个字段几乎影响全局的toString中类名是否移除包名 */
	public static boolean TYPE_TOSTRING_NO_PACKAGE = true;
	public static void toStringOptionalPackage(CharList sb, String type) {
		if (TYPE_TOSTRING_NO_PACKAGE) {
			int start = type.lastIndexOf('/')+1;
			if (Tokenizer.haveSlashes(type, start)) {
				Tokenizer.addSlashes(type, start, sb.append('"'), '\0').append('"');
			} else {
				sb.append(type, start, type.length());
			}
		} else {
			sb.append(type);
		}
	}

	/**
	 * 方法参数所占空间.
	 * @implNote 未检查方法描述的有效性
	 *
	 * @see AbstractCodeWriter#invokeItf(String, String, String)
	 */
	public static int paramSize(String desc) {
		int size = 0;
		int end = desc.indexOf(')');
		for (int i = 1; i < end; i++) {
			char c = desc.charAt(i);
			switch (c) {
				case Type.ARRAY -> {}
				case Type.CLASS -> {
					i = desc.indexOf(';', i+1);
					if (i <= 0) throw new IllegalArgumentException("方法描述无效:"+desc);
					size++;
				}
				case Type.DOUBLE, Type.LONG -> {
					if (desc.charAt(i - 1) == '[') size++;
					else size += 2;
				}
				default -> {
					if (!Type.isValid(c)) throw new IllegalArgumentException("方法描述无效:"+desc);
					size++;
				}
			}
		}
		return size;
	}

	static final MyHashMap<String, Object[]> ByName;
	static {
		ByName = new MyHashMap<>(MAP.length);
		for (Object[] o : MAP) {
			if (o != null && o[1] != null) ByName.put(o[1].toString(), o);
		}
	}

	/**
	 * 转换class为字段的asm type
	 */
	public static String class2asm(Class<?> clazz) { return class2asm(IOUtil.getSharedCharBuf(), clazz).toString(); }
	public static CharList class2asm(CharList sb, Class<?> clazz) {
		Class<?> tmp;
		while ((tmp = clazz.getComponentType()) != null) {
			clazz = tmp;
			sb.append('[');
		}

		if (clazz.isPrimitive()) return sb.append(ByName.get(clazz.getName())[0].toString());
		return sb.append('L').append(clazz.getName().replace('.', '/')).append(';');
	}

	/**
	 * 转换class为方法的asm type
	 */
	public static String class2asm(Class<?>[] classes, Class<?> returns) {
		CharList sb = IOUtil.getSharedCharBuf().append('(');

		for (Class<?> clazz : classes) {
			class2asm(sb, clazz);
		}
		sb.append(')');
		return class2asm(sb, returns).toString();
	}

	/**
	 * 转换方法asm type为人类可读(实际上是mojang用的map)类型
	 *
	 * @param types [String, double, void]
	 * @param methodName <init>
	 * @param trimPackage 删除包名(删除了就不能转换回去了！)
	 *
	 * @return void <init>(java.lang.String, double)
	 */
	public static String humanize(List<Type> types, String methodName, boolean trimPackage) {
		return humanize(types, methodName, trimPackage, IOUtil.getSharedCharBuf()).toString();
	}
	public static CharList humanize(List<Type> types, String methodName, boolean trimPackage, CharList sb) {
		Type t = types.remove(types.size() - 1);

		if (trimPackage && t.owner != null) {
			String o = t.owner;
			sb.append(o, o.lastIndexOf('/') + 1, o.length());
			for (int j = t.array() - 1; j >= 0; j--) sb.append("[]");
		} else {
			t.toString(sb);
		}
		sb.append(' ').append(methodName).append("(");

		if (!types.isEmpty()) {
			int i = 0;
			do {
				t = types.get(i++);
				if (trimPackage && t.owner != null) {
					String o = t.owner;
					sb.append(o, o.lastIndexOf('/') + 1, o.length());
					for (int j = t.array() - 1; j >= 0; j--) sb.append("[]");
				} else {
					t.toString(sb);
				}
				if (i == types.size()) break;
				sb.append(", ");
			} while (true);
		}

		types.add(t);

		return sb.append(')');
	}

	/**
	 * 转换人类可读(实际上是mojang用的map)类型为方法asm type
	 *
	 * @param in java.lang.String, double
	 * @param out void
	 *
	 * @return (Ljava / lang / String ; D)V
	 */
	public static String dehumanize(CharSequence in, CharSequence out) {
		CharList sb = IOUtil.getSharedCharBuf().append('(');

		List<String> list = TextUtil.split(new ArrayList<>(), in, ',');
		for (int i = 0; i < list.size(); i++) {
			String s = list.get(i);
			dehumanize0(s, sb);
		}
		return dehumanize0(out, sb.append(')')).toString();
	}
	private static CharList dehumanize0(CharSequence z, CharList sb) {
		if (z.length() == 0) return sb;
		CharList sb1 = new CharList().append(z);

		// Array filter
		while (sb1.length() > 2 && sb1.charAt(sb1.length() - 1) == ']' && sb1.charAt(sb1.length() - 2) == '[') {
			sb1.setLength(sb1.length() - 2);
			sb.append('[');
		}

		Object[] arr = ByName.get(sb1);
		if (arr != null) return sb.append(arr[0]);

		return sb.append('L').append(sb1.replace('.', '/')).append(';');
	}

	public static IType componentType(IType t) {
		IType type = t.clone();
		type.setArrayDim(type.array()-1);
		return type;
	}

	public static IType arrayTypeNC(IType t) {
		if (t.isPrimitive()) t = t.clone();
		t.setArrayDim(t.array()+1);
		return t;
	}
}