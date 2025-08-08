/*
 * Created by JFormDesigner on Sat Sep 09 21:07:44 CST 2023
 */

package roj.ci.minecraft;

import roj.archive.zip.ZipFile;
import roj.asm.MemberDescriptor;
import roj.asmx.mapper.Mapper;
import roj.asmx.mapper.Mapping;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.IntSet;
import roj.crypt.CryptoFactory;
import roj.gui.DoubleClickHelper;
import roj.gui.GuiUtil;
import roj.io.IOUtil;
import roj.text.TextReader;
import roj.util.Helpers;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author Roj234
 */
public class MappingUI extends JFrame {
	private static final DefaultListModel<String> previewList = new DefaultListModel<>();

	private static int alertShown;
	private static final DefaultListModel<NamedMapping> mappings = new DefaultListModel<>();
	private static final class NamedMapping {
		String name;
		Mapping mapping;

		@Override
		public String toString() {
			return (name == null ? "无名氏" : name)+" ("+mapping.getClass().getSimpleName() + "), "+mapping.getClassMap().size()+" entries";
		}
	}


	private void uiLoad(ActionEvent e) {
		File input = GuiUtil.fileLoadFrom("选择文件", this);
		if (input == null) return;

		try {
			load(input);
		} catch (Exception e1) {
			Helpers.athrow(e1);
		}
	}
	private void load(File input) throws IOException {
		String name1 = input.getName();
		String ext = IOUtil.extensionName(name1);
		Mapping m;

		zip:
		if (ext.equals("zip")) {
			try (var zf = new ZipFile(input)) {
				var tsrg = zf.getEntry("config.json");
				if (tsrg != null) {
					TSrgMapping m1 = new TSrgMapping();
					m1.readMcpConfig(input, m1.getParamMap(), new ArrayList<>());
					m = m1;
					break zip;
				}
			}

			YarnMapping m1 = new YarnMapping();
			m1.readYarnMap(input, new ArrayList<>(), null, m1.getParamMap());
			m = m1;
		} else if (ext.equals("tsrg")) {
			TSrgMapping m1 = new TSrgMapping();
			m1.readMcpConfig(input, m1.getParamMap(), new ArrayList<>());
			m = m1;
		} else if (ext.equals("tiny")) {
			YarnMapping m1 = new YarnMapping();
			m1.readIntermediaryMap(input.getName(), TextReader.auto(input), new ArrayList<>());
			m = m1;
		} else if (ext.equals("srg")) {
			m = new Mapping();
			m.loadMap(input, false);
		} else if (ext.equals("lzma")) {
			Mapper m1 = new Mapper();
			try (var src = new FileInputStream(input)) {
				m1.loadCache(src, true);
			}
			m = m1;
		} else if (name1.equals("client.txt") || name1.equals("server.txt")) {
			OjngMapping m1 = new OjngMapping();
			m1.readMojangMap(input.getName(), TextReader.auto(input), new ArrayList<>());
			m = m1;
		} else {
			List<NamedMapping> maps = uiMappingList.getSelectedValuesList();
			if (maps.size() != 1) {
				JOptionPane.showMessageDialog(this, "请选择一个映射表作为应用的来源\nMCP csv mapping不能单独使用", "提示", JOptionPane.WARNING_MESSAGE);
				return;
			}
			MCPMapping m1 = new MCPMapping(input, null);
			m1.apply(new ArrayList<>(), maps.get(0).mapping, m = new Mapping());
		}

		NamedMapping name = getNamedMapping();
		name.mapping = m;
		mappings.addElement(name);
	}

	private NamedMapping getNamedMapping() {
		NamedMapping m = new NamedMapping();
		String name = uiName.getText();
		uiName.setText("");
		m.name = name.isEmpty() ? null : name.trim();
		return m;
	}

	private void uiDel(ActionEvent e) {
		for (NamedMapping m : uiMappingList.getSelectedValuesList()) {
			mappings.removeElement(m);
		}
		System.gc();
	}

