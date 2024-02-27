package roj.generator;

import roj.ReferenceByGeneratedClass;
import roj.asm.Parser;
import roj.asm.cp.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.reflect.ClassDefiner;

import java.io.FileOutputStream;
import java.security.ProtectionDomain;
import java.util.Collections;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/5/23 0023 0:29
 */
class GenerateReflectionHook {
	@ReferenceByGeneratedClass
	private static final String PROP_NAME = "_ILJ9DC_", CLASS_NAME = "java/lang/🔓_IL🐟"; // "海阔凭鱼跃，天高任鸟飞"

	public static void run() throws Exception {
		ConstantData ILCD = new ConstantData();
		ILCD.name(CLASS_NAME);
		ILCD.interfaces().add("java/util/function/Function");
		ILCD.interfaces().add("java/util/function/BiConsumer");
		ILCD.parent("jdk/internal/reflect/MagicAccessorImpl");
		ILCD.newField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "theInternalUnsafe", new Type("jdk/internal/misc/Unsafe"));
		ILCD.npConstructor();

		CodeWriter w = ILCD.newMethod(ACC_PUBLIC | ACC_STATIC, "<clinit>", "()V");
		w.visitSize(2, 0);
		w.invokeS("jdk/internal/misc/Unsafe", "getUnsafe", "()Ljdk/internal/misc/Unsafe;");
		w.field(PUTSTATIC, ILCD, 0);
		w.invokeS("java/lang/System", "getProperties", "()Ljava/util/Properties;");
		w.ldc(new CstString(PROP_NAME));
		w.newObject(ILCD.name);
		w.invokeV("java/util/Properties", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
		w.one(RETURN);
		w.finish();

		w = ILCD.newMethod(ACC_PUBLIC, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
		w.visitSize(7, 2);
		w.one(ALOAD_1);
		w.clazz(CHECKCAST, "[Ljava/lang/Object;");
		w.one(ASTORE_0);
		w.unpackArray(0, ClassLoader.class, Class.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class, boolean.class, int.class, Object.class);
		w.invokeS("java/lang/ClassLoader", "defineClass0", "(Ljava/lang/ClassLoader;Ljava/lang/Class;Ljava/lang/String;[BIILjava/security/ProtectionDomain;ZILjava/lang/Object;)Ljava/lang/Class;");
		w.one(ARETURN);
		w.finish();

		// source target package
		w = ILCD.newMethod(ACC_PUBLIC, "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V");
		w.visitSize(3, 3);

		w.one(ALOAD_1);
		w.clazz(CHECKCAST, "[Ljava/lang/Object;");
		w.one(ASTORE_0);

		w.one(ALOAD_0);
		w.one(ICONST_0);
		w.one(AALOAD);
		w.one(DUP);
		w.clazz(INSTANCEOF, "java/lang/Class");
		Label marker = new Label();
		Label marker2 = new Label();
		w.jump(IFEQ, marker);
		w.clazz(CHECKCAST, "java/lang/Class");
		w.invokeV("java/lang/Class", "getModule", "()Ljava/lang/Module;");
		w.jump(GOTO, marker2);
		w.label(marker);
		w.clazz(CHECKCAST, "java/lang/Module");
		w.label(marker2);
		w.one(ASTORE_1);

		w.one(ALOAD_0);
		w.one(ICONST_1);
		w.one(AALOAD);
		w.one(DUP);
		w.clazz(INSTANCEOF, "java/lang/Class");
		marker = new Label();
		marker2 = new Label();
		w.jump(IFEQ, marker);
		w.clazz(CHECKCAST, "java/lang/Class");
		w.invokeV("java/lang/Class", "getModule", "()Ljava/lang/Module;");
		w.jump(GOTO, marker2);
		w.label(marker);
		w.clazz(CHECKCAST, "java/lang/Module");
		w.label(marker2);
		w.one(ASTORE_0);

		w.one(ALOAD_2);
		w.clazz(CHECKCAST, "java/lang/String");
		w.one(ASTORE_2);

		Label label = CodeWriter.newLabel();
		w.one(ALOAD_0);
		w.jump(IFNONNULL, label);

		w.one(ALOAD_1);
		w.one(ALOAD_2);
		w.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddExportsToAllUnnamed", "(Ljava/lang/String;)V");

		w.one(ALOAD_1);
		w.one(ALOAD_2);
		w.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddOpensToAllUnnamed", "(Ljava/lang/String;)V");

		w.one(RETURN);
		w.label(label);

		w.one(ALOAD_1);
		w.one(ALOAD_2);
		w.one(ALOAD_0);
		w.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddExports", "(Ljava/lang/String;Ljava/lang/Module;)V");

		w.one(ALOAD_1);
		w.one(ALOAD_2);
		w.one(ALOAD_0);
		w.invoke(INVOKEVIRTUAL, "java/lang/Module", "implAddOpens", "(Ljava/lang/String;Ljava/lang/Module;)V");

		w.one(RETURN);
		w.finish();

		var fos = new FileOutputStream(Main.resourcePath.getAbsolutePath()+"/roj/reflect/Injector.class");
		Parser.toByteArrayShared(ILCD).writeToStream(fos);
		fos.close();
	}
	public static void run2() throws Exception {
		ConstantData BE = makeImpl(true);
		ConstantData LE = makeImpl(false);

		var fos = new FileOutputStream(Main.resourcePath.getAbsolutePath()+"/roj/reflect/Unaligned$BE.class");
		Parser.toByteArrayShared(BE).writeToStream(fos);
		fos.close();

		fos = new FileOutputStream(Main.resourcePath.getAbsolutePath()+"/roj/reflect/Unaligned$LE.class");
		Parser.toByteArrayShared(LE).writeToStream(fos);
		fos.close();
	}

	private static ConstantData makeImpl(boolean nativeBE) {
		var impl = new ConstantData();
		impl.name("roj/reflect/Unaligned$Impl");
		impl.addInterface("roj/reflect/Unaligned");
		impl.parent(CLASS_NAME);
		impl.npConstructor();
		ClassDefiner.premake(impl);

		String unsafe = "jdk/internal/misc/Unsafe";
		String unsafeType = 'L'+unsafe+';';

		var w = impl.newMethod(ACC_PUBLIC|ACC_FINAL, "allocateUninitializedArray", "(Ljava/lang/Class;I)Ljava/lang/Object;");
		w.visitSize(3, 3);
		w.field(GETSTATIC, CLASS_NAME, "theInternalUnsafe", unsafeType);
		w.one(ALOAD_1);
		w.one(ILOAD_2);
		w.invoke(INVOKEVIRTUAL, unsafe, "allocateUninitializedArray", "(Ljava/lang/Class;I)Ljava/lang/Object;");
		w.one(ARETURN);
		w.finish();

		String[] name1 = {"getCharUnaligned", "getIntUnaligned", "getLongUnaligned"};
		String[] name2 = {"java/lang/Character","java/lang/Integer","java/lang/Long"};
		String[] name3 = {"putCharUnaligned", "putIntUnaligned", "putLongUnaligned"};
		String[] desc1 = {"(C)C", "(I)I", "(J)J"};
		String theDesc = "CIJ";

		int i = 0;
		int v = 16;
		for(;;) {
			String usDesc = "(Ljava/lang/Object;J)"+theDesc.charAt(i);
			String myDesc = usDesc.replace('C', 'I');

			w = impl.newMethod(ACC_PUBLIC|ACC_FINAL, "get"+v+"U"+(nativeBE ?'B':'L'), myDesc);
			w.visitSize(4, 4);
			w.field(GETSTATIC, CLASS_NAME, "theInternalUnsafe", unsafeType);
			w.one(ALOAD_1);
			w.one(LLOAD_2);
			w.invoke(INVOKEVIRTUAL, unsafe, name1[i], usDesc);
			w.one(i==2 ? LRETURN : IRETURN);
			w.finish();

			w = impl.newMethod(ACC_PUBLIC|ACC_FINAL, "get"+v+"U"+(nativeBE ?'L':'B'), myDesc);
			w.visitSize(4, 4);
			w.field(GETSTATIC, CLASS_NAME, "theInternalUnsafe", unsafeType);
			w.one(ALOAD_1);
			w.one(LLOAD_2);
			w.invoke(INVOKEVIRTUAL, unsafe, name1[i], usDesc);
			w.invoke(INVOKESTATIC, name2[i], "reverseBytes", desc1[i]);
			w.one(i==2 ? LRETURN : IRETURN);
			w.finish();

			usDesc = "(Ljava/lang/Object;J"+theDesc.charAt(i)+")V";
			myDesc = usDesc.replace('C', 'I');
			int size = v == 64 ? 6 : 5;

			w = impl.newMethod(ACC_PUBLIC|ACC_FINAL, "put"+v+"U"+(nativeBE ?'B':'L'), myDesc);
			w.visitSize(size, size);
			w.field(GETSTATIC, CLASS_NAME, "theInternalUnsafe", unsafeType);
			w.one(ALOAD_1);
			w.one(LLOAD_2);
			w.vars(i==2 ? LLOAD : ILOAD, 4);
			w.invoke(INVOKEVIRTUAL, unsafe, name3[i], usDesc);
			w.one(RETURN);
			w.finish();

			w = impl.newMethod(ACC_PUBLIC|ACC_FINAL, "put"+v+"U"+(nativeBE ?'L':'B'), myDesc);
			w.visitSize(size, size);
			w.field(GETSTATIC, CLASS_NAME, "theInternalUnsafe", unsafeType);
			w.one(ALOAD_1);
			w.one(LLOAD_2);
			w.vars(i==2 ? LLOAD : ILOAD, 4);
			w.invoke(INVOKESTATIC, name2[i], "reverseBytes", desc1[i]);
			w.invoke(INVOKEVIRTUAL, unsafe, name3[i], usDesc);
			w.one(RETURN);
			w.finish();

			if (i == 2) break;
			v <<= 1;
			i++;
		}

		Annotation annotation = new Annotation("Ljdk/internal/vm/annotation/ForceInline;", Collections.emptyMap());
		for (MethodNode mn : impl.methods) {
			if (mn.name().startsWith("<")) continue;
			mn.putAttr(new Annotations(true, annotation));
		}
		return impl;
	}
}