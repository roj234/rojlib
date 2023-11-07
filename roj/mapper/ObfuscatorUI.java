/*
 * Created by JFormDesigner on Fri Sep 08 11:46:26 CST 2023
 */

package roj.mapper;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.concurrent.task.AsyncTask;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.serial.Optional;
import roj.config.serial.SerializerFactory;
import roj.config.serial.SerializerUtils;
import roj.io.IOUtil;
import roj.mapper.obf.MyExcluder;
import roj.mapper.obf.nodename.*;
import roj.mapper.util.Desc;
import roj.mapper.util.ResWriter;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextWriter;
import roj.ui.*;
import roj.util.Helpers;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Roj234
 */
public class ObfuscatorUI extends JFrame {
	private static final Obfuscator obf = new Obfuscator();

	private void run0() throws Exception {
		List<File> lib = getLibraries();
		if (lib == null) return;

		long time = System.currentTimeMillis();

		Profiler p = new Profiler("obfuscator");
		p.begin();

		Map<String, byte[]> resource = new MyHashMap<>();

		Profiler.startSection("IO: input");

		List<Context> arr = Context.fromZip(new File(uiInputPath.getText()), Charset.forName(uiCharset.getText()), resource);
		ZipFileWriter zfw = new ZipFileWriter(new File(uiOutputPath.getText()), false);

		AsyncTask<Void> writer = new AsyncTask<>(new ResWriter(zfw, resource));
		TaskPool.Common().pushTask(writer);

		Profiler.endStartSection("exclusion");

		obf.rand = new Random((int) uiSeed.getValue());
		obf.exclusions = buildExclusions();
		obf.flags = uiFlag.getNumber().intValue();
		if ((obf.flags & Obfuscator.MANGLE_LINE) != 0) obf.lineLog = new CharList();

		obf.m.loadLibraries(lib);

		// 你就说行不行吧
		if (obf.clazz instanceof MoveMarker ||
			obf.field instanceof MoveMarker ||
			obf.method instanceof MoveMarker ||
			obf.clazz instanceof Move ||
			obf.field instanceof Move ||
			obf.method instanceof Move) {
			Move move = new Move(arr, obf.rand);

			if (obf.clazz instanceof MoveMarker || obf.clazz instanceof Move) obf.clazz = move;
			if (obf.field instanceof MoveMarker || obf.field instanceof Move) obf.field = move;
			if (obf.method instanceof MoveMarker || obf.method instanceof Move) obf.method = move;
		}

		Profiler.endStartSection("obfuscate");

		obf.obfuscate(arr);

		Profiler.endStartSection("IO: output");

		writer.get();
		for (int i = 0; i < arr.size(); i++) {
			Context ctx = arr.get(i);
			zfw.writeNamed(ctx.getFileName(), ctx.get());
		}
		zfw.finish();

		Profiler.endSection();
		p.popup();

		obf.dumpMissingClasses();
	}

	private void run(ActionEvent e) {
		uiRun.setEnabled(false);
		TaskPool.Common().pushTask(() -> {
			try {
				run0();
			} finally {
				uiRun.setEnabled(true);
				uiSaveMap.setEnabled(true);
				uiSaveLines.setEnabled(obf.lineLog != null);
			}
		});
	}

	public static void main(String[] args) throws Exception {
		UIUtil.systemLook();
		ObfuscatorUI f = new ObfuscatorUI();

		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.show();
	}

