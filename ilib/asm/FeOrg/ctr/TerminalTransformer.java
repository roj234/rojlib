package ilib.asm.FeOrg.ctr;

import ilib.api.ContextClassTransformer;
import roj.asm.Opcodes;
import roj.asm.cst.Constant;
import roj.asm.cst.CstRef;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.InsnList;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.util.Context;
import roj.asm.visitor.CodeWriter;
import roj.util.ByteList;

import net.minecraftforge.fml.common.FMLLog;

import java.util.List;

public class TerminalTransformer extends CodeWriter implements ContextClassTransformer {
	public TerminalTransformer() {}

	private TerminalTransformer(int unused) {
		bw = new ByteList();
	}

	@Override
	public void transform(String trName, Context ctx) {
		if (!check(clsName = trName)) return;

		ConstantData data = ctx.getData();

		mName = "IMPLIB快速常量检查";
		mDesc = "";

		boolean doVisit = false;
		List<Constant> csts = data.cp.array();
		for (int i = 0; i < csts.size(); i++) {
			Constant c = csts.get(i);
			if (c.type() == Constant.METHOD) {
				CstRef ref = (CstRef) c;
				if (ref.matches("java/lang/System", "exit", "(I)V")) {
					warn();
					ref.clazz(data.cp.getClazz(callbackOwner));
					ref.desc(data.cp.getDesc("systemExitCalled", "(I)V"));
				} else if (ref.className().equals("java/lang/Runtime")) {
					String n = ref.desc().name().str();
					if (n.equals("halt") || n.equals("exit")) {
						doVisit = true;
					}
				}
			}
		}

		if (doVisit) {
			TerminalTransformer v = new TerminalTransformer(1);
			v.clsName = clsName;
			v.doVisit(data);
		}
	}

	private void doVisit(ConstantData data) {
		cpw = data.cp;
		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode method = methods.get(i);
			mName = method.name();
			mDesc = method.rawDesc();

			Attribute attr = method.attrByName("Code");
			if (attr != null) {
				dirty = false;

				if (attr instanceof AttrCode) {
					filterTree((AttrCode) attr);
				} else {
					bw.clear();
					visitCopied(cpw, attr.getRawData());
					if (dirty) {
						((AttrUnknown) attr).setRawData(new ByteList(bw.toByteArray()));
					}
				}
			}
		}

		//attributeVisitor.cw = cw = attributeVisitor.cp = cp = null;
	}

	private static boolean filterTree(AttrCode attr) {
		boolean dirty = false;
		// 有时间了把Interpreter改成二进制模式
		InsnList insn = attr.instructions;
		for (int i = 0; i < insn.size(); i++) {
			InsnNode node = insn.get(i);
			if (node.nodeType() == InsnNode.T_INVOKE) {
				InvokeInsnNode node1 = (InvokeInsnNode) node;
				if (node1.owner.equals("java/lang/Runtime")) {
					if (node1.name.equals("exit") || node1.name.equals("halt")) {
						dirty = true;
						node1.owner = callbackOwner;
						node1.name = node1.name.equals("exit") ? "runtimeExitCalled" : "runtimeHaltCalled";
						node1.fullDesc("(Ljava/lang/Runtime;I)V");
					}
				}
			}
		}

		return dirty;
	}

	String clsName, mName, mDesc;

	@Override
	public void invoke(byte code, String owner, String name, String desc) {
		if (code == Opcodes.INVOKEVIRTUAL && "java/lang/Runtime".equals(owner)) {
			if (desc.equals("(I)V") && name.length() == 4) {
				switch (name) {
					case "exit": case "halt":
						warn();
						bw.put(Opcodes.INVOKESTATIC).putShort(cpw.getMethodRefId(callbackOwner, name.equals("exit") ? "runtimeExitCalled" : "runtimeHaltCalled", "(Ljava/lang/Runtime;I)V"));
						dirty = true;
						return;
				}
			}
		}
		super.invoke(code, owner, name, desc);
	}

	private void warn() {
		FMLLog.log.warn("=============================================================");
		FMLLog.log.warn("不允许MOD直接退出JAVA!");
		FMLLog.log.warn("来自: {}.{}{}", clsName, mName, mDesc);
		FMLLog.log.warn("请使用 FMLCommonHandler.exitJava();");
		FMLLog.log.warn("=============================================================");
	}

	private boolean dirty;

	private static final String callbackOwner = "net.minecraftforge.fml.common.asm.transformers.TerminalTransformer$ExitVisitor".replace('.', '/');

	public boolean check(String clsName) {
		return (!clsName.equals("net.minecraft.client.Minecraft") && !clsName.equals("net.minecraft.server.dedicated.DedicatedServer") && !clsName.equals(
			"net.minecraft.server.dedicated.ServerHangWatchdog") && !clsName.equals("net.minecraft.server.dedicated.ServerHangWatchdog$1") && !clsName.equals(
			"net.minecraftforge.fml.common.FMLCommonHandler") && !clsName.startsWith("com.jcraft.jogg.") && !clsName.startsWith("scala.sys.") && !clsName.startsWith(
			"net.minecraft.server.gui.MinecraftServerGui") && !clsName.startsWith("com.sun.jna."));
	}
}
