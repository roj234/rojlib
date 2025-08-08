package roj.compiler.runtime;

import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.collect.ArrayList;
import roj.util.NativeMemory;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/6/8 19:30
 */
public final class ReturnStack<T> {
	private static final int MEMORY_CAPACITY = 8 * 256;

	public static final ThreadLocal<ReturnStack<?>> TL = ThreadLocal.withInitial(ReturnStack::new);
	public static ReturnStack<?> get(int hashCode) {return TL.get().forWrite().put(hashCode);}

	private final NativeMemory memory;
	public ReturnStack() {this(MEMORY_CAPACITY);}
	public ReturnStack(int cap) {
		memory = new NativeMemory();
		memory.allocate(cap);
	}

	@ReferenceByGeneratedClass
	public ReturnStack<T> toImmutable() {
		int memoryCap = (int) (address - memory.address());
		System.out.println("CopyImmutable size="+memoryCap);
		ReturnStack<T> stack = new ReturnStack<>(memoryCap);
		U.copyMemory(memory.address(), stack.memory.address(), memoryCap);
		stack.objects.addAll(objects);
		stack.address += memoryCap;
		return stack;
	}
	public ReturnStack<T> forWrite() {
		objects.clear();
		return forRead();
	}
	ReturnStack<T> forRead() {
		address = memory.address();
		index = 0;
		return this;
	}
	public ReturnStack<T> forRead(int hashCode) {
		address = memory.address();
		index = 0;
		int genericHash = getI();
		if (genericHash != hashCode) throw new IncompatibleClassChangeError("Excepting generic hash "+hashCode+", but got "+genericHash);
		return this;
	}
	public long base() {return memory.address();}

	/**
	 * Optional: put return crc32 at +0, and throw IncompatibleClassChangeError on demand
	 */
	long address;
	final ArrayList<Object> objects = new ArrayList<>();
	int index;

	public ReturnStack<T> put(boolean v) {return put(v ? 1 : 0);}
	public ReturnStack<T> put(byte v) {
		U.putByte(address, v);
		address += 1;
		return this;
	}
	public ReturnStack<T> put(short v) {
		U.putShort(address, v);
		address += 2;
		return this;
	}
	public ReturnStack<T> put(char v) {
		U.putChar(address, v);
		address += 2;
		return this;
	}
	public ReturnStack<T> put(int v) {
		U.putInt(address, v);
		address += 4;
		return this;
	}
	public ReturnStack<T> put(float v) {
		U.putFloat(address, v);
		address += 4;
		return this;
	}

	public ReturnStack<T> put(long v) {
		U.putLong(address, v);
		address += 8;
		return this;
	}
	public ReturnStack<T> put(double v) {
		U.putDouble(address, v);
		address += 8;
		return this;
	}
	public ReturnStack<T> put(Object v) {
		objects.add(v);
		return this;
	}

	public int getI() {
		var v = U.getInt(address);
		address += 4;
		return v;
	}
	public long getJ() {
		var v = U.getLong(address);
		address += 8;
		return v;
	}
	public float getF() {return Float.intBitsToFloat(getI());}
	public double getD() {return Double.longBitsToDouble(getJ());}
	public Object getL() {return objects.set(index++, null);}
	public void skip(int count) {address += count;}
	public void skipL(int count) {index += count;}
}