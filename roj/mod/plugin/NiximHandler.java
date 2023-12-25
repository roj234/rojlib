package roj.mod.plugin;

import roj.asm.tree.ConstantData;
import roj.asm.tree.RawNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.util.AttrHelper;
import roj.asm.util.Context;
import roj.asmx.nixim.NiximSystemV2;
import roj.mapper.MapUtil;
import roj.mapper.Mapper;
import roj.mapper.util.Desc;
import roj.mod.Shared;
import roj.ui.CLIUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author solo6975
 * @since 2022/5/22 1:58
 */
public class NiximHandler implements Plugin {
	@Override
	public String name() {
		return "Nixim注解上下文处理程序";
	}

	@Override
	public void afterCompile(List<Context> ctx, boolean mapped, PluginContext pc) {
		if (!mapped) return;
		ctx = pc.getAnnotatedClass(ctx, NiximSystemV2.A_NIXIM_CLASS_FLAG);

		for (int i = 0; i < ctx.size(); i++) {
			ConstantData data = ctx.get(i).getData();
			Annotation nixim = AttrHelper.getAnnotation(AttrHelper.getAnnotations(data.cp, data, false), NiximSystemV2.A_NIXIM_CLASS_FLAG);

			// noinspection all
			String dest = nixim.getString("value");
			if (dest.equals("/")) dest = data.parent;
			else dest = dest.replace('.', '/');

			process(data, dest, data.fields);
			process(data, dest, data.methods);
		}
	}

	private static void process(ConstantData data, String dest, List<? extends RawNode> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			RawNode node = nodes.get(i);
			if (node.name().startsWith("func_") || node.name().startsWith("field_")) continue;

			List<Annotation> list = AttrHelper.getAnnotations(data.cp, node, false);
			if (list == null) continue;
			for (int j = 0; j < list.size(); j++) {
				Annotation anno = list.get(j);
				if (anno.type.equals(NiximSystemV2.A_SHADOW)) {
					String value = anno.getString("value", "");
					if (value.isEmpty()) {
						String prevOwner = anno.getString("owner", dest).replace('.', '/');
						String name = map(prevOwner, node);
						if (name != null) {
							String owner = MapUtil.getInstance().sharedDC.owner;
							if (!prevOwner.equals(owner)) {
								anno.put("owner", AnnVal.valueOf(owner));
							}
							anno.put("value", AnnVal.valueOf(name));
						} else {
							CLIUtil.warning("无法为对象找到签名: " + data.name + " " + node);
						}
					}
					break;
				} else if (anno.type.equals(NiximSystemV2.A_COPY)) {
					if (anno.getBoolean("map", false)) {
						String name = map(dest, node);
						if (name != null) {
							anno.put("value", AnnVal.valueOf(name));
						} else {
							CLIUtil.warning("无法为对象找到签名: " + data.name + " " + node);
						}
					}
					break;
				} else if (anno.type.equals(NiximSystemV2.A_INJECT)) {
					String value = anno.getString("value", "");
					if (value.isEmpty()) {
						String name = map(dest, node);
						if (name != null) {
							anno.put("value", AnnVal.valueOf(name));
						} else {
							CLIUtil.warning("无法为对象找到签名: " + data.name + " " + node);
						}
					}
					break;
				}
			}
		}
	}

	private static String map(String dest, RawNode node) {
		Shared.loadMapper();
		Mapper m = Shared.mapperFwd;

		Desc desc = MapUtil.getInstance().sharedDC;
		desc.owner = dest;
		desc.name = node.name();
		desc.param = m.checkFieldType || node.rawDesc().startsWith("(") ? node.rawDesc() : "";

		Map<Desc, String> map = desc.param.startsWith("(") ? m.getMethodMap() : m.getFieldMap();

		List<String> parents = m.getSelfSupers().getOrDefault(dest, Collections.emptyList());
		int i = 0;
		while (true) {
			String name = map.get(desc);
			if (name != null) return name;
			System.err.println("failed check " + desc);

			if (m.getStopAnchor().contains(desc)) break;

			if (i == parents.size()) break;
			desc.owner = parents.get(i++);
		}
		return null;
	}
}
