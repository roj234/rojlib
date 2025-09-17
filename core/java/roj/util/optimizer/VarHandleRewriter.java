package roj.util.optimizer;

import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.cp.Constant;
import roj.asm.cp.CstRef;
import roj.asm.frame.FrameVisitor;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.TransformException;
import roj.asmx.launcher.Autoload;
import roj.asmx.launcher.Tweaker;
import roj.ci.annotation.IndirectReference;
import roj.collect.ArrayList;
import roj.io.IOUtil;
import roj.reflect.Reflection;
import roj.reflect.Unsafe;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2025/09/17 00:14
 */
@Autoload
public class VarHandleRewriter implements ConstantPoolHooks.Hook<ClassNode> {
	private static final Logger LOGGER = Logger.getLogger("VHRewriter");
	private static final String PROXY_NAME = "java/lang/VHProxy";
	private static final String STATIC_SUFFIX = "$STATIC", ARRAY_SUFFIX = "$ARRAY";

	@IndirectReference
	public static long instanceField(Object o, Class<?> recv, String name, Class<?> type) {
		try {
			Field field = Reflection.getField(recv, name);
			if (!field.getType().isAssignableFrom(type)) throw new NoSuchFieldException("Field "+name+" ("+field.getType()+") is not "+type);
			return Unsafe.U.objectFieldOffset(field);
		} catch (Exception e) {
			Helpers.athrow(e);
			return 0;
		}
	}
	@IndirectReference
	public static Object[] staticField(Object o, Class<?> recv, String name, Class<?> type) {
		try {
			Field field = Reflection.getField(recv, name);
			if (!field.getType().isAssignableFrom(type)) throw new NoSuchFieldException("Field "+name+" ("+field.getType()+") is not "+type);
			return new Object[] {
				Unsafe.U.staticFieldOffset(field),
				Unsafe.U.staticFieldBase(field)
			};
		} catch (Exception e) {
			Helpers.athrow(e);
			return null;
		}
	}

	static {
		try {
			Reflection.defineSystemClass(makeProxy());
			Tweaker.CONDITIONAL.annotatedClass("roj/optimizer/FastVarHandle", new VarHandleRewriter());
		} catch (Exception e) {
			LOGGER.fatal("初始化失败", e);
		}
	}

	private interface Stem {
		int getTT(Object o);
		void putTT(Object o, int x);

		int getTTVolatile(Object o);
		void putTTVolatile(Object o, int x);
		int getTTAcquire(Object o);

		void putTTAcquire(Object o, int x);
		void putTTRelease(Object o, int x);

		int getTTOpaque(Object o);
		void putTTOpaque(Object o, int x);

		boolean compareAndSetTT(Object o, int expected, int x);
		boolean compareAndSetTTAcquire(Object o, int expected, int x);
		boolean compareAndSetTTRelease(Object o, int expected, int x);
		boolean compareAndSetTTPlain(Object o, int expected, int x);

		int getAndAddTT(Object o, int delta);
		int getAndAddTTRelease(Object o, int delta);
		int getAndAddTTAcquire(Object o, int delta);

		int getAndSetTT(Object o, int newValue);
		int getAndSetTTRelease(Object o, int newValue);
		int getAndSetTTAcquire(Object o, int newValue);

		boolean weakCompareAndSetTT(Object o, int expected, int x);
		boolean weakCompareAndSetTTAcquire(Object o, int expected, int x);
		boolean weakCompareAndSetTTRelease(Object o, int expected, int x);
		boolean weakCompareAndSetTTPlain(Object o, int expected, int x);

		int compareAndExchangeTT(Object o, int expected, int x);
		int compareAndExchangeTTAcquire(Object o, int expected, int x);
		int compareAndExchangeTTRelease(Object o, int expected, int x);

		int getAndBitwiseOrTT(Object o, int mask);
		int getAndBitwiseOrTTRelease(Object o, int mask);
		int getAndBitwiseOrTTAcquire(Object o, int mask);

		int getAndBitwiseAndTT(Object o, int mask);
		int getAndBitwiseAndTTRelease(Object o, int mask);
		int getAndBitwiseAndTTAcquire(Object o, int mask);

