package roj.asm.tree.attr;

import roj.asm.cp.ConstantPool;
import roj.asm.tree.anno.Annotation;
import roj.collect.CharMap;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
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
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.putShort(annotations.size());
		for (TypeAnno annotation : annotations) {
			annotation.toByteArray(pool, w);
		}
	}

	/**
	 * type_annotation {
	 * u1 target_type;
	 * union {...} target_type : target_info;
	 * type_path target_path;
	 * <p>
	 * <p>
	 * u2        type_index;
	 * u2        num_element_value_pairs;
	 * {
	 * u2            element_name_index;
	 * element_value value;
	 * } element_value_pairs[num_element_value_pairs];
	 * }
	 */
	public static List<TypeAnno> parse(ConstantPool pool, DynByteBuf r) {
		int len = r.readUnsignedShort();
		List<TypeAnno> annos = new SimpleList<>(len);
		while (len-- > 0) {
			TypeAnno anno;
			/**
			 * 0x00, 0x10 and 0x11 in ClassFile
			 * 0x01, 0x12, 0x14, 0x15, 0x16 and 0x17 in method
			 * 0x13	in field
			 * 0x40-0x4B in AttrCode
			 */
			final byte type = TargetType.verify(r.readByte());
			switch (type) {
				case TargetType.PARAM_CLZ: // type_parameter
				case TargetType.PARAM_METHOD:
					anno = (new T_ParamId(type, r.readUnsignedByte()));
					// u1 type_parameter_index;
					break;
				case TargetType.SUPER: // super
					// u2 super_index
					anno = new T_Ex(type, r.readUnsignedShort());
					/**
					 * A supertype_index value of 65535 specifies that the annotation appears on the superclass in an extends clause of a class declaration.
					 *
					 * Any other supertype_index value is an index into the interfaces array of the enclosing ClassFile structure,
					 *      and specifies that the annotation appears on that superinterface in either the implements clause of a class declaration or
					 *      the extends clause of an interface declaration.
					 */
					break;
				case TargetType.PARAM_BOUND_CLZ: // type_p_bound
				case TargetType.PARAM_BOUND_METHOD:
					anno = (new T_ParamBound(type, r.readUnsignedByte(), r.readUnsignedByte()));
					// u1 type_parameter_index;
					// u1 bound_index;
					/**
					 * the of type_parameter_index item specifies which type parameter declaration has an annotated bound.
					 *      A type_parameter_index value of 0 specifies the first type parameter declaration.
					 *
					 * the bound_index item specifies which bound of the type parameter declaration indicated by type_parameter_index is annotated.
					 *      A bound_index value of 0 specifies the first bound of a type parameter declaration.
					 *
					 * The type_parameter_bound_target item records that a bound is annotated, but does not record the type which constitutes the bound.
					 *      The type may be found by inspecting the class signature or method signature stored in the appropriate Signature attribute.
					 */
					break;
				case TargetType.EMPTY_FIELD: // empty
				case TargetType.EMPTY_RETURN:
				case TargetType.EMPTY_RECEIVER:
					anno = (new T_Empty(type));
					// none
					break;
				case TargetType.FORMAL_PARAM: // formal_param
					anno = (new T_ParamId(type, r.readUnsignedByte()));
					// u1 formal_parameter_index;

					/**
					 * the formal_parameter_index item specifies which formal parameter declaration has an annotated type.
					 *      A formal_parameter_index value of 0 specifies the first formal parameter declaration.
					 *
					 * The formal_parameter_target item records that a formal parameter's type is annotated, but does not record the type itself.
					 *      The type may be found by inspecting the method descriptor (§4.3.3) of the method_info structure enclosing
					 *              the RuntimeVisibleTypeAnnotations attribute.
					 *              A formal_parameter_index value of 0 indicates the first parameter descriptor in the method descriptor.
					 */
					break;
				case TargetType.THROWS: // throws
					anno = (new T_Ex(type, r.readUnsignedShort()));
					// u2 throws_type_index;

					/**
					 * the throws_type_index item is an index into
					 *      the exception_index_table array of the Exceptions attribute of the method_info structure enclosing
					 *      the RuntimeVisibleTypeAnnotations attribute.
					 */
					break;
				case TargetType.LOCAL_VAR: // localvar
				case TargetType.RESOURCE_VAR:
					int len2 = r.readUnsignedShort();

					List<int[]> list = new ArrayList<>(len2);
                    /*
                        u2 table_length;
                        {   u2 start_pc;
                            u2 length;
                            u2 index;
                        } table[table_length];
                     */
					for (int j = 0; j < len2; j++) {
						list.add(new int[] {r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort()});
					}

					anno = (new T_LV(type, list));
					break;

				/**
				 * the table_length item gives the number of entries in the table array. Each entry indicates a range of code array offsets within which a local variable has a value. It also indicates the index into the local variable array of the current frame at which that local variable can be found. Each entry contains the following three items:
				 *
				 * start_pc, length
				 * The given local variable has a value at indices into the code array in the interval [start_pc, start_pc + length), that is, between start_pc inclusive and start_pc + length exclusive.
				 *
				 * index
				 * The given local variable must be at index in the local variable array of the current frame.
				 *
				 * If the local variable at index is of type double or long, it occupies both index and index + 1.
				 *
				 * A table is needed to fully specify the local variable whose type is annotated, because a single local variable may be represented with different local variable indices over multiple live ranges. The start_pc, length, and index items in each table entry specify the same information as a LocalVariableTable attribute.
				 *
				 * The localvar_target item records that a local variable's type is annotated, but does not record the type itself. The type may be found by inspecting the appropriate LocalVariableTable attribute.
				 */

				case TargetType.CATCH: // catch
					anno = (new T_Ex(type, r.readUnsignedShort()));
					// u2 exception_table_index;

					/**
					 * the exception_table_index item is an index into the exception_table array of the Code attribute enclosing the RuntimeVisibleTypeAnnotations attribute.
					 *
					 * The possibility of more than one type in an exception parameter declaration arises from the multi-catch clause of the try statement, where the type of the exception parameter is a union of types (JLS §14.20). A compiler usually creates one exception_table entry for each type in the union, which allows the catch_target item to distinguish them. This preserves the correspondence between a type and its annotations.
					 */

					break;
				case TargetType.OFFSET_INSTANCEOF: // offset
				case TargetType.OFFSET_LAMBDA_METHOD:
				case TargetType.OFFSET_LAMBDA_NEW:
				case TargetType.OFFSET_NEW:
					anno = (new T_Offset(type, r.readUnsignedShort()));
					// u2 offset;
					/**
					 * the offset item specifies the code array offset of either the instanceof bytecode instruction corresponding to the instanceof expression, the new bytecode instruction corresponding to the new expression, or the bytecode instruction corresponding to the method reference expression.
					 */

					break;
				case TargetType.ARG_CAST: // type_arg
				case TargetType.ARG_CONSTRUCTOR:
				case TargetType.ARG_LAMBDA_METHOD:
				case TargetType.ARG_LAMBDA_NEW:
				case TargetType.ARG_METHOD:
					anno = (new T_ArgOffset(type, r.readUnsignedShort(), r.readUnsignedByte()));
					// u2 offset;
					// u1 type_argument_index;

					/**
					 * the offset item specifies the code array offset of either the bytecode instruction corresponding to the cast expression, the new bytecode instruction corresponding to the new expression, the bytecode instruction corresponding to the explicit constructor invocation statement, the bytecode instruction corresponding to the method invocation expression, or the bytecode instruction corresponding to the method reference expression.
					 *
					 * For a cast expression, the type_argument_index item specifies which type in the cast operator is annotated. A type_argument_index value of 0 specifies the first (or only) type in the cast operator.
					 *
					 * The possibility of more than one type in a cast expression arises from a cast to an intersection type.
					 *
					 * For an explicit type argument list, the type_argument_index item specifies which type argument is annotated. A type_argument_index value of 0 specifies the first type argument.
					 */
					break;

				default:
					throw OperationDone.NEVER;
			}

			/**
			 * 4.7.20.2. The type_path structure
			 *
			 * Wherever a type is used in a declaration or expression, the type_path structure identifies which part of the type is annotated.
			 * An annotation may appear on the type itself, but if the type is a reference type, then there are additional locations where an annotation may appear:
			 *
			 * A. Array,
			 *      then, on any component type of the array type, including the element type.
			 *      e.g.:
			 *          @A String @A [] @A []
			 *
			 * B. Nested type (T1.T2 without 'static' flag),
			 *      then, on the name of the top level type or any member type.
			 *      e.g.:
			 *          @A Out. @A Mid. @A Inner
			 *
			 * C. Parameterized type (T<A>, T<? extends A> ...),
			 *      then, on any type argument or on the bound of any wildcard type argument.
			 *      e.g.:
			 *          a. @A Map<@A String, @A Object>
			 *          b. List<@A ? extends @A String>
			 */

			anno.readTypePath(r);

			anno.annotation = Annotation.parse(pool, r);

			annos.add(anno);
		}

		return annos;
	}

	/**
	 * The kinds of target in Table 4.7.20-A and Table 4.7.20-B correspond to the type contexts in JLS §4.11.
	 * Namely, target_type values 0x10-0x17 and 0x40-0x42 correspond to type contexts 1-10,
	 * while target_type values 0x43-0x4B correspond to type contexts 11-16.
	 */
	public static final class TargetType {
		static final CharMap<String> byId = new CharMap<>();

		public static final byte
			/**
			 * Type parameter declaration of generic class -- type_parameter
			 */
			PARAM_CLZ = 0x0, /**
		 * Type parameter declaration of generic method -- type_parameter
		 */
		PARAM_METHOD = 0x1, /**
		 * Type in extends or implements clause of class declaration (including the direct superclass or
		 * direct superinterface of an anonymous class declaration), or in extends clause of interface declaration	supertype
		 */
		SUPER = 0x10, /**
		 * In bound of type parameter declaration of generic class -- type_parameter_bound
		 */
		PARAM_BOUND_CLZ = 0x11, /**
		 * In bound of type parameter declaration of generic method -- type_parameter_bound
		 */
		PARAM_BOUND_METHOD = 0x12, /**
		 * Type in field declaration -- empty
		 */
		EMPTY_FIELD = 0x13, /**
		 * Return type of method, or type of newly constructed object -- empty
		 */
		EMPTY_RETURN = 0x14, /**
		 * Receiver type of method or constructor -- empty
		 */
		EMPTY_RECEIVER = 0x15,

		/**
		 * Type in formal parameter declaration of method, constructor, or lambda expression -- formal_parameter
		 */
		FORMAL_PARAM = 0x16, /**
		 * Type in throws clause of method or constructor -- throws
		 */
		THROWS = 0x17,

		/**
		 * Type in local variable declaration -- localvar
		 */
		LOCAL_VAR = 0x40, /**
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
		OFFSET_INSTANCEOF = 0x43, /**
		 * Type in new expression -- offset
		 */
		OFFSET_NEW = 0x44, /**
		 * Type in method reference expression using ::new	offset
		 */
		OFFSET_LAMBDA_NEW = 0x45, /**
		 * Type in method reference expression using ::Identifier -- offset
		 */
		OFFSET_LAMBDA_METHOD = 0x46,

		/**
		 * Type in cast expression -- type_argument
		 */
		ARG_CAST = 0x47, /**
		 * Type argument for generic constructor in new expression or explicit constructor invocation statement -- type_argument
		 */
		ARG_CONSTRUCTOR = 0x48, /**
		 * Type argument for generic method in method invocation expression -- type_argument
		 */
		ARG_METHOD = 0x49, /**
		 * Type argument for generic constructor in method reference expression using ::new -- type_argument
		 */
		ARG_LAMBDA_NEW = 0x4A, /**
		 * Type argument for generic method in method reference expression using ::Identifier -- type_argument
		 */
		ARG_LAMBDA_METHOD = 0x4B;


		static {
			byId.put((char) 0x0, "type_parameter(Class)");
			byId.put((char) 0x1, "type_parameter(Method)");
			byId.put((char) 0x10, "super");
			byId.put((char) 0x11, "type_parameter_bound(Class)");
			byId.put((char) 0x12, "type_parameter_bound(Method)");
			byId.put((char) 0x13, "empty(Field)");
			byId.put((char) 0x14, "empty(Return)");
			byId.put((char) 0x15, "empty(Receiver)");
			byId.put((char) 0x16, "formal_parameter(Method)");
			byId.put((char) 0x17, "throws");

			byId.put((char) 0x40, "local_var");
			byId.put((char) 0x41, "resource_var");
			byId.put((char) 0x42, "catch");
			byId.put((char) 0x43, "offset(instanceof)");
			byId.put((char) 0x44, "offset(new)");
			byId.put((char) 0x45, "offset(lambda: new)");
			byId.put((char) 0x46, "offset(lambda: method)");
			byId.put((char) 0x47, "type_argument(cast)");
			byId.put((char) 0x48, "type_argument(constructor)");
			byId.put((char) 0x49, "type_argument(method)");
			byId.put((char) 0x4A, "type_argument(lambda: new)");
			byId.put((char) 0x4B, "type_argument(lambda: method)");
		}

		public static byte verify(byte c) {
			if (!byId.containsKey((char) c)) throw new IllegalArgumentException("Unknown target type '" + c + '\'');
			/*if (!validTypeAtPos(source, c)) {
				throw new IllegalArgumentException("Target type '" + byId.get((char) c) + "' is not allowed in " + source.getName());
			}*/
			return c;
		}

		/*public static boolean validTypeAtPos(Class<?> type, int pos) {
			switch (pos & 0xFF) {
				case 0x00:
				case 0x10:
				case 0x11:
					return type == Clazz.class || ClassAttributeVisitor.class.isAssignableFrom(type);
				case 0x13:
					return type == Field.class || FieldAttributeVisitor.class.isAssignableFrom(type);
				case 0x01:
				case 0x12:
				case 0x14:
				case 0x15:
				case 0x16:
				case 0x17:
					return type == Method.class || MethodAttributeVisitor.class.isAssignableFrom(type);
				case 0x40:
				case 0x41:
				case 0x42:
				case 0x43:
				case 0x44:
				case 0x45:
				case 0x46:
				case 0x47:
				case 0x48:
					return type == AttrCode.class || CodeAttributeVisitor.class.isAssignableFrom(type);

			}
			return false;
		}*/

		public static String toString(char c) {
			return byId.get(c);
		}
	}

	public String toString() {
		if (annotations.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (TypeAnno anno : annotations) {
			sb.append(anno).append('\n');
		}
		return sb.deleteCharAt(sb.length() - 1).toString();
	}

	public static abstract class TypeAnno {
		public final byte type;

		public byte[] typePaths;
		public Annotation annotation;

		public TypeAnno(byte type) {
			this.type = type;
			if (!check(type)) throw new IllegalArgumentException("Not supported type " + type);
		}

		abstract boolean check(byte type);

		public void toByteArray(ConstantPool pool, DynByteBuf w) {
			_t(pool, w.put(type));
			w.put((byte) (typePaths.length>>>1)).put(typePaths);
			annotation.toByteArray(pool, w);
		}

		abstract void _t(ConstantPool pool, DynByteBuf w);

		/**
		 * The type_path structure has the following format:
		 * <p>
		 * type_path {
		 * u1 path_length;
		 * {
		 * u1 type_path_kind;
		 * u1 type_argument_index;
		 * } path[path_length];
		 * }
		 */
		public void readTypePath(DynByteBuf r) {
			/**
			 * path_length:
			 *
			 * If it is 0,
			 *      the annotation appears directly on the type itself.
			 * else,
			 *      each entry in the path array represents an iterative, left-to-right step
			 *      towards the precise location of the A,B or C mentioned above.
			 *
			 *      (In array type, is can be described by the code below
			 *          <pre>
			 *          Class<?> clz = String[][].class;
			 *
			 *          do {
			 *              visit(clz);
			 *          } while((clz = clz.getComponentType()) != null);
			 *
			 *          </pre>
			 *      until the element type (String) is reached.)
			 *
			 * Each entry contains the following two items:
			 *
			 * type_path_kind:
			 *      0	Annotation is deeper in an array type
			 *      1	           .. deeper in a nested type
			 *      2	           .. on the bound (A) of a wildcard type argument of a parameterized type (? extends A)
			 *      3	           .. on a type argument (?) of a parameterized type
			 *
			 * type_argument_index
			 * If type_path_kind is in 0, 1 or 2, it is 0.
			 * else (3),
			 *      its value specifies which [type argument of a parameterized type] is annotated,
			 *          where 0 indicates the first.
			 *
			 * // kind, index
			 *
			 * Table 4.7.20.2-B. type_path structures for @A Map<@B ? extends @C String, @D List<@E Object>>
			 *
			 * Annotation	path_length	path
			 * @A 0    []
			 * @B 1    [{3, 0}]
			 * @C 2    [{3, 0}, {2, 0}]
			 * @D 1    [{3, 1}]
			 * @E 2    [{3, 1}, {3, 0}]
			 *
			 *
			 * Table 4.7.20.2-C. type_path structures for @I String @F [] @G [] @H []
			 *
			 * Annotation	path_length	path
			 * @F 0    []
			 * @G 1    [{0, 0}]
			 * @H 2    [{0, 0}, {0, 0}]
			 * @I 3    [{0, 0}, {0, 0}, {0, 0}]
			 *
			 *
			 * Table 4.7.20.2-D. type_path structures for @A List<@B Comparable<@F Object @C [] @D [] @E []>>
			 *
			 * Annotation	path_length	path
			 * @A 0    []
			 * @B 1    [{3, 0}]
			 * @C 2    [{3, 0}, {3, 0}]
			 * @D 3    [{3, 0}, {3, 0}, {0, 0}]
			 * @E 4    [{3, 0}, {3, 0}, {0, 0}, {0, 0}]
			 * @F 5    [{3, 0}, {3, 0}, {0, 0}, {0, 0}, {0, 0}]
			 *
			 *
			 * Table 4.7.20.2-E. type_path structures for @C Outer . @B Middle . @A Inner
			 *
			 * Annotation	path_length	path
			 * @A 2    [{1, 0}, {1, 0}]
			 * @B 1    [{1, 0}]
			 * @C 0    []
			 *
			 *
			 * Table 4.7.20.2-F. type_path structures for Outer . Middle<@D Foo . @C Bar> . Inner<@B String @A []>
			 *
			 * Annotation	path_length	path
			 * @A 3    [{1, 0}, {1, 0}, {3, 0}]
			 * @B 4    [{1, 0}, {1, 0}, {3, 0}, {0, 0}]
			 * @C 3    [{1, 0}, {3, 0}, {1, 0}]
			 * @D 2    [{1, 0}, {3, 0}]
			 */
			int len = r.readUnsignedByte()<<1;
			typePaths = r.readBytes(len);
		}
	}

	// <none>
	public static final class T_Empty extends TypeAnno {
		public T_Empty(byte type) {
			super(type);
		}

		@Override
		boolean check(byte type) {
			switch (type) {
				case TargetType.EMPTY_FIELD:
				case TargetType.EMPTY_RETURN:
				case TargetType.EMPTY_RECEIVER:
					return true;
			}
			return false;
		}

		@Override
		void _t(ConstantPool pool, DynByteBuf w) {}
	}

	// u2
	public static final class T_Offset extends TypeAnno {
		public int offset;

		public T_Offset(byte type, int offset) {
			super(type);
			this.offset = offset;
		}

		@Override
		boolean check(byte type) {
			switch (type) {
				case TargetType.OFFSET_INSTANCEOF: // offset
				case TargetType.OFFSET_LAMBDA_METHOD:
				case TargetType.OFFSET_LAMBDA_NEW:
				case TargetType.OFFSET_NEW:
					return true;
			}
			return false;
		}

		@Override
		void _t(ConstantPool pool, DynByteBuf w) {
			w.putShort(offset);
		}
	}

	// u2 u1
	public static final class T_ArgOffset extends TypeAnno {
		public int offset, arg_id;

		public T_ArgOffset(byte type, int offset, int arg_id) {
			super(type);
			this.offset = offset;
			this.arg_id = arg_id;
		}

		@Override
		boolean check(byte type) {
			switch (type) {
				case TargetType.ARG_CAST:
				case TargetType.ARG_CONSTRUCTOR:
				case TargetType.ARG_LAMBDA_METHOD:
				case TargetType.ARG_LAMBDA_NEW:
				case TargetType.ARG_METHOD:
					return true;
			}
			return false;
		}

		@Override
		void _t(ConstantPool pool, DynByteBuf w) {
			w.putShort(offset).put((byte) arg_id);
		}
	}

	// u1
	public static final class T_ParamId extends TypeAnno {
		public int id;

		public boolean isSignature() {
			return type == TargetType.PARAM_CLZ || type == TargetType.PARAM_METHOD;
		}

		public T_ParamId(byte type, int id) {
			super(type);
			this.id = id;
		}

		@Override
		void _t(ConstantPool pool, DynByteBuf w) {
			w.put((byte) id);
		}

		@Override
		boolean check(byte type) {
			switch (type) {
				case TargetType.FORMAL_PARAM:
				case TargetType.PARAM_CLZ:
				case TargetType.PARAM_METHOD:
					return true;
			}
			return false;
		}
	}

	// u1 u1
	public static final class T_ParamBound extends TypeAnno {
		public int id, boundId;

		public T_ParamBound(byte type, int id, int boundId) {
			super(type);
			this.id = id;
			this.boundId = boundId;
		}

		@Override
		void _t(ConstantPool pool, DynByteBuf w) {
			w.put((byte) id).put((byte) boundId);
		}

		@Override
		boolean check(byte type) {
			switch (type) {
				case TargetType.PARAM_BOUND_CLZ:
				case TargetType.PARAM_BOUND_METHOD:
					return true;
			}
			return false;
		}
	}

	// u2
	@Deprecated
	public static final class T_Ex extends TypeAnno {
		public int id;

		public T_Ex(byte type, int id) {
			super(type);
			this.id = id;
		}

		@Override
		void _t(ConstantPool pool, DynByteBuf w) {
			w.putShort(id);
		}

		@Override
		boolean check(byte type) {
			switch (type) {
				case TargetType.THROWS:
				case TargetType.CATCH:
				case TargetType.SUPER:
					return true;
			}
			return false;
		}
	}

	@Deprecated
	public static final class T_LV extends TypeAnno {
		public List<int[]> list;

		public T_LV(byte type, List<int[]> list) {
			super(type);
			this.list = list;
		}

		@Override
		void _t(ConstantPool pool, DynByteBuf w) {
			w.putShort(list.size());
			for (int i = 0; i < list.size(); i++) {
				int[] arr = list.get(i);
				for (int j = 0; j < 3; j++) {
					w.putShort(arr[j]);
				}
			}
		}

		@Override
		boolean check(byte type) {
			switch (type) {
				case TargetType.LOCAL_VAR:
				case TargetType.RESOURCE_VAR:
					return true;
			}
			return false;
		}
	}
}