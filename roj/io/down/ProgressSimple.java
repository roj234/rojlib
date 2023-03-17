package roj.io.down;

import roj.ui.CmdUtil;
import roj.ui.EasyProgressBar;

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

	protected EasyProgressBar bar;
	boolean kill;

	public EasyProgressBar bar() {
		return bar;
	}

	public ProgressSimple() {
		bar = new EasyProgressBar("下载进度");
		bar.setUnit("B");
		bar.setBarInterval(500);
		bar.setDataInterval(1000);
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
