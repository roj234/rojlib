package roj.plugins.obfuscator.naming;

import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.MemberDescriptor;
import roj.asm.MethodNode;
import roj.asmx.Context;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.Resolver;
import roj.text.CharList;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * 随机移动名称
 * 大概可以让人第一眼觉得没有混淆
 * 并且更好打断别人的思路
 *
 * @author Roj233
 * @since 2023/9/9 14:09
 */
public final class Move extends SimpleNamer {
	private final List<String> className = new ArrayList<>(), methodName = new ArrayList<>(), fieldName = new ArrayList<>();
	private int classId, methodId, fieldId;

	public Move(Resolver resolver, List<Context> ctxs, Random rnd) {
		List<Context> ctxs1 = new ArrayList<>(ctxs);
		Collections.shuffle(ctxs1, rnd);

		for (Context ctx : ctxs1) {
			ClassNode data = ctx.getData();
			className.add(data.name());
			for (MethodNode m : data.methods) if (!m.name().startsWith("<") && !isInherited(resolver, new MemberDescriptor(m.owner(), m.name(), m.rawDesc()))) methodName.add(m.name());
			for (FieldNode m : data.fields) fieldName.add(m.name());
		}
	}

	/**
	 * method所表示的方法是否从parents(若非空)或method.owner的父类继承/实现
	 */
	public Boolean isInherited(Resolver resolver, MemberDescriptor method) {
		ClassNode info = resolver.resolve(method.owner);
		if (info == null) return false;

		ComponentList ml = resolver.getMethodList(info, method.name);
		if (ml == ComponentList.NOT_FOUND) return false;

		List<MethodNode> methods = ml.getMethods();
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			if (mn.rawDesc().equals(method.rawDesc()) && ml.isOverriddenMethod(i)) {
				return true;
			}
		}

		return false;
	}

	private void apply(Random rnd, LinkedHashSet<String> list, HashMap<String, String> map) {
		ArrayList<String> target = new ArrayList<>(list);
		Collections.shuffle(target, rnd);
		int i = 0;
		for (String s : list) map.put(s, target.get(i++));
	}

	@Override
	protected boolean generateName(Random rnd, CharList sb, int type) {
		sb.clear();
		switch (type) {
			default:
			case 0: sb.append(className.get(classId++ % className.size())); break;
			case 1: sb.append(methodName.get(methodId++ % methodName.size())); break;
			case 2: sb.append(fieldName.get(fieldId++ % fieldName.size())); break;
		}
		return true;
	}
}