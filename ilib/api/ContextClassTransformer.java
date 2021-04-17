package ilib.api;

import roj.asm.util.Context;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * 更高效的transform方案
 *
 * @author Roj234
 * @since 2021/10/19 23:21
 */
public interface ContextClassTransformer extends IClassTransformer {
	@Override
	@Deprecated
	default byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (basicClass == null) return null;
		Context ctx = new Context(name, basicClass);
		transform(transformedName, ctx);
		if (ctx.inRaw()) {
			return ctx.get().list;
		}
		return ctx.get().toByteArray();
	}

	void transform(String transformedName, Context context);
}
