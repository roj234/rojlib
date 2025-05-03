package roj.asmx.event;

/**
 * @author Roj234
 * @since 2024/3/21 11:51
 */
public abstract class Event {
	public void cancel() { throw new UnsupportedOperationException(getClass().getName()+"不可取消"); }
	public boolean isCancellable() { return false; }
	public boolean isCanceled() { return false; }

	public String getGenericType() { return "L"+getGenericValueType().getName().replace('.', '/')+";"; }
	public Class<?> getGenericValueType() { return void.class; }
}