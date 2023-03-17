package roj.mapper.util;

import roj.asm.util.Context;
import roj.concurrent.task.AsyncTask;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2021/5/30 19:59
 */
public final class Worker extends AsyncTask<Void> {
	private final List<Context> files;
	private final Consumer<Context> action;

	public Worker(List<Context> files, Consumer<Context> action) {
		this.files = files;
		this.action = action;
	}

	@Override
	protected Void invoke() throws Exception {
		List<Context> f = this.files;
		Consumer<Context> c = this.action;
		for (int i = 0; i < f.size(); i++) {
			try {
				c.accept(f.get(i));
			} catch (Throwable e) {
				throw new RuntimeException(f.get(i).getFileName(), e);
			}
		}
		return null;
	}
}
