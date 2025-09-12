package roj.ci.plugin;

import roj.asm.ClassNode;
import roj.asm.ClassUtil;
import roj.asm.Member;
import roj.asm.MemberDescriptor;
import roj.asm.annotation.AnnVal;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.mapper.Mapper;
import roj.ui.Tty;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author solo6975
 * @since 2022/5/22 1:58
 */
public class NIXIM implements Processor {
	@Override
	public String name() {return "Nixim注解上下文处理程序";}

	@Override
	public void afterCompile(BuildContext pc) {
		var mapper = Objects.requireNonNull(pc.getProcessor(MAP.class), "Missing dep MAP for NIXIM").getProjectMapper(pc.project);
		var ctx = pc.getAnnotatedClass(CodeWeaver.A_INJECTION);

		for (int i = 0; i < ctx.size(); i++) {
			ClassNode data = ctx.get(i).getData();
			Annotation nixim = Annotation.findInvisible(data.cp, data, CodeWeaver.A_INJECTION);

			// noinspection all
			String dest = nixim.getString("value");
			if (dest.equals("/")) dest = data.parent();
			else dest = dest.replace('.', '/');

			process(mapper, data, dest, data.fields);
			process(mapper, data, dest, data.methods);
		}
	}

	private void process(Mapper mapper, ClassNode data, String dest, List<? extends Member> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			Member node = nodes.get(i);
			if (node.name().startsWith("func_") || node.name().startsWith("field_")) continue;

			List<Annotation> list = Annotations.getAnnotations(data.cp, node, false);
			for (int j = 0; j < list.size(); j++) {
				Annotation anno = list.get(j);
				if (anno.type().equals(CodeWeaver.A_SHADOW)) {
					String value = anno.getString("value", "");
					if (value.isEmpty()) {
						String prevOwner = anno.getString("owner", dest).replace('.', '/');
						String name = map(mapper, prevOwner, node);
						if (name != null) {
							String owner = ClassUtil.getInstance().sharedDesc.owner;
							if (!prevOwner.equals(owner)) {
								anno.put("owner", AnnVal.valueOf(owner));
							}
							anno.put("value", AnnVal.valueOf(name));
						} else {
							Tty.warning("无法为对象找到签名: " + data.name() + " " + node);
						}
					}
					break;
				} else if (anno.type().equals(CodeWeaver.A_COPY)) {
					if (anno.getBool("map", false)) {
						String name = map(mapper, dest, node);
						if (name != null) {
							anno.put("value", AnnVal.valueOf(name));
						} else {
							Tty.warning("无法为对象找到签名: " + data.name() + " " + node);
						}
					}
					break;
				} else if (anno.type().equals(CodeWeaver.A_INJECT_POINT)) {
					String value = anno.getString("value", "");
					if (value.isEmpty()) {
						String name = map(mapper, dest, node);
						if (name != null) {
							anno.put("value", AnnVal.valueOf(name));
						} else {
							Tty.warning("无法为对象找到签名: " + data.name() + " " + node);
						}
					}
					break;
				}
			}
		}
	}

	private String map(Mapper mapper, String dest, Member node) {
		MemberDescriptor desc = ClassUtil.getInstance().sharedDesc;
		desc.owner = dest;
		desc.name = node.name();
		desc.rawDesc = mapper.checkFieldType || node.rawDesc().startsWith("(") ? node.rawDesc() : "";

		Map<MemberDescriptor, String> map = desc.rawDesc.startsWith("(") ? mapper.getMethodMap() : mapper.getFieldMap();

		List<String> parents = mapper.getSelfSupers().getOrDefault(dest, Collections.emptyList());
		int i = 0;
		while (true) {
			String name = map.get(desc);
			if (name != null) return name;
			System.err.println("failed check " + desc);

			if (mapper.getStopAnchor().contains(desc)) break;

			if (i == parents.size()) break;
			desc.owner = parents.get(i++);
		}
		return null;
	}
}