	private void uiMerge(ActionEvent e) {
		if (alertShown != (alertShown |= 1))
			JOptionPane.showMessageDialog(this, "从上至下的显示顺序（而不是点击顺序）会决定合并顺序！", "警告", JOptionPane.WARNING_MESSAGE);

		NamedMapping out = new NamedMapping();
		out.mapping = new Mapping();

		List<NamedMapping> maps = uiMappingList.getSelectedValuesList();

		out.name = "M"+maps.size();
		for (NamedMapping m : maps) {
			out.mapping.merge(m.mapping, true);
			out.name += "-"+m.name;
		}
		mappings.addElement(out);
	}

	private void uiFlip(ActionEvent e) {
		NamedMapping m = new NamedMapping();
		NamedMapping om = uiMappingList.getSelectedValue();
		m.name = "F-"+om.name;
		m.mapping = om.mapping.reverse();
		mappings.addElement(m);
	}

	private void uiExtend(ActionEvent e) {
		if (alertShown != (alertShown |= 2))
			JOptionPane.showMessageDialog(this, "最后选择（点击）的对象将作为child", "警告", JOptionPane.WARNING_MESSAGE);

		NamedMapping child = mappings.get(uiMappingList.getSelectionModel().getLeadSelectionIndex());
		List<NamedMapping> l = uiMappingList.getSelectedValuesList(); l.remove(child);
		NamedMapping parent = l.get(0);

		NamedMapping m = new NamedMapping();
		m.mapping = new Mapping();
		m.name = "E-"+child.name+"-"+parent.name;
		m.mapping.merge(child.mapping, true);

		// Mapper{B->C} .extend ( Mapper{A->B} )   =>>  Mapper{A->C}
		m.mapping.extend(parent.mapping, true);

		mappings.addElement(m);
		uiMappingList.setSelectedValue(m, true);

		//uiDel2.setVisible(true);
		//preview(m);
	}

	private void uiSave(ActionEvent e) {
		File file = GuiUtil.fileSaveTo("保存映射表", uiMappingList.getSelectedValue().name+".srg", MappingUI.this);
		if (file == null) return;

		try {
			uiMappingList.getSelectedValue().mapping.saveMap(file);
			try (var fos = new FileOutputStream(file.getAbsolutePath()+".lzma")) {
				Mapper mapper = new Mapper();
				mapper.loadMap(file, false);
				mapper.saveCache(fos, 1);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public MappingUI() {
		initComponents();

		uiMappingList.setModel(mappings);
		uiMappingList.getSelectionModel().addListSelectionListener(evt -> {
			int len = uiMappingList.getSelectedIndices().length;
			boolean some = len > 0;
			boolean one = len == 1;
			boolean many = len > 1;

			uiDel.setEnabled(some);
			uiFlip.setEnabled(one);
			uiMerge.setEnabled(many);
			uiExtend.setEnabled(len == 2);
			uiSave.setEnabled(one);
			uiDelLeft.setEnabled(one);
			uiDelRight.setEnabled(one);
			List<NamedMapping> selectedValuesList = uiMappingList.getSelectedValuesList();
			if (len == 2 && selectedValuesList.get(0).equals(selectedValuesList.get(1))) {
				uiSave.setText("equal");
			} else {
				uiSave.setText("save");
			}
		});

		uiMappingList.addMouseListener(new DoubleClickHelper(uiMappingList, 300, (e) -> {
			uiDel2.setVisible(false);
			preview(uiMappingList.getSelectedValue());
		}));

		uiDelLeft.addActionListener(e -> {
			NamedMapping m = getNamedMapping();
			NamedMapping om = uiMappingList.getSelectedValue();
			if (m.name == null) m.name = "Dl-"+om.name;
			m.mapping = om.mapping.reverse();
			m.mapping.deleteClassMap();
			m.mapping.reverseSelf();
			mappings.addElement(m);
		});
		uiDelRight.addActionListener(e -> {
			NamedMapping m = getNamedMapping();
			NamedMapping om = uiMappingList.getSelectedValue();
			if (m.name == null) m.name = "Dr-"+om.name;
			m.mapping = new Mapping(om.mapping);
			m.mapping.deleteClassMap();
			mappings.addElement(m);
		});

		dlgPreview.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				setEnabled(true);
			}
		});

		uiPreview.setModel(previewList);
	}

