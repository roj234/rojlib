package roj.asm.tree.attr;

import roj.asm.cp.ConstantPool;
import roj.asm.tree.anno.Annotation;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class Annotations extends Attribute {
	public Annotations(boolean visibleForRuntime) {
		vis = visibleForRuntime;
		annotations = new SimpleList<>();
	}
	public Annotations(boolean visibleForRuntime, Annotation annotation) {
		vis = visibleForRuntime;
		annotations = Collections.singletonList(annotation);
	}
	public Annotations(boolean visibleForRuntime, Annotation... annotations) {
		vis = visibleForRuntime;
		this.annotations = Arrays.asList(annotations);
	}

	public Annotations(String name, DynByteBuf r, ConstantPool pool) {
		vis = name.equals("RuntimeVisibleAnnotations");
		annotations = parse(pool, r);
	}

	public final boolean vis;
	public List<Annotation> annotations;

	@Override
	public boolean isEmpty() { return annotations.isEmpty(); }

	@Override
	public String name() { return vis?"RuntimeVisibleAnnotations":"RuntimeInvisibleAnnotations"; }

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.putShort(annotations.size());
		for (int i = 0; i < annotations.size(); i++) {
			annotations.get(i).toByteArray(pool, w);
		}
	}

	public static List<Annotation> parse(ConstantPool pool, DynByteBuf r) {
		int len = r.readUnsignedShort();
		List<Annotation> annos = new SimpleList<>(len);
		while (len-- > 0) annos.add(Annotation.parse(pool, r));
		return annos;
	}

	public final String toString() { return toString(IOUtil.getSharedCharBuf(), 0).toString(); }
	public final CharList toString(CharList sb, int prefix) {
		if (!annotations.isEmpty()) {
			for (int i = 0; i < annotations.size(); i++) {
				sb.padEnd(' ', prefix).append(annotations.get(i)).append('\n');
			}
			sb.setLength(sb.length()-1);
		}
		return sb;
	}
}