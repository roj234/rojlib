package roj.kscript.util;

import roj.kscript.asm.Node;
import roj.kscript.type.KType;

/**
 * @author Roj234
 * @since 2020/8/10 17:53
 */
public final class Variable {
	public Variable(String name, KType def, Node start, Node end) {
		this.name = name;
		this.def = def;
		this.start = start;
		this.end = end;
	}

	public String name;
	public KType def;
	public Node start, end;

	public int index;

	@Override
	public String toString() {
		return "Var" + index + ':' + name + "=" + def + ", s=" + start + ", e=" + end + '}';
	}
}
