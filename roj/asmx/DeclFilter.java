package roj.asmx;

import roj.asm.cp.Constant;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstRef;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.RawNode;
import roj.asm.type.Desc;
import roj.asm.util.ClassUtil;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.util.Helpers;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/1/6 0006 23:03
 */
public class DeclFilter implements ITransformer {
	final MyHashMap<Object, Object> reference = new MyHashMap<>(), declare = new MyHashMap<>();

	public DeclFilter declaredClass(String owner, NodeTransformer<? super ConstantData> cb) { add(declare, owner, cb); return this; }
	public DeclFilter declaredMethod(String owner, String name, String desc, NodeTransformer<? super MethodNode> cb) { add(declare, new Desc(owner, name, desc), cb); return this; }
	public DeclFilter declaredField(String owner, String name, String desc, NodeTransformer<? super FieldNode> cb) { add(declare, new Desc(owner, name, desc), cb); return this; }
	public DeclFilter declaredAnyMethod(String name, String desc, NodeTransformer<? super MethodNode> cb) { add(declare, new Desc("", name, desc), cb); return this; }
	public DeclFilter declaredAnyField(String name, String desc, NodeTransformer<? super FieldNode> cb) { add(declare, new Desc("", name, desc), cb); return this; }
	public DeclFilter referencedMethod(String owner, String name, String desc, NodeTransformer<? super CstRef> cb) { add(reference, new Desc(owner, name, desc), cb); return this; }
	public DeclFilter referencedField(String owner, String name, String desc, NodeTransformer<? super CstRef> cb) { add(reference, new Desc(owner, name, desc), cb); return this; }
	public DeclFilter referencedAnyMethod(String name, String desc, NodeTransformer<? super CstRef> cb) { add(reference, new Desc("", name, desc), cb); return this; }
	public DeclFilter referencedAnyField(String name, String desc, NodeTransformer<? super CstRef> cb) { add(reference, new Desc("", name, desc), cb); return this; }
	public DeclFilter referencedClass(String name, NodeTransformer<? super CstClass> cb) { add(reference, name, cb); return this; }

	@SuppressWarnings("unchecked")
	static void add(Map<Object, Object> reference, Object key, Object cb) {
		Object prev = reference.putIfAbsent(key, cb);
		if (prev != null) {
			if (prev instanceof NodeTransformer<?>) {
				if (cb instanceof NodeTransformer) reference.put(key, SimpleList.asModifiableList(prev, cb));
				else {
					reference.put(key, cb);
					((List<Object>) cb).add(0, prev);
				}
			} else {
				if (cb instanceof NodeTransformer) ((List<Object>) prev).add(cb);
				else ((List<Object>) prev).addAll(((List<?>) cb));
			}
		}
	}

	@Override
	public boolean transform(String name, Context ctx) throws TransformException {
		var d = ClassUtil.getInstance().sharedDC;
		var data = ctx.getData();

		Object tr;
		boolean mod = false;

		var cpArr = data.cp.array();
		for (int i = 0; i < cpArr.size(); i++) {
			Constant c = cpArr.get(i);
			switch (c.type()) {
				case Constant.CLASS:
					tr = reference.get(((CstClass) c).name().str());
					if (tr != null) mod |= transform(tr, data, c);
				break;
				case Constant.FIELD:
				case Constant.METHOD:
				case Constant.INTERFACE:
					var ref = (CstRef) c;
					d.owner = ref.className();
					d.name = ref.descName();
					d.param = ref.descType();

					tr = reference.get(d);
					if (tr != null) mod |= transform(tr, data, c);

					d.owner = "";
					tr = reference.get(d);
					if (tr != null) mod |= transform(tr, data, c);
				break;
			}
		}

		tr = declare.get(data.name);
		if (tr != null) mod |= transform(tr, data, data);

		mod |= invokeDeclare(data.methods, d, data);
		mod |= invokeDeclare(data.fields, d, data);
		return mod;
	}

	private boolean invokeDeclare(SimpleList<? extends RawNode> nodes, Desc d, ConstantData data) throws TransformException {
		boolean mod = false;
		Object tr;

		for (int i = 0; i < nodes.size(); i++) {
			RawNode node = nodes.get(i);
			d.owner = data.name;
			d.name = node.name();
			d.param = node.rawDesc();

			tr = declare.get(d);
			if (tr != null) mod |= transform(tr, data, node);

			d.owner = "";
			tr = declare.get(d);
			if (tr != null) mod |= transform(tr, data, node);
		}
		return mod;
	}

	@SuppressWarnings("unchecked")
	private boolean transform(Object tr, ConstantData data, Object ctx) throws TransformException {
		if (tr instanceof NodeTransformer<?> trx) return trx.transform(data, Helpers.cast(ctx));

		List<NodeTransformer<?>> list = (List<NodeTransformer<?>>) tr;
		boolean mod = false;
		for (int i = 0; i < list.size(); i++) mod |= list.get(i).transform(data, Helpers.cast(ctx));
		return mod;
	}
}