package roj.launcher;

import roj.asm.TransformException;
import roj.asm.util.Context;

/**
 * @author Roj234
 * @since 2023/11/15 0015 22:41
 */
public class NonReentrant implements ITransformer {
	private final ITransformer tr;
	private static final ThreadLocal<Boolean> ENTER = new ThreadLocal<>();

	public NonReentrant(ITransformer tr) { this.tr = tr; }

	@Override
	public final boolean transform(String mappedName, Context ctx) throws TransformException {
		if (ENTER.get() == null) {
			ENTER.set(true);
			try {
				return transformNonReentrant(mappedName, ctx);
			} finally {
				ENTER.remove();
			}
		}
		return false;
	}

	protected boolean transformNonReentrant(String mappedName, Context ctx) throws TransformException { return tr.transform(mappedName, ctx); }
}
