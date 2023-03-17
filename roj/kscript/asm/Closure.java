package roj.kscript.asm;

import roj.kscript.type.KType;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2021/4/22 12:36
 */
public final class Closure extends IContext {
	KType[] lvt;

	public Closure(IContext ctx) {
		KType[] lvt1;
		if (ctx instanceof Frame) {
			lvt1 = ((Frame) ctx).lvt;
		} else {
			lvt1 = ((Closure) ctx).lvt;
		}

		lvt = new KType[lvt1.length];
		for (int i = 0; i < lvt1.length; i++) {
			lvt[i] = lvt1[i].copy();
		}
	}

	@Override
	public String toString() {
		return "<Closure>: " + Arrays.toString(lvt);
	}

	@Override
	KType getEx(String keys, KType def) {return null;}

	@Override
	public void put(String id, KType val) {}

	@Override
	KType getIdx(int index) {
		return lvt[index];
	}

	@Override
	void putIdx(int index, KType value) {
		lvt[index] = value;
	}
}
