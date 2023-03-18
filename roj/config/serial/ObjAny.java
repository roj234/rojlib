package roj.config.serial;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class ObjAny extends Adapter {
	final SerializerManager gen;

	ObjAny(SerializerManager type) {
		this.gen = type;
	}

	@Override
	void map(AdaptContext ctx, int size) {
		ctx.fieldId = -1;
	}

	@Override
	void list(AdaptContext ctx, int size) {
		Adapter ls = gen.get(List.class);
		ctx.push(ls);
		ls.list(ctx, size);
	}

	@Override
	void key(AdaptContext ctx, String key) {
		if (!key.equals("==")) throw new IllegalArgumentException("第一个key必须是对象类型('=='):"+key);
		ctx.setKeyHook(0);
	}

	@Override
	void read(AdaptContext ctx, Object o) {
		// primitive
		if (ctx.fieldId == -2) {
			ctx.ref = o;
			ctx.popd(true);
			return;
		}

		assert ctx.fieldId == 0;

		Adapter ser;
		try {
			ser = gen.getByName(o.toString());
		} catch (Throwable e) {
			throw new IllegalStateException("无法找到类 " + o, e);
		}
		ctx.curr = ser;
		ctx.fieldId = -2;
		ser.map(ctx, -1);
	}

	void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			Adapter ser = gen.get(o.getClass());
			if (ser == this) throw new IllegalArgumentException();

			if (ser.valueIsMap()) {
				c.valueMap(ser.fieldCount()+1);
				c.key("==");
				c.value(o.getClass().getName());
				ser.writeMap(c, o);
				c.pop();
			} else {
				ser.write(c, o);
			}
		}
	}
}
