package roj.config.mapper;

import roj.ci.annotation.IndirectReference;

/**
 * @author Roj234
 * @since 2023/3/25 12:59
 */
final class Skip extends TypeAdapter {
	@IndirectReference
	static final TypeAdapter INST = new Skip();

	public void read(MappingContext ctx, boolean l) { popIfNoContainer(ctx); }
	public void read(MappingContext ctx, int l) { popIfNoContainer(ctx); }
	public void read(MappingContext ctx, long l) { popIfNoContainer(ctx); }
	public void read(MappingContext ctx, float l) { popIfNoContainer(ctx); }
	public void read(MappingContext ctx, double l) { popIfNoContainer(ctx); }
	public void read(MappingContext ctx, Object o) { popIfNoContainer(ctx); }

	private static void popIfNoContainer(MappingContext ctx) {
		if (ctx.fieldId == -2) ctx.popd(false);
	}

	public void key(MappingContext ctx, String key) {
		ctx.push(this);
	}
	public void map(MappingContext ctx, int size) {
		if (ctx.fieldId == -1) ctx.push(this);
		ctx.fieldId = -1;
	}
	public void list(MappingContext ctx, int size) {
		if (ctx.fieldId == -1) ctx.push(this);
		ctx.fieldId = -1;
	}

	@Override
	public int fieldCount() { return -1; }
}