package roj.ui;

import javax.swing.*;

/**
 * @author Roj234
 * @since 2024/1/17 0017 18:37
 */
public class GuiProgressBar extends EasyProgressBar {
	private final JTextArea uiLog;
	private final JProgressBar uiProgress;

	public GuiProgressBar(JTextArea uiLog, JProgressBar uiProgress) {
		super("");
		this.uiLog = uiLog;
		this.uiProgress = uiProgress;
	}

	@Override
	public void updateForce(double percent) {
		if (uiProgress != null) uiProgress.setValue((int) (percent * 10000));
		super.updateForce(percent);
	}

	@Override
	public void setName(String name) {
		if (uiLog != null) uiLog.append(name + "\n");
		super.setName(name);
	}
}