		int getAndBitwiseXorTT(Object o, int mask);
		int getAndBitwiseXorTTRelease(Object o, int mask);
		int getAndBitwiseXorTTAcquire(Object o, int mask);
	}
	private static byte[] makeProxy() throws IOException {
		var impl = new ClassNode();
		impl.name(PROXY_NAME);
		impl.version = ClassNode.JavaVersion(9);
		impl.modifier = ACC_PUBLIC | ACC_FINAL;

		String unsafe = "jdk/internal/misc/Unsafe";
		String unsafeType = 'L'+unsafe+';';

		CodeWriter w;

		ClassNode template = ClassNode.parseSkeleton(IOUtil.getResource("roj/util/optimizer/VarHandleRewriter$Stem.class"));
		var types = new Type[]{
				Type.BOOLEAN_TYPE,
				Type.BYTE_TYPE,
				Type.CHAR_TYPE,
				Type.SHORT_TYPE,
				Type.INT_TYPE,
				Type.FLOAT_TYPE,
				Type.LONG_TYPE,
				Type.DOUBLE_TYPE,
				Type.klass("java/lang/Object")
		};

		for (MethodNode method : template.methods) {
			String methodName = method.name();
			int end = types.length - (methodName.startsWith("getAndB") ? 1 : 0);

			for (int i = 0; i < end; i++) {
				String newName = methodName.replace("TT", types[i].capitalized().replace("Object", "Reference"));
				w = impl.newMethod(
						method.modifier&(~ACC_ABSTRACT)|ACC_STATIC,
						newName,
						"()V"
				);
				w.field(GETSTATIC, Reflection.getMagicAccessorClass(), "theInternalUnsafe", unsafeType);

				w.insn(ALOAD_2);
				w.insn(LLOAD_0);

				List<Type> parameters = Type.getMethodTypes(method.rawDesc());
				for (int j = 0; j < parameters.size(); j++) {
					Type parameter = parameters.get(j);
					if (parameter == Type.INT_TYPE) {
						parameters.set(j, types[i]);
					}
				}

				List<Type> delegateParameter = w.method.parameters();
				delegateParameter.clear();
				delegateParameter.add(Type.LONG_TYPE);
				delegateParameter.addAll(parameters);
				w.method.setReturnType(delegateParameter.remove(delegateParameter.size()-1));

				int slot = 3;
				for (int j = 2; j < delegateParameter.size(); j++) {
					Type parameter = delegateParameter.get(j);
					w.varLoad(parameter, slot);
					slot += parameter.length();
				}

				String desc = Type.getMethodDescriptor(parameters);
				String s = "(Ljava/lang/Object;";
				w.invokeV(unsafe, newName, desc.substring(0, s.length())+"J"+desc.substring(s.length()));
				w.return_(w.method.returnType());

				w.computeFrames(FrameVisitor.COMPUTE_SIZES);
			}

			for (int i = 0; i < end; i++) {
				String newName = methodName.replace("TT", types[i].capitalized().replace("Object", "Reference"));
				w = impl.newMethod(
						method.modifier&(~ACC_ABSTRACT)|ACC_STATIC,
						newName,
						"()V"
				);

				List<Type> parameters = Type.getMethodTypes(method.rawDesc());
				for (int j = 0; j < parameters.size(); j++) {
					Type parameter = parameters.get(j);
					if (parameter == Type.INT_TYPE) {
						parameters.set(j, types[i]);
					}
				}

				List<Type> delegateParameter = w.method.parameters();
				delegateParameter.clear();
				delegateParameter.addAll(parameters);
				delegateParameter.add(1, Type.INT_TYPE);
				w.method.setReturnType(delegateParameter.remove(delegateParameter.size()-1));

				w.field(GETSTATIC, Reflection.getMagicAccessorClass(), "theInternalUnsafe", unsafeType);

				w.insn(ALOAD_0);
				w.insn(ILOAD_1);
				w.insn(I2L);
				if (i > 0 && i < types.length-1) {
					if (i > 1) {
						w.ldc(i/2);
						/*w.ldc(new int[]{
								0,0,
								1,1,
								2,2,
								3,3
						}[i]);*/
						w.insn(LSHL);
					}
				} else {
					w.field(GETSTATIC, unsafe, "ARRAY_"+types[i].toString().toUpperCase(Locale.ROOT)+"_INDEX_SCALE", "I");
					w.insn(I2L);
					w.insn(LMUL);
				}
				w.field(GETSTATIC, unsafe, "ARRAY_"+types[i].toString().toUpperCase(Locale.ROOT)+"_BASE_OFFSET", "I");
				w.insn(I2L);
				w.insn(LADD);

				int slot = 2;
				for (int j = 2; j < delegateParameter.size(); j++) {
					Type parameter = delegateParameter.get(j);
					w.varLoad(parameter, slot);
					slot += parameter.length();
				}

				String desc = Type.getMethodDescriptor(parameters);
				String s = "(Ljava/lang/Object;";
				w.invokeV(unsafe, newName, desc.substring(0, s.length())+"J"+desc.substring(s.length()));
				w.return_(w.method.returnType());

				w.computeFrames(FrameVisitor.COMPUTE_SIZES);
			}
		}

		var attribute = new Annotations(true, new Annotation("Ljdk/internal/vm/annotation/ForceInline;", Collections.emptyMap()));
		for (var mn : impl.methods) mn.addAttribute(attribute);

		return AsmCache.toByteArray(impl);
	}

