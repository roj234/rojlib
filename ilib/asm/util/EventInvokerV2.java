package ilib.asm.util;

import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.AttrUTF;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.reflect.DirectAccessor;
import roj.reflect.FastInit;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.asm.util.AccessFlag.PUBLIC;

/**
 * @author Roj234
 * @since 2020/11/14 16:15
 */
public final class EventInvokerV2 implements IEventListener {
	private final Object handler;
	private final Method method;
	private final Type filter;
	private final Object owner;
	private ModContainer mc;
	public final boolean recvCancel;
	private IEventListener built;
	private ArrayList<IEventListener> owner2;

	static final AtomicInteger id = new AtomicInteger(0);

	public void setContext(ModContainer owner) {
		this.mc = owner;
	}

	public void setList(ArrayList<IEventListener> list1) {
		this.owner2 = list1;
	}

	private interface INIT extends IEventListener {
		void setArr(Object arr);
		void setFilter(Type type);
		void setOwner(ModContainer owner);
		Object clone();
	}

	public EventInvokerV2(Method method, Object target, Type generic, Object owner, EventPriority priority, boolean recvCancel) {
		this.method = method;
		this.handler = target;
		this.owner = owner;
		this.recvCancel = recvCancel;
		this.filter = generic;
	}

	@SuppressWarnings("unchecked")
	public void invoke(Event event) {
		IEventListener me = null;
		for (int j = 0; j < 5; j++) {
			List<IEventListener> listeners = ((List<List<IEventListener>>) Helper.getListeners(owner)).get(j);
			for (int i = 0; i < listeners.size(); i++) {
				IEventListener listener = listeners.get(i);
				if (listener instanceof EventInvokerV2) {
					listeners.set(i, ((EventInvokerV2) listener).build());
				}
			}
		}
		Helper.forceRebuild(owner);

		build().invoke(event);
	}

	public String toString() {
		return "EI2: " + method;
	}

