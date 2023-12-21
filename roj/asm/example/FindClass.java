/*
 * Created by JFormDesigner on Fri Dec 22 21:56:16 CST 2023
 */

package roj.asm.example;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.CstRef;
import roj.asm.tree.Attributed;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.ui.GUIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * @author Roj234
 */
public class FindClass extends JFrame {
	public static void main(String[] args) {
		GUIUtil.systemLook();
		FindClass f = new FindClass();

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.show();
	}

	private void search() {
		Pattern p = uiSearch.getText().startsWith("!") ? Pattern.compile(uiSearch.getText().substring(1)) : Pattern.compile(uiSearch.getText(), Pattern.LITERAL);
		Predicate<CharSequence> filter = (id) -> p.matcher(id).find();

		MyHashSet<Attributed> out = new MyHashSet<>();
		if (uiSerClass.isSelected()) {
			for (ConstantData data : ref) {
				if (filter.test(data.name)) {
					out.add(data);
				}
				if (!uiDeclOnly.isSelected()) {
					for (Constant c : data.cp.array()) {
						if (c.type() == Constant.CLASS) {
							if (filter.test(c.getEasyCompareValue())) {
								out.add(data);
								break;
							}
						}
					}
				}
			}
		}
		CharList sb = IOUtil.getSharedCharBuf();
		if (uiSerNode.isSelected()) {
			for (ConstantData data : ref) {
				for (MethodNode n : data.methods) {
					sb.clear();
					sb.append(data.name).append('.').append(n.name()).append(n.rawDesc());
					if (filter.test(sb)) {
						out.add(data);
						break;
					}
				}
				for (FieldNode n : data.fields) {
					sb.clear();
					sb.append(data.name).append('.').append(n.name()).append(' ').append(n.rawDesc());
					if (filter.test(sb)) {
						out.add(data);
						break;
					}
				}
				if (uiDeclOnly.isSelected()) continue;

				for (Constant c : data.cp.array()) {
					if (c instanceof CstRef) {
						CstRef ref = (CstRef) c;
						String s = ref.descType();
						sb.clear();
						sb.append(ref.className()).append('.').append(ref.descName());
						if (!s.startsWith("(")) sb.append(' ');
						sb.append(s);
						if (filter.test(sb)) {
							out.add(data);
							break;
						}
					}
				}
			}
		}
		if (uiSerString.isSelected()) {
			for (ConstantData data : ref) {
				for (Constant c : data.cp.array()) {
					if (c.type() == Constant.STRING) {
						if (filter.test(c.getEasyCompareValue())) {
							out.add(data);
							break;
						}
					}
				}
			}
		}
		if (uiSerConstant.isSelected()) {
			for (ConstantData data : ref) {
				for (Constant c : data.cp.array()) {
					if (c.type() >= 3 && c.type() <= 6) {
						if (filter.test(c.getEasyCompareValue())) {
							out.add(data);
							break;
						}
					}
				}
			}
		}

		sb.clear();
		for (Attributed node : out) {
			sb.append(((ConstantData)node).name).append(' ');
		}
		uiResult.setText(sb.toString());
	}

	private void open(File file) {
		try (ZipArchive za = new ZipArchive(file)) {
			for (ZEntry value : za.getEntries().values()) {
				if (value.getName().toLowerCase().endsWith(".class")) {
					ConstantData data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(za.getInput(value)).toByteArray());
					ref.add(data);
				}
			}
			System.out.println("loaded "+ref.size()+" class");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private final SimpleList<ConstantData> ref = new SimpleList<>();
	public FindClass() {
		initComponents();
		GUIUtil.dropFilePath(this, this::open, false);
		uiOpen.addActionListener(e -> open(GUIUtil.fileLoadFrom("jar file", this)));
		ActionListener ser = e -> search();
		uiDeclOnly.addActionListener(ser);
		uiSerClass.addActionListener(ser);
		uiSerNode.addActionListener(ser);
		uiSerString.addActionListener(ser);
		uiSerConstant.addActionListener(ser);
		uiSearch.addKeyListener(new KeyAdapter() {
			public void keyTyped(KeyEvent e) { search(); }
		});
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		uiSearch = new JTextField();
		scrollPane1 = new JScrollPane();
		uiResult = new JTextPane();
		label1 = new JLabel();
		uiDeclOnly = new JCheckBox();
		uiSerClass = new JCheckBox();
		uiSerNode = new JCheckBox();
		uiSerString = new JCheckBox();
		uiSerConstant = new JCheckBox();
		checkBox5 = new JCheckBox();
		checkBox6 = new JCheckBox();
		uiOpen = new JButton();
		label2 = new JLabel();

		//======== this ========
		setTitle("FindClass");
		Container contentPane = getContentPane();
		contentPane.setLayout(null);
		contentPane.add(uiSearch);
		uiSearch.setBounds(5, 25, 280, uiSearch.getPreferredSize().height);

		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(uiResult);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(10, 165, 375, 235);

		//---- label1 ----
		label1.setText("search (regexp IF START WITH !)");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(5, 5), label1.getPreferredSize()));

		//---- uiDeclOnly ----
		uiDeclOnly.setText("Declaration only");
		contentPane.add(uiDeclOnly);
		uiDeclOnly.setBounds(new Rectangle(new Point(255, 80), uiDeclOnly.getPreferredSize()));

		//---- uiSerClass ----
		uiSerClass.setText("class");
		contentPane.add(uiSerClass);
		uiSerClass.setBounds(new Rectangle(new Point(10, 55), uiSerClass.getPreferredSize()));

		//---- uiSerNode ----
		uiSerNode.setText("element (field or method)");
		contentPane.add(uiSerNode);
		uiSerNode.setBounds(new Rectangle(new Point(65, 55), uiSerNode.getPreferredSize()));

		//---- uiSerString ----
		uiSerString.setText("string");
		contentPane.add(uiSerString);
		uiSerString.setBounds(new Rectangle(new Point(10, 80), uiSerString.getPreferredSize()));

		//---- uiSerConstant ----
		uiSerConstant.setText("constant (number)");
		contentPane.add(uiSerConstant);
		uiSerConstant.setBounds(new Rectangle(new Point(70, 80), uiSerConstant.getPreferredSize()));

		//---- checkBox5 ----
		checkBox5.setText("text");
		contentPane.add(checkBox5);
		checkBox5.setBounds(new Rectangle(new Point(10, 105), checkBox5.getPreferredSize()));

		//---- checkBox6 ----
		checkBox6.setText("text");
		contentPane.add(checkBox6);
		checkBox6.setBounds(new Rectangle(new Point(65, 105), checkBox6.getPreferredSize()));

		//---- uiOpen ----
		uiOpen.setText("Open");
		contentPane.add(uiOpen);
		uiOpen.setBounds(new Rectangle(new Point(305, 20), uiOpen.getPreferredSize()));

		//---- label2 ----
		label2.setText("found");
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(10, 150), label2.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(400, 440));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JTextField uiSearch;
	private JScrollPane scrollPane1;
	private JTextPane uiResult;
	private JLabel label1;
	private JCheckBox uiDeclOnly;
	private JCheckBox uiSerClass;
	private JCheckBox uiSerNode;
	private JCheckBox uiSerString;
	private JCheckBox uiSerConstant;
	private JCheckBox checkBox5;
	private JCheckBox checkBox6;
	private JButton uiOpen;
	private JLabel label2;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
