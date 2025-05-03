package roj.asm.type;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Range;
import roj.asm.AsmCache;
import roj.asm.Opcodes;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * 类型
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public sealed class Type implements IType permits Type.DirtyHacker {
	public static final char ARRAY = '[', CLASS = 'L', VOID = 'V', BOOLEAN = 'Z', BYTE = 'B', CHAR = 'C', SHORT = 'S', INT = 'I', FLOAT = 'F', DOUBLE = 'D', LONG = 'J';

	static final Object[][] MAP = new Object[26][];
	static {
		MAP[CLASS-BYTE] = new Object[]{"L", "object", new Type(CLASS), 4, "A"};
		MAP[VOID-BYTE] = new Object[]{"V", "void", new Type(VOID), 5, null};
		MAP[BOOLEAN-BYTE] = new Object[]{"Z", "boolean", new Type(BOOLEAN), 0, "I"};
		MAP[BYTE-BYTE] = new Object[]{"B", "byte", new Type(BYTE), 0, "I"};
		MAP[CHAR-BYTE] = new Object[]{"C", "char", new Type(CHAR), 0, "I"};
		MAP[SHORT-BYTE] = new Object[]{"S", "short", new Type(SHORT), 0, "I"};
		MAP[INT-BYTE] = new Object[]{"I", "int", new Type(INT), 0, "I"};
		MAP[FLOAT-BYTE] = new Object[]{"F", "float", new Type(FLOAT), 2, "F"};
		MAP[DOUBLE-BYTE] = new Object[]{"D", "double", new Type(DOUBLE), 3, "D"};
		MAP[LONG-BYTE] = new Object[]{"J", "long", new Type(LONG), 1, "L"};
	}

	public static String getDesc(int type) {
		if (type < BYTE || type > ARRAY) return Helpers.nonnull();
		Object[] arr = MAP[type-BYTE];
		if (arr == null) return Helpers.nonnull();
		return arr[0].toString();
	}
	public static String getName(int type) {
		if (type < BYTE || type > ARRAY) return Helpers.nonnull();
		Object[] arr = MAP[type-BYTE];
		if (arr == null) return Helpers.nonnull();
		return arr[1].toString();
	}

	public static boolean isValid(int c) {
		if (c < BYTE || c > ARRAY) return false;
		return MAP[c-BYTE] != null;
	}

	// for Lava Compiler only
	// since 2024/11/30 13:30
	public static final class DirtyHacker extends Type {
		public DirtyHacker(int type, String owner) {
			super((byte) type, true);
			this.owner = owner;
		}

		@Override public boolean isPrimitive() {return false;}
		//@Override public int getActualType() {return type;}
		//@Override public Type rawType() {return std(type);}

		@Override
		public void toString(CharList sb) {
			super.toString(sb);
			if (type != 'L') sb.append("<alias of ").append(getName(type)).append(">");
		}
	}

	public final byte type;
	public String owner;
	private byte array;

	private Type(byte c, boolean _unused) {type = c;}
	@Deprecated public Type(char type) {this.type = (byte) type;}

	/**
	 * TYPE_OTHER
	 */
	private Type(int type, int array) {
		if (!isValid(type)) throw new IllegalArgumentException("类型不合法: "+(char)type);
		if (type == ARRAY) throw new IllegalArgumentException("不能创建ARRAY类型的实例");
		if (type == CLASS) throw new IllegalArgumentException("不能使用此方法创建CLASS类型的实例");

		this.type = (byte) type;
		setArrayDim(array);
	}
	/**
	 * TYPE_CLASS
	 */
	private Type(String owner, int array) {
		this.type = CLASS;
		this.owner = Objects.requireNonNull(owner);
		setArrayDim(array);
	}

	public static Type primitive(@MagicConstant(intValues = {VOID,BOOLEAN,BYTE,CHAR,SHORT,INT,FLOAT,DOUBLE,LONG}) int primitive) {
		if (primitive >= BYTE && primitive <= ARRAY) {
			Object[] arr = MAP[primitive-BYTE];
			if (arr != null) return (Type) arr[2];
		}
		throw new IllegalArgumentException("Illegal type desc '"+(char)primitive+"'("+primitive+")");
	}
	public static Type primitive(@MagicConstant(intValues = {VOID,BOOLEAN,BYTE,CHAR,SHORT,INT,FLOAT,DOUBLE,LONG}) int primitive, @Range(from = 0, to = 255) int array) {return new Type(primitive, array);}
	public static Type klass(String type, @Range(from = 0, to = 255) int array) {return new Type(type, array);}
	public static Type klass(String type) {return new Type(type, 0);}
	public static Type custom(int type) {return new Type((char)type);}
	//region from Class descriptor
	public static Type fieldDesc(String desc) {return parse(desc, 0);}
	public static List<Type> methodDesc(String desc) {
		SimpleList<Type> p = AsmCache.getInstance().methodTypeTmp();
		methodDesc(desc, p); return new SimpleList<>(p);
	}
	public static void methodDesc(String desc, List<Type> params) {
		int array = 0;
		foundError:
		if (desc.charAt(0) == '(') for (int i = 1; i < desc.length(); i++) {
			char c = desc.charAt(i);
			switch (c) {
				case 'L' -> {
					int typeEnd = desc.indexOf(';', ++i);
					if (typeEnd < 0) break foundError;
					params.add(klass(desc.substring(i, typeEnd), array));
					array = 0;
					i = typeEnd;
				}
				default -> {
					if (!isValid(c)) break foundError;
					params.add(array == 0 ? primitive(c) : primitive(c, array));
					array = 0;
				}
				case '[' -> array++;
				case ')' -> {
					params.add(parse(desc, i+1));
					return;
				}
			}
		}

		throw new IllegalArgumentException("方法描述无效:"+desc);
	}
	public static Type methodDescReturn(String desc) {
		int index = desc.indexOf(')');
		if (index < 0) throw new IllegalArgumentException("方法描述无效:"+desc);
		return parse(desc, index+1);
	}

	private static Type parse(String desc, int off) {
		char c0 = desc.charAt(off);
		switch (c0) {
			case ARRAY:
				int pos = desc.lastIndexOf('[')+1;
				Type t = parse(desc, pos);
				if (t.owner == null) t = Type.primitive(t.type, pos - off);
				else t.setArrayDim(pos - off);
				return t;
			case CLASS:
				if (!desc.endsWith(";")) throw new IllegalArgumentException("类型 '" + desc + "' 未以;结束");
				return Type.klass(desc.substring(off + 1, desc.length() - 1));
			default: return primitive(c0);
		}
	}
	//endregion

	@Override public byte genericType() {return STANDARD_TYPE;}
	@Override public final void toDesc(CharList sb) {
		for (int i = array&0xFF; i > 0; i--) sb.append('[');
		sb.append((char) type);
		if (type == CLASS) sb.append(owner).append(';');
	}
	@Override public void toString(CharList sb) {
		if (owner != null) TypeHelper.toStringOptionalPackage(sb, owner);
		else sb.append(getName(type));
		for (int i = array&0xFF; i > 0; i--) sb.append("[]");
	}
	public String toString() {
		var sb = IOUtil.getSharedCharBuf();
		toString(sb);
		return sb.toString();
	}

	/**
	 * 转换方法type为字符串
	 */
	public static String toMethodDesc(List<? extends IType> list) { return toMethodDesc(list, null); }
	public static String toMethodDesc(List<? extends IType> list, String prev) {
		CharList sb = IOUtil.getSharedCharBuf().append('(');

		for (int i = 0; i < list.size(); i++) {
			// return value
			if (i == list.size() - 1) sb.append(')');

			list.get(i).rawType().toDesc(sb);
		}
		return sb.equals(prev) ? prev : sb.toString();
	}

	@Override public void validate(int position, int index) {
		switch (position) {
			case INPUT_ENV -> {
				if (type == VOID) throw new IllegalStateException("输入参数不能是void类型");
			}
			case THROW_ENV -> {
				if (type != CLASS || array != 0) throw new IllegalStateException(this+"不是异常类型");
			}
		}
	}

	public boolean isPrimitive() {return array == 0 && type != CLASS;}
	public int getActualType() {return array == 0 ? type : CLASS;}
	/**
	 * 当是KLASS类型且不是数组时返回类的直接表示形式，否则返回Desc
	 */
	public String getActualClass() {
		if (array == 0 && owner != null) return owner;
		return toDesc();
	}

	/**
	 * 变量的长度，对于非数组的double或long返回2，对于void返回0，否则返回1
	 * @return 0,1,2
	 */
	@Range(from = 0, to = 2)
	public int length() {return type == VOID ? 0 : (array == 0 && (type == LONG || type == DOUBLE)) ? 2 : 1;}

	/**
	 * 操作码前缀
	 * @return ILFDA
	 */
	public String opcodePrefix() {return MAP[getActualType()-BYTE][4].toString();}
	/**
	 * 返回基础操作码code适合当前Type的变种
	 */
	public byte shiftedOpcode(int code) {
		int shift = (int) MAP[getActualType()-BYTE][3];
		int data = Opcodes.shift(code);
		if (data >>> 8 <= shift) throw new IllegalStateException(Opcodes.showOpcode(code)+"不存在适合"+this+"的变种");
		return (byte) ((data&0xFF)+shift);
	}

	public static Type fromJavaType(Class<?> clazz) {
		int array = 0;
		Class<?> tmp;
		while ((tmp = clazz.getComponentType()) != null) {
			clazz = tmp;
			array++;
		}

		if (clazz.isPrimitive()) {
			Type type = (Type) TypeHelper.ByName.get(clazz.getName())[2];
			return array == 0 ? type : primitive(type.type, array);
		}

		return klass(clazz.getName().replace('.', '/'), array);
	}
	public Class<?> toJavaType(ClassLoader loader) throws ClassNotFoundException {
		return switch (getActualType()) {
			case VOID -> void.class;
			case BOOLEAN -> boolean.class;
			case BYTE -> byte.class;
			case CHAR -> char.class;
			case SHORT -> short.class;
			case INT -> int.class;
			case FLOAT -> float.class;
			case DOUBLE -> double.class;
			case LONG -> long.class;
			default -> Class.forName(getActualClass().replace('/', '.'), false, loader);
		};
	}

	@Override public Type rawType() {return this;}
	@Override public int array() {return array&0xFF;}
	@Override public void setArrayDim(int array) {
		if (array > 255 || array < 0) throw new ArrayIndexOutOfBoundsException(array);
		if (type == VOID && array != 0) throw new IllegalStateException("创建VOID数组");
		this.array = (byte) array;
	}

	@Override public String owner() {return owner;}
	@Override public void owner(String owner) {
		if (this.owner == null || owner == null) throw new IllegalStateException("不是KLASS类型");
		this.owner = owner;
	}

	@Override public void rename(UnaryOperator<String> fn) {
		if (owner != null) owner = fn.apply(owner);
	}

	@Override
	public Type clone() {
		try {
			return (Type) super.clone();
		} catch (CloneNotSupportedException e) {
			Helpers.athrow(e);
			return Helpers.nonnull();
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Type type1 = (Type) o;

		if (type != type1.type) return false;
		if (array != type1.array) return false;
		return type != CLASS || owner.equals(type1.owner);
	}

	@Override
	public int hashCode() {
		int result = type;
		result = 31 * result + (owner != null ? owner.hashCode() : 0);
		result = 31 * result + array;
		return result;
	}
}