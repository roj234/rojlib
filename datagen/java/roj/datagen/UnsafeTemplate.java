package roj.datagen;

import java.lang.reflect.Field;

/**
 * 告诉我有哪些东西可用
 * @author Roj234
 * @since 2025/9/17 17:26
 */
interface UnsafeTemplate {
	int addressSize();

	long objectFieldOffset(Field f);
	long staticFieldOffset(Field f);
	Object staticFieldBase(Field f);

	int arrayBaseOffset(Class<?> arrayClass);
	int arrayIndexScale(Class<?> arrayClass);

	Object allocateUninitializedArray(Class<?> componentType, int length);
	Object allocateInstance(Class<?> cls) throws InstantiationException;

	void ensureClassInitialized(Class<?> c);
	//region 单地址模式 deprecated
	byte getByte(long address);
	char getChar(long address);
	int getInt(long address);
	long getLong(long address);
	float getFloat(long address);
	double getDouble(long address);
	long getAddress(long address);

	void putByte(long address, byte x);
	void putShort(long address, short x);
	void putChar(long address, char x);
	void putInt(long address, int x);
	void putLong(long address, long x);
	void putFloat(long address, float x);
	void putDouble(long address, double x);
	void putAddress(long address, long x);
	//endregion
	//region 内存读写 plain
	boolean getBoolean(Object o, long offset);
	byte getByte(Object o, long offset);
	short getShort(Object o, long offset);
	char getChar(Object o, long offset);
	int getInt(Object o, long offset);
	long getLong(Object o, long offset);
	float getFloat(Object o, long offset);
	double getDouble(Object o, long offset);
	Object getReference(Object o, long offset);

	int get16UL(Object o, long offset);
	int get32UL(Object o, long offset);
	long get64UL(Object o, long offset);

	int get16UB(Object o, long offset);
	int get32UB(Object o, long offset);
	long get64UB(Object o, long offset);

	void put16UL(Object o, long offset, int x);
	void put32UL(Object o, long offset, int x);
	void put64UL(Object o, long offset, long x);

	void put16UB(Object o, long offset, int x);
	void put32UB(Object o, long offset, int x);
	void put64UB(Object o, long offset, long x);

	void putBoolean(Object o, long offset, boolean x);
	void putByte(Object o, long offset, byte x);
	void putShort(Object o, long offset, short x);
	void putChar(Object o, long offset, char x);
	void putInt(Object o, long offset, int x);
	void putLong(Object o, long offset, long x);
	void putFloat(Object o, long offset, float x);
	void putDouble(Object o, long offset, double x);
	void putReference(Object o, long offset, Object x);
	//endregion
	//region 内存分配
	long allocateMemory(long bytes);
	long reallocateMemory(long address, long bytes);
	void setMemory(Object o, long offset, long bytes, byte value);
	void setMemory(long address, long bytes, byte value);
	void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes);
	void copyMemory(long srcAddress, long destAddress, long bytes);
	void freeMemory(long address);
	//endregion
	//region 内存读写 volatile acquire release opaque
	int getIntVolatile(Object o, long offset);
	Object getReferenceVolatile(Object o, long offset);
	void putReferenceVolatile(Object o, long offset, Object x);
	boolean compareAndSetInt(Object o, long offset, int expected, int x);
	boolean compareAndSetReference(Object o, long offset, Object expected, Object x);
	Object getAndSetReference(Object o, long offset, Object newValue);
	//endregion
	int getLoadAverage(double[] loadavg, int nelems);
}