	private void preview(NamedMapping m) {
		int i = 1000;
		IntSet id = null;

		if (m.mapping.getClassMap().size() > i) {
			Random r = CryptoFactory.L64W64X128MixRandom();
			id = new IntSet(i);
			while (i-- > 0) while (!id.add(r.nextInt(m.mapping.getClassMap().size())));
		}

		Map<String, List<String>> tmp = new HashMap<>();
		i = 0;
		for (Map.Entry<String, String> entry : m.mapping.getClassMap().entrySet()) {
			if (id == null || id.remove(i++))
				tmp.computeIfAbsent(entry.getKey(), Helpers.fnArrayList()).add(entry.getKey() + " => " + entry.getValue());
		}
		for (Map.Entry<MemberDescriptor, String> entry : m.mapping.getMethodMap().entrySet()) {
			MemberDescriptor d = entry.getKey();
			List<String> list = tmp.get(d.owner);
			if (list != null) list.add("  "+d.name+d.rawDesc + " => " + entry.getValue());
		}
		for (Map.Entry<MemberDescriptor, String> entry : m.mapping.getFieldMap().entrySet()) {
			List<String> list = tmp.get(entry.getKey().owner);
			if (list != null) list.add("  "+entry.getKey().name + " => " + entry.getValue());
		}

		previewList.removeAllElements();
		for (List<String> s : tmp.values()) {
			for (String ss : s) {
				previewList.addElement(ss);
			}
		}

		dlgPreview.show();
	}

