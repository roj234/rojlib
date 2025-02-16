package roj.plugins.ci.plugin;

import roj.asm.util.Context;
import roj.asmx.AnnotationRepo;
import roj.io.IOUtil;
import roj.plugins.ci.FMD;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2025/3/18 0018 15:29
 */
public class AnnotationCache implements Processor {
	@Override
	public String name() {return "注解缓存提供程序";}

	@Override
	public List<Context> process(List<Context> classes, ProcessEnvironment ctx) {
		if (ctx.increment == 0 && "true".equals(ctx.project.getVariables().get("fmd:annotation_cache:enable"))) {
			var ar = new AnnotationRepo();
			for (Context file : classes) ar.addRaw(file.get(), file.getFileName());
			var buf = IOUtil.getSharedByteBuf();
			ar.serialize(buf);
			ctx.generatedFiles.put("META-INF/annotations.repo", DynByteBuf.wrap(buf.toByteArray()));
			FMD.LOGGER.debug("AnnotationCache created, size={}.", buf.wIndex());
		}
		return classes;
	}
}
