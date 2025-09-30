package roj.ci.plugin;

import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstRefUTF;
import roj.asm.type.Type;
import roj.ci.BuildContext;
import roj.ci.MCMake;
import roj.collect.BiMap;
import roj.io.IOUtil;
import roj.text.Formatter;

import java.util.List;

/**
 * @author Roj234
 * @since 2025/2/16 23:02
 */
public class ANNOTATION implements Processor {
	@Override public String name() {return "编译期注解处理程序";}
	@Override public boolean defaultEnabled() {return true;}

	@Override
	public void afterCompilePre(BuildContext ctx) {
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
			classMap.put(annotatedElement.owner(), aliasTarget.owner());
		});
	}

	@Override
	public void afterCompilePost(BuildContext ctx) {
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
							MCMake.LOGGER.warn("未完全匹配的格式字符串 '"+string+"' ("+annotatedClass.get(i).getFileName()+")");
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