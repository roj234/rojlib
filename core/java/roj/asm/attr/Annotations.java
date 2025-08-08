package roj.asm.attr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Attributed;
import roj.asm.annotation.Annotation;
import roj.asm.cp.ConstantPool;
import roj.collect.ArrayList;
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
		annotations = new ArrayList<>();
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

	@NotNull
	public static List<Annotation> getAnnotations(ConstantPool cp, Attributed node, boolean vis) {
		var attr = node.getAttribute(cp, vis? RtAnnotations : ClAnnotations);
		return attr == null ? Collections.emptyList() : attr.annotations;
	}

	@Override
	public boolean writeIgnore() { return annotations.isEmpty(); }

	@Override
	public String name() { return vis?"RuntimeVisibleAnnotations":"RuntimeInvisibleAnnotations"; }

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.putShort(annotations.size());
		for (int i = 0; i < annotations.size(); i++) {
			annotations.get(i).toByteArray(w, pool);
		}
	}

	public static List<Annotation> parse(ConstantPool pool, DynByteBuf r) {
		int len = r.readUnsignedShort();
		List<Annotation> annos = new ArrayList<>(len);
		while (len-- > 0) annos.add(Annotation.parse(pool, r));
		return annos;
	}

	public final String toString() { return toString(IOUtil.getSharedCharBuf(), 0).toString(); }
	public final CharList toString(CharList sb, int prefix) {
		if (!annotations.isEmpty()) {
			for (int i = 0; i < annotations.size(); i++) {
				sb.padEnd(' ', prefix).append(annotations.get(i)).append('\n');
			}
		}
		return sb;
	}
}