package roj.plugins.ci.plugin;

import roj.asm.ClassNode;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstRefUTF;
import roj.asm.type.Type;
import roj.asmx.Context;
import roj.io.IOUtil;
import roj.plugins.ci.FMD;
import roj.text.Formatter;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/5/22 1:58
 */
public class ANNOTATION implements Processor {
	@Override
	public String name() {return "编译期注解处理程序";}

	@Override
	public List<Context> process(List<Context> classes, ProcessEnvironment pc) {
		var ctx = pc.getAnnotatedClass(classes, "roj/plugins/ci/annotation/ReplaceConstant");
		for (int i = 0; i < ctx.size(); i++) {
			ClassNode data = ctx.get(i).getData();
			ConstantPool cp = data.cp();

			List<Annotation> annotations = Annotations.getAnnotations(cp, data, false);
			for (int j = 0; j < annotations.size(); j++) {
				Annotation annotation = annotations.get(j);
				if (annotation.type().equals("roj/plugins/ci/annotation/ReplaceConstant")) {
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
						String string = Formatter.simple(template).format(pc.project.variables, IOUtil.getSharedCharBuf()).toString();
						str.setValue(cp.getUtf(string));
						if (string.contains("${"))
							FMD.LOGGER.warn(string+" (在文件"+ctx.get(i).getFileName()+"中)");
					}
				}
			}
		}

		ctx = pc.getAnnotatedClass(classes, "roj/plugins/ci/annotation/CompileOnly");
		for (int i = 0; i < ctx.size(); i++) {
			classes.remove(ctx.get(i));
		}

		ctx = pc.getAnnotatedClass(classes, "roj/compiler/api/AliasOf");
		for (int i = 0; i < ctx.size(); i++) {
			Context context = ctx.get(i);
			classes.remove(context);

			ClassNode data = context.getData();

			List<Annotation> annotations = Annotations.getAnnotations(data.cp(), data, false);
			Annotation aliasOf = null;
			for (int j = 0; j < annotations.size(); j++) {
				aliasOf = annotations.get(j);
				if (aliasOf.type().equals("roj/compiler/api/AliasOf")) {
					annotations.remove(j);
					break;
				}
			}

			Type aliasTarget = aliasOf.getClass("value");

			pc.getCPHooks().referencedClass(context.getClassName(), (_ctx, node) -> {
				_ctx.cp.setUTFValue(node.value(), aliasTarget.owner);
				return true;
			});
		}
		return classes;
	}
}