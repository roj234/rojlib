package roj.config.mapper;

import roj.collect.ToIntMap;
import roj.config.ValueEmitter;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class AnyAdapter extends TypeAdapter {
	final Factory gen;

	AnyAdapter(Factory type) { this.gen = type; }

	@Override
	public void map(MappingContext ctx, int size) { ctx.fieldId = -1; /* not primitive (-2) */ }

	// 为了能读取之前版本序列化的数据而保留..
	@Override
	public void list(MappingContext ctx, int size) {
		TypeAdapter ls = gen.get(List.class);
		ctx.push(ls);
		ls.list(ctx, size);
	}

	@Override
	public void key(MappingContext ctx, String key) {
		if (!key.isEmpty()) throw new IllegalArgumentException("第一个key必须是空键:"+key);
		ctx.setKeyHook(0);
	}

	@Override
	public void read(MappingContext ctx, Object o) {
		// primitive
		if (ctx.fieldId == -2) {
			ctx.setRef(o);
			ctx.popd(true);
			return;
		}

		assert ctx.fieldId == 0;

		if (o instanceof Number n) {
			PooledAdapter.PrevObjectId.read(ctx, n.intValue());
			return;
		}

		TypeAdapter ser;
		try {
			ser = gen.getByName(o.toString());
		} catch (Throwable e) {
			throw new IllegalStateException("无法找到类 "+o, e);
		}

		if (ctx instanceof MappingContextEx ex) ex.captureRef();

		ctx.fieldId = -2;
		if (ser.valueIsMap()) {
			ctx.replace(ser);
			ser.map(ctx, -1);
		} else {
			ctx.replace(new TypeAdapter() {
				@Override
				public void key(MappingContext ctx, String key) {
					if (!key.equals("v")) throw new IllegalArgumentException("第二个key必须是'v':"+key);
					ctx.push(ser);
				}
				@Override
				public void read(MappingContext ctx, Object o) {
					ctx.ref = o;
					ctx.fieldId = -1;
				}
				@Override
				public int fieldCount() { return 0; }
			});
		}
	}

	public void write(ValueEmitter c, Object o) {
		if (o == null) c.emitNull();
		else {
			TypeAdapter ser = gen.get(o.getClass());
			if (ser == this && o.getClass() != Object.class) {
				throw new IllegalArgumentException();
			}

			ToIntMap<Object> pool = MappingContextEx.OBJECT_POOL.get();

			int objectId;
			if (pool != null) {
				objectId = pool.putOrGet(o, pool.size(), -1);
			} else if (!ser.valueIsMap()) {
				// 如果未启用ObjectPool那么依然使用之前的序列化方式
				// 这更节约文件空间，缺点是无法区分数组和列表
				ser.write(c, o);
				return;
			} else {
				objectId = -1;
			}

			if (objectId >= 0) {
				c.emitMap(1);
				c.emitKey("");
				c.emit(objectId);
			} else {
				if (ser.isOptional()) c.emitMap();
				else c.emitMap(ser.fieldCount()+1);
				c.emitKey("");
				c.emit(o.getClass().getName());
				if (ser.valueIsMap()) {
					ser.writeMap(c, o);
				} else {
					c.emitKey("v");
					ser.write(c, o);
				}
			}

			c.pop();
		}
	}
}