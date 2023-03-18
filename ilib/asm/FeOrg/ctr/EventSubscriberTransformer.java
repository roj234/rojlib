package ilib.asm.FeOrg.ctr;

import ilib.api.ContextClassTransformer;
import ilib.asm.Loader;
import roj.asm.Parser;
import roj.asm.tree.AccessData;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.MoFNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.Context;
import roj.collect.MyHashSet;

import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Modifier;
import java.util.List;

public final class EventSubscriberTransformer implements ContextClassTransformer {
	private static MyHashSet<String> has;

	public static void lockdown() {
		has = new MyHashSet<>();
		for (ASMDataTable.ASMData data : Loader.Annotations.getAll(SubscribeEvent.class.getName())) {
			has.add(data.getClassName());
		}
	}

	@Override
	public void transform(String trName, Context ctx) {
		if (has != null && !has.contains(trName)) return;

		AccessData aad = ctx.inRaw() ? Parser.parseAccess(ctx.get().list) : null;
		ConstantData cz = aad == null ? ctx.getData() : Parser.parseConstants(ctx.get().list);

		List<? extends MethodNode> methods = cz.methods;
		for (int j = 0; j < methods.size(); j++) {
			MoFNode m = methods.get(j);

			Attribute attr = m.attrByName("RuntimeVisibleAnnotations");
			if (attr == null) continue;

			List<Annotation> annos = Annotations.parse(cz.cp, Parser.reader(attr));
			for (int i = 0; i < annos.size(); i++) {
				Annotation ann = annos.get(i);
				if ("net/minecraftforge/fml/common/eventhandler/SubscribeEvent".equals(ann.clazz)) {
					if (Modifier.isPrivate(m.modifier())) {
						String msg = "Cannot apply @SubscribeEvent to private method %s/%s%s";
						throw new RuntimeException(String.format(msg, cz.name, m.name(), m.rawDesc()));
					}

					if (aad != null) {
						aad.methods.get(j).modifier(toPublic(m.modifier()));
					} else {
						m.modifier((char) toPublic(m.modifier()));
					}
					break;
				}
			}
		}

		if (aad != null) {
			aad.modifier(toPublic(cz.access));
		} else {
			cz.access = (char) toPublic(cz.access);
		}
	}

	private static int toPublic(int access) {
		return access & 0xFFFFFFF9 | 1;
	}
}
