package roj.io.down;

import roj.ui.EasyProgressBar;
import roj.ui.Terminal;

/**
 * @author Roj234
 * @since 2020/9/13 12:33
 */
public class ProgressSimple implements IProgress {
	public void onFinish(Downloader dn) {
		bar.setName(dn.owner.file.getName());
		bar.end("下载完成");
	}

	protected EasyProgressBar bar;
	boolean kill;

	public EasyProgressBar bar() {return bar;}

	public ProgressSimple() {
		bar = new EasyProgressBar("下载", "B");
	}

	@Override public boolean wasShutdown() {return kill;}
	@Override public void shutdown() {bar.end("下载失败", Terminal.RED);kill = true;}

	@Override public void onJoin(Downloader dn) {bar.addTotal(dn.getTotal());}
	@Override public void onChange(Downloader dn) {bar.increment(dn.getDelta());}
}