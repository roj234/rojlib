package roj.asm.attr;

import roj.asm.MethodNode;
import roj.asm.annotation.Annotation;
import roj.asm.cp.ConstantPool;
import roj.collect.ArrayList;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class ParameterAnnotations extends Attribute {
	public static final String VISIBLE = "RuntimeVisibleParameterAnnotations", INVISIBLE = "RuntimeInvisibleParameterAnnotations";

	public static List<List<Annotation>> getParameterAnnotation(ConstantPool cp, MethodNode m, boolean vis) {
		ParameterAnnotations pa = m.getAttribute(cp, vis ? RtParameterAnnotations : ClParameterAnnotations);
		return pa == null ? null : pa.annotations;
	}

	public ParameterAnnotations(boolean visibleForRuntime) {
		vis = visibleForRuntime;
		annotations = new ArrayList<>();
	}

	public ParameterAnnotations(String name, DynByteBuf r, ConstantPool pool) {
		vis = name.equals(VISIBLE);

		int len = r.readUnsignedByte();
		List<List<Annotation>> annos = annotations = new ArrayList<>(len);
		while (len-- > 0) {
			annos.add(Annotations.parse(pool, r));
		}
	}

	public final boolean vis;
	public List<List<Annotation>> annotations;

	@Override
	public boolean writeIgnore() { return annotations.isEmpty(); }
	@Override
	public String name() { return vis?VISIBLE:INVISIBLE; }

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.put(annotations.size());
		for (int i = 0; i < annotations.size(); i++) {
			List<Annotation> list = annotations.get(i);
			w.putShort(list.size());
			for (int j = 0; j < list.size(); j++) {
				list.get(j).toByteArray(w, pool);
			}
		}
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