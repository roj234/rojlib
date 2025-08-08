package roj.ci.plugin;

import roj.asmx.AnnotationRepo;
import roj.asmx.Context;
import roj.collect.HashSet;
import roj.io.IOUtil;
import roj.ci.FMD;
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
	public void afterCompile(ProcessEnvironment ctx) {
		if (ctx.increment <= ProcessEnvironment.INC_REBUILD && "true".equals(ctx.project.getVariables().getOrDefault("fmd:annotation_cache", "true"))) {
			var repo = new AnnotationRepo();
			for (Context file : ctx.getClasses()) repo.add(file);

			//ctx.getLibraryClass()
			List<Context> whitelist = ctx.getAnnotatedClass("roj/ci/annotation/InRepo");
			if (whitelist.isEmpty()) {
				for (var itr = repo.getAnnotations().entrySet().iterator(); itr.hasNext(); ) {
					var entry = itr.next();
					if (DEFAULT_BLACKLIST.contains(entry.getKey())) itr.remove();
				}
			} else {
				Set<String> classNames = new HashSet<>();
				for (Context ctx1 : whitelist) classNames.add(ctx1.getClassName());

				for (var itr = repo.getAnnotations().entrySet().iterator(); itr.hasNext(); ) {
					var entry = itr.next();
					if (!classNames.contains(entry.getKey())) itr.remove();
				}
			}

			if (!repo.getAnnotations().isEmpty()) {
				var buf = IOUtil.getSharedByteBuf();
				repo.serialize(buf);
				ctx.addFile(AnnotationRepo.CACHE_NAME, DynByteBuf.wrap(buf.toByteArray()));
				FMD.LOGGER.debug("AnnotationCache created, size={}.", buf.wIndex());
			}
		}
	}
}
