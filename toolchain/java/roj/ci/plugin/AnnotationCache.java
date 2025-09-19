package roj.ci.plugin;

import roj.asmx.AnnotationRepo;
import roj.ci.BuildContext;
import roj.ci.MCMake;
import roj.collect.HashSet;
import roj.io.IOUtil;
import roj.text.TextUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.Set;

/**
 * @author Roj234
 * @since 2025/3/18 15:29
 */
public class AnnotationCache implements Processor {
	@Override
	public String name() {return "注解缓存提供程序";}

	@Override
	public void afterCompile(BuildContext ctx) {
		if (ctx.updateCount > 0 && "true".equals(ctx.project.getVariables().getOrDefault("fmd:annotation_cache", "true"))) {
			var repo = new AnnotationRepo();

			Set<String> whitelist = new HashSet<>(TextUtil.split(ctx.project.getVariables().getOrDefault("fmd:annotation_inrepo", ""), ';'));
			ctx.getDepAnnotations("roj/ci/annotation/InRepo", el -> whitelist.add(el.owner()));
			MCMake.LOGGER.debug("AnnotationCache whitelist={}", whitelist);

			for (String type : whitelist) {
				ctx.getBundledAnnotations(type, el -> {
					repo.getAnnotations().computeIfAbsent(type, Helpers.fnHashSet()).add(el);
				});
			}

			if (!repo.getAnnotations().isEmpty()) {
				var buf = IOUtil.getSharedByteBuf();
				repo.serialize(buf);
				ctx.addFile(AnnotationRepo.CACHE_NAME, DynByteBuf.wrap(buf.toByteArray()));
				MCMake.LOGGER.debug("AnnotationCache size={}.", buf.wIndex());
			}
		}
	}
}
