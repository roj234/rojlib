package ilib.asm.FeOrg.ctr;

import ilib.api.ContextClassTransformer;
import ilib.asm.Loader;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstDynamic;
import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstRef;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.BootstrapMethods;
import roj.asm.tree.attr.BootstrapMethods.BootstrapMethod;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.asm.visitor.CodeVisitor;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public final class SideTransformer implements ContextClassTransformer {
	private static final String SIDE = FMLLaunchHandler.side().name();
	private static MyHashSet<String> has;

	public static void lockdown() {
		has = new MyHashSet<>();
		for (ASMDataTable.ASMData data : Loader.Annotations.getAll(SideOnly.class.getName())) {
			has.add(data.getClassName());
		}
	}

	@Override
	public void transform(String trName, Context ctx) {
		if (has != null && !has.contains(trName)) return;
		ConstantData data = ctx.getData();
		ConstantPool pool = data.cp;

		if (remove(pool, data.attrByName("RuntimeVisibleAnnotations"), SIDE)) throw new RuntimeException(data.name + " 不应在 " + SIDE + " 加载");

		List<? extends FieldNode> fields = data.fields;
		for (int i = fields.size() - 1; i >= 0; i--) {
			FieldNode sp = fields.get(i);
			if (remove(pool, sp.attrByName("RuntimeVisibleAnnotations"), SIDE)) {
				fields.remove(i);
			}
		}

		Attribute bsm = data.attrByName("BootstrapMethods");
		if (bsm == null) return;

		LambdaGatherer LG = new LambdaGatherer(bsm, pool);
		LG.owner = data.name;

		List<? extends MethodNode> methods = data.methods;
		for (int i = methods.size() - 1; i >= 0; i--) {
			MethodNode method = methods.get(i);
			if (remove(pool, method.attrByName("RuntimeVisibleAnnotations"), SIDE)) {
				methods.remove(i);
				LG.accept(method);
			}
		}

		List<BootstrapMethods.BootstrapMethod> handles = new SimpleList<>();
		do {
			handles.clear();
			handles.addAll(LG.handles);
			LG.handles.clear();

			for (int i = methods.size() - 1; i >= 0; i--) {
				MethodNode m = methods.get(i);
				if ((m.accessFlag() & AccessFlag.SYNTHETIC) == 0) continue;
				for (int j = 0; j < handles.size(); j++) {
					CstNameAndType method = handles.get(j).implementor().desc();
					if (m.name().equals(method.getName().getString()) && m.rawDesc().equals(method.getType().getString())) {
						methods.remove(i);
						LG.accept(m);
						break;
					}
				}
			}
		} while (!handles.isEmpty());
	}

	public static final String SIDEONLY_TYPE = SideOnly.class.getName().replace('.', '/');

	private boolean remove(ConstantPool cst, Attribute anns, String side) {
		if (anns == null) return false;

		DynByteBuf r = Parser.reader(anns);
		for (int i = r.readUnsignedShort(); i > 0; i--) {
			Annotation ann = Annotation.deserialize(cst, r);
			if (ann.clazz.equals(SIDEONLY_TYPE)) {
				if (!ann.getEnumValue("value", "").equals(side)) {
					return true;
				}
			}
		}
		return false;
	}

	private static class LambdaGatherer extends CodeVisitor {
		private static final BootstrapMethods.BootstrapMethod META_FACTORY = new BootstrapMethods.BootstrapMethod(
			"java/lang/invoke/LambdaMetafactory", "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
			6);

		String owner;
		private final List<BootstrapMethod> methods;

		LambdaGatherer(Attribute attribute, ConstantPool pool) {
			methods = BootstrapMethods.parse(Parser.reader(attribute), pool);
			cp = pool;
		}

		final List<BootstrapMethods.BootstrapMethod> handles = new SimpleList<>();

		public void accept(MethodNode method) {
			Attribute code = method.attrByName("Code");
			if (code == null) return;
			visit(cp, Parser.reader(code));
		}

		@Override
		public void invoke_dynamic(CstDynamic dyn, int type) {
			BootstrapMethods.BootstrapMethod handle = methods.get(dyn.tableIdx);
			if (META_FACTORY.equals0(handle)) {
				CstRef method = handle.implementor();
				if (method.getClassName().equals(owner)) {
					handles.add(handle);
				}
			}
		}
	}
}
