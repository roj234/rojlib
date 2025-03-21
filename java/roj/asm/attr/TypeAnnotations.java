package roj.asm.attr;

import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.annotation.Annotation;
import roj.asm.cp.ConstantPool;
import roj.asm.insn.AttrCode;
import roj.collect.CharMap;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 反正我没那个心思在编译器里实现它: <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.20">Spec</a>
 * @author Roj234
 * @since 2021/3/7 13:36
 */
public final class TypeAnnotations extends Attribute {
	public static final String VISIBLE_NAME = "RuntimeVisibleTypeAnnotations", INVISIBLE_NAME = "RuntimeInvisibleTypeAnnotations";

	public TypeAnnotations(boolean visibleForRuntime) {
		vis = visibleForRuntime;
		annotations = new SimpleList<>();
	}

	public TypeAnnotations(String name, DynByteBuf r, ConstantPool pool) {
		vis = name.equals(VISIBLE_NAME);
		annotations = parse(pool, r);
	}

	public final boolean vis;
	public List<TypeAnno> annotations;

	@Override
	public String name() { return vis?VISIBLE_NAME:INVISIBLE_NAME; }

	@Override
	public boolean writeIgnore() {return annotations.isEmpty();}
	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.putShort(annotations.size());
		for (TypeAnno annotation : annotations) annotation.toByteArray(w, pool);
	}

	public static List<TypeAnno> parse(ConstantPool pool, DynByteBuf r) {
		int len = r.readUnsignedShort();
		List<TypeAnno> annos = new SimpleList<>(len);
		while (len-- > 0) {
			byte type = r.readByte();
			TypeAnno anno = switch (type) {
				case TypeAnno.PARAM_CLZ, TypeAnno.PARAM_METHOD, TypeAnno.FORMAL_PARAM -> new Param(type, r.readUnsignedByte());
				case TypeAnno.SUPER, TypeAnno.THROWS, TypeAnno.CATCH -> new Nth(type, r.readUnsignedShort());
				case TypeAnno.PARAM_BOUND_CLZ, TypeAnno.PARAM_BOUND_METHOD -> new ParamBound(type, r.readUnsignedByte(), r.readUnsignedByte());
				case TypeAnno.EMPTY_FIELD, TypeAnno.EMPTY_RETURN, TypeAnno.EMPTY_RECEIVER -> new Empty(type);
				case TypeAnno.LOCAL_VAR, TypeAnno.RESOURCE_VAR -> {
					int len2 = r.readUnsignedShort();
					List<int[]> table = new ArrayList<>(len2);
					for (int j = 0; j < len2; j++) {
						table.add(new int[] {r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort()});
					}
					yield new T_LVT(type, table);
				}
				case TypeAnno.OFFSET_INSTANCEOF, TypeAnno.OFFSET_LAMBDA_METHOD, TypeAnno.OFFSET_LAMBDA_NEW, TypeAnno.OFFSET_NEW -> new Indexed(type, r.readUnsignedShort());
				case TypeAnno.ARG_CAST, TypeAnno.ARG_CONSTRUCTOR, TypeAnno.ARG_LAMBDA_METHOD, TypeAnno.ARG_LAMBDA_NEW, TypeAnno.ARG_METHOD -> new Nth_Indexed(type, r.readUnsignedShort(), r.readUnsignedByte());
				default -> throw new IllegalArgumentException("Unknown target type '"+type+'\'');
			};

			anno.readTypePath(r);
			anno.annotation = Annotation.parse(pool, r);

			annos.add(anno);
		}

		return annos;
	}

	public String toString() {
		if (annotations.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (TypeAnno anno : annotations) sb.append(anno).append('\n');
		return sb.deleteCharAt(sb.length() - 1).toString();
	}

	public static abstract class TypeAnno {
		static final CharMap<String> TargetType = new CharMap<>();

		public static final byte
		/**
		 * Type parameter declaration of generic class -- type_parameter
		 */
		PARAM_CLZ = 0x0,
		/**
		 * Type parameter declaration of generic method -- type_parameter
		 */
		PARAM_METHOD = 0x1,
		/**
		 * Type in extends or implements clause of class declaration (including the direct superclass or
		 * direct superinterface of an anonymous class declaration), or in extends clause of interface declaration	supertype
		 */
		SUPER = 0x10,
		/**
		 * In bound of type parameter declaration of generic class -- type_parameter_bound
		 */
		PARAM_BOUND_CLZ = 0x11,
		/**
		 * In bound of type parameter declaration of generic method -- type_parameter_bound
		 */
		PARAM_BOUND_METHOD = 0x12,
		/**
		 * Type in field declaration -- empty
		 */
		EMPTY_FIELD = 0x13,
		/**
		 * Return type of method, or type of newly constructed object -- empty
		 */
		EMPTY_RETURN = 0x14,
		/**
		 * Receiver type of method or constructor -- empty
		 */
		EMPTY_RECEIVER = 0x15,
		/**
		 * Type in formal parameter declaration of method, constructor, or lambda expression -- formal_parameter
		 */
		FORMAL_PARAM = 0x16,
		/**
		 * Type in throws clause of method or constructor -- throws
		 */
		THROWS = 0x17,

		/**
		 * Type in local variable declaration -- localvar
		 */
		LOCAL_VAR = 0x40,
		/**
		 * Type in resource variable declaration -- localvar
		 */
		RESOURCE_VAR = 0x41,

		/**
		 * Type in exception parameter declaration -- catch
		 */
		CATCH = 0x42,

		/**
		 * Type in instanceof expression -- offset
		 */
		OFFSET_INSTANCEOF = 0x43,
		/**
		 * Type in new expression -- offset
		 */
		OFFSET_NEW = 0x44,
		/**
		 * Type in method reference expression using ::new	offset
		 */
		OFFSET_LAMBDA_NEW = 0x45,
		/**
		 * Type in method reference expression using ::Identifier -- offset
		 */
		OFFSET_LAMBDA_METHOD = 0x46,

		/**
		 * Type in cast expression -- type_argument
		 */
		ARG_CAST = 0x47,
		/**
		 * Type argument for generic constructor in new expression or explicit constructor invocation statement -- type_argument
		 */
		ARG_CONSTRUCTOR = 0x48,
		/**
		 * Type argument for generic method in method invocation expression -- type_argument
		 */
		ARG_METHOD = 0x49,
		/**
		 * Type argument for generic constructor in method reference expression using ::new -- type_argument
		 */
		ARG_LAMBDA_NEW = 0x4A,
		/**
		 * Type argument for generic method in method reference expression using ::Identifier -- type_argument
		 */
		ARG_LAMBDA_METHOD = 0x4B;

		static {
			TargetType.put((char) 0x0, "type_parameter(Class)");
			TargetType.put((char) 0x1, "type_parameter(Method)");
			TargetType.put((char) 0x10, "super");
			TargetType.put((char) 0x11, "type_parameter_bound(Class)");
			TargetType.put((char) 0x12, "type_parameter_bound(Method)");
			TargetType.put((char) 0x13, "empty(Field)");
			TargetType.put((char) 0x14, "empty(Return)");
			TargetType.put((char) 0x15, "empty(Receiver)");
			TargetType.put((char) 0x16, "formal_parameter(Method)");
			TargetType.put((char) 0x17, "throws");

			TargetType.put((char) 0x40, "local_var");
			TargetType.put((char) 0x41, "resource_var");
			TargetType.put((char) 0x42, "catch");
			TargetType.put((char) 0x43, "offset(instanceof)");
			TargetType.put((char) 0x44, "offset(new)");
			TargetType.put((char) 0x45, "offset(lambda: new)");
			TargetType.put((char) 0x46, "offset(lambda: method)");
			TargetType.put((char) 0x47, "type_argument(cast)");
			TargetType.put((char) 0x48, "type_argument(constructor)");
			TargetType.put((char) 0x49, "type_argument(method)");
			TargetType.put((char) 0x4A, "type_argument(lambda: new)");
			TargetType.put((char) 0x4B, "type_argument(lambda: method)");
		}

		/**
		 * 0x00, 0x10 and 0x11 in ClassFile
		 * 0x01, 0x12, 0x14, 0x15, 0x16 and 0x17 in method
		 * 0x13	in field
		 * 0x40-0x4B in AttrCode
		 */
		public static boolean validTypeAtPos(Class<?> type, int pos) {
			return switch (pos & 0xFF) {
				case 0x00, 0x10, 0x11 -> type == ClassNode.class;
				case 0x13 -> type == FieldNode.class;
				case 0x01, 0x12, 0x14, 0x15, 0x16, 0x17 -> type == MethodNode.class;
				case 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48 -> type == AttrCode.class;
				default -> false;
			};
		}

		public final byte type;

		public byte[] typePaths;
		public Annotation annotation;

		TypeAnno(byte type) {
			this.type = type;
			if (!ck(type)) throw new IllegalArgumentException("Not supported type "+type);
		}

		public void readTypePath(DynByteBuf r) {
			int len = r.readUnsignedByte()<<1;
			typePaths = r.readBytes(len);
		}

		public final void toByteArray(DynByteBuf w, ConstantPool pool) {
			wt(pool, w.put(type));
			w.put((byte) (typePaths.length>>>1)).put(typePaths);
			annotation.toByteArray(w, pool);
		}

		@Override
		public String toString() {return getClass().getName()+'('+TargetType.get((char) type)+')';}

		abstract boolean ck(byte type);
		abstract void wt(ConstantPool pool, DynByteBuf w);
	}

	/**
	 * either the type in a field declaration,
	 *   the type in a record component declaration,
	 *   the return type of a method, the type of a newly constructed object,
	 *   or the receiver type of a method or constructor.
	 */
	public static final class Empty extends TypeAnno {
		public Empty(byte type) {super(type);}

		@Override
		boolean ck(byte type) {
			return switch (type) {
				case TypeAnno.EMPTY_FIELD, TypeAnno.EMPTY_RETURN, TypeAnno.EMPTY_RECEIVER -> true;
				default -> false;
			};
		}

		@Override
		void wt(ConstantPool pool, DynByteBuf w) {}
	}

	/**
	 * either the type in an instanceof expression or a new expression,
	 *   or the type before the :: in a method reference expression.
	 */
	public static final class Indexed extends TypeAnno {
		/**
		 * the [bci] specifies the code array offset of either
		 *   the instanceof expression (on 'instanceof' opcode),
		 *   the new expression (on 'new' opcode),
		 *   or the method reference expression.
		 */
		public int bci;

		public Indexed(byte type, int bci) {
			super(type);
			this.bci = bci;
		}

		@Override
		void wt(ConstantPool pool, DynByteBuf w) {w.putShort(bci);}

		@Override
		boolean ck(byte type) {
			return switch (type) {
				case TypeAnno.OFFSET_INSTANCEOF, TypeAnno.OFFSET_LAMBDA_METHOD, TypeAnno.OFFSET_LAMBDA_NEW, TypeAnno.OFFSET_NEW -> true;
				default -> false;
			};
		}
	}

	/**
	 * either on the [arg_id]th type in a cast expression,
	 * or on the [arg_id]th type argument in the explicit type argument list for any of the following:
	 *   a new expression, an explicit constructor invocation statement, a method invocation expression, or a method reference expression.
 	 */
	public static final class Nth_Indexed extends TypeAnno {
		/**
		 * the [bci] specifies the code array offset of either
		 *   the cast expression,
		 *   the new expression (on 'new' opcode),
		 *   the explicit constructor invocation statement,
		 *   the method invocation expression,
		 *   or the method reference expression.
		 */
		public int bci;
		/**
		 * Starting from 0,
		 * For a cast expression, the [arg_id] specifies which type in the cast operator is annotated.
		 * The possibility of more than one type in a cast expression arises from a cast to an intersection type.
		 *
		 * For an explicit type argument list, the [arg_id] item specifies which type argument is annotated.
		 */
		public int arg_id;

		public Nth_Indexed(byte type, int bci, int arg_id) {
			super(type);
			this.bci = bci;
			this.arg_id = arg_id;
		}

		@Override
		void wt(ConstantPool pool, DynByteBuf w) {w.putShort(bci).put(arg_id);}

		@Override
		boolean ck(byte type) {
			return switch (type) {
				case TypeAnno.ARG_CAST, TypeAnno.ARG_CONSTRUCTOR, TypeAnno.ARG_LAMBDA_METHOD, TypeAnno.ARG_LAMBDA_NEW, TypeAnno.ARG_METHOD -> true;
				default -> false;
			};
		}
	}

	/**
	 * nth [type] in a formal parameter declaration of a method, constructor, or lambda expression.
	 */
	public static final class Param extends TypeAnno {
		public int param;

		public boolean isSignature() {return type == TypeAnno.PARAM_CLZ || type == TypeAnno.PARAM_METHOD;}

		public Param(byte type, int param) {
			super(type);
			this.param = param;
		}

		@Override
		void wt(ConstantPool pool, DynByteBuf w) {w.put(param);}

		@Override
		boolean ck(byte type) {
			return switch (type) {
				case TypeAnno.FORMAL_PARAM, TypeAnno.PARAM_CLZ, TypeAnno.PARAM_METHOD -> true;
				default -> false;
			};
		}
	}

	/**
	 * mth [bound] of the nth [type parameter declaration] of a generic class, interface, method, or constructor.
	 */
	public static final class ParamBound extends TypeAnno {
		public int param, bound;

		public ParamBound(byte type, int param, int bound) {
			super(type);
			this.param = param;
			this.bound = bound;
		}

		@Override
		void wt(ConstantPool pool, DynByteBuf w) {w.put(param).put(bound);}

		@Override
		boolean ck(byte type) {
			return switch (type) {
				case TypeAnno.PARAM_BOUND_CLZ, TypeAnno.PARAM_BOUND_METHOD -> true;
				default -> false;
			};
		}
	}

	/**
	 * nth type in the [throws, catch, extends/implements] clause of a method or constructor declaration.
	 */
	public static final class Nth extends TypeAnno {
		public int id;

		public Nth(byte type, int id) {
			super(type);
			this.id = id;
		}

		@Override
		void wt(ConstantPool pool, DynByteBuf w) {w.putShort(id);}

		@Override
		boolean ck(byte type) {
			return switch (type) {
				case TypeAnno.THROWS, TypeAnno.CATCH, TypeAnno.SUPER -> true;
				default -> false;
			};
		}
	}

	/**
	 * type in a local variable declaration, including a variable declared as a resource in a try-with-resources statement.
	 */
	public static final class T_LVT extends TypeAnno {
		/**
		 * u2 start_pc;
		 * u2 length;
		 * u2 index;
		 */
		public List<int[]> list;

		public T_LVT(byte type, List<int[]> list) {
			super(type);
			this.list = list;
		}

		@Override
		void wt(ConstantPool pool, DynByteBuf w) {
			w.putShort(list.size());
			for (int i = 0; i < list.size(); i++) {
				int[] arr = list.get(i);
				for (int j = 0; j < 3; j++) {
					w.putShort(arr[j]);
				}
			}
		}

		@Override
		boolean ck(byte type) {
			return switch (type) {
				case TypeAnno.LOCAL_VAR, TypeAnno.RESOURCE_VAR -> true;
				default -> false;
			};
		}
	}
}