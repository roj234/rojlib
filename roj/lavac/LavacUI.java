/*
 * Created by JFormDesigner on Fri Sep 15 12:21:08 CST 2023
 */

package roj.lavac;

import roj.collect.SimpleList;
import roj.concurrent.timing.ScheduledTask;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.lavac.parser.CompileContext;
import roj.lavac.parser.CompileUnit;
import roj.lavac.util.LibraryZipFile;
import roj.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 */
public class LavacUI extends JFrame {
	CompileContext context = new CompileContext();
	List<CompileUnit> javas = new SimpleList<>();

	public static void main(String[] args) throws Exception {
		UIUtil.systemLook();
		LavacUI f = new LavacUI();

		f.setDefaultCloseOperation(EXIT_ON_CLOSE);
		f.show();
	}

	private ScheduledTask task;
	public LavacUI() {
		initComponents();

		uiLoadLib.addActionListener((e) -> {
			context = new CompileContext();
			String text = uiLibs.getText();
			IOUtil.findAllFiles(new File(text), file -> {
				if (file.getName().endsWith("jar") || file.getName().endsWith("zip")) {
					try {
						context.addLibrary(new LibraryZipFile(file));
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
				return false;
			});
		});
		uiLoadJava.addActionListener((e) -> {
			javas.clear();
			List<File> files = IOUtil.findAllFiles(new File(uiJava.getText()), file -> file.getName().endsWith(".java"));
			for (File f : files) {
				try {
					javas.add(new CompileUnit(f.getAbsolutePath(), new FileInputStream(f), context));
				} catch (FileNotFoundException ex) {
					ex.printStackTrace();
				}
			}
		});
		button1.addActionListener((e) -> {
			int i = 0;
			try {
				for (i = javas.size() - 1; i >= 0; i--) {
					if (!javas.get(i).S0_Init())
						// special process for package-info or empty java
						javas.remove(i);
				}
				for (i = 0; i < javas.size(); i++) {
					javas.get(i).S1_Struct();
				}
			} catch (IOException | ParseException ex) {
				System.out.println("file="+javas.get(i).getFilePath());
				ex.printStackTrace();
			}

		});
		button2.addActionListener((e) -> {
			try {
				for (int i = 0; i < javas.size(); i++) {
					javas.get(i).S2_Parse();
				}
			} catch (ParseException ex) {
				ex.printStackTrace();
			}

		});
		button3.addActionListener((e) -> {
			try {
				for (int i = 0; i < javas.size(); i++) {
					javas.get(i).S3_Code();
				}
			} catch (ParseException ex) {
				ex.printStackTrace();
			}

		});
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		button1 = new JButton();
		uiJava = new JTextField();
		button2 = new JButton();
		button3 = new JButton();
		button4 = new JButton();
		spinner1 = new JSpinner();
		scrollPane1 = new JScrollPane();
		uiLibs = new JTextArea();
		uiLoadLib = new JButton();
		uiLoadJava = new JButton();
		scrollPane2 = new JScrollPane();
		uiExpr = new JTextArea();

		//======== this ========
		setTitle("Lavac");
		Container contentPane = getContentPane();
		contentPane.setLayout(null);

		//---- button1 ----
		button1.setText("1");
		contentPane.add(button1);
		button1.setBounds(new Rectangle(new Point(90, 115), button1.getPreferredSize()));

		//---- uiJava ----
		uiJava.setText("D:\\mc\\FMD-1.5.2\\projects\\implib\\java");
		contentPane.add(uiJava);
		uiJava.setBounds(35, 40, 275, uiJava.getPreferredSize().height);

		//---- button2 ----
		button2.setText("2");
		contentPane.add(button2);
		button2.setBounds(new Rectangle(new Point(155, 115), button2.getPreferredSize()));

		//---- button3 ----
		button3.setText("3");
		contentPane.add(button3);
		button3.setBounds(new Rectangle(new Point(215, 115), button3.getPreferredSize()));

		//---- button4 ----
		button4.setText("4");
		contentPane.add(button4);
		button4.setBounds(new Rectangle(new Point(265, 115), button4.getPreferredSize()));
		contentPane.add(spinner1);
		spinner1.setBounds(110, 200, 95, spinner1.getPreferredSize().height);

		//======== scrollPane1 ========
		{

			//---- uiLibs ----
			uiLibs.setText("D:\\mc\\FMD-1.5.2\\class");
			scrollPane1.setViewportView(uiLibs);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(290, 265, 270, 140);

		//---- uiLoadLib ----
		uiLoadLib.setText("load");
		contentPane.add(uiLoadLib);
		uiLoadLib.setBounds(new Rectangle(new Point(290, 235), uiLoadLib.getPreferredSize()));

		//---- uiLoadJava ----
		uiLoadJava.setText("load");
		contentPane.add(uiLoadJava);
		uiLoadJava.setBounds(new Rectangle(new Point(35, 70), uiLoadJava.getPreferredSize()));

		//======== scrollPane2 ========
		{

			//---- uiExpr ----
			uiExpr.setText("expression");
			scrollPane2.setViewportView(uiExpr);
		}
		contentPane.add(scrollPane2);
		scrollPane2.setBounds(350, 50, 170, 185);

		contentPane.setPreferredSize(new Dimension(570, 445));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JButton button1;
	private JTextField uiJava;
	private JButton button2;
	private JButton button3;
	private JButton button4;
	private JSpinner spinner1;
	private JScrollPane scrollPane1;
	private JTextArea uiLibs;
	private JButton uiLoadLib;
	private JButton uiLoadJava;
	private JScrollPane scrollPane2;
	private JTextArea uiExpr;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
