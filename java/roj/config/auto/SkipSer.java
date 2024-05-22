package roj.config.auto;

import roj.ReferenceByGeneratedClass;

/**
 * @author Roj234
 * @since 2023/3/25 0025 12:59
 */
final class SkipSer extends Adapter {
	@ReferenceByGeneratedClass
	static final Adapter INST = new SkipSer();

	public void read(AdaptContext ctx, boolean l) { popIfNoContainer(ctx); }
	public void read(AdaptContext ctx, int l) { popIfNoContainer(ctx); }
	public void read(AdaptContext ctx, long l) { popIfNoContainer(ctx); }
	public void read(AdaptContext ctx, float l) { popIfNoContainer(ctx); }
	public void read(AdaptContext ctx, double l) { popIfNoContainer(ctx); }
	public void read(AdaptContext ctx, Object o) { popIfNoContainer(ctx); }

	private static void popIfNoContainer(AdaptContext ctx) {
		if (ctx.fieldId == -2) ctx.popd(false);
	}

	public void key(AdaptContext ctx, String key) {
		ctx.push(this);
	}
	public void map(AdaptContext ctx, int size) {
		if (ctx.fieldId == -1) ctx.push(this);
		ctx.fieldId = -1;
	}
	public void list(AdaptContext ctx, int size) {
		if (ctx.fieldId == -1) ctx.push(this);
		ctx.fieldId = -1;
	}

	@Override
	public int fieldCount() { return -1; }
}