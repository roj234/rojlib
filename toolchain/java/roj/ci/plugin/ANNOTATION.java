package roj.ci.plugin;

import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstRefUTF;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.ci.BuildContext;
import roj.collect.BiMap;
import roj.io.IOUtil;
import roj.text.Formatter;

import java.util.List;

import static roj.asm.Opcodes.ACC_STATIC;
import static roj.asm.Opcodes.ALOAD_0;
import static roj.ci.MCMake.log;

/**
 * @author Roj234
 * @since 2025/2/16 23:02
 */
public class ANNOTATION implements Plugin {
	@Override public String name() {return "编译期注解处理程序";}
	@Override public boolean defaultEnabled() {return true;}

	@Override
	public void process(BuildContext ctx) {
		var annotatedClass = ctx.getAnnotatedClasses("roj/ci/annotation/ExcludeFromArtifact");
		for (int i = 0; i < annotatedClass.size(); i++) {
			ctx.removeClass(annotatedClass.get(i));
		}
		annotatedClass = ctx.getAnnotatedClasses("roj/ci/annotation/AliasOf");
		for (int i = 0; i < annotatedClass.size(); i++) {
			ctx.removeClass(annotatedClass.get(i));
		}

		BiMap<String, String> classMap = ctx.getMapper().getClassMap();
		ctx.getDepAnnotations("roj/ci/annotation/AliasOf", annotatedElement -> {
			Annotation aliasOf = annotatedElement.annotations().get("roj/ci/annotation/AliasOf");
			Type aliasTarget = aliasOf.getClass("value");
			String altValue = aliasOf.getString("altValue", null);

			// 对于简单的成员名称转换，尽管你可以使用classNodeEvents，但是数据驱动的Mapper支持多线程，而且将考虑ClassNode中现有及未来的所有引用
			classMap.put(annotatedElement.owner(), altValue == null ? aliasTarget.owner() : altValue.replace('.', '/'));
		});

		ctx.getDepAnnotations("roj/ci/annotation/StaticHook", element -> {
			Annotation annotation = element.annotations().get("roj/ci/annotation/StaticHook");
			MemberDescriptor descriptor = MemberDescriptor.fromJavapLike(annotation.getString("value"));

			// 使用事件驱动的方式来按需遍历类，而不是ctx.getChangedClass()然后判断名称是否合适。
			ctx.classNodeEvents().declaredMethod(descriptor.owner, descriptor.name, descriptor.rawDesc, (classNode, methodNode) -> {
				CodeWriter cw = methodNode.overwrite(classNode.cp);

				int staticOffset = (methodNode.modifier & ACC_STATIC) == 0 ? 1 : 0;

				int slots = TypeHelper.paramSize(methodNode.rawDesc())+staticOffset;
				cw.visitSize(slots, slots);

				if (staticOffset != 0) cw.insn(ALOAD_0);

				List<Type> parameters = cw.method.parameters();
				for (int i = 0; i < parameters.size(); i++) {
					cw.varLoad(parameters.get(i), i+ staticOffset);
				}

				cw.invokeS(element.owner(), element.name(), element.desc());
				cw.return_(cw.method.returnType());
				cw.finish();

				return true;
			});
		});

		// 对于更加复杂的需求，你可以加载甚至动态生成Mixin
		//ctx.addMixin(mixin);

		// 注意，事件驱动的转换必须在post阶段之前调用
	}

	@Override
	public void postProcess(BuildContext ctx) {
		var annotatedClass = ctx.getAnnotatedClasses("roj/ci/annotation/ReplaceConstant");
		for (int i = 0; i < annotatedClass.size(); i++) {
			ClassNode data = annotatedClass.get(i).getData();
			ConstantPool cp = data.cp();

			List<Annotation> annotations = Annotations.getAnnotations(cp, data, false);
			for (int j = 0; j < annotations.size(); j++) {
				Annotation annotation = annotations.get(j);
				if (annotation.type().equals("roj/ci/annotation/ReplaceConstant")) {
					annotations.remove(j);
					break;
				}
			}

			List<Constant> array = cp.constants();
			for (Constant constant : array) {
				if (constant.type() == Constant.STRING) {
					CstRefUTF str = (CstRefUTF) constant;
					String template = str.value().str();
					if (template.contains("${")) {
						String string = Formatter.simple(template).format(ctx.getVariables(annotatedClass.get(i)), IOUtil.getSharedCharBuf()).toString();
						str.setValue(cp.getUtf(string));
						if (string.contains("${"))
							log.warn("未完全匹配的格式字符串 '"+string+"' ("+annotatedClass.get(i).getFileName()+")");
					}
				}
			}
		}

		annotatedClass = ctx.getAnnotatedClasses("roj/ci/annotation/Public");
		for (int i = 0; i < annotatedClass.size(); i++) {
			ClassNode data = annotatedClass.get(i).getData();
			data.modifier |= Opcodes.ACC_PUBLIC;
			for (MethodNode method : data.methods) {
				Annotation annotation = Annotation.findInvisible(data.cp, method, "roj/ci/annotation/Public");
				if (annotation != null) {
					method.modifier |= Opcodes.ACC_PUBLIC;
				}
			}
			for (FieldNode field : data.fields) {
				Annotation annotation = Annotation.findInvisible(data.cp, field, "roj/ci/annotation/Public");
				if (annotation != null) {
					field.modifier |= Opcodes.ACC_PUBLIC;
				}
			}
		}
	}
}