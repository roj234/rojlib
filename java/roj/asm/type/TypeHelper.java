package roj.asm.type;

import roj.asm.AsmShared;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;

import static roj.asm.type.Type.*;

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

	public static List<Type> parseMethod(String desc) {
		SimpleList<Type> p = AsmShared.local().methodTypeTmp();
		parseMethod(desc, p); return new SimpleList<>(p);
	}
	@SuppressWarnings("fallthrough")
	public static void parseMethod(String desc, List<Type> params) {
		if (desc.charAt(0) != '(') throw new IllegalArgumentException("方法描述符无效:"+desc);

		int arr = 0;
		for (int i = 1; i < desc.length(); i++) {
			char c = desc.charAt(i);
			switch (c) {
				case ')':
					Type returns = parse(desc, i+1);
					params.add(returns);
					return;
				case '[':
					arr++;
				break;
				case 'L':
					int j = desc.indexOf(';', i+1);
					if (j < 0) throw new IllegalArgumentException("雷星未终止:"+desc);
					params.add(new Type(desc.substring(i+1, j), arr));
					arr = 0;
					i = j;
				break;
				default:
					if (!isValid(c)) throw new IllegalArgumentException(desc);
					params.add(arr == 0 ? std(c) : new Type(c, arr));
					arr = 0;
				break;
			}
		}
	}

	/**
	 * 方法参数所占空间
	 *
	 * @see roj.asm.visitor.AbstractCodeWriter#invokeItf(String, String, String)
	 */
	public static int paramSize(String desc) {
		int size = 0;
		int end = desc.indexOf(')');

		for (int i = 1; i < end; i++) {
			char c = desc.charAt(i);
			switch (c) {
				case ARRAY: break;
				case CLASS:
					i = desc.indexOf(';', i+1);
					if (i <= 0) throw new IllegalArgumentException("class end missing: "+desc);
					size++;
				break;
				case DOUBLE: case LONG:
					if (desc.charAt(i-1) == '[') size++;
					else size += 2;
				break;
				default:
					if (!isValid(c)) throw new IllegalArgumentException("unknown character '"+c+"' in "+desc);
					size++;
				break;
			}
		}
		return size;
	}

	public static Type parseReturn(String desc) {
		int index = desc.indexOf(')');
		if (index < 0) throw new IllegalArgumentException("不是方法描述");
		return parse(desc, index+1);
	}

	public static Type parseField(String desc) { return parse(desc, 0); }

	private static Type parse(String desc, int off) {
		char c0 = desc.charAt(off);
		switch (c0) {
			case ARRAY:
				int pos = desc.lastIndexOf('[')+1;
				Type t = parse(desc, pos);
				if (t.owner == null) t = new Type(t.type, pos - off);
				else t.setArrayDim(pos - off);
				return t;
			case CLASS:
				if (!desc.endsWith(";")) throw new IllegalArgumentException("'类'类型 '" + desc + "' 未以';'结束");
				return new Type(desc.substring(off+1, desc.length()-1));
			default: return std(c0);
		}
	}

	/**
	 * 转换字段type为字符串
	 * @see Type#toDesc()
	 */
	public static String getField(IType type) {
		if (type instanceof Type && type.isPrimitive()) return toDesc(((Type) type).type);

		CharList sb = IOUtil.getSharedCharBuf();
		type.toDesc(sb);
		return sb.toString();
	}

	/**
	 * 转换方法type为字符串
	 */
	public static String getMethod(List<Type> list) { return getMethod(list, null); }
	public static String getMethod(List<Type> list, String prev) {
		CharList sb = IOUtil.getSharedCharBuf().append('(');

		for (int i = 0; i < list.size(); i++) {
			// return value
			if (i == list.size() - 1) sb.append(')');

			list.get(i).toDesc(sb);
		}
		return sb.equals(prev) ? prev : sb.toString();
	}

	private static final MyHashMap<String, Object[]> PD;
	static {
		PD = new MyHashMap<>(MAP.length);
		for (Object[] o : MAP) {
			if (o != null && o[1] != null) PD.put(o[1].toString(), o);
		}
	}

	public static int toPrimitiveType(String primitiveName) {
		Object[] objects = PD.get(primitiveName);
		return objects == null ? -1 : objects[0].toString().charAt(0);
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

		if (clazz.isPrimitive()) return sb.append(PD.get(clazz.getName())[0].toString());
		return sb.append('L').append(clazz.getName().replace('.', '/')).append(';');
	}

	public static Type class2type(Class<?> clazz) {
		int arr = 0;
		Class<?> tmp;
		while ((tmp = clazz.getComponentType()) != null) {
			clazz = tmp;
			arr++;
		}

		if (clazz.isPrimitive()) {
			Type type = (Type) PD.get(clazz.getName())[2];
			return arr == 0 ? type : new Type(type.type, arr);
		}

		return new Type(clazz.getName().replace('.', '/'), arr);
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

		Object[] arr = PD.get(sb1);
		if (arr != null) return sb.append(arr[0]);

		return sb.append('L').append(sb1.replace('.', '/')).append(';');
	}

	public static IType componentType(IType t) {
		IType type = t.clone();
		type.setArrayDim(type.array()-1);
		return type;
	}
}