	private static final String HANDLER_DESC = "net/minecraftforge/fml/common/eventhandler/IEventListener";
	private static final String HANDLER_FUNC_DESC = "(Lnet/minecraftforge/fml/common/eventhandler/Event;)V";
	private static final ConcurrentHashMap<Method, INIT> cache = new ConcurrentHashMap<>();
	public static H Helper;
	static {
		try {
			Class<?> inst = Class.forName("net.minecraftforge.fml.common.eventhandler.ListenerList$ListenerListInst");
			Helper = DirectAccessor
				.builder(H.class)
				.delegate_o(inst, "forceRebuild")
				.access(inst, "priorities", "getListeners", null)
				.delegate_o(ListenerList.class, "getInstance")
				.build();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public interface H {
		Object getListeners(Object o);
		void forceRebuild(Object o);
		Object getInstance(Object o, int id);
	}

	private IEventListener build() {
		if (built != null) return built;

		IEventListener me = built = buildHook();
		int index = owner2.indexOf(this);
		if (index >= 0) owner2.set(index, me);
		return me;
	}

	private IEventListener buildHook() {
		Method method = this.method;

		INIT built = cache.get(method);
		if (built != null) {
			if (!(built instanceof Cloneable)) {
				return built;
			}

			built = (INIT) built.clone();
		} else {
			ConstantData c = new ConstantData();

			DirectAccessor.makeHeader(getUniqueName(method).replace('.', '/'), HANDLER_DESC, c);
			FastInit.prepare(c);
			c.addInterface("ilib/asm/util/EventInvokerV2$INIT");

			c.attributes().add(AttrUTF.Source("ImpLib生成的事件执行者"));

			CodeWriter cw = c.newMethod(PUBLIC, "invoke", HANDLER_FUNC_DESC);
			cw.visitSize(2, 2);

			Label label = CodeWriter.newLabel();

			String clz = method.getDeclaringClass().getName().replace('.', '/');
			String signature = "(L" + method.getParameterTypes()[0].getName().replace('.', '/') + ";)V";

			if (!recvCancel) {
				//cw.frames = Collections.singletonList(new Frame(Frame.same, label));

				cw.one(Opcodes.ALOAD_1);
				cw.invoke(Opcodes.INVOKEVIRTUAL, "net/minecraftforge/fml/common/eventhandler/Event", "isCanceled", "()Z");
				cw.jump(Opcodes.IFNE, label);
			}

			if (filter != null) {
				c.cloneable();

				//cw.frames = Collections.singletonList(new Frame(Frame.same, label));

				int fid = addField(c, "java/lang/reflect/Type", "setFilter", "(Ljava/lang/reflect/Type;)V");

				cw.one(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, c, fid);
				cw.one(Opcodes.ALOAD_1);
				cw.invokeItf("net/minecraftforge/fml/common/eventhandler/IGenericEvent", "getGenericType", "()Ljava/lang/reflect/Type;");
				cw.jump(Opcodes.IF_acmpne, label);
			}

			if (mc != null) {
				c.cloneable();
				cw.visitSize(3, 4);

				int fid = addField(c, "net/minecraftforge/fml/common/ModContainer", "setOwner", null);

				Loader loader = Loader.instance();
				cw.invoke(Opcodes.INVOKESTATIC, "net/minecraftforge/fml/common/Loader", "instance", "()Lnet/minecraftforge/fml/common/Loader;");
				cw.one(Opcodes.DUP);
				cw.one(Opcodes.ASTORE_2);

				// ModContainer old = loader.activeModContainer();
				cw.invoke(Opcodes.INVOKESPECIAL, "net/minecraftforge/fml/common/Loader", "activeModContainer", "()Lnet/minecraftforge/fml/common/ModContainer;");
				cw.one(Opcodes.ASTORE_3);

				// loader.setActiveModContainer(owner);
				cw.one(Opcodes.ALOAD_2);
				cw.one(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, c, fid);
				cw.invoke(Opcodes.INVOKESPECIAL, "net/minecraftforge/fml/common/Loader", "setActiveModContainer", "(Lnet/minecraftforge/fml/common/ModContainer;)V");

				// ((IContextSetter) event).setModContainer(owner);
				cw.one(Opcodes.ALOAD_1);
				cw.one(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, c, fid);
				cw.invokeItf("net/minecraftforge/fml/common/eventhandler/IContextSetter", "setModContainer", "(Lnet/minecraftforge/fml/common/ModContainer;)V");
			}

			if (handler != null) {
				int fid = addField(c, "java/lang/Object", "setArr", null);

				c.cloneable();

				cw.one(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, c, fid);

				cw.one(Opcodes.ALOAD_1);
				cw.invoke(Opcodes.INVOKEVIRTUAL, clz, method.getName(), signature);
			} else {
				cw.one(Opcodes.ALOAD_1);
				cw.invoke(Opcodes.INVOKESTATIC, clz, method.getName(), signature);
			}

			if (mc != null) {
				// loader.setActiveModContainer(old);
				cw.one(Opcodes.ALOAD_2);
				cw.one(Opcodes.ALOAD_3);
				cw.invoke(Opcodes.INVOKESPECIAL, "net/minecraftforge/fml/common/Loader", "setActiveModContainer", "(Lnet/minecraftforge/fml/common/ModContainer;)V");
			}

			cw.label(label);
			cw.one(Opcodes.RETURN);
			cw.finish();
			// todo FRAMEs

			built = (INIT) FastInit.make(c);
			cache.put(method, built);
		}

		if (handler != null) built.setArr(handler);
		if (filter != null) built.setFilter(filter);
		if (mc != null) built.setOwner(mc);
		return built;
	}

	private static int addField(ConstantData cz, String type, String setName, String setDesc) {
		int id = cz.newField(PUBLIC, Integer.toString(cz.fields.size()), new roj.asm.type.Type(type));

		CodeWriter cw = cz.newMethod(PUBLIC, setName, setDesc == null ? "(L" + type + ";)V" : setDesc);

		cw.visitSize(2,2);

		cw.one(Opcodes.ALOAD_0);
		cw.one(Opcodes.ALOAD_1);
		cw.field(Opcodes.PUTFIELD, cz, id);
		cw.one(Opcodes.RETURN);
		cw.finish();

		return id;
	}

	private static String getUniqueName(Method callback) {
		return "ilib.eh2." + callback.getDeclaringClass().getSimpleName() + "_" + callback.getName() + '$' + id.getAndIncrement();
	}
}

