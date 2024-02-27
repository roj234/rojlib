package roj.compiler.runtime;

import roj.collect.SimpleList;
import roj.util.NativeMemory;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2024/6/8 0008 19:30
 */
public final class ReturnStack<T> implements GenericIgnore {
	private static final int MEMORY_CAPACITY = 8 * 256;

	public static final ThreadLocal<ReturnStack<?>> TL = ThreadLocal.withInitial(ReturnStack::new);
	public static ReturnStack<?> get(int hashCode) {return TL.get().forWrite().put(hashCode);}

	private final NativeMemory memory;
	public ReturnStack() {this(MEMORY_CAPACITY);}
	public ReturnStack(int cap) {memory = new NativeMemory(cap);}

	public ReturnStack<T> toImmutable(int memoryCap) {
		ReturnStack<T> stack = new ReturnStack<>(memoryCap);
		u.copyMemory(memory.address(), stack.memory.address(), memoryCap);
		stack.objects.addAll(objects);
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
	public long address() {return memory.address();}

	/**
	 * Optional: put return crc32 at +0, and throw IncompatibleClassChangeError on demand
	 */
	private long address;
	private final SimpleList<Object> objects = new SimpleList<>();
	private int index;

	public ReturnStack<T> put(boolean v) {return put(v ? 1 : 0);}
	public ReturnStack<T> put(byte v) {
		u.putByte(address, v);
		address += 1;
		return this;
	}
	public ReturnStack<T> put(short v) {
		u.putShort(address, v);
		address += 2;
		return this;
	}
	public ReturnStack<T> put(char v) {
		u.putChar(address, v);
		address += 2;
		return this;
	}
	public ReturnStack<T> put(int v) {
		u.putInt(address, v);
		address += 4;
		return this;
	}
	public ReturnStack<T> put(float v) {
		u.putFloat(address, v);
		address += 4;
		return this;
	}

	public ReturnStack<T> put(long v) {
		u.putLong(address, v);
		address += 8;
		return this;
	}
	public ReturnStack<T> put(double v) {
		u.putDouble(address, v);
		address += 8;
		return this;
	}
	public ReturnStack<T> put(Object v) {
		objects.add(v);
		return this;
	}

	public int getI() {
		var v = u.getInt(address);
		address += 4;
		return v;
	}
	public long getJ() {
		var v = u.getLong(address);
		address += 8;
		return v;
	}
	public Object getL() {return objects.set(index++, null);}
}