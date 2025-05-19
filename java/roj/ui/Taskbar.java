package roj.ui;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Range;
import roj.RojLib;
import roj.util.NativeException;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Windows任务栏进度条ITaskbarList3接口
 *
 * <p>适用于Windows 7及以上操作系统。
 * <p>注意：退出程序前应清除进度。
 *
 * @author Roj234
 * @since 2025/05/28 02:02
 * @see <a href="https://docs.microsoft.com/en-us/windows/win32/api/shobjidl_core/nn-shobjidl_core-itaskbarlist3">ITaskbarList3接口文档</a>
 */
public class Taskbar {
	/**
	 * 无进度条状态（隐藏进度条）
	 * @see #setProgressType(long, int)
	 */
	public static final int TBPF_OFF = 0,
	/**
	 * 不确定进度模式（显示滚动动画）
	 * @see #setProgressType(long, int)
	 */
	TBPF_INDETERMINATE = 1,

	/**
	 * 正常进度模式（绿色）
	 * @see #setProgressType(long, int)
	 */
	TBPF_NORMAL = 2,

	/**
	 * 错误状态（红色）
	 * @see #setProgressType(long, int)
	 */
	TBPF_ERROR = 3,

	/**
	 * 暂停状态（黄色）
	 * @see #setProgressType(long, int)
	 */
	TBPF_PAUSED = 4;

	private static final AtomicReference<Object> lock = new AtomicReference<>();
	private static long handle;

	private static int progressType = TBPF_OFF;

	static {
		try {
			RojLib.fastJni();
			initNatives();
			handle = NativeVT.GetConsoleWindow();
		} catch (UnsatisfiedLinkError|NativeException e) {
			e.printStackTrace();
		}
	}
	private static native void initNatives() throws NativeException;

	public static void setWindowHandle(long windowHandle) {
		if (handle == 0 || windowHandle == 0) throw new NativeException("handle == 0");
		handle = windowHandle;
	}

	/**
	 * 尝试获取任务栏控制锁
	 * <p>
	 * 通过原子锁机制确保同一时刻只有一个对象能修改任务栏状态。
	 * 当锁未被占用时，调用此方法的对象将获得控制权。
	 * </p>
	 *
	 * @param o 请求锁的对象
	 * @return true-成功获得锁; false-平台不支持,句柄未初始化或锁已被占用
	 */
	public static boolean acquire(Object o) {return handle != 0 && (lock.get() == o || lock.compareAndSet(null, o));}
	public static boolean release(Object o) {return handle != 0 && lock.compareAndSet(o, null);}

	/**
	 * 设置任务栏进度条显示模式
	 * @param type  进度类型
	 * @throws IllegalArgumentException 当传入无效类型参数时抛出
	 * @throws NativeException 当本地方法调用失败时抛出
	 * @apiNote 程序退出前必须调用本方法设置TBPF_OFF，
	 *          否则可能导致任务栏状态残留
	 */
	public static void setProgressType(@MagicConstant(intValues = {TBPF_OFF,TBPF_INDETERMINATE,TBPF_NORMAL,TBPF_ERROR,TBPF_PAUSED}) int type) {
		if (progressType != type) {
			progressType = type;
			setProgressType(handle, type == TBPF_OFF ? 0 : 1 << (type-1));
		}
	}

	/**
	 * 设置进度条当前值
	 * <p>
	 * 根据进度值自动切换进度模式：
	 * <ul>
	 *   <li>当total ≤ 0时：切换为{@link #TBPF_INDETERMINATE}模式</li>
	 *   <li>其他情况：切换为{@link #TBPF_NORMAL}模式并更新进度</li>
	 * </ul>
	 *
	 * @param progress 当前进度值（≥0）
	 * @param total    总进度值（≥-1，≤0时触发不确定模式）
	 */
	public static void setProgressValue(@Range(from = 0, to = Long.MAX_VALUE) long progress, @Range(from = -1, to = Long.MAX_VALUE) long total) {
		if (total <= 0) {
			setProgressType(TBPF_INDETERMINATE);
			return;
		} else {
			setProgressType(TBPF_NORMAL);
		}

		setProgressValue(handle, progress, total);
	}

	private static native void setProgressType(long handle, int type);
	private static native void setProgressValue(long handle, long progress, long total);
}