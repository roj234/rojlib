package roj.plugins.ci.plugin;

import roj.asmx.AnnotationRepo;
import roj.asmx.Context;
import roj.collect.HashSet;
import roj.io.IOUtil;
import roj.plugins.ci.FMD;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2025/3/18 15:29
 */
public class AnnotationCache implements Processor {
	static final Set<String> DEFAULT_BLACKLIST = new HashSet<>("java/lang/Deprecated", "java/lang/annotation/Target", "java/lang/annotation/Retention", "org/jetbrains/annotations/Nullable", "org/jetbrains/annotations/NotNull", "org/jetbrains/annotations/Contract");

	@Override
	public String name() {return "注解缓存提供程序";}

	@Override
	public List<Context> process(List<Context> classes, ProcessEnvironment ctx) {
		if (ctx.increment == 0 && "true".equals(ctx.project.getVariables().get("fmd:annotation_cache:enable"))) {
			var ar = new AnnotationRepo();
			for (Context file : classes) ar.addRaw(file.getClassBytes(), file.getFileName());

			List<Context> whitelist = ctx.getAnnotatedClass(classes, "roj/plugins/ci/annotation/InRepo");
			if (whitelist.isEmpty()) {
				for (var itr = ar.getAnnotations().entrySet().iterator(); itr.hasNext(); ) {
					var entry = itr.next();
					if (DEFAULT_BLACKLIST.contains(entry.getKey())) itr.remove();
				}
			} else {
				Set<String> classNames = new HashSet<>();
				for (Context ctx1 : whitelist) classNames.add(ctx1.getClassName());

				for (var itr = ar.getAnnotations().entrySet().iterator(); itr.hasNext(); ) {
					var entry = itr.next();
					if (!classNames.contains(entry.getKey())) itr.remove();
				}
			}

			var buf = IOUtil.getSharedByteBuf();
			ar.serialize(buf);
			ctx.generatedFiles.put("META-INF/annotations.repo", DynByteBuf.wrap(buf.toByteArray()));
			FMD.LOGGER.debug("AnnotationCache created, size={}.", buf.wIndex());
		}
		return classes;
	}
}
