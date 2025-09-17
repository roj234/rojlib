package roj.config.mapper;

import org.jetbrains.annotations.Nullable;
import roj.collect.BitSet;
import roj.config.TreeEmitter;
import roj.config.ValueEmitter;
import roj.config.node.ConfigValue;

/**
 * @author Roj233
 * @since 2025/9/18 23:45
 */
final class TreeAdapter extends TypeAdapter {
	static final TreeAdapter INSTANCE = new TreeAdapter();

	TreeAdapter() {}

	private TreeEmitter emitter;

	@Override public void read(MappingContext ctx, boolean l) {emitter.emit(l);checkEnd(ctx);}
	@Override public void read(MappingContext ctx, int l) {emitter.emit(l);checkEnd(ctx);}
	@Override public void read(MappingContext ctx, long l) {emitter.emit(l);checkEnd(ctx);}
	@Override public void read(MappingContext ctx, float l) {emitter.emit(l);checkEnd(ctx);}
	@Override public void read(MappingContext ctx, double l) {emitter.emit(l);checkEnd(ctx);}
	@Override public void read(MappingContext ctx, byte[] o) {emitter.emit(o);checkEnd(ctx);}
	@Override public void read(MappingContext ctx, int[] o) {emitter.emit(o);checkEnd(ctx);}
	@Override public void read(MappingContext ctx, long[] o) {emitter.emit(o);checkEnd(ctx);}
	@Override public void read(MappingContext ctx, Object o) {emitter.emit(o == null ? null : o.toString());checkEnd(ctx);}

	@Override
	public void push(MappingContext ctx) {
		if (this == INSTANCE) {
			TreeAdapter adapter = new TreeAdapter();
			adapter.emitter = new TreeEmitter();
			ctx.replace(adapter);
		}
	}

	private void checkEnd(MappingContext ctx) {
		if (emitter.isEnded()) {
			ConfigValue o = emitter.get();
			ctx.setRef(o);
			if (!o.getType().isContainer())
				ctx.popd(true);
		}
	}

	@Override public void key(MappingContext ctx, String key) {emitter.emitKey(key);}
	@Override public void pop(MappingContext ctx) {emitter.pop();checkEnd(ctx);}

	@Override
	public void map(MappingContext ctx, int size) {
		if (ctx.fieldId == -1) ctx.push(this);
		ctx.fieldId = -1;
		emitter.emitMap();
	}

	@Override
	public void list(MappingContext ctx, int size) {
		if (ctx.fieldId == -1) ctx.push(this);
		ctx.fieldId = -1;
		emitter.emitList();
	}

	@Override
	public int plusOptional(int fieldState, @Nullable BitSet fieldStateEx) { return 1; }

	@Override
	public void write(ValueEmitter c, Object o) {((ConfigValue) o).accept(c);}
}