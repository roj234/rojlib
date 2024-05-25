package roj.compiler.asm;

import roj.asm.tree.anno.Annotation;
import roj.collect.MyHashMap;
import roj.util.Helpers;

public final class AnnotationPrimer extends Annotation {
	public int pos;
	public boolean assertValueOnly;

	public AnnotationPrimer(String type, int pos) {
		this.setType(type);
		this.values = new MyHashMap<>();
		this.pos = pos - 1;
	}

	public void newEntry(String key, Object val) {values.put(key, Helpers.cast(val));}
}