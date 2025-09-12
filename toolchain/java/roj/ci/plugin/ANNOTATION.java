package roj.ci.plugin;

import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstRefUTF;
import roj.asm.type.Type;
import roj.asmx.Context;
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
	public void afterCompile(BuildContext ctx) {
		var annotatedClass = ctx.getAnnotatedClass("roj/ci/annotation/ReplaceConstant");
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

			List<Constant> array = cp.data();
			for (Constant constant : array) {
				if (constant.type() == Constant.STRING) {
					CstRefUTF str = (CstRefUTF) constant;
					String template = str.value().str();
					if (template.contains("${")) {
						String string = Formatter.simple(template).format(ctx.getVariables(annotatedClass.get(i)), IOUtil.getSharedCharBuf()).toString();
						str.setValue(cp.getUtf(string));
						if (string.contains("${"))
							MCMake.LOGGER.warn(string+" (在文件"+annotatedClass.get(i).getFileName()+"中)");
					}
				}
			}
		}

		annotatedClass = ctx.getAnnotatedClass("roj/ci/annotation/ExcludeFromArtifact");
		for (int i = 0; i < annotatedClass.size(); i++) {
			ctx.removeClasses(annotatedClass.get(i));
		}

		annotatedClass = ctx.getAnnotatedClass("roj/ci/annotation/Public");
		for (int i = 0; i < annotatedClass.size(); i++) {
			annotatedClass.get(i).getData().modifier |= Opcodes.ACC_PUBLIC;
		}

		BiMap<String, String> classMap = ctx.getMapper().getClassMap();

		annotatedClass = ctx.getAnnotatedClass("roj/ci/annotation/AliasOf");
		for (int i = 0; i < annotatedClass.size(); i++) {
			Context context = annotatedClass.get(i);
			ctx.removeClasses(context);

			ClassNode data = context.getData();

			List<Annotation> annotations = Annotations.getAnnotations(data.cp(), data, false);
			Annotation aliasOf = null;
			for (int j = 0; j < annotations.size(); j++) {
				aliasOf = annotations.get(j);
				if (aliasOf.type().equals("roj/ci/annotation/AliasOf")) {
					annotations.remove(j);
					break;
				}
			}

			Type aliasTarget = aliasOf.getClass("value");
			classMap.put(data.name(), aliasTarget.owner());
		}
	}
}