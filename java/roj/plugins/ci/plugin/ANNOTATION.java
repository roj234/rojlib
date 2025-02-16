package roj.plugins.ci.plugin;

import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstRefUTF;
import roj.asm.util.Context;
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
			ConstantPool cp = ctx.get(i).getData().cp();
			List<Constant> array = cp.array();
			for (Constant constant : array) {
				if (constant.type() == Constant.STRING) {
					CstRefUTF str = (CstRefUTF) constant;
					String template = str.name().str();
					if (template.contains("${")) {
						try {
							str.setValue(cp.getUtf(Formatter.simple(template).format(pc.project.variables, IOUtil.getSharedCharBuf()).toString()));
						} catch (IllegalArgumentException e) {
							FMD.LOGGER.warn(e.getMessage()+" (在文件"+ctx.get(i).getFileName()+"中)");
						}
					}
				}
			}
		}

		ctx = pc.getAnnotatedClass(classes, "roj/plugins/ci/annotation/CompileOnly");
		for (int i = 0; i < ctx.size(); i++) {
			classes.remove(ctx.get(i));
		}
		return classes;
	}
}