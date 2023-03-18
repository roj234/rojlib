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
public final class Annotations extends Attribute {
	public static final String VISIBLE = "RuntimeVisibleAnnotations", INVISIBLE = "RuntimeInvisibleAnnotations";

	public Annotations(String name) {
		super(name);
		annotations = new SimpleList<>();
	}

	public Annotations(String name, DynByteBuf r, ConstantPool pool) {
		super(name);
		annotations = parse(pool, r);
	}

	public List<Annotation> annotations;

	@Override
	public boolean isEmpty() {
		return annotations.isEmpty();
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.putShort(annotations.size());
		for (int i = 0; i < annotations.size(); i++) {
			annotations.get(i).toByteArray(pool, w);
		}
	}

	public static List<Annotation> parse(ConstantPool pool, DynByteBuf r) {
		int len = r.readUnsignedShort();
		List<Annotation> annos = new SimpleList<>(len);
		while (len-- > 0) annos.add(Annotation.deserialize(pool, r));
		return annos;
	}

	public String toString() {
		if (annotations.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < annotations.size(); i++) {
			sb.append(annotations.get(i)).append('\n');
		}
		return sb.deleteCharAt(sb.length() - 1).toString();
	}
}