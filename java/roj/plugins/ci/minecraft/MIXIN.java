package roj.plugins.ci.minecraft;

import roj.asm.tree.ConstantData;
import roj.asm.tree.RawNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValClass;
import roj.asm.tree.anno.Annotation;
import roj.asm.type.Desc;
import roj.asm.util.ClassUtil;
import roj.asm.util.Context;
import roj.asmx.mapper.Mapper;
import roj.plugins.ci.FMD;
import roj.plugins.ci.plugin.ProcessEnvironment;
import roj.plugins.ci.plugin.Processor;
import roj.ui.Terminal;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author solo6975
 * @since 2022/5/22 1:58
 */
public class MIXIN implements Processor {
	Mapper m;

	@Override
	public String name() {return "Mixin注解上下文处理程序";}

	@Override
	public List<Context> process(List<Context> classes, ProcessEnvironment pc) {
		m = FMD.MapPlugin.getProjectMapper(pc.project);

		var ctx = pc.getAnnotatedClass(classes, "org/spongepowered/asm/mixin/Mixin");

		for (int i = 0; i < ctx.size(); i++) {
			ConstantData data = ctx.get(i).getData();
			Annotation mixin = Annotation.findInvisible(data.cp, data, "org/spongepowered/asm/mixin/Mixin");

			// noinspection all
			var dest = mixin.getArray("value");

			var type = ((AnnValClass) dest.get(0)).value;
			process(data, type.owner, data.fields);
			process(data, type.owner, data.methods);
		}
		m = null;
		return classes;
	}

	private void process(ConstantData data, String dest, List<? extends RawNode> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			RawNode node = nodes.get(i);

			List<Annotation> list = Annotation.getAnnotations(data.cp, node, false);
			for (int j = 0; j < list.size(); j++) {
				Annotation anno = list.get(j);
				if (anno.type().equals("org/spongepowered/asm/mixin/Shadow") || anno.type().equals("org/spongepowered/asm/mixin/Overwrite")) {
					anno.put("remap", AnnVal.valueOf(false));

					System.out.println("Mixin => "+anno+"|"+dest+"|"+node);
					String name = map(dest, node, null);
					if (name != null) node.name(name);
					else {
						Terminal.warning("无法为对象找到签名: " + data.name + " " + node);
					}
					break;
				}
				if (anno.type().equals("org/spongepowered/asm/mixin/Inject")) {
					anno.put("remap", AnnVal.valueOf(false));

					System.out.println("Mixin => "+anno+"|"+dest+"|"+node);
					String name = map(dest, node, anno.getString("method", null));
					if (name != null) anno.put("method", AnnVal.valueOf(name));
					else {
						Terminal.warning("无法为对象找到签名: " + data.name + " " + node);
					}
					break;
				}
			}
		}
	}

	private String map(String dest, RawNode node, String altName) {
		Desc desc = ClassUtil.getInstance().sharedDC;
		desc.owner = dest;
		desc.name = altName == null || altName.isEmpty() || altName.equals("/") ? node.name() : altName;
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