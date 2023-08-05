package roj.config.serial;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class ObjAny extends Adapter {
	private static final String ID_PREVOBJECT = "#REF";
	private static final String ID_PREVOBJECT_ID = "_";
	private static final class PrevObject extends Adapter {
		static final PrevObject INSTANCE = new PrevObject();

		void key(AdaptContext ctx, String key) {
			if (!key.equals(ID_PREVOBJECT_ID)) throw new IllegalArgumentException("Excepting ID_PREVOBJECT_ID");
			ctx.setKeyHook(0);
		}

		void read(AdaptContext ctx, int no) {
			if (!(ctx instanceof AdaptContextEx) || no < 0 || no >= ((AdaptContextEx) ctx).objectsR.size()) {
				throw new IllegalArgumentException("PREVOBJECT_ID("+no+") exceeds bound");
			} else {
				ctx.ref = ((AdaptContextEx) ctx).objectsR.get(no);
				ctx.popd(true);
			}
		}
	}

	final SerializerFactory gen;

	ObjAny(SerializerFactory type) {
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

		if (o.equals(ID_PREVOBJECT)) {
			ctx.curr = PrevObject.INSTANCE;
			ctx.fieldId = -1;
			return;
		}

		Adapter ser;
		try {
			ser = gen.getByName(o.toString());
		} catch (Throwable e) {
			throw new IllegalStateException("无法找到类 " + o, e);
		}
		ctx.replace(ser);
		ctx.fieldId = -2;
		ser.map(ctx, -1);

		if (ctx instanceof AdaptContextEx)
			((AdaptContextEx) ctx).objectsR.add(ctx.ref);
	}

	void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			Adapter ser = gen.get(o.getClass());
			if (ser == this) throw new IllegalArgumentException();

			if (ser.valueIsMap()) {
				c.valueMap(ser.fieldCount()+1);
				c.key("==");

				AdaptContextEx dyn = AdaptContextEx.LOCAL_OBJS.get();
				if (dyn != null && !dyn.objectW.putIntIfAbsent(o, dyn.objectW.size())) {
					c.value(ID_PREVOBJECT);
					c.key(ID_PREVOBJECT_ID);
					c.value(dyn.objectW.getInt(o));
				} else {
					c.value(o.getClass().getName());
					ser.writeMap(c, o);
				}

				c.pop();
			} else {
				ser.write(c, o);
			}
		}
	}
}
