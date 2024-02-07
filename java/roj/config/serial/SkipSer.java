package roj.config.serial;

/**
 * @author Roj234
 * @since 2023/3/25 0025 12:59
 */
final class SkipSer extends Adapter {
	static final Adapter INST = new SkipSer();

	void read(AdaptContext ctx, boolean l) { popIfNoContainer(ctx); }
	void read(AdaptContext ctx, int l) { popIfNoContainer(ctx); }
	void read(AdaptContext ctx, long l) { popIfNoContainer(ctx); }
	void read(AdaptContext ctx, float l) { popIfNoContainer(ctx); }
	void read(AdaptContext ctx, double l) { popIfNoContainer(ctx); }
	void read(AdaptContext ctx, Object o) { popIfNoContainer(ctx); }

	private static void popIfNoContainer(AdaptContext ctx) {
		if (ctx.fieldId == -2) ctx.popd(false);
	}

	void key(AdaptContext ctx, String key) {
		ctx.push(this);
	}
	void map(AdaptContext ctx, int size) {
		if (ctx.fieldId == -1) ctx.push(this);
		ctx.fieldId = -1;
	}
	void list(AdaptContext ctx, int size) {
		if (ctx.fieldId == -1) ctx.push(this);
		ctx.fieldId = -1;
	}

	@Override
	int fieldCount() { return -1; }
}