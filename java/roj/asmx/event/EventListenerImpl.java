package roj.asmx.event;

import roj.asm.tree.ConstantData;
import roj.asm.tree.RawNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Generic;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.visitor.AbstractCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.io.IOUtil;
import roj.reflect.Bypass;
import roj.reflect.ClassDefiner;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;

import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/3/21 0021 11:56
 */
final class EventListenerImpl implements EventListener {
	private final Object handler;
	private final List<ListenerInfo> infos;

	private EventListener built;

	public EventListenerImpl(Object handler, ListenerInfo info) {
		this.handler = handler;
		this.infos = Collections.singletonList(info);
	}

	public void invoke(Event event) { impl().invoke(event); }
	@Override
	public boolean isFor(Object handler, String methodName, String methodDesc) {
		if (this.handler == handler && infos.size() == 1) {
			RawNode mn = infos.get(0).mn;
			return mn.rawDesc().equals(methodDesc) && mn.name().equals(methodName);
		}
		return false;
	}

	public String toString() { return "EventListener[raw]: "+ infos; }

	final EventListener impl() {
		if (built != null) return built;

		if (infos.size() == 1) {
			ListenerInfo mn = infos.get(0);

			ASM impl;
			synchronized (mn) {
				impl = (ASM) mn.impl;
				if (impl == null) {
					mn.impl = impl = asm();
				}
			}

			if (handler != null) {
				impl = (ASM) impl.clone();
				impl.setInstance(handler);
			}

			return built = impl;
		} else {
			// frozen mode
			ASM asm = asm();
			asm.setInstance(handler);
			return built = asm;
		}
	}
	// region asm
	private interface ASM extends EventListener {
		void setInstance(Object arr);
		Object clone();
	}

	private ASM asm() {
		ListenerInfo info = this.infos.get(0);
		ConstantData c = new ConstantData();

		c.parent(Bypass.MAGIC_ACCESSOR_CLASS);
		c.name("roj/gen/GEL$"+ReflectionUtils.uniqueId());
		ClassDefiner.premake(c);
		c.addInterface("roj/asmx/event/EventListenerImpl$ASM");

		CodeWriter cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "invoke", "(Lroj/asmx/event/Event;)V");
		cw.visitSize(1, 2);

		Label start = CodeWriter.newLabel();

		if ((info.flags&128) != 0) {
			cw.one(ALOAD_1);
			cw.invokeV("roj/asmx/event/Event", "isCanceled", "()Z");
			cw.jump(IFEQ, start);
			cw.one(RETURN);
		}

		Signature sign = info.mn.parsedAttr(null, Attribute.SIGNATURE);
		if (sign != null && sign.values.get(0) instanceof Generic g) {
			cw.visitSizeMax(2, 0);

			CharList buf = IOUtil.getSharedCharBuf();
			for (int i = 0; i < g.children.size(); i++) g.children.get(i).toDesc(buf);
			cw.ldc(buf.toString());
			cw.one(ALOAD_1);
			cw.invokeV("roj/asmx/event/Event", "getGenericType", "()Ljava/lang/String;");
			cw.invokeV("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
			cw.jump(IFNE, start);
			cw.one(RETURN);
		}

		cw.label(start);
		if (infos.size() > 1) {
			c.cloneable();
			// local: listener[], event, manager
			cw.visitSizeMax(2, 2);

			int fid = addField(c, "[Ljava/lang/Object;");

			cw.one(ALOAD_0);
			cw.field(GETFIELD, c, fid);
			cw.one(ASTORE_0);

			for (int i = 0; i < infos.size(); i++) {
				RawNode mn = infos.get(i).mn;

				cw.one(ALOAD_0);
				cw.ldc(i);
				cw.one(AALOAD);
				// always true
				cw.clazz(CHECKCAST, mn.ownerClass());
				callEvent(c, cw, mn, 0);
			}
			cw.one(RETURN);
		} else {
			int fid = callEvent(c, cw, info.mn, -1);
			cw.one(RETURN);
			cw.finish();

			cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "isFor", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z");
			cw.visitSize(2, 4);

			Label fail = AbstractCodeWriter.newLabel();
			if ((info.mn.modifier()&ACC_STATIC) != 0) {
				cw.one(ACONST_NULL);
			} else {
				cw.one(ALOAD_0);
				cw.field(GETFIELD, c, fid);
			}
			cw.one(ALOAD_1);
			cw.jump(IF_acmpne, fail);

			cw.ldc(info.mn.name());
			cw.one(ALOAD_2);
			cw.invokeV("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
			cw.jump(IFEQ, fail);

			cw.ldc(info.mn.rawDesc());
			cw.one(ALOAD_3);
			cw.invokeV("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
			cw.jump(IFEQ, fail);

			cw.one(ICONST_1);
			cw.one(IRETURN);

			cw.label(fail);
			cw.one(ICONST_0);
			cw.one(IRETURN);
		}

		cw.finish();
		return (ASM) ClassDefiner.make(c);
	}
	private static int callEvent(ConstantData out, CodeWriter c, RawNode listener, int fid) {
		String clz = listener.ownerClass();
		if ((listener.modifier()&ACC_STATIC) != 0) {
			c.one(ALOAD_1);
			c.invokeS(clz, listener.name(), listener.rawDesc());
		} else {
			c.visitSizeMax(2, 0); // object event
			if (fid < 0) {
				out.cloneable();
				fid = addField(out, clz);
				c.one(ALOAD_0);
				c.field(GETFIELD, out, fid);
			}
			c.one(ALOAD_1);
			c.invokeV(clz, listener.name(), listener.rawDesc());
		}
		return fid;
	}
	private static int addField(ConstantData out, String type) {
		int fid = out.newField(ACC_PRIVATE, "instance", new Type(type));
		CodeWriter c = out.newMethod(ACC_PUBLIC|ACC_FINAL, "setInstance", "(Ljava/lang/Object;)V");
		c.visitSize(2,2);
		c.one(ALOAD_0);
		c.one(ALOAD_1);
		c.clazz(CHECKCAST, type);
		c.field(PUTFIELD, out, fid);
		c.one(RETURN);
		c.finish();
		return fid;
	}
	// endregion
}