package roj.event;

import roj.asm.ClassNode;
import roj.asm.Member;
import roj.asm.attr.Attribute;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.type.Generic;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.ci.annotation.Public;
import roj.io.IOUtil;
import roj.reflect.Reflection;
import roj.text.CharList;

import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/3/21 11:56
 */
final class HandlerInvoker implements EventListener {
	private final Object handler;
	private final List<ListenerInfo> infos;

	private EventListener built;

	public HandlerInvoker(Object handler, ListenerInfo info) {
		this.handler = handler;
		this.infos = Collections.singletonList(info);
	}

	public void invoke(Event event) { impl().invoke(event); }
	@Override
	public boolean isFor(Object handler, String methodName, String methodDesc) {
		if (this.handler == handler && infos.size() == 1) {
			Member mn = infos.get(0).method;
			return mn.rawDesc().equals(methodDesc) && mn.name().equals(methodName);
		}
		return false;
	}

	public String toString() { return "EventListener[raw]: "+infos; }

	final EventListener impl() {
		if (built != null) return built;

		if (infos.size() == 1) {
			ListenerInfo mn = infos.get(0);

			Impl impl;
			synchronized (mn) {
				impl = (Impl) mn.impl;
				if (impl == null) {
					mn.impl = impl = asm();
				}
			}

			if (handler != null) {
				impl = (Impl) impl.copyWith(handler);
			}

			return built = impl;
		} else {
			// frozen模式 a.k.a 无法取消，只生成一个类，性能可能更高。
			Impl impl = (Impl) asm().copyWith(handler);
			return built = impl;
		}
	}
	// region asm
	@Public
	private interface Impl extends EventListener {
		default Object copyWith(Object data) {return this;}
	}

	private Impl asm() {
		ListenerInfo info = infos.get(0);
		ClassNode c = new ClassNode();

		c.name("roj/event/ASMEventInvoker$"+Reflection.uniqueId());
		c.addInterface("roj/event/HandlerInvoker$Impl");

		CodeWriter cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "invoke", "(Lroj/event/Event;)V");
		cw.visitSize(1, 2);

		Label start = CodeWriter.newLabel();

		if ((info.flags&128) != 0) {
			cw.insn(ALOAD_1);
			cw.invokeV("roj/event/Event", "isCanceled", "()Z");
			cw.jump(IFEQ, start);
			cw.insn(RETURN);
		}

		Signature sign = info.method.getAttribute(null, Attribute.SIGNATURE);
		if (sign != null && sign.values.get(0) instanceof Generic g) {
			cw.visitSizeMax(2, 0);

			CharList buf = IOUtil.getSharedCharBuf();
			for (int i = 0; i < g.children.size(); i++) g.children.get(i).toDesc(buf);
			cw.ldc(buf.toString());
			cw.insn(ALOAD_1);
			cw.invokeV("roj/event/Event", "getGenericType", "()Ljava/lang/String;");
			cw.invokeV("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
			cw.jump(IFNE, start);
			cw.insn(RETURN);
		}

		cw.label(start);
		String eventType = info.event.replace('.', '/');
		if (infos.size() > 1) {
			c.cloneable();
			// local: listener[], event, manager
			cw.visitSizeMax(2, 2);

			int fid = addInstanceField(c, "[Ljava/lang/Object;");

			cw.insn(ALOAD_0);
			cw.field(GETFIELD, c, fid);
			cw.insn(ASTORE_0);

			for (int i = 0; i < infos.size(); i++) {
				Member mn = infos.get(i).method;

				cw.insn(ALOAD_0);
				cw.ldc(i);
				cw.insn(AALOAD);
				// always true
				cw.clazz(CHECKCAST, mn.owner());
				callEvent(c, cw, mn, eventType, 0);
			}
			cw.insn(RETURN);
		} else {
			int fid = callEvent(c, cw, info.method, eventType, -1);
			cw.insn(RETURN);
			cw.finish();

			cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "isFor", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z");
			cw.visitSize(2, 4);

			Label fail = AbstractCodeWriter.newLabel();
			if ((info.method.modifier()&ACC_STATIC) != 0) {
				cw.insn(ACONST_NULL);
			} else {
				cw.insn(ALOAD_0);
				cw.field(GETFIELD, c, fid);
			}
			cw.insn(ALOAD_1);
			cw.jump(IF_acmpne, fail);

			cw.ldc(info.method.name());
			cw.insn(ALOAD_2);
			cw.invokeV("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
			cw.jump(IFEQ, fail);

			cw.ldc(info.method.rawDesc());
			cw.insn(ALOAD_3);
			cw.invokeV("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
			cw.jump(IFEQ, fail);

			cw.insn(ICONST_1);
			cw.insn(IRETURN);

			cw.label(fail);
			cw.insn(ICONST_0);
			cw.insn(IRETURN);
		}

		cw.finish();
		var loader = handler instanceof Class<?> t ? t.getClassLoader() : handler.getClass().getClassLoader();
		return (Impl) Reflection.createInstance(loader, c, "ASMEventInvoker");
	}
	private static int callEvent(ClassNode out, CodeWriter c, Member listener, String eventType, int fid) {
		String clz = listener.owner();
		if ((listener.modifier()&ACC_STATIC) != 0) {
			c.insn(ALOAD_1);
			c.clazz(CHECKCAST, eventType);
			c.invokeS(clz, listener.name(), listener.rawDesc());
		} else {
			c.visitSizeMax(2, 0); // object event
			if (fid < 0) {
				fid = addInstanceField(out, clz);
				c.insn(ALOAD_0);
				c.field(GETFIELD, out, fid);
			}
			c.insn(ALOAD_1);
			c.clazz(CHECKCAST, eventType);
			c.invokeV(clz, listener.name(), listener.rawDesc());
		}
		return fid;
	}
	private static int addInstanceField(ClassNode out, String type) {
		out.addInterface("java/lang/Cloneable");

		int fid = out.newField(ACC_PRIVATE, "instance", Type.klass(type));
		CodeWriter c = out.newMethod(ACC_PUBLIC|ACC_FINAL, "copyWith", "(Ljava/lang/Object;)Ljava/lang/Object;");
		c.visitSize(2,2);

		c.insn(ALOAD_0);
		c.invoke(INVOKESPECIAL, "java/lang/Object", "clone", "()Ljava/lang/Object;");
		c.clazz(CHECKCAST, out.name());
		c.insn(ASTORE_0);

		c.insn(ALOAD_0);
		c.insn(ALOAD_1);
		c.clazz(CHECKCAST, type);
		c.field(PUTFIELD, out, fid);

		c.insn(ALOAD_0);
		c.insn(ARETURN);
		c.finish();
		return fid;
	}
	// endregion
}