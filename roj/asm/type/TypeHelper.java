package roj.asm.type;

import roj.collect.SimpleList;
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
	/**
	 * 转换方法asm type字符串为对象
	 */
	public static List<Type> parseMethod(String desc) {
		List<Type> params = new SimpleList<>();
		parseMethod(desc, params);
		return params;
	}

	@SuppressWarnings("fallthrough")
	public static void parseMethod(String desc, List<Type> params) {
		int index = desc.indexOf(')');

		CharList tmp = IOUtil.getSharedCharBuf();

		boolean ref = false;
		int arr = 0;
		for (int i = 1; i < index; i++) {
			char c = desc.charAt(i);
			switch (c) {
				case ';':
					if (!ref) {
						throw new IllegalArgumentException(desc);
					} else {
						params.add(new Type(tmp.toString(), arr));
						tmp.clear();
						arr = 0;
						ref = false;
					}
					break;
				case '[':
					arr++;
					break;
				case 'L':
					if (!ref) {
						ref = true;
						break;
					}
				default:
					if (ref) {
						tmp.append(c);
					} else {
						if (!isValid(c)) throw new IllegalArgumentException(desc);
						params.add(arr == 0 ? std(c) : new Type(c, arr));
						arr = 0;
					}
					break;
			}
		}

		Type returns = parse(desc, index + 1);
		params.add(returns);
	}

	/**
	 * 方法参数所占空间
	 *
	 * @see roj.asm.tree.insn.InvokeItfInsnNode#serialize
	 */
	public static int paramSize(String desc) {
		int cnt = 0;
		int end = desc.indexOf(')');

		int clz = 0;
		for (int i = 1; i < end; i++) {
			char c = desc.charAt(i);
			switch (c) {
				case ';':
					if ((clz & 1) == 0) {
						throw new IllegalArgumentException(desc);
					} else {
						cnt++;
						clz = 0;
					}
					break;
				case '[':
					clz |= 2;
					break;
				case 'L':
					clz |= 1;
					break;
				default:
					if ((clz & 1) == 0) {
						if ((clz & 2) == 0 && (c == DOUBLE || c == LONG)) {
							cnt += 2;
						} else {
							cnt++;
						}
						clz = 0;
					}
					break;
			}
		}
		return cnt;
	}

	/**
	 * 方法返回类型
	 */
	public static Type parseReturn(String str) {
		int index = str.indexOf(')');
		if (index < 0) throw new IllegalArgumentException("不是方法描述");
		return parse(str, index + 1);
	}

	/**
	 * 转换字段asm type字符串为对象
	 */
	public static Type parseField(String s) {
		return parse(s, 0);
	}

	private static Type parse(String s, int off) {
		char c0 = s.charAt(off);
		switch (c0) {
			case ARRAY:
				int pos = s.lastIndexOf('[')+1;
				Type t = parse(s, pos);
				if (t.owner == null) t = new Type(t.type, pos - off);
				else t.setArrayDim(pos - off);
				return t;
			case CLASS:
				if (!s.endsWith(";")) throw new IllegalArgumentException("'类'类型 '" + s + "' 未以';'结束");
				return new Type(s.substring(off+1, s.length()-1));
			default: return std(c0);
		}
	}

	/**
	 * 转换字段type为字符串
	 */
	public static String getField(Type type) {
		if (type.owner == null && type.array() == 0) return toDesc(type.type);

		CharList sb = IOUtil.getSharedCharBuf();
		type.toDesc(sb);
		return sb.toString();
	}

	/**
	 * 转换方法type为字符串
	 */
	public static String getMethod(List<Type> list) {
		return getMethod(list, null);
	}
	public static String getMethod(List<Type> list, String prev) {
		CharList sb = IOUtil.getSharedCharBuf().append('(');

		for (int i = 0; i < list.size(); i++) {
			// return value
			if (i == list.size() - 1) sb.append(')');

			list.get(i).toDesc(sb);
		}
		return sb.equals(prev) ? prev : sb.toString();
	}

	/**
	 * 转换class为字段的asm type
	 */
	public static String class2asm(Class<?> clazz) {
		return class2asm(IOUtil.getSharedCharBuf(), clazz).toString();
	}
	public static CharList class2asm(CharList sb, Class<?> clazz) {
		Class<?> tmp;
		while ((tmp = clazz.getComponentType()) != null) {
			clazz = tmp;
			sb.append('[');
		}
		if (clazz.isPrimitive()) {
			switch (clazz.getName()) {
				case "int": return sb.append(INT);
				case "short": return sb.append(SHORT);
				case "double": return sb.append(DOUBLE);
				case "long": return sb.append(LONG);
				case "float": return sb.append(FLOAT);
				case "char": return sb.append(CHAR);
				case "byte": return sb.append(BYTE);
				case "boolean": return sb.append(BOOLEAN);
				case "void": return sb.append(VOID);
			}
		}
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
	 * @param trimPackage 删除包名
	 *
	 * @return void <init>(java.lang.String, double)
	 */
	public static String humanize(List<Type> types, String methodName, boolean trimPackage) {
		Type ret = types.remove(types.size() - 1);

		CharList sb = IOUtil.getSharedCharBuf();
		ret.toString(sb);
		sb.append(' ').append(methodName).append("(");

		if (!types.isEmpty()) {
			int i = 0;
			do {
				Type t = types.get(i++);
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

		types.add(ret);

		return sb.append(')').toString();
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

		switch (sb1.toString()) {
			case "int": return sb.append(INT);
			case "short": return sb.append(SHORT);
			case "double": return sb.append(DOUBLE);
			case "long": return sb.append(LONG);
			case "float": return sb.append(FLOAT);
			case "char": return sb.append(CHAR);
			case "byte": return sb.append(BYTE);
			case "boolean": return sb.append(BOOLEAN);
			case "void": return sb.append(VOID);
		}
		for (int i = 0; i < sb1.length(); i++) {
			if (sb1.charAt(i) == '.') sb1.set(i, '/');
		}
		sb.append('L').append(sb1);
		return sb.append(';');
	}

}