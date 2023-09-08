package roj.mapper.obf.policy;

import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.mapper.MapUtil;
import roj.mapper.util.Desc;
import roj.text.CharList;
import roj.util.ArrayUtil;

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
	private final List<String> className = new SimpleList<>(), methodName = new SimpleList<>(), fieldName = new SimpleList<>();
	private int classId, methodId, fieldId;

	public Move(List<Context> ctxs, Random rnd) {
		List<Context> ctxs1 = new SimpleList<>(ctxs);
		ArrayUtil.shuffle(ctxs1, rnd);

		for (Context ctx : ctxs1) {
			ConstantData data = ctx.getData();
			className.add(data.name);
			for (MethodNode m : data.methods) if (!m.name().startsWith("<") && !MapUtil.checkObjectInherit(new Desc(m.ownerClass(),m.name(),m.rawDesc()))) methodName.add(m.name());
			for (FieldNode m : data.fields) fieldName.add(m.name());
		}
	}

	private void apply(Random rnd, LinkedHashSet<String> list, MyHashMap<String, String> map) {
		SimpleList<String> target = new SimpleList<>(list);
		ArrayUtil.shuffle(target, rnd);
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
