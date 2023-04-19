package roj.ui;

import roj.collect.SimpleList;
import roj.text.CharList;

import java.io.PrintStream;

/**
 * @author Roj234
 * @since 2023/1/19 0019 8:08
 */
public abstract class BottomLine implements AutoCloseable {
	protected final CharList batch = new CharList();

	private void insert() {
		if (sysOutDeg == null) return;
		synchronized (sysOutDeg) {
			if (active.isEmpty()) {
				System.setOut(sysOutDeg);
				System.setErr(sysOutDeg);
				CmdUtil.out = sysOutDeg;
			} else if (active.size() > 64) return;

			active.add(this);
			sysOut.println();
		}
	}

	protected final void render(CharList b) {
		if (sysOutDeg == null) return;
		synchronized (sysOutDeg) {
			int pos = active.indexOf(this);
			if (pos < 0) {
				if (active.size() > 64) return;

				insert();
				pos = active.indexOf(this);
			}

			CmdUtil.cursorUpCol0(active.size()-pos);
			sysOut.println(b);
			int j = active.size()-pos-1;
			if (j > 0) CmdUtil.cursorDown(j);
		}
	}

	public final void dispose() {
		dispose(false);
	}
	public final void dispose(boolean clearText) {
		if (sysOutDeg == null) return;
		synchronized (sysOutDeg) {
			int pos = active.indexOf(this);
			if (pos < 0) return;

			active.remove(pos);
			if (active.isEmpty()) {
				System.setOut(sysOut);
				System.setErr(sysErr);
				CmdUtil.out = sysOut;
			}

			batchClear();
			if (!clearText) {
				CmdUtil.cursorUpCol0(1);
				CmdUtil.clearLine();
				sysOut.println(batch);
			}

			batchRender();
		}
		doDispose();
	}

	protected void doDispose() {}

	@Override
	public void close() {
		dispose(false);
	}

	private static void batchClear() {
		for (int i = 0; i < active.size(); i++) {
			CmdUtil.cursorUp(1);
			CmdUtil.clearLine();
		}
	}
	private static void batchRender() {
		for (int i = 0; i < active.size(); i++) {
			sysOut.println(active.get(i).batch);
		}
	}

	protected static final PrintStream sysOut, sysErr;
	protected static DelegatedPrintStream sysOutDeg;
	private static final SimpleList<BottomLine> active = new SimpleList<>();

	static {
		sysOut = CmdUtil.out;
		sysErr = System.err;
		if (CmdUtil.enabled()) {
			sysOutDeg = new DelegatedPrintStream(1024) {
				@Override
				protected void newLine() {
					batchClear();

					sysOut.println(sb);
					sb.clear();

					batchRender();
				}
			};
		} else {
			System.err.println("Warning: JANSI library is not loaded!");
		}
	}
}
