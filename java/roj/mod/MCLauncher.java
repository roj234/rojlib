package roj.mod;

import roj.concurrent.task.ITask;
import roj.ui.CLIUtil;

import javax.swing.*;
import java.awt.event.*;

/**
 * Roj234's Minecraft Launcher (Obsoleted)
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class MCLauncher {
	public static void clearLogs(ActionEvent e) {
		if (checkMCRun()) return;

		CLIUtil.info("清除了没啥用的日志文件!");
	}

	// region check
	static RunMinecraftTask task;

	private static boolean checkMCRun() {
		if (task != null && !task.isDone()) {
			int n = JOptionPane.showConfirmDialog(null, "MC没有退出,是否结束进程?", "询问", JOptionPane.YES_NO_OPTION);
			if (n == JOptionPane.YES_OPTION) task.cancel();
			else return true;
		}
		return false;
	}
	// endregion

	static final class RunMinecraftTask implements ITask {
		boolean log;
		boolean run;
		Process process;

		RunMinecraftTask(boolean log) {
			this.log = log;
		}

		@Override
		public void execute() throws Exception {

			run = true;
		}

		public boolean isDone() { return run; }

		@Override
		public boolean isCancelled() { return false; }
		@Override
		public boolean cancel() {
			if (run || process == null) return false;
			process.destroyForcibly();
			process = null;
			return true;
		}
	}
}