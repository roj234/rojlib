package ilib.asm;

import ilib.api.ContextClassTransformer;
import roj.asm.Parser;
import roj.asm.util.Context;
import roj.asm.util.TransformUtil;

import java.util.Collection;

import static roj.asm.ATList.getMapping;

/**
 * @author Roj234
 * @since 2021/5/29 16:43
 */
public class ATProxy implements ContextClassTransformer {
	public ATProxy() {
		Loader.addTransformer(this);
	}

	@Override
	public byte[] transform(String name, String trName, byte[] clz) {
		Loader.wrapTransformers();

		Collection<String> toOpen = getMapping().get(name);
		if (toOpen != null) TransformUtil.makeAccessible(Parser.parseAccess(clz), toOpen);
		return clz;
	}

	@Override
	public void transform(String trName, Context ctx) {
		Collection<String> toOpen = getMapping().get(trName);
		if (toOpen == null) return;
		TransformUtil.makeAccessible(ctx.getData(), toOpen);
	}
}