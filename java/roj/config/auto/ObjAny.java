package roj.config.auto;

import roj.collect.ToIntMap;
import roj.config.serial.CVisitor;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class ObjAny extends Adapter {
	final SerializerFactoryImpl gen;

	ObjAny(SerializerFactoryImpl type) { this.gen = type; }

	@Override
	public void map(AdaptContext ctx, int size) { ctx.fieldId = -1; /* not primitive (-2) */ }

	// 为了能读取之前版本序列化的数据而保留..
	@Override
	public void list(AdaptContext ctx, int size) {
		Adapter ls = gen.get(List.class);
		ctx.push(ls);
		ls.list(ctx, size);
	}

	@Override
	public void key(AdaptContext ctx, String key) {
		if (!key.isEmpty()) throw new IllegalArgumentException("第一个key必须是空键:"+key);
		ctx.setKeyHook(0);
	}

	@Override
	public void read(AdaptContext ctx, Object o) {
		// primitive
		if (ctx.fieldId == -2) {
			ctx.setRef(o);
			ctx.popd(true);
			return;
		}

		assert ctx.fieldId == 0;

		if (o instanceof Number n) {
			ObjectPoolWrapper.PrevObjectId.read(ctx, n.intValue());
			return;
		}

		Adapter ser;
		try {
			ser = gen.getByName(o.toString());
		} catch (Throwable e) {
			throw new IllegalStateException("无法找到类 "+o, e);
		}

		if (ctx instanceof AdaptContextEx ex) ex.captureRef();

		ctx.fieldId = -2;
		if (ser.valueIsMap()) {
			ctx.replace(ser);
			ser.map(ctx, -1);
		} else {
			ctx.replace(new Adapter() {
				@Override
				public void key(AdaptContext ctx, String key) {
					if (!key.equals("v")) throw new IllegalArgumentException("第二个key必须是'v':"+key);
					ctx.push(ser);
				}
				@Override
				public void read(AdaptContext ctx, Object o) {
					ctx.ref = o;
					ctx.fieldId = -1;
				}
				@Override
				public int fieldCount() { return 0; }
			});
		}
	}

	public void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			Adapter ser = gen.get(o.getClass());
			if (ser == this && o.getClass() != Object.class) {
				throw new IllegalArgumentException();
			}

			ToIntMap<Object> pool = AdaptContextEx.OBJECT_POOL.get();

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
				c.valueMap(1);
				c.key("");
				c.value(objectId);
			} else {
				if (ser.isOptional()) c.valueMap();
				else c.valueMap(ser.fieldCount()+1);
				c.key("");
				c.value(o.getClass().getName());
				if (ser.valueIsMap()) {
					ser.writeMap(c, o);
				} else {
					c.key("v");
					ser.write(c, o);
				}
			}

			c.pop();
		}
	}
}