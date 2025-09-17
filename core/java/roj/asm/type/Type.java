package roj.asm.type;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Range;
import roj.asm.AsmCache;
import roj.asm.Opcodes;
import roj.collect.ArrayList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.OperationDone;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * 类型
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public sealed class Type implements IType permits Type.ADT {
	public static final char ARRAY = '[', CLASS = 'L', VOID = 'V', BOOLEAN = 'Z', BYTE = 'B', CHAR = 'C', SHORT = 'S', INT = 'I', LONG = 'J', FLOAT = 'F', DOUBLE = 'D';

	/** The {@code void} type. */
	public static final Type VOID_TYPE = primitive(VOID);
	/** The {@code boolean} type. */
	public static final Type BOOLEAN_TYPE = primitive(BOOLEAN);
	/** The {@code byte} type. */
	public static final Type BYTE_TYPE = primitive(BYTE);
	/** The {@code char} type. */
	public static final Type CHAR_TYPE = primitive(CHAR);
	/** The {@code short} type. */
	public static final Type SHORT_TYPE = primitive(SHORT);
	/** The {@code int} type. */
	public static final Type INT_TYPE = primitive(INT);
	/** The {@code float} type. */
	public static final Type FLOAT_TYPE = primitive(FLOAT);
	/** The {@code double} type. */
	public static final Type DOUBLE_TYPE = primitive(DOUBLE);
	/** The {@code long} type. */
	public static final Type LONG_TYPE = primitive(LONG);

	public static String getName(int type) {return R.byId[type-BYTE].name;}
	public String capitalized() {return R.byId[getActualType()-BYTE].capitalizedName;}

	public static final int SORT_VOID = 0, SORT_BOOLEAN = 1, SORT_BYTE = 2, SORT_CHAR = 3, SORT_SHORT = 4, SORT_INT = 5, SORT_LONG = 6, SORT_FLOAT = 7, SORT_DOUBLE = 8, SORT_OBJECT = 9;
	/**
	 * 获取类型的’类型‘。
	 * 原文这个sort真是一语双关.
	 * 可以用来方便的switch
	 * <pre>
	 * | Type    | Sort |
	 * |---------|------|
	 * | VOID    | 0    |
	 * | BOOLEAN | 1    |
	 * | BYTE    | 2    |
	 * | CHAR    | 3    |
	 * | SHORT   | 4    |
	 * | INT     | 5    |
	 * | LONG    | 6    |
	 * | FLOAT   | 7    |
	 * | DOUBLE  | 8    |
	 * | OBJECT  | 9    |
	 *
	 * @param type 类型常量 (e.g., {@link #INT})
	 * @throws IllegalArgumentException 如果 type 无效
	 */
	@Range(from = SORT_VOID, to = SORT_OBJECT)
	public static int getSort(int type) { return R.byId[type-BYTE].sort; }
	/**
	 * 根据排序值获取基本类型常量。
	 * -1 返回 VOID。
	 * 映射见 getSort 的表格（反向）。
	 *
	 * @param sort 排序值 (-1 到 7)
	 * @return 类型常量，无效抛异常
	 * @throws IllegalArgumentException 如果 sort 无效
	 */
	public static int getBySort(@Range(from = 0, to = 9) int sort) { return R.bySort[sort]; }

	static boolean isValid(int c) {
		if (c < BYTE || c > ARRAY) return false;
		return R.byId[c-BYTE] != null;
	}

	// for Lava Compiler only
	// since 2024/11/30 13:30
	@Deprecated
	public static final class ADT extends Type {
		public ADT(int type, String owner) {
			super((char) type);
			this.owner = owner;
		}

		@Override public boolean isPrimitive() {return false;}
		//@Override public int getActualType() {return type;}
		//@Override public Type rawType() {return std(type);}

		@Override
		public void toString(CharList sb) {
			super.toString(sb);
			if (type != CLASS) sb.append("<alias of ").append(getName(type)).append(">");
		}
	}

	@MagicConstant(intValues = {VOID,BOOLEAN,BYTE,CHAR,SHORT,INT,FLOAT,DOUBLE,LONG,CLASS})
	public final byte type;
	public String owner;
	private byte array;

	Type(char type) {this.type = (byte) type;}

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
			var arr = R.byId[primitive-BYTE];
			if (arr != null) return arr.singleton;
		}
		throw new IllegalArgumentException("Illegal type desc '"+(char)primitive+"'("+primitive+")");
	}
	public static Type primitive(@MagicConstant(intValues = {VOID,BOOLEAN,BYTE,CHAR,SHORT,INT,FLOAT,DOUBLE,LONG}) int primitive, @Range(from = 0, to = 255) int array) {return new Type(primitive, array);}
	public static Type klass(String type, @Range(from = 0, to = 255) int array) {return new Type(type, array);}
	public static Type klass(String type) {return new Type(type, 0);}
	public static Type custom(int type) {return new Type((char)type);}
	//region from 解析标识符
	public static Type getType(String desc) {return parse(desc, 0);}
	public static List<Type> getMethodTypes(String desc) {
		ArrayList<Type> tmp = AsmCache.getInstance().methodTypeTmp();
		Type returnType = getArgumentTypes(desc, tmp);
		tmp.add(returnType);
		return new ArrayList<>(tmp);
	}
	/**
	 * 解析方法描述
	 * @param desc 方法描述
	 * @param argumentTypes 入参
	 * @return 返回值
	 */
	public static Type getArgumentTypes(String desc, List<Type> argumentTypes) {
		int array = 0;
		foundError:
		if (desc.charAt(0) == '(') for (int i = 1; i < desc.length(); i++) {
			char c = desc.charAt(i);
			switch (c) {
				case 'L' -> {
					int typeEnd = desc.indexOf(';', ++i);
					if (typeEnd < 0) break foundError;
					argumentTypes.add(klass(desc.substring(i, typeEnd), array));
					array = 0;
					i = typeEnd;
				}
				default -> {
					if (!isValid(c)) break foundError;
					argumentTypes.add(array == 0 ? primitive(c) : primitive(c, array));
					array = 0;
				}
				case '[' -> array++;
				case ')' -> {
					return parse(desc, i+1);
				}
			}
		}

		throw new IllegalArgumentException("方法描述无效:"+desc);
	}
	public static Type getReturnType(String desc) {
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
	@Override public String toDesc() {
		int type = getActualType();
		if (type != Type.CLASS) return R.byId[type - Type.BYTE].desc;
		return IType.super.toDesc();
	}
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
	public static String getMethodDescriptor(List<? extends IType> methodTypes) {
		CharList sb = IOUtil.getSharedCharBuf().append('(');

		for (int i = 0; i < methodTypes.size(); i++) {
			// return value
			if (i == methodTypes.size() - 1) sb.append(')');

			methodTypes.get(i).rawType().toDesc(sb);
		}
		return sb.toString();
	}
	public static String getMethodDescriptor(List<? extends IType> argumentTypes, IType returnType) { return getMethodDescriptor(argumentTypes, returnType, null); }
	public static String getMethodDescriptor(List<? extends IType> argumentTypes, IType returnType, String prev) {
		CharList sb = IOUtil.getSharedCharBuf().append('(');

		for (int i = 0; i < argumentTypes.size(); i++) {
			argumentTypes.get(i).rawType().toDesc(sb);
		}
		returnType.rawType().toDesc(sb.append(')'));

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
	public String opcodePrefix() {return R.byId[getActualType()-BYTE].opPrefix;}
	/**
	 * 返回基础操作码code适合当前Type的变种
	 */
	public byte getOpcode(int code) {
		int shift = R.byId[getActualType()-BYTE].opShift;
		int data = Opcodes.shift(code);
		if (data >>> 8 <= shift) throw new IllegalStateException(Opcodes.toString(code)+"不存在适合"+this+"的变种");
		return (byte) ((data&0xFF)+shift);
	}

	public static Type getType(Class<?> clazz) {
		int array = 0;
		Class<?> tmp;
		while ((tmp = clazz.getComponentType()) != null) {
			clazz = tmp;
			array++;
		}

		if (clazz.isPrimitive()) {
			Type type = R.byName.get(clazz.getName()).singleton;
			return array == 0 ? type : primitive(type.type, array);
		}

		return klass(clazz.getName().replace('.', '/'), array);
	}
	public Class<?> toClass(ClassLoader loader) throws ClassNotFoundException {
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
			throw OperationDone.NEVER;
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