	@Override
	public boolean transform(ClassNode context, ClassNode node) throws TransformException {
		LOGGER.debug("开始转换 {}", node.name());

		ArrayList<FieldNode> fields = node.fields;
		int size = fields.size();
		for (int i = 0; i < size; i++) {
			FieldNode field = fields.get(i);
			if (field.name().endsWith(STATIC_SUFFIX) && field.rawDesc().equals("Ljava/lang/invoke/VarHandle;")) {
				node.newField(field.modifier, field.name() + "^b", "Ljava/lang/Object;");
			}
		}

		for (Constant c : context.cp.constants()) {
			if (c.type() == Constant.FIELD) {
				CstRef c1 = (CstRef) c;
				if (c1.rawDesc().equals("Ljava/lang/invoke/VarHandle;")) {
					context.cp.setUTFValue(c1.nameAndType().rawDesc(), "J");
				}
			}
		}

		var transformer = new CodeWriter() {
			static final String[] POSTFIX = {"Volatile", "Acquire", "Release", "Plain", "Opaque"};
			static final Type ERASER = Type.klass("java/lang/Object");

			char lastArrayType;

			@Override
			public void field(byte code, String owner, String name, String type) {
				if (name.endsWith(ARRAY_SUFFIX) && type.equals("J")) {
					lastArrayType = name.charAt(name.length()-ARRAY_SUFFIX.length()-1);
					return;
				}
				// INSTANCE INSTANCE FIELD
				if (name.endsWith(STATIC_SUFFIX) && type.equals("J")) {
					switch (code) {
						case PUTFIELD -> {
							super.insn(DUP2);

							super.ldc(0);
							super.insn(AALOAD);
							super.clazz(CHECKCAST, "java/lang/Long");
							super.invokeV("java/lang/Long", "longValue", "()J");
							super.field(code, owner, name, type);

							super.ldc(1);
							super.insn(AALOAD);
							super.field(code, owner, name+"^b", "Ljava/lang/Object;");
						}
						case PUTSTATIC -> {
							super.insn(DUP);

							super.ldc(0);
							super.insn(AALOAD);
							super.clazz(CHECKCAST, "java/lang/Long");
							super.invokeV("java/lang/Long", "longValue", "()J");
							super.field(code, owner, name, type);

							super.ldc(1);
							super.insn(AALOAD);
							super.field(code, owner, name+"^b", "Ljava/lang/Object;");
						}
						case GETFIELD -> {
							// 注意，为了避免pattern matching或猜测代码块，基于当前栈，并不利用额外变量时，我们只能这么处理
							super.insn(DUP);
							super.field(code, owner, name, type);
							super.insn(DUP2_X1);
							super.insn(POP2);
							super.field(code, owner, name+"^b", "Ljava/lang/Object;");
						}
						case GETSTATIC -> {
							super.field(code, owner, name, type);
							super.field(code, owner, name+"^b", "Ljava/lang/Object;");
						}
					}
					return;
				}
				super.field(code, owner, name, type);
			}

			@Override
			public void invoke(byte code, String owner, String name, String desc, boolean isInterfaceMethod) {
				if (desc.equals("(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/VarHandle;")) {
					if (name.equals("findVarHandle")) {
						super.invoke(INVOKESTATIC, "roj/util/optimizer/VarHandleRewriter", "instanceField", "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)J", false);
					} else {
						super.invoke(INVOKESTATIC, "roj/util/optimizer/VarHandleRewriter", "staticField", "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)[Ljava/lang/Object;", false);
					}
					return;
				}
				if (name.equals("arrayElementVarHandle") && desc.equals("(Ljava/lang/Class;)Ljava/lang/invoke/VarHandle;")) {
					super.insn(code != INVOKESTATIC ? POP2 : POP);
					super.ldc(0L);
					return;
				}

				if (owner.equals("java/lang/invoke/VarHandle")) {
					name = name.replace("set", "put");
					int insertAt = name.length();
					for (String postfix : POSTFIX) {
						if (name.endsWith(postfix)) {
							insertAt -= postfix.length();
							break;
						}
					}

					computeFrames(FrameVisitor.COMPUTE_FRAMES | FrameVisitor.COMPUTE_SIZES);

					List<Type> types = Type.getMethodTypes(desc);
					LOGGER.trace("转换调用 From {}.{} {}", owner, name, types);

					Type returnType = types.get(types.size() - 1);
					for (int i = 0; i < types.size(); i++) {
						Type type = types.get(i);
						if (!type.isPrimitive()) types.set(i, ERASER);
					}

					var argc = switch (name.substring(0, insertAt)) {
						case "get" -> 2;
						case "put", "getAndAdd", "getAndSet", "getAndBitwiseOr", "getAndBitwiseAnd", "getAndBitwiseXor" -> 3;
						case "compareAndSet", "weakCompareAndSet", "compareAndExchange" -> 4;
						default -> 0;
					};

					Type methodType = argc == 2/* get */ ? returnType : types.get(types.size() - 2);

					if (types.size() < argc) {
						// static field
						types.add(0, ERASER);
					}

					boolean ignoreReturnValue = !name.startsWith("put") && returnType == Type.VOID_TYPE && methodType != Type.VOID_TYPE;
					if (ignoreReturnValue) types.set(types.size()-1, methodType);

					var arrayType = lastArrayType;
					lastArrayType = 0;
					if (arrayType != 0) {
						// array handle

						String stringType = methodType.capitalized();
						name = name.substring(0, insertAt) + (stringType.equals("Object") ? "Reference" : stringType) + name.substring(insertAt);

						LOGGER.trace("转换调用 Array {}.{} {}", PROXY_NAME, name, types);
						super.invoke(Opcodes.INVOKESTATIC, PROXY_NAME, name, Type.getMethodDescriptor(types), false);
					} else {
						types.add(0, Type.LONG_TYPE);

						String stringType = methodType.capitalized();
						name = name.substring(0, insertAt) + (stringType.equals("Object") ? "Reference" : stringType) + name.substring(insertAt);

						LOGGER.trace("转换调用 InstanceOrStatic {}.{} {}", PROXY_NAME, name, types);
						super.invoke(Opcodes.INVOKESTATIC, PROXY_NAME, name, Type.getMethodDescriptor(types), false);
					}

					if (ignoreReturnValue) {
						super.insn((byte) (POP - 1 + methodType.length()));
					} else if (!returnType.isPrimitive() && !returnType.equals(ERASER))
						super.clazz(Opcodes.CHECKCAST, returnType);
					return;
				}
				super.invoke(code, owner, name, desc, isInterfaceMethod);
			}
		};

		for (MethodNode method : context.methods) {
			LOGGER.trace("正在转换方法 {}", method.name());
			method.transform(context.cp, new ByteList(), transformer);
		}

		return true;
	}
}
