package roj.asmx;

import roj.asm.cp.Constant;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstRef;
import roj.asm.tree.*;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Desc;
import roj.asm.util.ClassUtil;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.util.AttributeKey;
import roj.util.Helpers;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/1/6 0006 23:03
 */
public class NodeFilter implements ITransformer {
	final MyHashMap<Object, Object> reference = new MyHashMap<>(), declare = new MyHashMap<>(),
		annotationClass = new MyHashMap<>(), annotationField = new MyHashMap<>(), annotationMethod = new MyHashMap<>();

	public NodeFilter declaredClass(String owner, NodeTransformer<? super ConstantData> cb) { add(declare, owner, cb); return this; }
	public NodeFilter declaredMethod(String owner, String name, String desc, NodeTransformer<? super MethodNode> cb) { add(declare, new Desc(owner, name, desc), cb); return this; }
	public NodeFilter declaredField(String owner, String name, String desc, NodeTransformer<? super FieldNode> cb) { add(declare, new Desc(owner, name, desc), cb); return this; }
	public NodeFilter declaredAnyMethod(String name, String desc, NodeTransformer<? super MethodNode> cb) { add(declare, new Desc("", name, desc), cb); return this; }
	public NodeFilter declaredAnyField(String name, String desc, NodeTransformer<? super FieldNode> cb) { add(declare, new Desc("", name, desc), cb); return this; }
	public NodeFilter referencedMethod(String owner, String name, String desc, NodeTransformer<? super CstRef> cb) { add(reference, new Desc(owner, name, desc), cb); return this; }
	public NodeFilter referencedField(String owner, String name, String desc, NodeTransformer<? super CstRef> cb) { add(reference, new Desc(owner, name, desc), cb); return this; }
	public NodeFilter referencedAnyMethod(String name, String desc, NodeTransformer<? super CstRef> cb) { add(reference, new Desc("", name, desc), cb); return this; }
	public NodeFilter referencedAnyField(String name, String desc, NodeTransformer<? super CstRef> cb) { add(reference, new Desc("", name, desc), cb); return this; }
	public NodeFilter referencedClass(String name, NodeTransformer<? super CstClass> cb) { add(reference, name, cb); return this; }
	public NodeFilter annotatedClass(String type, NodeTransformer<? super ConstantData> tr) { add(annotationClass, type, tr); return this; }
	public NodeFilter annotatedField(String type, NodeTransformer<? super FieldNode> tr) { add(annotationField, type, tr); return this; }
	public NodeFilter annotatedMethod(String type, NodeTransformer<? super MethodNode> tr) { add(annotationMethod, type, tr); return this; }

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

		mod |= checkAnnotation(data, data, Attribute.ClAnnotations, annotationClass);
		mod |= checkAnnotation(data, data, Attribute.RtAnnotations, annotationClass);

		tr = declare.get(data.name);
		if (tr != null) mod |= transform(tr, data, data);

		mod |= invokeDeclare(data, data.methods, d, annotationMethod);
		mod |= invokeDeclare(data, data.fields, d, annotationField);
		return mod;
	}
	private boolean invokeDeclare(ConstantData data, SimpleList<? extends RawNode> nodes, Desc d, MyHashMap<Object, Object> ref) throws TransformException {
		boolean mod = false;
		Object tr;

		for (int i = 0; i < nodes.size(); i++) {
			RawNode node = nodes.get(i);

			mod |= checkAnnotation(data, node, Attribute.ClAnnotations, ref);
			mod |= checkAnnotation(data, node, Attribute.RtAnnotations, ref);

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
	private boolean checkAnnotation(ConstantData data, Attributed node, AttributeKey<Annotations> flag, MyHashMap<Object, Object> ref) throws TransformException {
		Annotations attr = node.parsedAttr(data.cp, flag);
		boolean mod = false;
		if (attr != null) {
			List<Annotation> annotations = attr.annotations;
			for (int i = 0; i < annotations.size(); i++) {
				Object o = ref.get(annotations.get(i).type());
				if (o != null) mod |= transform(o, data, node);
			}
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