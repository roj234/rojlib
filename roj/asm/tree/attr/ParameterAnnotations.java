package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.tree.anno.Annotation;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class ParameterAnnotations extends Attribute {
	public static final String VISIBLE = "RuntimeVisibleParameterAnnotations", INVISIBLE = "RuntimeInvisibleParameterAnnotations";

	public ParameterAnnotations(String name) {
		super(name);
	}

	public ParameterAnnotations(String name, DynByteBuf r, ConstantPool pool) {
		super(name);
		annotations = parse(pool, r);
	}

	public List<List<Annotation>> annotations;

	@Override
	public boolean isEmpty() {
		return annotations.isEmpty();
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.put((byte) annotations.size());
		for (int i = 0; i < annotations.size(); i++) {
			List<Annotation> list = annotations.get(i);
			w.putShort(list.size());
			for (int j = 0; j < list.size(); j++) {
				list.get(j).toByteArray(pool, w);
			}
		}
	}

	public static List<List<Annotation>> parse(ConstantPool cp, DynByteBuf r) {
		int len = r.readUnsignedByte();
		List<List<Annotation>> annos = new SimpleList<>(len);
		while (len-- > 0) {
			annos.add(Annotations.parse(cp, r));
		}
		return annos;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder(" Parameters' Annotation: \n");
		for (int i = 0; i < annotations.size(); i++) {
			List<Annotation> list = annotations.get(i);
			if (list.isEmpty()) continue;
			sb.append("            #").append(i + 1).append(": \n");
			for (int j = 0; j < list.size(); j++) {
				Annotation anno = list.get(j);
				sb.append("               ").append(anno).append('\n');
			}
		}
		return sb.toString();
	}
}