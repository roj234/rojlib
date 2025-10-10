package roj.compiler.plugins.struct;

import roj.asm.Attributed;
import roj.asm.ClassDefinition;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.annotation.Annotation;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.api.Compiler;
import roj.compiler.api.Processor;

import java.util.Set;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2025/10/26 04:09
 */
public class StructPlugin implements Processor {
	public static void register(Compiler compiler) {
		compiler.addResolveListener(0, info -> {
			if (info.name().equals("roj/compiler/plugins/struct/StructImpl")) {
				info.parent(null);
			}
			return info;
		});
		compiler.addAnnotationProcessor(new StructPlugin());
	}

	@Override public Set<String> acceptedAnnotations() {return Set.of("roj/compiler/plugins/struct/Struct");}

	@Override
	public void handle(CompileContext ctx, ClassDefinition file, Attributed node, Annotation annotation) {
		CompileUnit unit = (CompileUnit) file;

		// LavaPPPP.Struct 转换规则 (Draft)
		// 代码结构限制:
		// 1. 不能有构造器
		// 2. 不能有默认值
		// 代码转换规则：
		// 1. 所有对字段的访问替换成Unsafe，参考如下
		// 2. 实例方法转换为静态方法，第0个参数转换为long address
		// 3. 伪实例(编译期类型)通过 ((Pointer<Type>)Pointer.from(1234L)).value() 转换，不过实际上全部是long
		//    通过Into相关处理函数
		//    TODO 规范化伪类型API，Context/as/long，特别是显式和隐式转换，允许自动调用toString并移除现有StringConcat中等的函数。
		//         这可能需要插入更多的隐式函数调用，需要修改TypeCast.Result类？
		// 4. 隐式生成toString，就像 record
		unit.interfaces().clear();
		unit.parent("roj/compiler/plugins/struct/StructImpl");

		ArrayList<MethodNode> methods = unit.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode method = methods.get(i);
			if ((method.modifier & ACC_STATIC) == 0) {
				if (method.name().equals("<init>")) {
					methods.remove(i--);
					continue;
				}

				method.parameters().add(0, Type.LONG_TYPE);
				method.modifier |= ACC_STATIC;

				var fake = method.copyDesc();
				fake.addAttribute(new StructInstanceMethod());
				unit.methods.add(fake);
				unit.dontSerialize(fake);
			}
		}

		int offset = 0;
		var packed = annotation.getBool("packed", true);

		for (FieldNode field : unit.fields) {
			if ((field.modifier& ACC_STATIC) == 0) {
				field.addAttribute(new StructField());
				unit.dontSerialize(field);

				Type type = field.fieldType();

				int dataSize = switch (type.getActualType()) {
					default -> 0;
					case Type.BOOLEAN, Type.BYTE -> 1;
					case Type.CHAR, Type.SHORT -> 2;
					case Type.INT, Type.FLOAT -> 4;
					case Type.LONG, Type.DOUBLE -> 8;
				};

				if (!packed) offset = (offset + dataSize - 1) & -dataSize;

				var getter = unit.newMethod(ACC_PUBLIC | ACC_STATIC, file.name(), "(J)"+type.toDesc());
				getter.visitSize(5, 2);
				getter.field(GETSTATIC, "roj/reflect/Unsafe", "U", "Lroj/reflect/Unsafe;");
				getter.insn(LLOAD_0);
				getter.ldc((long) offset);
				getter.insn(LADD);
				getter.invokeV("roj/reflect/Unsafe", "get"+type.capitalized(), "(L)"+type.toDesc());
				getter.return_(type);
				getter.finish();

				var setter = unit.newMethod(ACC_PUBLIC | ACC_STATIC, file.name(), "(J"+type.toDesc()+")V");
				setter.visitSize(5, 2 + type.length());
				setter.field(GETSTATIC, "roj/reflect/Unsafe", "U", "Lroj/reflect/Unsafe;");
				setter.insn(LLOAD_0);
				setter.ldc((long) offset);
				setter.insn(LADD);
				setter.invokeV("roj/reflect/Unsafe", "put"+type.capitalized(), "(L"+type.toDesc()+")V");
				setter.insn(RETURN);
				setter.finish();

				offset += dataSize;
			}
		}

		ctx.compiler.unlink(unit);
	}
}
