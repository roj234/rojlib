package roj.asm.annotation;

import roj.asm.type.Type;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.text.CharList;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AList extends CList {
	public static final AList EMPTY = new AList(Collections.emptyList());

	public AList(List<CEntry> v) {super(v);}

	public Type getType(int i) {return ((AClass)list.get(i)).value;}
	public String getEnumValue(int j) {return ((AEnum)list.get(j)).field;}
	public AList getArray(int i) {return (AList) list.get(i);}
	public Annotation getAnnotation(int i) {return (Annotation) list.get(i);}

	public String toString() {
		CharList sb = new CharList().append('{');
		if (list.size() > 0) {
			int i = 0;
			while (true) {
				sb.append(list.get(i));
				if (++i == list.size()) break;
				sb.append(", ");
			}
		}
		return sb.append('}').toStringAndFree();
	}
}