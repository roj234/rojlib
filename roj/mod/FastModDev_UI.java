/*
 * Created by JFormDesigner on Thu Sep 14 15:08:27 CST 2023
 */

package roj.mod;

import javax.swing.*;
import java.awt.*;

/**
 * @author Roj234
 */
public class FastModDev_UI extends JFrame {
	public FastModDev_UI() {
		initComponents();
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		JScrollPane scrollPane1 = new JScrollPane();
		textPane1 = new JTextPane();
		uiCompileType = new JComboBox<>();
		uiCompile = new JButton();
		uiIncrement = new JCheckBox();
		uiHotReload = new JCheckBox();
		uiRelease = new JButton();
		uiRefTool = new JButton();
		uiObf = new JButton();
		uiProject = new JButton();
		uiAT = new JButton();
		uiKill = new JButton();
		JLabel label1 = new JLabel();
		JLabel label2 = new JLabel();
		dlgEditProject = new JDialog();
		JLabel label5 = new JLabel();
		textField1 = new JTextField();
		JLabel label6 = new JLabel();
		textField2 = new JTextField();
		textField3 = new JTextField();
		JLabel label7 = new JLabel();
		textField4 = new JTextField();
		JLabel label8 = new JLabel();
		spinner1 = new JSpinner();
		button8 = new JButton();
		JLabel label9 = new JLabel();
		dlgObf = new JDialog();
		JScrollPane scrollPane2 = new JScrollPane();
		uiObfLibPath = new JTextArea();
		uiObfToSrg = new JButton();
		uiObfDoLib = new JCheckBox();
		JLabel label3 = new JLabel();
		uiObfInput = new JTextField();
		JLabel label4 = new JLabel();
		uiObfFromSrg = new JButton();
		uiObfState = new JLabel();
		dlgRefTool = new JDialog();
		uiRefSearch = new JTextField();
		uiRefSearchType = new JComboBox();
		JScrollPane scrollPane3 = new JScrollPane();
		uiRefResult = new JList();
		uiRefCopy = new JCheckBox();
		uiRefCopyType = new JComboBox();

		//======== this ========
		setTitle("FastModDev 2.3.0 By Roj234");
		Container contentPane = getContentPane();
		contentPane.setLayout(null);

		//======== scrollPane1 ========
		{

			//---- textPane1 ----
			textPane1.setText("FMD \u66f4\u5feb\u7684mod\u5f00\u53d1\u73af\u5883 2.1.0 By Roj234 https://www.github.com/roj234/rojlib  \u53ef\u7528\u6307\u4ee4: build, run, project, edit, ref, at, reobf, deobf, gc, reload, auto '");
			scrollPane1.setViewportView(textPane1);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(25, 210, 525, 220);
		contentPane.add(uiCompileType);
		uiCompileType.setBounds(new Rectangle(new Point(140, 40), uiCompileType.getPreferredSize()));

		//---- uiCompile ----
		uiCompile.setText("\u7f16\u8bd1");
		contentPane.add(uiCompile);
		uiCompile.setBounds(new Rectangle(new Point(80, 40), uiCompile.getPreferredSize()));

		//---- uiIncrement ----
		uiIncrement.setText("\u81ea\u52a8\u589e\u91cf");
		contentPane.add(uiIncrement);
		uiIncrement.setBounds(new Rectangle(new Point(200, 40), uiIncrement.getPreferredSize()));

		//---- uiHotReload ----
		uiHotReload.setText("\u70ed\u91cd\u8f7d");
		contentPane.add(uiHotReload);
		uiHotReload.setBounds(new Rectangle(new Point(270, 40), uiHotReload.getPreferredSize()));

		//---- uiRelease ----
		uiRelease.setText("\u91ca\u653e\u6587\u4ef6");
		contentPane.add(uiRelease);
		uiRelease.setBounds(new Rectangle(new Point(570, 5), uiRelease.getPreferredSize()));

		//---- uiRefTool ----
		uiRefTool.setText("\u67e5\u6620\u5c04\u8868");
		contentPane.add(uiRefTool);
		uiRefTool.setBounds(new Rectangle(new Point(570, 80), uiRefTool.getPreferredSize()));

		//---- uiObf ----
		uiObf.setText("\u624b\u52a8\u6620\u5c04");
		contentPane.add(uiObf);
		uiObf.setBounds(new Rectangle(new Point(570, 55), uiObf.getPreferredSize()));

		//---- uiProject ----
		uiProject.setText("\u66f4\u6362\u9879\u76ee");
		contentPane.add(uiProject);
		uiProject.setBounds(new Rectangle(new Point(570, 30), uiProject.getPreferredSize()));

		//---- uiAT ----
		uiAT.setText("\u91cd\u65b0AT");
		contentPane.add(uiAT);
		uiAT.setBounds(new Rectangle(new Point(575, 105), uiAT.getPreferredSize()));

		//---- uiKill ----
		uiKill.setText("\u5173\u95ed\u5ba2\u6237\u7aef");
		contentPane.add(uiKill);
		uiKill.setBounds(new Rectangle(new Point(450, 180), uiKill.getPreferredSize()));

		//---- label1 ----
		label1.setText("\u8bf7\u4fdd\u6301\u63a7\u5236\u53f0\u5f00\u542f");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(10, 10), label1.getPreferredSize()));

