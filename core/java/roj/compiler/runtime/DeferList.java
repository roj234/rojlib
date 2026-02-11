package roj.compiler.runtime;

/**
 * @author Roj234
 * @since 2026/02/07 10:22
 */
public final class DeferList {
	public static final String CLASS_NAME = "roj/compiler/runtime/DeferList";

	private static final ThreadLocal<DeferList> DEFER_LIST = ThreadLocal.withInitial(DeferList::new);
	public static DeferList get() {return DEFER_LIST.get();}

	private Object[] list;
	private int size;

	private DeferList() {list = new Object[5];}

	public void ensureCapacity(int space) {
		int remainSize = list.length - size;
		if (remainSize < space) {
			int newCap = size + space;

			Object[] newList = new Object[newCap];
			if (size > 0) System.arraycopy(list, 0, newList, 0, size);
			list = newList;
		}
	}

	public int size() { return size; }

	public void add(Object r) {
		ensureCapacity(1);
		list[size++] = r;
	}

	public static void defer(Throwable local, DeferList instance, int prevSize) {
		for (int i = instance.size - 1; i >= prevSize; i--) {
			Object o = instance.list[i];
			instance.list[i] = null;
			try {
				if (o instanceof Runnable r) r.run();
				else ((AutoCloseable) o).close();
			} catch (Throwable e) {
				if (local == null) local = e;
				else local.addSuppressed(e);
			}
		}
		instance.size = prevSize;

		if (local != null)
			RtUtil.athrow(local);
	}
}