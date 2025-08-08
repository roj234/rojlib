package roj.io;

import roj.collect.ArrayList;
import roj.config.auto.Optional;
import roj.text.DateTime;
import roj.text.TextUtil;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static roj.text.DateTime.*;

/**
 * 实现基于时间和存储空间的文件自动清理策略。
 *
 * <p>本类提供灵活的文件保留策略，支持两种清理机制：
 * <ol>
 *   <li><b>时间维度保留</b> - 按时间粒度保留最近的文件：
 *     <ul>
 *       <li>秒级保留（{@link #last}）</li>
 *       <li>小时级保留（{@link #hour}）</li>
 *       <li>天级保留（{@link #day}）</li>
 *       <li>周级保留（{@link #week}）</li>
 *       <li>月级保留（{@link #month}）</li>
 *       <li>年级保留（{@link #year}）</li>
 *     </ul>
 *   </li>
 *   <li><b>存储空间限制</b> - 通过 {@link #maxSize} 控制最大磁盘使用量</li>
 * </ol>
 *
 * <p>典型工作流程：
 * <pre>
 * // 初始化策略
 * FlexibleRetiringPolicy policy = new FlexibleRetiringPolicy();
 * policy.directory = new File("/var/log");
 * policy.setMaxSize("10G");  // 设置最大空间
 * policy.hour = 24;          // 保留24小时内文件
 * policy.day = 7;            // 保留7天内文件
 *
 * // 全量清理
 * policy.reload();
 *
 * // 动态添加文件时清理
 * policy.addLatestFile(newFile);
 * </pre>
 *
 * @see #reload()
 * @see #addLatestFile(File)
 * @author Roj234
 * @since 2025/05/25 12:54
 */
@Optional(write = Optional.WriteMode.ALWAYS)
public class FlexibleRetiringPolicy {
	@Optional(read = Optional.ReadMode.REQUIRED)
	private File directory;
	/** 目录允许的最大存储空间（字节） */
	@Optional(read = Optional.ReadMode.REQUIRED)
	public long maxSize = Long.MAX_VALUE;
	/** 保留最近N秒内的所有文件 */
	public int last;
	/** 保留最近N小时内的文件（每小时至少保留1个最新文件） */
	public int hour;
	/** 保留最近N天内的文件（每天至少保留1个最新文件） */
	public int day;
	/** 保留最近N周内的文件（每周至少保留1个最新文件） */
	public int week;
	/** 保留最近N个月内的文件（每月至少保留1个最新文件） */
	public int month;
	/** 保留最近N年内的文件（每年至少保留1个最新文件） */
	public int year;

	private transient List<FileInfo> fileList;
	private transient long remainSize;

	// for serializer
	private FlexibleRetiringPolicy() {}
	public FlexibleRetiringPolicy(File directory) {
		this.directory = directory;
	}

	private static final class FileInfo {
		final String file;
		final long lastModified;
		final long size;

		public FileInfo(File file) {
			this.file = file.getName();
			this.lastModified = file.lastModified();
			this.size = file.length();
		}
	}
	private transient final int[] calendarCache = new int[CORE_FIELD_COUNT], calendarCache2 = new int[CORE_FIELD_COUNT];

	/**
	 * 设置目录最大存储空间限制。
	 * 支持带单位的大小表示（如 "10G", "500M"），自动转换为字节数。
	 *
	 * @param maxSize 存储空间字符串（例如 "1.5G"）
	 */
	public void setMaxSize(String maxSize) {
		this.maxSize = (long) TextUtil.unscaledNumber1024(maxSize);
	}

	/**
	 * 动态添加新文件并立即触发清理。
	 * 将文件加入列表头部（视为最新文件），根据策略删除过期文件，
	 * 同时确保剩余空间不超过 {@link #maxSize} 限制。
	 *
	 * @param file 新添加的文件对象
	 */
	public void addLatestFile(File file) {
		FileInfo info = new FileInfo(file);
		this.fileList.add(0, info);

		List<FileInfo> retired = new ArrayList<>();
		int i = retire(fileList, retired);

		long remainSize = this.remainSize - info.size;
		while (remainSize < 0 && i > 0) {
			remainSize += fileList.get(--i).size;
		}

		deleteFile(retired, fileList, i);
	}

	/**
	 * 重新加载目录文件并执行清理。
	 * 扫描目录下所有文件，按最后修改时间倒序排序后，
	 * 依次应用时间保留规则和空间限制策略，删除不符合条件的文件。
	 */
	public void reload() {
		File[] files = directory.listFiles();
		if (files == null) {
			directory.mkdirs();
			files = new File[0];
		}
		Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

		List<FileInfo> fileList = this.fileList = new ArrayList<>(files.length);
		for (File file : files) fileList.add(new FileInfo(file));

		List<FileInfo> retired = new ArrayList<>();
		int i = retire(fileList, retired);

		int j = 0;
		long maxSize = this.maxSize;
		while (j < i) {
			maxSize -= fileList.get(j).size;
			if (maxSize < 0) break;
			j++;
		}
		this.remainSize = maxSize;

		deleteFile(retired, fileList, j);
	}

	private void deleteFile(List<FileInfo> retired, List<FileInfo> fileList, int start) {
		retired.addAll(fileList.subList(start, fileList.size()));
		for (FileInfo info : retired) {
			new File(directory, info.file).delete();
		}
		fileList.removeAll(retired);
	}

	private int[] copyCalendar() {
		System.arraycopy(calendarCache, 0, calendarCache2, 0, CORE_FIELD_COUNT);
		return calendarCache2;
	}
	private int retire(List<FileInfo> files, List<FileInfo> retired) {
		int i = 0;
		int[] calendar = DateTime.of(System.currentTimeMillis(), calendarCache);

		calendar[MILLISECOND] = 0;
		i = filter(i, files, copyCalendar(), SECOND, 1, last, retired);
		calendar[SECOND] = 0;
		calendar[MINUTE] = 0;
		i = filter(i, files, copyCalendar(), HOUR, 1, hour, retired);
		calendar[MILLISECOND] = -TimeZone.getDefault().getOffset(System.currentTimeMillis());
		calendar[HOUR] = 0;
		i = filter(i, files, copyCalendar(), DAY, 1, day, retired);
		i = filter(i, files, copyCalendar(), DAY, 7, week, retired);
		calendar[DAY] = 1;
		i = filter(i, files, copyCalendar(), MONTH, 1, month, retired);
		calendar[MONTH] = 1;
		i = filter(i, files, calendar, YEAR, 1, year, retired);
		return i;
	}

	private static int filter(int i, List<FileInfo> files, int[] time, int unit, int increment, int limit, List<FileInfo> retired) {
		long periodStart = DateTime.toTimeStamp(DateTime.normalize(time));
		for (int j = 0; j < limit; j++) {
			time[unit] -= increment;
			long periodEnd = DateTime.toTimeStamp(DateTime.normalize(time));

			FileInfo latestFile = null;
			while (i < files.size()) {
				FileInfo file = files.get(i);

				long lastModified = file.lastModified;
				if (lastModified < periodEnd) break;

				i++;

				if (lastModified < periodStart && latestFile == null) latestFile = file;
				else retired.add(file);
			}

			periodStart = periodEnd;
		}

		return i;
	}
}
