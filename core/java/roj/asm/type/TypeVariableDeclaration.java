package roj.asm.type;

import roj.collect.ArrayList;

/**
 * @author Roj234
 * @since 2026/1/26 1:52
 */
public class TypeVariableDeclaration extends ArrayList<IType> {
	public String name;
	byte state;

	public TypeVariableDeclaration(String name) {this(name, 1);}
	public TypeVariableDeclaration(String name, int size) {
		super(size);
		this.name = name;
	}

	public TypeVariableDeclaration(String name, Object... element) {
		super();
		this.name = name;
		_setArray(element);
		_setSize(element.length);
	}

	@Override
	public void ensureCapacity(int cap) {
		if (list.length < cap) {
			int newCap = cap > 4 ? cap + 4 : cap + 1;
			Object[] newList = new Object[newCap];
			if (size > 0) System.arraycopy(list, 0, newList, 0, size);
			list = newList;
		}
	}

	public static TypeVariableDeclaration newUnresolved(String name) {
		TypeVariableDeclaration decl = new TypeVariableDeclaration(name, 0);
		decl.state = 1;
		return decl;
	}

	public int getState() {return state;}
	public void clearUnresolved() {state = 0;}

	@Override public int hashCode() {return System.identityHashCode(this);}
	@Override public boolean equals(Object o) {return o == this;}

	@Override
	public String toString() {return name+"="+(state != 0 ?"Unresolved "+size+" refs":super.toString());}
}