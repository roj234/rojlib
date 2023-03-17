package roj.manage;

import roj.text.TextUtil;

import java.lang.management.*;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2020/8/10 22:59
 */
public class SystemInfo {
	static MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

	public static void displaySystemInfo() {
		System.out.println(getMemoryUsage());

        /*int i = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("垃圾回收器" + (i++) + getGCInfo(bean));
        }*/

		System.out.println("平均CPU: " + ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());

		//final ClassLoadingMXBean bean1 = ManagementFactory.getClassLoadingMXBean();
		//System.out.println("类加载统计: All: " + bean1.getTotalLoadedClassCount() + " Loaded: " + bean1.getLoadedClassCount());

		//final CompilationMXBean bean = ManagementFactory.getCompilationMXBean();
		//System.out.println("JIT: " + bean.getName() + " Time: " + bean.getTotalCompilationTime());
	}

	private static StringBuilder getGCInfo(GarbageCollectorMXBean bean) {
		return new StringBuilder().append("Name: ")
								  .append(bean.getName())
								  .append(" Count: ")
								  .append(bean.getCollectionCount())
								  .append(" Time: ")
								  .append(bean.getCollectionTime())
								  .append(" Pools: ")
								  .append(Arrays.toString(bean.getMemoryPoolNames()));
	}

	public static StringBuilder getThreadInfo() {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		return new StringBuilder().append("Started: ")
								  .append(bean.getTotalStartedThreadCount())
								  .append(" Daemon: ")
								  .append(bean.getDaemonThreadCount())
								  .append(" Inactive: ")
								  .append(bean.getPeakThreadCount())
								  .append(" Running: ")
								  .append(bean.getThreadCount());

	}

	public static long getMemoryUsedO() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	public static long getMemoryUsed() {
		return memory.getHeapMemoryUsage().getUsed() + memory.getNonHeapMemoryUsage().getUsed();
	}

	public static StringBuilder getMemoryUsage() {
		return appendMemoryData(appendMemoryData(new StringBuilder().append("堆内存: \n"), memory.getHeapMemoryUsage()).append("非堆内存: \n"), memory.getNonHeapMemoryUsage());
	}

	private static StringBuilder appendMemoryData(StringBuilder sb, MemoryUsage usage) {
		return sb.append("  * ")
				 .append("初始值: ")
				 .append(TextUtil.scaledNumber(usage.getInit()))
				 .append('B')
				 .append('\n')
				 .append("  * ")
				 .append("已使用: ")
				 .append(TextUtil.scaledNumber(usage.getUsed()))
				 .append('B')
				 .append('\n')
				 .append("  * ")
				 .append("已提交: ")
				 .append(TextUtil.scaledNumber(usage.getCommitted()))
				 .append('B')
				 .append('\n')
				 .append("  * ")
				 .append("最大值: ")
				 .append(TextUtil.scaledNumber(usage.getMax()))
				 .append('B')
				 .append('\n');
	}
}