	private final SpinnerNumberModel uiFlag = new SpinnerNumberModel();
	private final DefaultListModel<ExclusionEntry> pActive = new DefaultListModel<>();
	public ObfuscatorUI() {
		initComponents();

		UIUtil.dropFilePath(uiInputPath, (f) -> uiOutputPath.setText(new File(f.getName()).getAbsolutePath()), false);
		UIUtil.dropFilePath(uiOutputPath, null, false);
		UIUtil.dropFilePath(uiLibPath, null, true);

		createObfTypes(uiClassObf, (x) -> {
			obf.clazz = x;
			uiKeepPackage.setEnabled(x != null);
			uiKeepPackage.setSelected(false);
		});
		createObfTypes(uiMethodObf, (x) -> obf.method = x);
		createObfTypes(uiFieldObf, (x) -> obf.field = x);
		//createObfTypes(uiClassObf, (x) -> obf.param = x);
		uiKeepPackage.addActionListener((e) -> obf.clazz.setKeepPackage(uiKeepPackage.isSelected()));

		uiStringEnc.setModel(new DefaultComboBoxModel<>(new String[] {"无", "加密方法A", "加密方法B", "加密方法C", "解密(沙盒)", "⚠ 解密(无限制)"}));

		uiSyntheic.setModel(new DefaultComboBoxModel<>(new String[] {"不处理", "+合成", "反混淆"}));

		DefaultTreeModel pList = (DefaultTreeModel) uiPackageList.getModel();
		pList.setRoot(null);
		// region load
		uiPackageSelected.setModel(pActive);
		uiLoadPackage.addActionListener((e) -> {
			File in = new File(uiInputPath.getText());
			uiLoadPackage.setEnabled(false);
			TaskPool.Common().pushTask(() -> {
				MyHashSet<String> packages = new MyHashSet<>();
				try (ZipArchive za = new ZipArchive(in)) {
					for (ZEntry value : za.getEntries().values()) {
						String name = value.getName();
						if (name.endsWith(".class")) {
							String className = Parser.parseAccess(IOUtil.getSharedByteBuf().readStreamFully(za.getInput(value)), false).name;
							String exceptClassName = name.substring(0, name.length()-6);

							if (className.equals(exceptClassName)) {
								int i = className.length();
								while ((i = className.lastIndexOf('/', i-1)) >= 0) {
									packages.add(className.substring(0, i));
								}
								packages.add(className);
							}
						}
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}

				ExclusionNode root = new ExclusionNode(in.getName(), 0); root.fullName = "";
				ExclusionNode node = root;

				Object[] array = packages.toArray();
				Arrays.sort(array);
				int level = 0;
				for (int i = 0; i < array.length; i++) {
					String o = array[i].toString();
					int myLevel = packageLevel(o);

					while (myLevel < level) {
						node = node.parent;
						level--;
					}
					boolean inserted = false;
					while (myLevel > level) {
						node = node.children.get(node.children.size()-1);
						ExclusionNode next = new ExclusionNode(o, o.lastIndexOf('/')+1);
						node.insert(next, node.getChildCount());
						level++;
						inserted = true;
					}

					if (!inserted) {
						ExclusionNode next = new ExclusionNode(o, o.lastIndexOf('/')+1);
						node.insert(next, node.getChildCount());
					}
				}

				pList.setRoot(root);
				uiLoadPackage.setEnabled(true);
			});
		});
		// endregion
		// region LR move
		uiSelectPackage.addActionListener((e) -> {
			TreePath[] paths = uiPackageList.getSelectionPaths();
			if (paths == null) return;

			for (TreePath path : paths) {
				ExclusionEntry entry = new ExclusionEntry();
				entry.name = ((ExclusionNode) path.getLastPathComponent()).fullName;
				if (entry.name == null) continue;
				if (!pActive.contains(entry)) pActive.addElement(entry);
			}
		});
		uiUnselectPackage.addActionListener((e) -> {
			List<ExclusionEntry> list = uiPackageSelected.getSelectedValuesList();
			for (int i = 0; i < list.size(); i++) pActive.removeElement(list.get(i));
		});
		// endregion
		// region edit
		uiPackageSelected.addMouseListener(new DoubleClickHelper(uiPackageSelected, 300, (e) -> {
			ExclusionEntry entry = uiPackageSelected.getSelectedValue();
			uiEFlagName.setText(entry.name);
			uiEFlag.setValue(entry.flag);
			uiEFlagInherit.setSelected(entry.inherit);
			uiEFlagPriority.setSelected(entry.priority);

			dialogEFlag.show();
		}));
		/** EX_CLASS=1, EX_FIELD=2, EX_METHOD=4, EX_GENERIC=8, EX_STRING=16, EX_STRING_GENERATOR=32 */
		createBiFlag((SpinnerNumberModel) uiEFlag.getModel(), uiEFlag1,uiEFlag2,uiEFlag4,uiEFlag8,uiEFlag16,uiEFlag32);
		dialogEFlag.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				ExclusionEntry entry = uiPackageSelected.getSelectedValue();
				entry.flag = (int) uiEFlag.getValue();
				entry.inherit = uiEFlagInherit.isSelected();
				entry.priority = uiEFlagPriority.isSelected();
				entry.auto = false;

				pActive.set(pActive.indexOf(entry), entry);
			}
		});
		// endregion
		uiOpenDialogExc.addActionListener((e) -> dialogAutoExclusion.show());
		uiExcStart.addActionListener((e) -> {
			uiExcStart.setEnabled(false);
			uiPackageSelected.setModel(new DefaultListModel<>());
			TaskPool.Common().pushTask(() -> {
				uiExcStart.setEnabled(true);
				int flag = 0;
				if (uiExcEnum.isSelected()) flag |= 1;
				if (uiExcAFU.isSelected()) flag |= 2;
				if (uiExcCFN.isSelected()) flag |= 4;
				if (uiExcRefl.isSelected()) flag |= 8;
				if (uiExcIL.isSelected()) {
					addExclusion("roj/reflect/FastInit//__", 4);
					flag |= 16;
				}
				if (uiExcNative.isSelected()) flag |= 32;

				try (ZipArchive za = new ZipArchive(uiInputPath.getText())) {
					for (ZEntry value : za.getEntries().values()) {
						String name = value.getName();
						if (name.endsWith(".class")) {
							ConstantData data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(za.getInput(value)));

							if (name.substring(0, name.length()-6).equals(data.name)) {
								checkExclusion(data, flag);
							}
						}
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				dialogAutoExclusion.hide();
				uiPackageSelected.setModel(pActive);
			});
		});

		// FLAGS
		createBiFlag(uiFlag, null,null,uiDelAttr,uiDelCodeAttr,uiInvGeneric,uiInvGenericVar,uiMangleLVT,uiMangleLVT_EX,uiMangleLine,uiDelFrame,uiKeepLines/*OBF_STRING*/);
		uiFlag.addChangeListener((e) -> {
			int state = uiFlag.getNumber().intValue();
			uiSyntheic.setSelectedIndex(state&3);
		});
		uiSyntheic.addActionListener((e) -> {
			int state = uiFlag.getNumber().intValue() & ~3;
			int index = uiSyntheic.getSelectedIndex();
			state |= index;
			uiFlag.setValue(state);
		});
		uiDelCodeAttr.addActionListener((e) -> {
			boolean b = uiDelCodeAttr.isSelected();
			if (!b) uiKeepLines.setSelected(false);
			uiKeepLines.setEnabled(b);
		});
		uiStringEnc.addActionListener((e) -> {
			String s = uiStringEnc.getSelectedItem().toString();
			System.out.println("enc method[Not implemented]: " + s);
		});

		OnChangeHelper helper = new OnChangeHelper(this);
		helper.addRoot(dialogEFlag);
		helper.addRoot(dialogAutoExclusion);

		helper.addEventListener(uiInputPath, (x) -> {
			boolean b = uiInputPath.getDocument().getLength() > 0 && new File(uiInputPath.getText()).isFile();
			uiRun.setEnabled(b);
			uiLoadPackage.setEnabled(b);
			uiOpenDialogExc.setEnabled(b);
			if (b) uiLoadPackage.doClick();
			else ((DefaultTreeModel) uiPackageList.getModel()).setRoot(null);
		});

		uiSaveCfg.addActionListener((e) -> {
			File file = UIUtil.fileSaveTo("保存混淆器配置", "obfuscator.yml", ObfuscatorUI.this);
			if (file == null) return;

			try {
				saveYml(file.getAbsolutePath());
			} catch (IOException ex) {
				Helpers.athrow(ex);
			}
		});
		uiLoadCfg.addActionListener((e) -> {
			File file = UIUtil.fileLoadFrom("加载混淆器配置", ObfuscatorUI.this);
			if (file == null) return;

			try {
				readYml(file);
			} catch (IOException | ParseException ex) {
				Helpers.athrow(ex);
			}
		});
		uiSaveMap.addActionListener((e) -> {
			File file = UIUtil.fileSaveTo("保存映射表", "obfuscator.map", ObfuscatorUI.this);
			if (file == null) return;

			try {
				obf.m.saveMap(file);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});
		uiSaveLines.addActionListener((e) -> {
			File file = UIUtil.fileSaveTo("保存行号表", "lines.log", ObfuscatorUI.this);
			if (file == null) return;

			try (TextWriter fos = TextWriter.to(file)) {
				fos.append(obf.lineLog);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});
	}

	// region packageList
	private static int packageLevel(String o) {
		int j = 0;
		for (int i = 0; i < o.length(); i++)
			if (o.charAt(i) == '/') j++;
		return j;
	}

	private static void createBiFlag(SpinnerNumberModel sm, JCheckBox... flags) {
		for (int i = 0; i < flags.length; i++) {
			JCheckBox flag = flags[i];
			if (flag != null) {
				ChangeListener cb = createListener(sm, 1 << i);
				cb.stateChanged(new ChangeEvent(flag));
				flag.addChangeListener(cb);
			}
		}
		sm.addChangeListener((e) -> {
			int flag = ((SpinnerNumberModel) e.getSource()).getNumber().intValue();
			for (int i = 0; i < flags.length; i++) {
				JCheckBox flag1 = flags[i];
				if (flag1 != null) flag1.setSelected((flag&(1<<i)) != 0);
			}
		});
	}
	private static ChangeListener createListener(SpinnerNumberModel m, int flag) {
		return (e) -> {
			JCheckBox source = (JCheckBox) e.getSource();
			if (source.isSelected()) m.setValue(m.getNumber().intValue() | flag);
			else m.setValue(m.getNumber().intValue() & ~flag);
		};
	}

	private static final class ExclusionNode extends TreeNodeImpl<ExclusionNode> {
		String fullName;
		String name;

		public ExclusionNode(String name, int pos) {
			this.name = name.substring(pos);
			this.fullName = name;
		}

		@Override
		public String toString() { return name; }
	}
	private static final class ExclusionEntry {
		String name;
		int flag;
		@Optional
		boolean inherit, priority;
		transient boolean auto;

		@Override
		public String toString() {
			CharList sb = new CharList();
			sb.append(name).append(" (").append(flag);
			if (auto) sb.append(", 自动");
			if (inherit) sb.append(", 继承");
			if (priority) sb.append(", 优先");
			return sb.append(")").toStringAndFree();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			return name.equals(((ExclusionEntry) o).name);
		}

		@Override
		public int hashCode() { return name.hashCode(); }
	}

	private InheritableRuleset buildExclusions() {
		InheritableRuleset ruleset = new InheritableRuleset("/");
		for (int i = 0; i < pActive.getSize(); i++) {
			ExclusionEntry entry = pActive.getElementAt(i);
			ruleset.set(entry.name, entry.flag, entry.priority, entry.inherit);
		}
		return ruleset;
	}

	private void checkExclusion(ConstantData data, int flag) {
		if (0 != (data.modifier() & AccessFlag.ENUM) && (flag & 1) != 0) {
			addExclusion(data.name+"//values", 4);
			addExclusion(data.name+"//valueOf", 4);
			addExclusion(data.name, 2);
		}

		MyExcluder cv = new MyExcluder();
		List<? extends MethodNode> methods = data.methods;
		for (int j = 0; j < methods.size(); j++) {
			MethodNode mn = methods.get(j);
			if ((flag&32) != 0 && (mn.modifier()&AccessFlag.NATIVE) != 0) {
				addExclusion(data.name, 1);
				addExclusion(data.name+"//"+mn.name(), 4);
			}

			AttrUnknown code = (AttrUnknown) mn.attrByName("Code");
			if (code == null) continue;
			cv.visitCopied(data.cp, code.getRawData());
		}
		for (Desc ex : cv.excludes) {
			if (ex.owner.startsWith("java/") || ex.owner.startsWith("javax/")) continue;

			switch (ex.flags) {
				case 1:
					if ((flag&2) != 0) addExclusion(ex.owner+"//"+ex.name, 2);
				break;
				case 2:
					if ((flag&4) != 0) addExclusion(ex.owner, 1);
				break;
				case 3:
					if ((flag&16) != 0) addExclusion(ex.owner, 4);
				break;
				case 4:
					if ((flag&8) != 0) addExclusion(ex.owner+"//"+ex.name, 6);
				break;
			}
		}
	}
	private void addExclusion(String name, int flag) {
		ExclusionEntry entry = new ExclusionEntry();
		entry.name = name;
		entry.flag = flag;
		entry.auto = true;

		int i = pActive.indexOf(entry);
		if (i < 0) pActive.addElement(entry);
		else pActive.get(i).flag |= flag;
	}

	// endregion
	private static final class CKV<T> {
		final String name;
		final Supplier<T> generator;
		CKV(String name, Supplier<T> gen) {
			this.name = name;
			this.generator = gen;
		}
		public T generate() { return generator == null ? null : generator.get(); }
		public String toString() { return name; }
	}
	// region obfType
	private static final class MoveMarker implements NameObfuscator {
		private transient int useless;
		public String obfClass(String name, Set<String> noDuplicate, Random rand) { return null; }
		public String obfName(Set<String> noDuplicate, Desc d, Random rand) { return null; }
	}
	private static final Vector<CKV<NameObfuscator>> obfType = new Vector<>();
	public static void addObf(String name, Supplier<NameObfuscator> gen) { obfType.add(new CKV<>(name, gen)); }
	static {
		Supplier<NameObfuscator> 我是分隔符 = () -> {
			JOptionPane.showMessageDialog(null, "我是分隔符,选我等于没选");
			return null;
		};

		addObf("无", null);
		addObf("- 随机字符 -", 我是分隔符);
		addObf(" i1lI1lIi", () -> CharMix.newIII(10,10));
		addObf(" ˉ-—一﹍﹎＿_", () -> CharMix.newDelim(10, 10));
		addObf(" ﹟#﹩$﹠&﹪%", () -> CharMix.中华文化博大精深(10, 10));
		addObf("- 有序随机字符 -", 我是分隔符);
		addObf(" abc", ABC::new);
		addObf("- 自文件 -", 我是分隔符);
		addObf(" 每行一个名称", () -> {
			File file = UIUtil.fileLoadFrom("选择字符串文件");
			if (file == null) return null;

			try {
				return new StringList(file);
			} catch (IOException e) {
				Helpers.athrow(e);
				return null;
			}
		});
		addObf(" 移动(输入的重组)", MoveMarker::new);
		addObf("- 反混淆 -", 我是分隔符);
		addObf(" 唯一名称", Deobfuscate::new);
	}
	private static void createObfTypes(JComboBox<CKV<NameObfuscator>> box, Consumer<NameObfuscator> callback) {
		box.setModel(new DefaultComboBoxModel<>(obfType));
		box.addActionListener((x) -> {
			CKV<NameObfuscator> sel = Helpers.cast(box.getSelectedItem());
			if (sel != null) callback.accept(sel.generate());
		});
	}
	// endregion
	// region save to json
	public static final class SaveTo {
		int flag, seed;
		@Optional
		NameObfuscator classObf, methodObf, fieldObf, paramObf;
		String lib;
		String charset;
		ExclusionEntry[] exclusions;
	}
	private void saveYml(String file) throws IOException {
		SerializerFactory f = SerializerUtils.newSerializerFactory(SerializerFactory.GENERATE | SerializerFactory.ALLOW_DYNAMIC | SerializerFactory.NO_CONSTRUCTOR);
		SerializerUtils.serializeCharArrayToString(f);
		SaveTo o = new SaveTo();
		o.flag = uiFlag.getNumber().intValue();
		o.seed = (int) uiSeed.getValue();
		o.classObf = obf.clazz;
		o.methodObf = obf.method;
		o.fieldObf = obf.field;
		o.paramObf = obf.param;
		o.lib = uiLibPath.getText();
		o.charset = uiCharset.getText();
		o.exclusions = new ExclusionEntry[pActive.size()];
		pActive.copyInto(o.exclusions);
		ConfigMaster.write(o, file, "YAML", f.adapter(SaveTo.class));
	}
	private void readYml(File file) throws IOException, ParseException {
		SerializerFactory f = SerializerUtils.newSerializerFactory(SerializerFactory.GENERATE | SerializerFactory.ALLOW_DYNAMIC | SerializerFactory.NO_CONSTRUCTOR);
		SerializerUtils.serializeCharArrayToString(f);
		SaveTo o = ConfigMaster.adapt(f.adapter(SaveTo.class), file);
		uiFlag.setValue(o.flag);
		uiSeed.setValue(o.seed);
		obf.clazz = o.classObf;
		obf.method = o.methodObf;
		obf.field = o.fieldObf;
		obf.param = o.paramObf;
		uiLibPath.setText(o.lib);
		uiCharset.setText(o.charset);
		pActive.clear();
		for (ExclusionEntry exclusion : o.exclusions)
			pActive.addElement(exclusion);
	}
	// endregion

	private List<File> getLibraries() {
		List<File> lib = new SimpleList<>();
		for (String line : new LineReader(uiLibPath.getText())) {
			if (line.startsWith("#")) continue;
			File f = new File(line);
			if (!f.exists()) {
				JOptionPane.showMessageDialog(this, "lib "+f+" 不存在");
				return null;
			} else if (f.isFile()) {
				lib.add(f);
			} else {
				lib.addAll(IOUtil.findAllFiles(f));
			}
		}
		return lib;
	}

	// region UI
	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		uiSaveCfg = new JButton();
		uiLoadCfg = new JButton();
		uiClassObf = new JComboBox<>();
		uiMethodObf = new JComboBox<>();
		uiFieldObf = new JComboBox<>();
		uiInputPath = new JTextField();
		uiOutputPath = new JTextField();
		JLabel label1 = new JLabel();
		JLabel label2 = new JLabel();
		JLabel label3 = new JLabel();
		uiInvGeneric = new JCheckBox();
		uiInvGenericVar = new JCheckBox();
		uiMangleLine = new JCheckBox();
		uiSeed = new JSpinner();
		JLabel label4 = new JLabel();
		uiSyntheic = new JComboBox<>();
		JSeparator separator1 = new JSeparator();
		JLabel label5 = new JLabel();
		uiDelAttr = new JCheckBox();
		uiDelCodeAttr = new JCheckBox();
		uiDelFrame = new JCheckBox();
		uiMangleLVT = new JCheckBox();
		uiMangleLVT_EX = new JCheckBox();
		uiFlagCS = new JCheckBox();
		uiKeepLines = new JCheckBox();
		JLabel label6 = new JLabel();
		uiStringEnc = new JComboBox<>();
		uiKeepPackage = new JCheckBox();
		JScrollPane scrollPane1 = new JScrollPane();
		uiPackageList = new JTree();
		JScrollPane scrollPane2 = new JScrollPane();
		uiPackageSelected = new JList<>();
		uiSelectPackage = new JButton();
		uiUnselectPackage = new JButton();
		uiLoadPackage = new JButton();
		uiRun = new JButton();
		uiCharset = new JTextField();
		JLabel label7 = new JLabel();
		JScrollPane scrollPane3 = new JScrollPane();
		uiLibPath = new JTextArea();
		JLabel label8 = new JLabel();
		JLabel label9 = new JLabel();
		JLabel label10 = new JLabel();
		JLabel label11 = new JLabel();
		uiSaveMap = new JButton();
		uiSaveLines = new JButton();
		uiOpenDialogExc = new JButton();
		dialogEFlag = new JDialog();
		uiEFlag = new JSpinner();
		uiEFlagName = new JLabel();
		uiEFlagInherit = new JCheckBox();
		uiEFlagPriority = new JCheckBox();
		uiEFlag1 = new JCheckBox();
		uiEFlag2 = new JCheckBox();
		uiEFlag4 = new JCheckBox();
		uiEFlag8 = new JCheckBox();
		uiEFlag16 = new JCheckBox();
		uiEFlag32 = new JCheckBox();
		JSeparator separator2 = new JSeparator();
		dialogAutoExclusion = new JDialog();
		uiExcEnum = new JCheckBox();
		uiExcIL = new JCheckBox();
		uiExcAFU = new JCheckBox();
		uiExcCFN = new JCheckBox();
		uiExcRefl = new JCheckBox();
		uiExcNative = new JCheckBox();
		uiExcStart = new JButton();

		//======== this ========
		setTitle("Roj234 Obfuscator 2.0");
		setResizable(false);
		Container contentPane = getContentPane();
		contentPane.setLayout(null);

		//---- uiSaveCfg ----
		uiSaveCfg.setText("\u4fdd\u5b58\u914d\u7f6e");
		contentPane.add(uiSaveCfg);
		uiSaveCfg.setBounds(new Rectangle(new Point(635, 5), uiSaveCfg.getPreferredSize()));

		//---- uiLoadCfg ----
		uiLoadCfg.setText("\u52a0\u8f7d\u914d\u7f6e");
		contentPane.add(uiLoadCfg);
		uiLoadCfg.setBounds(new Rectangle(new Point(635, 30), uiLoadCfg.getPreferredSize()));
		contentPane.add(uiClassObf);
		uiClassObf.setBounds(385, 135, 120, uiClassObf.getPreferredSize().height);
		contentPane.add(uiMethodObf);
		uiMethodObf.setBounds(385, 185, 120, uiMethodObf.getPreferredSize().height);
		contentPane.add(uiFieldObf);
		uiFieldObf.setBounds(385, 160, 120, uiFieldObf.getPreferredSize().height);
		contentPane.add(uiInputPath);
		uiInputPath.setBounds(35, 5, 250, uiInputPath.getPreferredSize().height);
		contentPane.add(uiOutputPath);
		uiOutputPath.setBounds(35, 30, 250, uiOutputPath.getPreferredSize().height);

		//---- label1 ----
		label1.setText("\u8f93\u5165");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(7, 8), label1.getPreferredSize()));

		//---- label2 ----
		label2.setText("\u8f93\u51fa");
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(7, 33), label2.getPreferredSize()));

		//---- label3 ----
		label3.setText("\u7c7b");
		contentPane.add(label3);
		label3.setBounds(new Rectangle(new Point(364, 138), label3.getPreferredSize()));

		//---- uiInvGeneric ----
		uiInvGeneric.setText("\u641e\u4e71\u6cdb\u578b");
		contentPane.add(uiInvGeneric);
		uiInvGeneric.setBounds(new Rectangle(new Point(360, 90), uiInvGeneric.getPreferredSize()));

		//---- uiInvGenericVar ----
		uiInvGenericVar.setText("in LVTT");
		contentPane.add(uiInvGenericVar);
		uiInvGenericVar.setBounds(new Rectangle(new Point(430, 90), uiInvGenericVar.getPreferredSize()));

		//---- uiMangleLine ----
		uiMangleLine.setText("\u641e\u4e71\u884c\u53f7");
		contentPane.add(uiMangleLine);
		uiMangleLine.setBounds(new Rectangle(new Point(360, 50), uiMangleLine.getPreferredSize()));
		contentPane.add(uiSeed);
		uiSeed.setBounds(385, 210, 120, uiSeed.getPreferredSize().height);

		//---- label4 ----
		label4.setText("\u79cd\u5b50");
		contentPane.add(label4);
		label4.setBounds(new Rectangle(new Point(359, 214), label4.getPreferredSize()));
		contentPane.add(uiSyntheic);
		uiSyntheic.setBounds(415, 9, 64, uiSyntheic.getPreferredSize().height);

		//---- separator1 ----
		separator1.setOrientation(SwingConstants.VERTICAL);
		contentPane.add(separator1);
		separator1.setBounds(355, 5, 5, 280);

		//---- label5 ----
		label5.setText("\u8282\u70b9\u63cf\u8ff0");
		contentPane.add(label5);
		label5.setBounds(new Rectangle(new Point(364, 12), label5.getPreferredSize()));

		//---- uiDelAttr ----
		uiDelAttr.setText("\u79fb\u9664\u8c03\u8bd5\u5c5e\u6027");
		uiDelAttr.setSelected(true);
		contentPane.add(uiDelAttr);
		uiDelAttr.setBounds(new Rectangle(new Point(360, 30), uiDelAttr.getPreferredSize()));

		//---- uiDelCodeAttr ----
		uiDelCodeAttr.setText("in 'Code'");
		contentPane.add(uiDelCodeAttr);
		uiDelCodeAttr.setBounds(new Rectangle(new Point(455, 30), uiDelCodeAttr.getPreferredSize()));

		//---- uiDelFrame ----
		uiDelFrame.setText("\u964d\u7ea7\u81f3Java7");
		contentPane.add(uiDelFrame);
		uiDelFrame.setBounds(new Rectangle(new Point(360, 110), uiDelFrame.getPreferredSize()));

		//---- uiMangleLVT ----
		uiMangleLVT.setText("\u641e\u4e71\u53d8\u91cf(LVT)");
		contentPane.add(uiMangleLVT);
		uiMangleLVT.setBounds(new Rectangle(new Point(360, 70), uiMangleLVT.getPreferredSize()));

		//---- uiMangleLVT_EX ----
		uiMangleLVT_EX.setText("\u66f4\u4e71");
		contentPane.add(uiMangleLVT_EX);
		uiMangleLVT_EX.setBounds(new Rectangle(new Point(460, 70), uiMangleLVT_EX.getPreferredSize()));

		//---- uiFlagCS ----
		uiFlagCS.setText("uiFlatCS");
		contentPane.add(uiFlagCS);
		uiFlagCS.setBounds(new Rectangle(new Point(540, 60), uiFlagCS.getPreferredSize()));

		//---- uiKeepLines ----
		uiKeepLines.setText("\u4fdd\u7559\u884c\u53f7");
		uiKeepLines.setEnabled(false);
		contentPane.add(uiKeepLines);
		uiKeepLines.setBounds(new Rectangle(new Point(530, 30), uiKeepLines.getPreferredSize()));

		//---- label6 ----
		label6.setText("\u5b57\u7b26\u4e32\u52a0\u5bc6");
		contentPane.add(label6);
		label6.setBounds(new Rectangle(new Point(359, 239), label6.getPreferredSize()));
		contentPane.add(uiStringEnc);
		uiStringEnc.setBounds(425, 236, 80, uiStringEnc.getPreferredSize().height);

		//---- uiKeepPackage ----
		uiKeepPackage.setText("\u4fdd\u7559\u5305\u540d");
		uiKeepPackage.setEnabled(false);
		contentPane.add(uiKeepPackage);
		uiKeepPackage.setBounds(new Rectangle(new Point(505, 134), uiKeepPackage.getPreferredSize()));

		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(uiPackageList);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(5, 310, 380, 225);

		//======== scrollPane2 ========
		{
			scrollPane2.setViewportView(uiPackageSelected);
		}
		contentPane.add(scrollPane2);
		scrollPane2.setBounds(390, 310, 320, 225);

		//---- uiSelectPackage ----
		uiSelectPackage.setText("\u589e ->");
		uiSelectPackage.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiSelectPackage);
		uiSelectPackage.setBounds(new Rectangle(new Point(349, 289), uiSelectPackage.getPreferredSize()));

		//---- uiUnselectPackage ----
		uiUnselectPackage.setText("<- \u5220");
		uiUnselectPackage.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiUnselectPackage);
		uiUnselectPackage.setBounds(new Rectangle(new Point(387, 289), uiUnselectPackage.getPreferredSize()));

		//---- uiLoadPackage ----
		uiLoadPackage.setText("\u8bfb\u53d6\u8f93\u5165");
		uiLoadPackage.setMargin(new Insets(2, 4, 2, 4));
		uiLoadPackage.setEnabled(false);
		contentPane.add(uiLoadPackage);
		uiLoadPackage.setBounds(new Rectangle(new Point(55, 285), uiLoadPackage.getPreferredSize()));

		//---- uiRun ----
		uiRun.setText("\u6da6");
		uiRun.setFont(new Font("\u5b8b\u4f53", Font.PLAIN, 30));
		uiRun.setMargin(new Insets(4, 2, 2, 2));
		uiRun.setEnabled(false);
		uiRun.addActionListener(e -> run(e));
		contentPane.add(uiRun);
		uiRun.setBounds(290, 5, 60, 60);

		//---- uiCharset ----
		uiCharset.setText("GB18030");
		contentPane.add(uiCharset);
		uiCharset.setBounds(450, 261, 55, uiCharset.getPreferredSize().height);

		//---- label7 ----
		label7.setText("\u4f9d\u8d56");
		contentPane.add(label7);
		label7.setBounds(new Rectangle(new Point(7, 60), label7.getPreferredSize()));

		//======== scrollPane3 ========
		{

			//---- uiLibPath ----
			uiLibPath.setText("# \u6bcf\u884c\u4e00\u4e2a\u6587\u4ef6or\u6587\u4ef6\u5939\n# uiFlatCS\u3001\u964d\u7ea7\u81f3Java7\u3001\u5b57\u7b26\u4e32\u52a0\u5bc6 \u90fd\u6ca1\u6709\u5b9e\u73b0\n# \u4ece\u914d\u7f6e\u6587\u4ef6\u6062\u590d\u65f6\uff0c\u6df7\u6dc6\u65b9\u5f0f\u7684\u4e0b\u62c9\u6846\u5e76\u4e0d\u4f1a\u53d8\u66f4\n# \u4f60\u53ef\u4ee5\u4fee\u6539\u914d\u7f6e\u6587\u4ef6\u6765\u5b9e\u73b0GUI\u65e0\u6cd5\u5b9e\u73b0\u7684\u529f\u80fd");
			scrollPane3.setViewportView(uiLibPath);
		}
		contentPane.add(scrollPane3);
		scrollPane3.setBounds(5, 80, 345, 200);

		//---- label8 ----
		label8.setText("\u8f93\u5165\u7684\u5305");
		contentPane.add(label8);
		label8.setBounds(new Rectangle(new Point(5, 289), label8.getPreferredSize()));

		//---- label9 ----
		label9.setText("\u5305\u6392\u9664");
		contentPane.add(label9);
		label9.setBounds(new Rectangle(new Point(670, 290), label9.getPreferredSize()));

		//---- label10 ----
		label10.setText("\u65b9\u6cd5");
		contentPane.add(label10);
		label10.setBounds(new Rectangle(new Point(359, 163), label10.getPreferredSize()));

		//---- label11 ----
		label11.setText("\u5b57\u6bb5");
		contentPane.add(label11);
		label11.setBounds(new Rectangle(new Point(359, 188), label11.getPreferredSize()));

		//---- uiSaveMap ----
		uiSaveMap.setText("\u4fdd\u5b58\u6620\u5c04");
		uiSaveMap.setEnabled(false);
		uiSaveMap.setMargin(new Insets(2, 4, 2, 4));
		contentPane.add(uiSaveMap);
		uiSaveMap.setBounds(new Rectangle(new Point(225, 55), uiSaveMap.getPreferredSize()));

		//---- uiSaveLines ----
		uiSaveLines.setText("\u4fdd\u5b58\u884c\u53f7");
		uiSaveLines.setEnabled(false);
		uiSaveLines.setMargin(new Insets(2, 4, 2, 4));
		contentPane.add(uiSaveLines);
		uiSaveLines.setBounds(new Rectangle(new Point(160, 55), uiSaveLines.getPreferredSize()));

		//---- uiOpenDialogExc ----
		uiOpenDialogExc.setText("\u81ea\u52a8");
		uiOpenDialogExc.setMargin(new Insets(2, 2, 2, 2));
		uiOpenDialogExc.setEnabled(false);
		contentPane.add(uiOpenDialogExc);
		uiOpenDialogExc.setBounds(new Rectangle(new Point(637, 286), uiOpenDialogExc.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(720, 540));
		pack();
		setLocationRelativeTo(getOwner());

		//======== dialogEFlag ========
		{
			dialogEFlag.setTitle("\u9ad8\u7ea7\u6392\u9664\u53c2\u6570");
			dialogEFlag.setModal(true);
			Container dialogEFlagContentPane = dialogEFlag.getContentPane();
			dialogEFlagContentPane.setLayout(null);
			dialogEFlagContentPane.add(uiEFlag);
			uiEFlag.setBounds(5, 5, 65, uiEFlag.getPreferredSize().height);
			dialogEFlagContentPane.add(uiEFlagName);
			uiEFlagName.setBounds(75, 8, 145, 16);

			//---- uiEFlagInherit ----
			uiEFlagInherit.setText("\u5e94\u7528\u4e8e\u5b50\u5bf9\u8c61");
			dialogEFlagContentPane.add(uiEFlagInherit);
			uiEFlagInherit.setBounds(new Rectangle(new Point(5, 30), uiEFlagInherit.getPreferredSize()));

			//---- uiEFlagPriority ----
			uiEFlagPriority.setText("\u4f18\u5148");
			dialogEFlagContentPane.add(uiEFlagPriority);
			uiEFlagPriority.setBounds(new Rectangle(new Point(100, 30), uiEFlagPriority.getPreferredSize()));

			//---- uiEFlag1 ----
			uiEFlag1.setText("\u8df3\u8fc7\u7c7b\u540d");
			dialogEFlagContentPane.add(uiEFlag1);
			uiEFlag1.setBounds(new Rectangle(new Point(5, 60), uiEFlag1.getPreferredSize()));

			//---- uiEFlag2 ----
			uiEFlag2.setText("\u8df3\u8fc7\u5b57\u6bb5");
			dialogEFlagContentPane.add(uiEFlag2);
			uiEFlag2.setBounds(new Rectangle(new Point(5, 80), uiEFlag2.getPreferredSize()));

			//---- uiEFlag4 ----
			uiEFlag4.setText("\u8df3\u8fc7\u65b9\u6cd5");
			dialogEFlagContentPane.add(uiEFlag4);
			uiEFlag4.setBounds(new Rectangle(new Point(5, 100), uiEFlag4.getPreferredSize()));

			//---- uiEFlag8 ----
			uiEFlag8.setText("\u4e0d\u751f\u6210\u6cdb\u578b");
			dialogEFlagContentPane.add(uiEFlag8);
			uiEFlag8.setBounds(new Rectangle(new Point(75, 100), uiEFlag8.getPreferredSize()));

			//---- uiEFlag16 ----
			uiEFlag16.setText("\u4e0d\u52a0\u5bc6\u5b57\u7b26\u4e32");
			dialogEFlagContentPane.add(uiEFlag16);
			uiEFlag16.setBounds(new Rectangle(new Point(75, 60), uiEFlag16.getPreferredSize()));

			//---- uiEFlag32 ----
			uiEFlag32.setText("\u4e0d\u751f\u6210\u5b57\u7b26\u4e32\u52a0\u5bc6\u51fd\u6570");
			dialogEFlagContentPane.add(uiEFlag32);
			uiEFlag32.setBounds(new Rectangle(new Point(75, 80), uiEFlag32.getPreferredSize()));
			dialogEFlagContentPane.add(separator2);
			separator2.setBounds(5, 55, 210, separator2.getPreferredSize().height);

			dialogEFlagContentPane.setPreferredSize(new Dimension(225, 130));
			dialogEFlag.pack();
			dialogEFlag.setLocationRelativeTo(dialogEFlag.getOwner());
		}

		//======== dialogAutoExclusion ========
		{
			dialogAutoExclusion.setTitle("\u81ea\u52a8\u6392\u9664");
			dialogAutoExclusion.setModal(true);
			Container dialogAutoExclusionContentPane = dialogAutoExclusion.getContentPane();
			dialogAutoExclusionContentPane.setLayout(null);

			//---- uiExcEnum ----
			uiExcEnum.setText("\u679a\u4e3e\u4e2d\u7684\u5b57\u6bb5\u3001values\u3001valueOf");
			dialogAutoExclusionContentPane.add(uiExcEnum);
			uiExcEnum.setBounds(new Rectangle(new Point(0, 0), uiExcEnum.getPreferredSize()));

			//---- uiExcIL ----
			uiExcIL.setText("RojLib DirectAccessor");
			uiExcIL.setSelected(true);
			dialogAutoExclusionContentPane.add(uiExcIL);
			uiExcIL.setBounds(new Rectangle(new Point(0, 20), uiExcIL.getPreferredSize()));

			//---- uiExcAFU ----
			uiExcAFU.setText("AtomicFieldUpdater");
			uiExcAFU.setSelected(true);
			dialogAutoExclusionContentPane.add(uiExcAFU);
			uiExcAFU.setBounds(new Rectangle(new Point(0, 40), uiExcAFU.getPreferredSize()));

			//---- uiExcCFN ----
			uiExcCFN.setText("Class.forName");
			uiExcCFN.setSelected(true);
			dialogAutoExclusionContentPane.add(uiExcCFN);
			uiExcCFN.setBounds(new Rectangle(new Point(0, 60), uiExcCFN.getPreferredSize()));

			//---- uiExcRefl ----
			uiExcRefl.setText("Class.get(Declared)?(Method|Field)");
			uiExcRefl.setSelected(true);
			dialogAutoExclusionContentPane.add(uiExcRefl);
			uiExcRefl.setBounds(new Rectangle(new Point(0, 80), uiExcRefl.getPreferredSize()));

			//---- uiExcNative ----
			uiExcNative.setText("\u5916\u90e8\u51fd\u6570(native)");
			dialogAutoExclusionContentPane.add(uiExcNative);
			uiExcNative.setBounds(new Rectangle(new Point(0, 100), uiExcNative.getPreferredSize()));

			//---- uiExcStart ----
			uiExcStart.setText("start");
			dialogAutoExclusionContentPane.add(uiExcStart);
			uiExcStart.setBounds(new Rectangle(new Point(80, 120), uiExcStart.getPreferredSize()));

			dialogAutoExclusionContentPane.setPreferredSize(new Dimension(230, 155));
			dialogAutoExclusion.pack();
			dialogAutoExclusion.setLocationRelativeTo(dialogAutoExclusion.getOwner());
		}
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JButton uiSaveCfg;
	private JButton uiLoadCfg;
	private JComboBox<CKV<NameObfuscator>> uiClassObf;
	private JComboBox<CKV<NameObfuscator>> uiMethodObf;
	private JComboBox<CKV<NameObfuscator>> uiFieldObf;
	private JTextField uiInputPath;
	private JTextField uiOutputPath;
	private JCheckBox uiInvGeneric;
	private JCheckBox uiInvGenericVar;
	private JCheckBox uiMangleLine;
	private JSpinner uiSeed;
	private JComboBox<String> uiSyntheic;
	private JCheckBox uiDelAttr;
	private JCheckBox uiDelCodeAttr;
	private JCheckBox uiDelFrame;
	private JCheckBox uiMangleLVT;
	private JCheckBox uiMangleLVT_EX;
	private JCheckBox uiFlagCS;
	private JCheckBox uiKeepLines;
	private JComboBox<String> uiStringEnc;
	private JCheckBox uiKeepPackage;
	private JTree uiPackageList;
	private JList<ExclusionEntry> uiPackageSelected;
	private JButton uiSelectPackage;
	private JButton uiUnselectPackage;
	private JButton uiLoadPackage;
	private JButton uiRun;
	private JTextField uiCharset;
	private JTextArea uiLibPath;
	private JButton uiSaveMap;
	private JButton uiSaveLines;
	private JButton uiOpenDialogExc;
	private JDialog dialogEFlag;
	private JSpinner uiEFlag;
	private JLabel uiEFlagName;
	private JCheckBox uiEFlagInherit;
	private JCheckBox uiEFlagPriority;
	private JCheckBox uiEFlag1;
	private JCheckBox uiEFlag2;
	private JCheckBox uiEFlag4;
	private JCheckBox uiEFlag8;
	private JCheckBox uiEFlag16;
	private JCheckBox uiEFlag32;
	private JDialog dialogAutoExclusion;
	private JCheckBox uiExcEnum;
	private JCheckBox uiExcIL;
	private JCheckBox uiExcAFU;
	private JCheckBox uiExcCFN;
	private JCheckBox uiExcRefl;
	private JCheckBox uiExcNative;
	private JButton uiExcStart;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
	// endregion
}