	public static void main(String[] args) throws Exception {
		GuiUtil.systemLaf();
		MappingUI f = new MappingUI();

		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.show();
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        var scrollPane1 = new JScrollPane();
        uiMappingList = new JList<>();
        uiName = new JTextField();
        var label1 = new JLabel();
        var uiLoad = new JButton();
        uiDel = new JButton();
        uiFlip = new JButton();
        uiMerge = new JButton();
        uiExtend = new JButton();
        uiSave = new JButton();
        uiDelLeft = new JButton();
        uiDelRight = new JButton();
        dlgPreview = new JDialog();
        var scrollPane2 = new JScrollPane();
        uiPreview = new JList<>();
        uiDel2 = new JButton();

        //======== this ========
        setTitle("MappingBuilder");
        setResizable(false);
        var contentPane = getContentPane();
        contentPane.setLayout(null);

        //======== scrollPane1 ========
        {
            scrollPane1.setViewportView(uiMappingList);
        }
        contentPane.add(scrollPane1);
        scrollPane1.setBounds(10, 35, 320, 275);
        contentPane.add(uiName);
        uiName.setBounds(35, 5, 100, uiName.getPreferredSize().height);

        //---- label1 ----
        label1.setText("\u540d\u79f0");
        contentPane.add(label1);
        label1.setBounds(new Rectangle(new Point(6, 8), label1.getPreferredSize()));

        //---- uiLoad ----
        uiLoad.setText("\u52a0\u8f7d");
        uiLoad.setMargin(new Insets(2, 4, 2, 4));
        uiLoad.addActionListener(e -> uiLoad(e));
        contentPane.add(uiLoad);
        uiLoad.setBounds(new Rectangle(new Point(135, 5), uiLoad.getPreferredSize()));

        //---- uiDel ----
        uiDel.setText("del");
        uiDel.setMargin(new Insets(2, 4, 2, 4));
        uiDel.setEnabled(false);
        uiDel.addActionListener(e -> uiDel(e));
        contentPane.add(uiDel);
        uiDel.setBounds(new Rectangle(new Point(250, 320), uiDel.getPreferredSize()));

        //---- uiFlip ----
        uiFlip.setText("Flip");
        uiFlip.setMargin(new Insets(2, 4, 2, 4));
        uiFlip.setEnabled(false);
        uiFlip.addActionListener(e -> uiFlip(e));
        contentPane.add(uiFlip);
        uiFlip.setBounds(new Rectangle(new Point(45, 320), uiFlip.getPreferredSize()));

        //---- uiMerge ----
        uiMerge.setText("Merge");
        uiMerge.setMargin(new Insets(2, 4, 2, 4));
        uiMerge.setEnabled(false);
        uiMerge.addActionListener(e -> uiMerge(e));
        contentPane.add(uiMerge);
        uiMerge.setBounds(new Rectangle(new Point(80, 320), uiMerge.getPreferredSize()));

        //---- uiExtend ----
        uiExtend.setText("Extend");
        uiExtend.setMargin(new Insets(2, 4, 2, 4));
        uiExtend.setEnabled(false);
        uiExtend.addActionListener(e -> uiExtend(e));
        contentPane.add(uiExtend);
        uiExtend.setBounds(new Rectangle(new Point(125, 320), uiExtend.getPreferredSize()));

        //---- uiSave ----
        uiSave.setText("save");
        uiSave.setMargin(new Insets(2, 4, 2, 4));
        uiSave.setEnabled(false);
        uiSave.addActionListener(e -> uiSave(e));
        contentPane.add(uiSave);
        uiSave.setBounds(new Rectangle(new Point(285, 320), uiSave.getPreferredSize()));

        //---- uiDelLeft ----
        uiDelLeft.setText("Dl");
        uiDelLeft.setBorder(new EmptyBorder(5, 5, 5, 5));
        uiDelLeft.setToolTipText("\u7528\u53f3\u4fa7\u7684\u540d\u79f0\u8986\u76d6\u6574\u4e2aClassMap");
        uiDelLeft.setEnabled(false);
        contentPane.add(uiDelLeft);
        uiDelLeft.setBounds(new Rectangle(new Point(175, 320), uiDelLeft.getPreferredSize()));

        //---- uiDelRight ----
        uiDelRight.setText("Dr");
        uiDelRight.setBorder(new EmptyBorder(5, 5, 5, 5));
        uiDelRight.setToolTipText("\u7528\u5de6\u4fa7\u7684\u540d\u79f0\u8986\u76d6\u6574\u4e2aClassMap");
        uiDelRight.setEnabled(false);
        contentPane.add(uiDelRight);
        uiDelRight.setBounds(new Rectangle(new Point(205, 320), uiDelRight.getPreferredSize()));

        contentPane.setPreferredSize(new Dimension(345, 410));
        pack();
        setLocationRelativeTo(getOwner());

        //======== dlgPreview ========
        {
            dlgPreview.setTitle("\u9884\u89c8");
            var dlgPreviewContentPane = dlgPreview.getContentPane();
            dlgPreviewContentPane.setLayout(null);

            //======== scrollPane2 ========
            {

                //---- uiPreview ----
                uiPreview.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                scrollPane2.setViewportView(uiPreview);
            }
            dlgPreviewContentPane.add(scrollPane2);
            scrollPane2.setBounds(5, 5, 390, 280);

            //---- uiDel2 ----
            uiDel2.setText("\u53d6\u6d88\u64cd\u4f5c");
            uiDel2.addActionListener(e -> uiDel(e));
            dlgPreviewContentPane.add(uiDel2);
            uiDel2.setBounds(new Rectangle(new Point(160, 290), uiDel2.getPreferredSize()));

            dlgPreviewContentPane.setPreferredSize(new Dimension(400, 325));
            dlgPreview.pack();
            dlgPreview.setLocationRelativeTo(dlgPreview.getOwner());
        }
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    private JList<NamedMapping> uiMappingList;
    private JTextField uiName;
    private JButton uiDel;
    private JButton uiFlip;
    private JButton uiMerge;
    private JButton uiExtend;
    private JButton uiSave;
    private JButton uiDelLeft;
    private JButton uiDelRight;
    private JDialog dlgPreview;
    private JList<String> uiPreview;
    private JButton uiDel2;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}