package roj.io.down;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2020/9/13 12:33
 */
public class ProgressMulti extends ProgressSimple {
	protected final List<Downloader> workers = new ArrayList<>();

	@Override
	public synchronized void onFinish(Downloader dn) {
		workers.remove(dn);
		if (workers.isEmpty()) super.onFinish(dn);
	}

	@Override
	public synchronized void onJoin(Downloader dn) {
		workers.add(dn);
		super.onJoin(dn);
	}
}
