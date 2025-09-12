package roj.ci.minecraft;

import org.jetbrains.annotations.Nullable;
import roj.asm.*;
import roj.asm.annotation.AList;
import roj.asm.annotation.AnnVal;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstNameAndType;
import roj.asm.type.Generic;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asmx.mapper.Mapper;
import roj.ci.MCMake;
import roj.ci.plugin.BuildContext;
import roj.ci.plugin.MAP;
import roj.ci.plugin.Processor;
import roj.collect.ArrayList;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author solo6975
 * @since 2022/5/22 1:58
 */
public class MIXIN implements Processor {
	@Override
	public String name() {return "Mixin注解上下文处理程序";}

	@Override
	public void afterCompile(BuildContext ctx) {
		var m = Objects.requireNonNull(ctx.getProcessor(MAP.class), "Missing dep MAP for MIXIN").getProjectMapper(ctx.project);

		var annotatedClass = ctx.getAnnotatedClass("org/spongepowered/asm/mixin/Mixin");
		for (int i = 0; i < annotatedClass.size(); i++) {
			ClassNode data = annotatedClass.get(i).getData();
			Annotation mixin = Annotation.findInvisible(data.cp, data, "org/spongepowered/asm/mixin/Mixin");

			// noinspection all
			var targetClasses = mixin.getList("value");
			String targetClass;
			if (targetClasses.isEmpty()) targetClass = mixin.getList("targets").getString(0);
			else targetClass = targetClasses.getType(0).owner;
			processMembers(m, data, targetClass, data.fields);
			processMembers(m, data, targetClass, data.methods);
		}
	}

	private void processMembers(Mapper mapper, ClassNode mixinClass, String targetClass, List<? extends Member> members) {
		for (int i = 0; i < members.size(); i++) {
			Member member = members.get(i);

			List<Annotation> annotations = Annotations.getAnnotations(mixinClass.cp, member, true);
			for (int j = 0; j < annotations.size(); j++) {
				Annotation anno = annotations.get(j);
				if (anno.type().equals("org/spongepowered/asm/mixin/Shadow") || anno.type().equals("org/spongepowered/asm/mixin/Overwrite")) {
					anno.put("remap", AnnVal.valueOf(false));

					String mappedName = mapMemberName(mapper, mixinClass.cp, targetClass, member, null);
					if (mappedName != null) {
						if (anno.type().endsWith("Shadow")) {
							CstNameAndType desc = mixinClass.cp.getDesc(member.name(), member.rawDesc());
							desc.name(mixinClass.cp.getUtf(mappedName));
						}

						member.name(mappedName);
					} else {
						MCMake.LOGGER.warn("无法为对象{}.{}找到映射", mixinClass.name(), member.name());
					}
					break;
				}
				if (anno.type().equals("org/spongepowered/asm/mixin/injection/Redirect")) {
					Annotation at = anno.getAnnotation("at");
					String targetDesc = at.getString("target");
					String owner;

					int klass = targetDesc.indexOf(';');
					owner = targetDesc.substring(1, klass).replace('.', '/');
					int pvrvm = targetDesc.indexOf('(', klass);

					MemberDescriptor target = new MemberDescriptor(owner,  targetDesc.substring(klass+1, pvrvm), targetDesc.substring(pvrvm));

					String mappedTarget = tryMapMember(mapper, target.owner, target, false, true);
					if (mappedTarget != null) {
						at.put("target", AnnVal.valueOf("L"+target.owner+";"+mappedTarget+target.rawDesc));
					} else {
						MCMake.LOGGER.warn("无法为对象{}.{}找到映射", target);
					}
				}
				if (anno.type().equals("org/spongepowered/asm/mixin/injection/Inject") || anno.type().equals("org/spongepowered/asm/mixin/injection/Redirect")
					|| anno.type().equals("org/spongepowered/asm/mixin/injection/ModifyConstant")) {
					anno.put("remap", AnnVal.valueOf(false));

					var methods = anno.getList("method");
					if (methods.size() <= 1) {
						String mappedName = mapMemberName(mapper, mixinClass.cp, targetClass, member, methods.isEmpty() ? null : methods.getString(0));
						if (mappedName != null) {
							anno.put("method", new AList(Collections.singletonList(AnnVal.valueOf(mappedName))));
						} else {
							MCMake.LOGGER.warn("无法为对象{}.{}找到映射", mixinClass.name(), member.name());
						}
					}
					break;
				}
			}
		}
	}

	private String mapMemberName(Mapper mapper, ConstantPool cp, String targetClass, Member member, String forcedName) {
		if (forcedName != null && forcedName.equals("/")) return member.name();

		String memberDesc = member.rawDesc();
		if (member instanceof MethodNode mn && !mn.parameters().isEmpty()) {
			Type lastArg = mn.parameters().get(mn.parameters().size() - 1);
			if (lastArg.owner() != null && lastArg.owner().startsWith("org/spongepowered/asm/mixin/injection/callback/")) {
				var parameters = new ArrayList<>(mn.parameters());
				parameters.set(parameters.size()-1, mn.returnType());
				if (lastArg.owner().endsWith("CallbackInfoReturnable")) {
					Signature signature = mn.getSignature(cp);
					try {
						Type returnType = ((Generic) signature.values.get(signature.values.size() - 2)).children.get(0).rawType();
						parameters.set(parameters.size() - 1, returnType);
					} catch (Exception e) {
						MCMake.LOGGER.warn("failed to obtain generic CallbackInfoReturnable {}", signature);
					}
				}
				memberDesc = Type.toMethodDesc(parameters);
			}
		}

		MemberDescriptor desc = ClassUtil.getInstance().sharedDesc;
		desc.owner = targetClass;
		desc.name = forcedName == null || forcedName.isEmpty() ? member.name() : forcedName;
		desc.rawDesc = mapper.checkFieldType || memberDesc.startsWith("(") ? memberDesc : "";

		boolean hasEmbeddedDesc = false;
		int i = desc.name.indexOf('(');
		if (i >= 0) {
			hasEmbeddedDesc = true;
			desc.rawDesc = desc.name.substring(i);
			desc.name = desc.name.substring(0, i);
		}

		return tryMapMember(mapper, targetClass, desc, hasEmbeddedDesc, member instanceof MethodNode);
	}

	@Nullable
	private String tryMapMember(Mapper mapper, String targetClass, MemberDescriptor desc, boolean hasEmbeddedDesc, boolean isMethod) {
		Map<MemberDescriptor, String> mapping = isMethod ? mapper.getMethodMap() : mapper.getFieldMap();

		List<String> parents = mapper.getSelfSupers().getOrDefault(targetClass, Collections.emptyList());
		int i = 0;
		while (true) {
			String mappedName = mapping.get(desc);
			if (mappedName != null) return hasEmbeddedDesc ? mappedName + desc.rawDesc() : mappedName;
			MCMake.LOGGER.debug("NotFoundInCurrent: {}", desc);

			if (mapper.getStopAnchor().contains(desc)) break;

			if (i == parents.size()) break;
			desc.owner = parents.get(i++);
		}
		return null;
	}
}