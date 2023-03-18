package ilib.asm.FeOrg.ctr;

import ilib.api.ContextClassTransformer;
import roj.asm.Opcodes;
import roj.asm.frame.Var2;
import roj.asm.frame.VarType;
import roj.asm.tree.*;
import roj.asm.tree.anno.Annotation;
import roj.asm.util.AttrHelper;
import roj.asm.util.Context;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Frame2;
import roj.asm.visitor.FrameVisitor;
import roj.asm.visitor.Label;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2022/4/23 2:27
 */
public class EventSubTrans implements ContextClassTransformer {
	@Override
	public void transform(String name, Context ctx) {
		if (!name.equals("net.minecraftforge.fml.common.eventhandler.Event") && !name.startsWith("net.minecraft.") && name.indexOf(46) != -1) {
			ConstantData data = ctx.getData();

			String superClass = data.parent;
			if (superClass.startsWith("java/")) return;

			Class<?> p = null;
			try {
				p = EventSubscriptionTransformer.class.getClassLoader().loadClass(superClass.replace('/', '.'));
			} catch (ClassNotFoundException ignored) {}
			if (p != null && Event.class.isAssignableFrom(p)) {
				buildEvents(data);
			}
		}
	}

	static final String voidDesc = "()V";
	static final String boolDesc = "()Z";
	static final String listClass = "net/minecraftforge/fml/common/eventhandler/ListenerList";
	static final String listDesc = "L" + listClass + ";";
	static final String listDescM = "()" + listDesc;

	private boolean buildEvents(ConstantData data) {
		boolean edited = false;
		boolean hasSetup = false;
		boolean hasGetListenerList = false;
		boolean hasDefaultCtr = false;
		boolean hasCancelable = false;
		boolean hasResult = false;

		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MoFNode method = methods.get(i);
			switch (method.rawDesc()) {
				case "()V":
					switch (method.name()) {
						case "setup":
							if ((method.modifier() & 4) != 0) hasSetup = true;
							break;
						case "<init>":
							hasDefaultCtr = true;
							break;
					}
					break;
				case "()Z":
					if ((method.modifier() & 1) != 0) {
						switch (method.name()) {
							case "isCancelable":
								hasCancelable = true;
								break;
							case "hasResult":
								hasResult = true;
								break;
						}
					}
					break;
				case "()Lnet/minecraftforge/fml/common/eventhandler/ListenerList;":
					if (method.name().equals("getListenerList") && (method.modifier() & 1) != 0) {
						hasGetListenerList = true;
					}
					break;
			}
		}

		Method method;
		sect:
		if (!hasResult || !hasCancelable) {
			List<Annotation> anns = AttrHelper.getAnnotations(data.cp, data, true);
			if (anns == null) break sect;
			for (int i = 0; i < anns.size(); i++) {
				Annotation ann = anns.get(i);
				if (!hasResult && ann.clazz.equals("net/minecraftforge/fml/common/eventhandler/Event$HasResult")) {
					CodeWriter cw = data.newMethod(1, "hasResult", boolDesc);
					cw.visitSize(1, 2);
					cw.one(ICONST_1);
					cw.one(IRETURN);
					edited = true;
				} else if (!hasCancelable && ann.clazz.equals("net/minecraftforge/fml/common/eventhandler/Cancelable")) {
					CodeWriter cw = data.newMethod(1, "isCancelable", boolDesc);
					cw.visitSize(1, 2);
					cw.one(ICONST_1);
					cw.one(IRETURN);
					edited = true;
				}
			}
		}

		if (hasSetup) {
			if (!hasGetListenerList) {
				throw new RuntimeException("Event class defines setup() but does not define getListenerList! " + data.name);
			} else {
				return edited;
			}
		} else {
			data.fields.add(Helpers.cast(new Field(10, "LISTENER_LIST", listDesc)));

			if (!hasDefaultCtr) {
				CodeWriter cw = data.newMethod(1, "<init>", voidDesc);
				cw.visitSize(1,1);
				cw.one(ALOAD_0);
				cw.invoke(INVOKESPECIAL, data.parent, "<init>", voidDesc);
				cw.one(RETURN);
			}

			CodeWriter cw = data.newMethod(4,"setup", voidDesc);
			cw.visitSize(3,1);
			cw.one(ALOAD_0);
			cw.invoke(INVOKESPECIAL, data.parent, "setup", voidDesc);
			cw.field(GETSTATIC, data.name, "LISTENER_LIST", listDesc);
			Label inited = new Label();
			cw.jump(IFNONNULL, inited);

			cw.clazz(Opcodes.NEW, listClass);
			cw.one(DUP);
			cw.one(ALOAD_0);
			cw.invoke(INVOKESPECIAL, data.parent, "getListenerList", listDescM);
			cw.invoke(INVOKESPECIAL, listClass, "<init>", "("+listDesc+")V");
			cw.field(PUTSTATIC, data.name, "LISTENER_LIST", listDesc);

			cw.label(inited);
			cw.one(RETURN);

			cw.visitExceptions();
			cw.visitAttributes();

			//  same #0xb1 返回, Event
			Frame2 f = new Frame2(Frame2.same, inited.getValue());
			f.locals(new Var2(VarType.REFERENCE, data.name));

			int stack = cw.visitAttributeI("StackMapTable");
			if (stack >= 0) {
				DynByteBuf bw = cw.bw;
				FrameVisitor.writeFrames(Collections.singletonList(f), bw.putShort(1), data.cp);
				cw.visitAttributeIEnd(stack);
			}

			cw = data.newMethod(4,"getListenerList", listDescM);
			cw.visitSize(1,1);
			cw.field(GETSTATIC, data.name, "LISTENER_LIST", listDesc);
			cw.one(ARETURN);
			return true;
		}
	}
}
