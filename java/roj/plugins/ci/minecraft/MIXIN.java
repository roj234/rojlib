package roj.plugins.ci.minecraft;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.RawNode;
import roj.asm.annotation.AList;
import roj.asm.annotation.AnnVal;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.cp.CstNameAndType;
import roj.asm.type.Desc;
import roj.asm.type.Type;
import roj.asm.util.ClassUtil;
import roj.asm.util.Context;
import roj.asmx.mapper.Mapper;
import roj.collect.SimpleList;
import roj.plugins.ci.FMD;
import roj.plugins.ci.plugin.ProcessEnvironment;
import roj.plugins.ci.plugin.Processor;

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
			ClassNode data = ctx.get(i).getData();
			Annotation mixin = Annotation.findInvisible(data.cp, data, "org/spongepowered/asm/mixin/Mixin");

			// noinspection all
			var dest = mixin.getArray("value");
			String type;
			if (dest.isEmpty()) type = mixin.getArray("targets").getString(0);
			else type = dest.getType(0).owner;
			process(data, type, data.fields);
			process(data, type, data.methods);
		}
		FMD.LOGGER.debug("处理了{}个Mixin", ctx.size());
		m = null;
		return classes;
	}

	private void process(ClassNode data, String dest, List<? extends RawNode> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			RawNode node = nodes.get(i);

			List<Annotation> list = Annotations.getAnnotations(data.cp, node, true);
			for (int j = 0; j < list.size(); j++) {
				Annotation anno = list.get(j);
				if (anno.type().equals("org/spongepowered/asm/mixin/Shadow") || anno.type().equals("org/spongepowered/asm/mixin/Overwrite")) {
					anno.put("remap", AnnVal.valueOf(false));

					String name = map(dest, node, null);
					if (name != null) {
						if (anno.type().endsWith("Shadow")) {
							CstNameAndType desc = data.cp.getDesc(node.name(), node.rawDesc());
							desc.name(data.cp.getUtf(name));
						}

						node.name(name);
					} else {
						FMD.LOGGER.warn("无法为对象{}.{}找到映射", data.name(), node.name());
					}
					break;
				}
				if (anno.type().equals("org/spongepowered/asm/mixin/injection/Redirect")) {
					Annotation at = anno.getAnnotation("at");
					String string = at.getString("target");
					String owner;

					int klass = string.indexOf(';');
					owner = string.substring(1, klass).replace('.', '/');
					int pvrvm = string.indexOf('(', klass);

					Desc desc = new Desc(owner,  string.substring(klass+1, pvrvm), string.substring(pvrvm));

					String s = tryMap(desc.owner, desc, false);
					if (s != null) {
						at.put("target", AnnVal.valueOf("L"+desc.owner+";"+s+desc.param));
					} else {
						FMD.LOGGER.warn("无法为对象{}.{}找到映射", desc);
					}
				}
				if (anno.type().equals("org/spongepowered/asm/mixin/injection/Inject") || anno.type().equals("org/spongepowered/asm/mixin/injection/Redirect")
					|| anno.type().equals("org/spongepowered/asm/mixin/injection/ModifyConstant")) {
					anno.put("remap", AnnVal.valueOf(false));

					var method = anno.getArray("method");
					if (method.size() <= 1) {
						String name = map(dest, node, method.isEmpty() ? null : method.getString(0));
						if (name != null) {
							anno.put("method", new AList(Collections.singletonList(AnnVal.valueOf(name))));
						} else {
							FMD.LOGGER.warn("无法为对象{}.{}找到映射", data.name(), node.name());
						}
					}
					break;
				}
			}
		}
	}

	private String map(String dest, RawNode node, String altName) {
		if (altName != null && altName.equals("/")) return node.name();

		String nodeDesc = node.rawDesc();
		if (node instanceof MethodNode mn && !mn.parameters().isEmpty()) {
			Type type = mn.parameters().get(mn.parameters().size() - 1);
			if (type.owner() != null && type.owner().startsWith("org/spongepowered/asm/mixin/injection/callback/")) {
				var tmp = new SimpleList<>(mn.parameters());
				tmp.set(tmp.size() - 1, mn.returnType());
				nodeDesc = Type.toMethodDesc(tmp);
			}
		}

		Desc desc = ClassUtil.getInstance().sharedDC;
		desc.owner = dest;
		desc.name = altName == null || altName.isEmpty() ? node.name() : altName;
		desc.param = m.checkFieldType || nodeDesc.startsWith("(") ? nodeDesc : "";

		boolean inputWithDesc = false;
		int i1 = desc.name.indexOf('(');
		if (i1 >= 0) {
			inputWithDesc = true;
			desc.param = desc.name.substring(i1);
			desc.name = desc.name.substring(0, i1);
		}

		return tryMap(dest, desc, inputWithDesc);
	}

	@Nullable
	private String tryMap(String dest, Desc desc, boolean inputWithDesc) {
		Map<Desc, String> map = desc.param.startsWith("(") ? m.getMethodMap() : m.getFieldMap();

		List<String> parents = m.getSelfSupers().getOrDefault(dest, Collections.emptyList());
		int i = 0;
		while (true) {
			String name = map.get(desc);
			if (name != null) return inputWithDesc ? name + desc.rawDesc() : name;
			FMD.LOGGER.debug("NotFoundInCurrent: {}", desc);

			if (m.getStopAnchor().contains(desc)) break;

			if (i == parents.size()) break;
			desc.owner = parents.get(i++);
		}
		return null;
	}
}