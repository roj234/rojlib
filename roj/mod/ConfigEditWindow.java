package roj.mod;

import roj.collect.MyHashSet;
import roj.text.TextUtil;
import roj.ui.GUIUtil;

import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.mod.Shared.BASE;
import static roj.mod.Shared.project;

/**
 * @author Roj234
 * @since 2023/1/7 0007 0:55
 */
final class ConfigEditWindow extends JFrame {
	static final Matcher WINDOWS_FILE_NAME = Pattern.compile("^[^<>|\"\\\\/:]+$").matcher("");
	static final Matcher MOD_VERSION = Pattern.compile("^(\\d+\\.?)+?([-_][a-zA-Z0-9]+)?$").matcher("");

	private static ConfigEditWindow opened;

	private final Project p;
	private final JTextField verInp, atInput, charsetInp, dependInp;
	private JFrame parent;

	static void open(Project p, JFrame win) {
		ConfigEditWindow prev = opened;
		if (prev != null) {
			prev.dispose();
			synchronized (prev) {
				prev.notifyAll();
			}
		}

		ConfigEditWindow cfg = opened = new ConfigEditWindow(p);
		cfg.parent = win;

		synchronized (cfg) {
			try {
				cfg.wait();
			} catch (InterruptedException ignored) {}
		}
	}

	private ConfigEditWindow(Project p) {
		super(p.getFile().getName());
		this.p = p;

		GUIUtil.setLogo(this, "FMD_logo.png");

		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JPanel panel = new JPanel();

		JLabel verLab = new JLabel("mod版本:");
		verLab.setBounds(10, 10, 80, 25);
		panel.add(verLab);

		verInp = new JTextField(p.version, 20);
		verInp.setBounds(100, 10, 165, 25);
		panel.add(verInp);

		JLabel atLab = new JLabel("AT配置文件名称(?):");
		atLab.setBounds(10, 40, 80, 25);
		atLab.setToolTipText("AccessTransformer配置(通常为xxx_at.cfg)\n不使用留空");
		panel.add(atLab);

		atInput = new JTextField(p.atName, 20);
		atInput.setBounds(100, 40, 165, 25);
		panel.add(atInput);

		JLabel charsetLab = new JLabel("代码字符集:");
		charsetLab.setBounds(10, 70, 80, 25);
		panel.add(charsetLab);

		charsetInp = new JTextField(p.charset.name(), 20);
		charsetInp.setBounds(100, 70, 165, 25);
		panel.add(charsetInp);

		JLabel dependLab = new JLabel("前置:");
		dependLab.setBounds(10, 100, 80, 25);
		dependLab.setToolTipText("多个用竖线(|)隔开");
		panel.add(dependLab);

		dependInp = new JTextField(p.dependencyString(), 20);
		dependInp.setBounds(100, 100, 165, 25);
		panel.add(dependInp);

		JButton save = new JButton("保存");
		save.setBounds(40, 130, 60, 25);
		save.addActionListener(this::save);
		panel.add(save);

		JButton close = new JButton("关闭");
		close.setBounds(180, 130, 60, 25);
		close.addActionListener((e) -> {
			if (parent != null) {
				System.exit(0);
			} else {
				ConfigEditWindow.this.dispose();
				synchronized (this) {
					notifyAll();
				}
			}
		});
		panel.add(close);

		panel.setLayout(null);
		panel.setSize(300, 190);
		add(panel);

		pack();
		setVisible(true);
		setResizable(false);
		setSize(300, 190);
		validate();
	}

	private void save(ActionEvent event) {
		Set<String> error = new MyHashSet<>();

		if (atInput.getText().length() > 0 && !WINDOWS_FILE_NAME.reset(atInput.getText()).matches()) {
			error.add("AT文件名不合法");
		} else {
			p.atName = atInput.getText();
		}

		try {
			p.charset = Charset.forName(charsetInp.getText());
		} catch (Throwable e1) {
			error.add("编码不支持");
		}

		if (!MOD_VERSION.reset(verInp.getText()).matches()) {
			error.add("版本不合法");
		} else {
			p.version = verInp.getText();
		}

		p.setDependencyString(dependInp.getText());

		if (error.isEmpty()) {
			p.save();

			File path = new File(BASE, "projects/"+p.name+"/java");
			if (!path.isDirectory()) path.mkdirs();

			if (project != null && p.getFile().equals(project.getFile())) {
				project.reload();
			}

			dispose();
			if (parent != null) parent.setVisible(true);

			synchronized (this) {
				notifyAll();
			}
			opened = null;
		} else {
			JOptionPane.showMessageDialog(this, TextUtil.deepToString(error), "错误", JOptionPane.WARNING_MESSAGE);
		}
	}
}
