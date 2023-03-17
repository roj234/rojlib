package roj.lavac.asm;

import roj.asm.tree.IClass;
import roj.asm.tree.anno.Annotation;
import roj.collect.MyHashMap;
import roj.lavac.block.ParseTask;
import roj.util.Helpers;

public class AnnotationPrimer extends Annotation {
	public int idx;
	public boolean assertValueOnly;
	public IClass clazzInst;

	public AnnotationPrimer(String type, int idx) {
		this.clazz = type;
		this.values = new MyHashMap<>();
		this.idx = idx - 1;
	}

	public void newEntry(String key, ParseTask val) {
		values.put(key, Helpers.cast(val));
	}
}
