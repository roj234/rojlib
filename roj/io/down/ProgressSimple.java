package roj.io.down;

import roj.ui.CmdUtil;
import roj.ui.ProgressBar;

/**
 * @author Roj234
 * @since 2020/9/13 12:33
 */
public class ProgressSimple implements IProgress {
	public static int sampleInterval = 100;

	public void onFinish(Downloader dn) {
		bar.setName(dn.owner.file.getName());
		bar.end("下载完成");
		bar.dispose();
	}

	protected ProgressBar bar;
	boolean kill;

	public ProgressBar bar() {
		return bar;
	}

	public ProgressSimple() {
		bar = new ProgressBar("下载进度");
		bar.setUnit("B");
	}

	@Override
	public boolean wasShutdown() {
		return kill;
	}

	@Override
	public void shutdown() {
		bar.end("下载失败", CmdUtil.Color.RED);
		bar.dispose();
		kill = true;
	}

	long last;

	@Override
	public void onChange(Downloader dn) {
		long t = System.currentTimeMillis();
		if (t - last < sampleInterval) return;
		last = t;

		double pct = ((double) dn.getDownloaded() / dn.getTotal());
		bar.update(pct, (int) dn.getAverageSpeed());
	}
}