		//---- label2 ----
		label2.setText("\u6e38\u620f\u65e5\u5fd7");
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(30, 185), label2.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(660, 465));
		pack();
		setLocationRelativeTo(getOwner());

		//======== dlgEditProject ========
		{
			dlgEditProject.setTitle("\u7f16\u8f91\u9879\u76ee");
			Container dlgEditProjectContentPane = dlgEditProject.getContentPane();
			dlgEditProjectContentPane.setLayout(null);

			//---- label5 ----
			label5.setText("\u540d\u79f0");
			dlgEditProjectContentPane.add(label5);
			label5.setBounds(new Rectangle(new Point(10, 8), label5.getPreferredSize()));
			dlgEditProjectContentPane.add(textField1);
			textField1.setBounds(45, 5, 180, textField1.getPreferredSize().height);

			//---- label6 ----
			label6.setText("\u7248\u672c");
			dlgEditProjectContentPane.add(label6);
			label6.setBounds(new Rectangle(new Point(10, 38), label6.getPreferredSize()));
			dlgEditProjectContentPane.add(textField2);
			textField2.setBounds(45, 35, 180, textField2.getPreferredSize().height);
			dlgEditProjectContentPane.add(textField3);
			textField3.setBounds(45, 65, 180, textField3.getPreferredSize().height);

			//---- label7 ----
			label7.setText("\u5b57\u7b26\u96c6");
			dlgEditProjectContentPane.add(label7);
			label7.setBounds(new Rectangle(new Point(5, 68), label7.getPreferredSize()));
			dlgEditProjectContentPane.add(textField4);
			textField4.setBounds(45, 95, 162, textField4.getPreferredSize().height);

			//---- label8 ----
			label8.setText("AT");
			dlgEditProjectContentPane.add(label8);
			label8.setBounds(new Rectangle(new Point(15, 100), label8.getPreferredSize()));
			dlgEditProjectContentPane.add(spinner1);
			spinner1.setBounds(45, 125, 55, spinner1.getPreferredSize().height);

			//---- button8 ----
			button8.setText("\u2026");
			button8.setMargin(new Insets(2, 2, 2, 2));
			dlgEditProjectContentPane.add(button8);
			button8.setBounds(new Rectangle(new Point(205, 94), button8.getPreferredSize()));

			//---- label9 ----
			label9.setText("\u5907\u4efd");
			dlgEditProjectContentPane.add(label9);
			label9.setBounds(new Rectangle(new Point(10, 128), label9.getPreferredSize()));

			dlgEditProjectContentPane.setPreferredSize(new Dimension(235, 160));
			dlgEditProject.pack();
			dlgEditProject.setLocationRelativeTo(dlgEditProject.getOwner());
		}

		//======== dlgObf ========
		{
			dlgObf.setTitle("\u624b\u52a8\u6620\u5c04");
			Container dlgObfContentPane = dlgObf.getContentPane();
			dlgObfContentPane.setLayout(null);

			//======== scrollPane2 ========
			{
				scrollPane2.setViewportView(uiObfLibPath);
			}
			dlgObfContentPane.add(scrollPane2);
			scrollPane2.setBounds(5, 50, 345, 210);

			//---- uiObfToSrg ----
			uiObfToSrg.setText("\u5230SRG");
			uiObfToSrg.setMargin(new Insets(2, 4, 2, 4));
			dlgObfContentPane.add(uiObfToSrg);
			uiObfToSrg.setBounds(new Rectangle(new Point(47, 265), uiObfToSrg.getPreferredSize()));

			//---- uiObfDoLib ----
			uiObfDoLib.setText("\u4ec5\u5904\u7406\u9009\u4e2d\u9879\uff08\u672a\u9009\u4e2d\u7684\u4f5c\u4e3a\u4f9d\u8d56\uff09");
			dlgObfContentPane.add(uiObfDoLib);
			uiObfDoLib.setBounds(new Rectangle(new Point(130, 265), uiObfDoLib.getPreferredSize()));

			//---- label3 ----
			label3.setText("\u8f93\u5165       \u6bcf\u884c\u4e00\u4e2a\u6587\u4ef6");
			dlgObfContentPane.add(label3);
			label3.setBounds(new Rectangle(new Point(5, 30), label3.getPreferredSize()));
			dlgObfContentPane.add(uiObfInput);
			uiObfInput.setBounds(70, 5, 280, uiObfInput.getPreferredSize().height);

			//---- label4 ----
			label4.setText("\u8f93\u51fa\u6587\u4ef6\u5939");
			dlgObfContentPane.add(label4);
			label4.setBounds(new Rectangle(new Point(5, 8), label4.getPreferredSize()));

			//---- uiObfFromSrg ----
			uiObfFromSrg.setText("\u4eceSRG");
			uiObfFromSrg.setMargin(new Insets(2, 4, 2, 4));
			dlgObfContentPane.add(uiObfFromSrg);
			uiObfFromSrg.setBounds(new Rectangle(new Point(5, 265), uiObfFromSrg.getPreferredSize()));
			dlgObfContentPane.add(uiObfState);
			uiObfState.setBounds(new Rectangle(new Point(150, 30), uiObfState.getPreferredSize()));

			dlgObfContentPane.setPreferredSize(new Dimension(355, 295));
			dlgObf.pack();
			dlgObf.setLocationRelativeTo(dlgObf.getOwner());
		}

		//======== dlgRefTool ========
		{
			dlgRefTool.setTitle("\u6620\u5c04\u8868\u67e5\u8be2");
			Container dlgRefToolContentPane = dlgRefTool.getContentPane();
			dlgRefToolContentPane.setLayout(null);
			dlgRefToolContentPane.add(uiRefSearch);
			uiRefSearch.setBounds(5, 5, 325, uiRefSearch.getPreferredSize().height);
			dlgRefToolContentPane.add(uiRefSearchType);
			uiRefSearchType.setBounds(new Rectangle(new Point(329, 5), uiRefSearchType.getPreferredSize()));

			//======== scrollPane3 ========
			{
				scrollPane3.setViewportView(uiRefResult);
			}
			dlgRefToolContentPane.add(scrollPane3);
			scrollPane3.setBounds(5, 30, 385, 410);

			//---- uiRefCopy ----
			uiRefCopy.setText("\u9009\u4e2d\u65f6\u590d\u5236");
			dlgRefToolContentPane.add(uiRefCopy);
			uiRefCopy.setBounds(new Rectangle(new Point(5, 445), uiRefCopy.getPreferredSize()));
			dlgRefToolContentPane.add(uiRefCopyType);
			uiRefCopyType.setBounds(new Rectangle(new Point(90, 447), uiRefCopyType.getPreferredSize()));

			dlgRefToolContentPane.setPreferredSize(new Dimension(400, 475));
			dlgRefTool.pack();
			dlgRefTool.setLocationRelativeTo(dlgRefTool.getOwner());
		}
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JTextPane textPane1;
	private JComboBox<String> uiCompileType;
	private JButton uiCompile;
	private JCheckBox uiIncrement;
	private JCheckBox uiHotReload;
	private JButton uiRelease;
	private JButton uiRefTool;
	private JButton uiObf;
	private JButton uiProject;
	private JButton uiAT;
	private JButton uiKill;
	private JDialog dlgEditProject;
	private JTextField textField1;
	private JTextField textField2;
	private JTextField textField3;
	private JTextField textField4;
	private JSpinner spinner1;
	private JButton button8;
	private JDialog dlgObf;
	private JTextArea uiObfLibPath;
	private JButton uiObfToSrg;
	private JCheckBox uiObfDoLib;
	private JTextField uiObfInput;
	private JButton uiObfFromSrg;
	private JLabel uiObfState;
	private JDialog dlgRefTool;
	private JTextField uiRefSearch;
	private JComboBox uiRefSearchType;
	private JList uiRefResult;
	private JCheckBox uiRefCopy;
	private JComboBox uiRefCopyType;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
