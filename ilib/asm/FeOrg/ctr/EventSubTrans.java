package ilib.asm.FeOrg.ctr;

import ilib.api.ContextClassTransformer;
import roj.asm.Opcodes;
import roj.asm.frame.Var2;
import roj.asm.frame.VarType;
import roj.asm.tree.*;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.util.AttrHelper;
import roj.asm.util.Context;
import roj.asm.util.InsnList;
import roj.asm.visitor.Frame2;
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
							if ((method.accessFlag() & 4) != 0) hasSetup = true;
							break;
						case "<init>":
							hasDefaultCtr = true;
							break;
					}
					break;
				case "()Z":
					if ((method.accessFlag() & 1) != 0) {
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
					if (method.name().equals("getListenerList") && (method.accessFlag() & 1) != 0) {
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
					method = new Method(1, data, "hasResult", boolDesc);
					method.code = new AttrCode(method);
					method.code.localSize = 2;
					method.code.stackSize = 1;
					method.code.instructions.add(NPInsnNode.of(4));
					method.code.instructions.add(NPInsnNode.of(172));
					data.methods.add(Helpers.cast(method));
					edited = true;
				} else if (!hasCancelable && ann.clazz.equals("net/minecraftforge/fml/common/eventhandler/Cancelable")) {
					method = new Method(1, data, "isCancelable", boolDesc);
					method.code = new AttrCode(method);
					method.code.localSize = 2;
					method.code.stackSize = 1;
					method.code.instructions.add(NPInsnNode.of(4));
					method.code.instructions.add(NPInsnNode.of(172));
					data.methods.add(Helpers.cast(method));
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
				method = new Method(1, data, "<init>", voidDesc);
				method.code = new AttrCode(method);
				method.code.localSize = 1;
				method.code.stackSize = 1;
				InsnList insn = method.code.instructions;
				insn.add(NPInsnNode.of(ALOAD_0));
				insn.add(new InvokeInsnNode(INVOKESPECIAL, data.parent, "<init>", voidDesc));
				insn.add(NPInsnNode.of(177));
				data.methods.add(Helpers.cast(method));
			}

			method = new Method(4, data, "setup", voidDesc);
			method.code = new AttrCode(method);
			method.code.localSize = 1;
			method.code.stackSize = 3;
			InsnList insn = method.code.instructions;
			insn.add(NPInsnNode.of(ALOAD_0));
			insn.add(new InvokeInsnNode(INVOKESPECIAL, data.parent, "setup", voidDesc));

			insn.add(new FieldInsnNode(GETSTATIC, data.name, "LISTENER_LIST", listDesc));
			LabelInsnNode inited = new LabelInsnNode();
			insn.add(new JumpInsnNode(IFNONNULL, inited));

			insn.add(new ClassInsnNode(Opcodes.NEW, listClass));
			insn.add(NPInsnNode.of(DUP));
			insn.add(NPInsnNode.of(ALOAD_0));
			insn.add(new InvokeInsnNode(INVOKESPECIAL, data.parent, "getListenerList", listDescM));
			insn.add(new InvokeInsnNode(INVOKESPECIAL, listClass, "<init>", "(" + listDesc + ")V"));
			insn.add(new FieldInsnNode(PUTSTATIC, data.name, "LISTENER_LIST", listDesc));

			insn.add(inited);
			NPInsnNode lastRet = NPInsnNode.of(177);
			insn.add(lastRet);

			//  same #0xb1 返回, Event
			Frame2 f = new Frame2(Frame2.same, lastRet);
			f.locals(new Var2(VarType.REFERENCE, data.name));
			method.code.frames = Collections.singletonList(f);

			data.methods.add(Helpers.cast(method));

			method = new Method(1, data, "getListenerList", listDescM);
			method.code = new AttrCode(method);
			method.code.localSize = 1;
			method.code.stackSize = 1;
			insn = method.code.instructions;
			insn.add(new FieldInsnNode(GETSTATIC, data.name, "LISTENER_LIST", listDesc));
			insn.add(NPInsnNode.of(176));
			data.methods.add(Helpers.cast(method));
			return true;
		}
	}
}
