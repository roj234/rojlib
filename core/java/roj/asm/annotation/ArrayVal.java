package roj.asm.annotation;

import roj.asm.type.Type;
import roj.config.node.ConfigValue;
import roj.config.node.ListValue;
import roj.text.CharList;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class ArrayVal extends ListValue {
	public static final ArrayVal EMPTY = new ArrayVal(Collections.emptyList());

	public ArrayVal(List<ConfigValue> v) {super(v);}

	public Type getType(int i) {return ((TypeVal) elements.get(i)).value;}
	public String getEnumValue(int j) {return ((EnumVal) elements.get(j)).field;}
	public ArrayVal getArray(int i) {return (ArrayVal) elements.get(i);}
	public Annotation getAnnotation(int i) {return (Annotation) elements.get(i);}

	public String toString() {
		CharList sb = new CharList().append('{');
		if (elements.size() > 0) {
			int i = 0;
			while (true) {
				sb.append(elements.get(i));
				if (++i == elements.size()) break;
				sb.append(", ");
			}
		}
		return sb.append('}').toStringAndFree();
	}
}