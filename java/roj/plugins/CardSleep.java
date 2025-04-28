/*
 * Created by JFormDesigner on Thu Dec 14 06:01:02 CST 2023
 */

package roj.plugins;

import roj.collect.Int2IntMap;
import roj.collect.IntSet;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.concurrent.TaskExecutor;
import roj.concurrent.TaskPool;
import roj.config.CsvParser;
import roj.gui.CMBoxValue;
import roj.gui.DoubleClickHelper;
import roj.gui.GuiUtil;
import roj.gui.OnChangeHelper;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.VMUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.function.Consumer;

/**
 * 其实我一开始是用C++写的
 * @author Roj234
 */
public class CardSleep extends JFrame {
	private static TaskExecutor monitorRead;
	private static Process monitor;
	private static long prevUpdate;

	public static void main(String[] args) throws Exception {
		PluginHandler p = new PluginHandler();
		p.onLoad();
		p.onEnable();
		p.instance.setDefaultCloseOperation(EXIT_ON_CLOSE);
		Runtime.getRuntime().addShutdownHook(new Thread(p::onDisable));
	}
	@SimplePlugin(id = "card_sleep", version = "1.1", desc = "让你的显卡更凉快")
	public static class PluginHandler extends Plugin {
		private CardSleep instance;

		@Override
		protected void onLoad() {
			if (!VMUtil.isRoot()) System.err.println("本程序需要管理员权限才能完美工作");
			GuiUtil.systemLaf();
		}

		@Override
		protected void onEnable() {
			assert instance == null;

			monitorRead = new TaskExecutor();
			monitorRead.setName("ProcessRead");
			monitorRead.setDaemon(true);
			monitorRead.start();

			CardSleep f = instance = new CardSleep();
			f.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			f.setVisible(true);
		}

		@Override
		protected void onDisable() {
			Process p = monitor;
			if (p != null) p.destroy();

			monitorRead.shutdown();
			monitorRead = null;

			if (VMUtil.isShutdownInProgress()) return;
			instance.dispose();
			instance = null;
		}
	}

	private static class MyGraph extends JComponent {
		private final RingBuffer<Integer> records;
		public MyGraph(RingBuffer<Integer> records) {
			this.records = records;
		}

		@Override
		public void paint(Graphics g) {
			int off = 0;
			g.setColor(Color.WHITE);
			g.fillRect(0,0,getWidth(),getHeight());

			g.setColor(new Color(233, 77,30));
			for (int record : records) {
				int usage = (record & 127);
				int height = usage * getHeight() / 100;
				g.fillRect(off, getHeight()-height, 1, height);

				off++;
			}

			g.setColor(new Color(30, 132, 233));
			off = 0;
			int prevY = -1;
			for (int record : records) {
				int freq = findRecord(records == coreRecords ? myCard.coreFreq : myCard.memFreq, record);
				int height = (100-freq) * getHeight() / 100 - 1;
				g.drawLine(off-1, prevY < 0 ? height : prevY, off, height);
				prevY = height;

				off++;
			}
		}

		private int findRecord(int[] arr, int record) {
			float i = Arrays.binarySearch(arr, record >>> 7);
			if (i < 0) i = -i - 1;
			if (i > arr.length-1) i = arr.length-1;
			return (int)(i/arr.length*100);
		}
	}
	private static class MyGraph2 extends JComponent {
		private final RingBuffer<Float> records;
		public MyGraph2(RingBuffer<Float> records) {
			this.records = records;
		}

		@Override
		public void paint(Graphics g) {
			g.setColor(Color.WHITE);
			g.fillRect(0,0,getWidth(),getHeight());

			g.setColor(new Color(30, 132, 233));
			int off = 0;
			int prevY = -1;
			for (float record : records) {
				float freq = record / myCard.pMax * 100;
				int height = (int) ((100-freq) * getHeight() / 100 - 1);
				g.drawLine(off-1, prevY < 0 ? height : prevY, off, height);
				prevY = height;

				off++;
			}
		}
	}

	private static RingBuffer<Integer> coreRecords, memRecords;
	private static RingBuffer<Float> powerRecords;

	private static GraphicCard myCard;
	private static final class GraphicCard {
		final String uuid, name, longDesc;
		int tMax;
		final float pMin, pMax;
		int[] coreFreq, memFreq, memFreqAssoc;
		GraphicCard(List<String> desc) {
			uuid = desc.get(0);
			name = desc.get(1);
			try {
				tMax = Integer.parseInt(desc.get(9));
			} catch (Exception ignored) {}
			pMin = Float.parseFloat(desc.get(5));
			pMax = Float.parseFloat(desc.get(6));
			longDesc = name+" ("+desc.get(2)+" PCIe "+desc.get(3)+".0x"+desc.get(4)+") \n" +
				"TMax="+tMax+",PL="+pMin+"-"+pMax+"W,Core="+desc.get(7)+"MHz,Mem="+desc.get(8)+"MHz";
		}

		@Override
		public String toString() { return name; }
	}

	private static class CoreFreqSpinner extends SpinnerListModel { // hack createEditor()
		private final int[] coreFreqList;
		private int idx;

		public CoreFreqSpinner(int[] coreFreqList) { this.coreFreqList = coreFreqList; idx = coreFreqList.length-1; }

		@Override
		public Object getValue() { return coreFreqList[idx]; }

		@Override
		public void setValue(Object value) {
			String s = value.toString();
			int v = TextUtil.isNumber(s) == 0 ? Integer.parseInt(s) : 0;
			if (v < coreFreqList[0]) v = 0;
			else if (v > coreFreqList[coreFreqList.length-1]) v = coreFreqList.length-1;
			else {
				int pIdx = Arrays.binarySearch(coreFreqList, v);
				if (pIdx < 0) {
					pIdx = -pIdx - 1;
					int delta = Math.abs(coreFreqList[pIdx]-v);
					if (Math.abs(coreFreqList[pIdx+1]-v) < delta) {
						pIdx++;
					}
				}

				v = pIdx;
			}

			idx = v;
			fireStateChanged();
		}

		@Override
		public Object getNextValue() { return idx == coreFreqList.length-1 ? coreFreqList[idx] : coreFreqList[idx+1]; }
		@Override
		public Object getPreviousValue() {return idx == 0 ? coreFreqList[0] : coreFreqList[idx-1]; }
	}

	public CardSleep() {
		initComponents();
		new OnChangeHelper(this);

		DefaultListModel<GraphicCard> cards = new DefaultListModel<>();
		try {
			query(strings -> cards.addElement(new GraphicCard(strings)), "--query-gpu=gpu_uuid,name,pci.bus_id,pcie.link.gen.max,pcie.link.width.max,power.min_limit,power.max_limit,clocks.max.graphics,clocks.max.memory,temperature.gpu.tlimit");
		} catch (ArrayIndexOutOfBoundsException e) {
			query(strings -> cards.addElement(new GraphicCard(strings)), "--query-gpu=gpu_uuid,name,pci.bus_id,pcie.link.gen.max,pcie.link.width.max,power.min_limit,power.max_limit,clocks.max.graphics,clocks.max.memory");
		}
		uiCardList.setModel(cards);

		uiCardList.addMouseListener(new DoubleClickHelper(uiCardList, 500, list -> {
			Container pane = getContentPane();
			pane.remove(scrollPane1);
			pane.repaint();

			GraphicCard card = cards.get(uiCardList.getSelectedIndex());
			myCard = card;
			uiST_InstantInfo.setText(card.longDesc);

			Int2IntMap memFreqTmp = new Int2IntMap();
			IntSet coreFreqTmp = new IntSet();
			query(line -> {
				int memFreq1 = Integer.parseInt(line.get(0));
				int coreFreq1 = Integer.parseInt(line.get(1));
				coreFreqTmp.add(coreFreq1);
				Int2IntMap.Entry entry = memFreqTmp.getEntryOrCreate(memFreq1);
				if (coreFreq1 > entry.v) entry.v = coreFreq1;
			}, "--query-supported-clocks=mem,gr", "-i="+card.uuid);

			uiPowerLimit.setModel(new SpinnerNumberModel(card.pMax, card.pMin, card.pMax, 1));
			uiPowerLimit.setEnabled(true);
			uiTempLimit.setModel(new SpinnerNumberModel(80, 60, 85, 1));
			uiTempLimit.setEnabled(true);

			Vector<CMBoxValue> memFreqList = new Vector<>();
			for (Int2IntMap.Entry entry : memFreqTmp.selfEntrySet()) {
				memFreqList.add(new CMBoxValue(entry.getIntKey()+" MHz", entry.getIntKey()));
			}
			memFreqList.sort((o1, o2) -> Integer.compare(o1.value, o2.value));

			myCard.memFreq = new int[memFreqTmp.size()];
			myCard.memFreqAssoc = new int[memFreqTmp.size()];
			for (int i = 0; i < memFreqList.size(); i++) {
				CMBoxValue value = memFreqList.get(i);
				myCard.memFreq[i] = value.value;
				myCard.memFreqAssoc[i] = memFreqTmp.get(value.value);
			}

			uiMinMem.setModel(new DefaultComboBoxModel<>(memFreqList));
			uiMinMem.setSelectedIndex(1);
			uiMinMem.setEnabled(true);
			uiMaxMem.setModel(new DefaultComboBoxModel<>(memFreqList));
			uiMaxMem.setSelectedIndex(memFreqList.size()-1);
			uiMaxMem.setEnabled(true);

			int[] coreFreqList = myCard.coreFreq = coreFreqTmp.toIntArray();
			Arrays.sort(coreFreqList);

			CoreFreqSpinner model = new CoreFreqSpinner(coreFreqList);
			uiMinCore.setModel(model);
			model.setValue(600);

			uiMinCore.setEnabled(true);
			uiMaxCore.setModel(new CoreFreqSpinner(coreFreqList));
			uiMaxCore.setEnabled(true);

			uiApplyStaticCfg.setEnabled(true);
			uiST_Toggle.setEnabled(true);

			JOptionPane.showMessageDialog(CardSleep.this, "选择了："+card.longDesc);
		}));

		uiCheckInterval.setModel(new SpinnerNumberModel(150,20,1500,1));
		uiApplyStaticCfg.addActionListener(e -> {
			List<String> args = Arrays.asList(new String[3]);
			args.set(0, "nvidia-smi");

			try {
				args.set(1, "-lgc");
				args.set(2, "0,"+uiMaxCore.getValue());
				new ProcessBuilder().command(args).start();

				args.set(1, "-lmc");
				args.set(2, "0,"+((CMBoxValue) uiMaxMem.getSelectedItem()).value);
				new ProcessBuilder().command(args).start();

				args.set(1, "-pl");
				args.set(2, uiPowerLimit.getValue().toString());
				new ProcessBuilder().command(args).start();

				args.set(1, "-gtt");
				args.set(2, uiTempLimit.getValue().toString());
				new ProcessBuilder().command(args).start();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});

		uiST_Core.addActionListener(e -> {
			boolean b = uiST_Core.isSelected();
			uiST_TargetCore.setEnabled(b);
			uiST_TargetCoreUB.setEnabled(b);
			uiST_TargetCoreLB.setEnabled(b);
		});
		uiST_Memory.addActionListener(e -> {
			boolean b = uiST_Memory.isSelected();
			uiST_TargetMem.setEnabled(b);
			uiST_TargetMemUB.setEnabled(b);
			uiST_TargetMemLB.setEnabled(b);
		});

		coreRecords = new RingBuffer<>(uiGraphCore.getWidth());
		memRecords = new RingBuffer<>(uiGraphMem.getWidth());
		powerRecords = new RingBuffer<>(uiGraphPower.getWidth());

		Container pane = getContentPane();

		JComponent graphCore = new MyGraph(coreRecords);
		graphCore.setBounds(uiGraphCore.getBounds());
		pane.remove(uiGraphCore);
		pane.add(graphCore);

		JComponent graphMem = new MyGraph(memRecords);
		graphMem.setBounds(uiGraphMem.getBounds());
		pane.remove(uiGraphMem);
		pane.add(graphMem);

		JComponent graphPwr = new MyGraph2(powerRecords);
		graphPwr.setBounds(uiGraphPower.getBounds());
		pane.remove(uiGraphPower);
		pane.add(graphPwr);

		MouseAdapter remove = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					GuiUtil.removeComponent(e.getComponent());
				}
			}
		};
		graphCore.addMouseListener(remove);
		graphMem.addMouseListener(remove);
		graphPwr.addMouseListener(remove);

		uiST_DecrCount.setModel(new SpinnerNumberModel(7, 1, 50, 1));
		uiST_TargetCore.setModel(createMyModel(77));
		uiST_TargetCoreUB.setModel(createMyModel(88));
		uiST_TargetCoreLB.setModel(createMyModel(70));
		uiST_TargetMem.setModel(createMyModel(40));
		uiST_TargetMemUB.setModel(createMyModel(55));
		uiST_TargetMemLB.setModel(createMyModel(25));
		uiST_Toggle.addActionListener(e -> {
			Process p = monitor;
			if (p != null) {
				p.destroy();
				uiST_Toggle.setText("开始");
				uiApplyStaticCfg.setEnabled(true);
				uiCheckInterval.setEnabled(true);
				uiPowerLimit.setEnabled(true);
				uiTempLimit.setEnabled(true);
				monitor = null;
				return;
			}

			SimpleList<String> args = new SimpleList<>();
			args.add("nvidia-smi");
			args.add("--query-gpu=utilization.gpu,clocks.current.graphics,utilization.memory,clocks.current.memory,temperature.gpu,power.draw.instant");
			args.add("-i="+myCard.uuid);
			args.add("--format=csv,noheader,nounits");
			args.add("-lms="+uiCheckInterval.getValue());
			ProcessBuilder pb = new ProcessBuilder().command(args).redirectOutput(ProcessBuilder.Redirect.PIPE);
			Process p1;
			try {
				monitor = p1 = pb.start();
			} catch (Exception ex) {
				ex.printStackTrace();
				return;
			}

			uiApplyStaticCfg.setEnabled(false);
			uiCheckInterval.setEnabled(false);
			uiPowerLimit.setEnabled(false);
			uiTempLimit.setEnabled(false);
			uiST_Toggle.setText("停止");
			Arrays.fill(prevFreq, 0);

			monitorRead.submit(() -> {
				CsvParser cp = new CsvParser();
				byte[] buf = new byte[256];
				int rIndex = 0, wIndex = 0;

				try (InputStream in = p1.getInputStream()) {
					while (true) {
						int r = in.read(buf, wIndex, buf.length - wIndex);
						if (r < 0) break;

						for (int i = 0; i < r; i++) {
							if (buf[wIndex] == '\n') {
								cp.forEachLine(ByteList.wrap(buf, rIndex, wIndex-rIndex), (rowId, lines) -> {
									for (int j = 0; j < lines.size(); j++) lines.set(j, lines.get(j).trim());

									int cfreq, cusage, mfreq, musage;
									float pwr;
									try {
										cfreq = Integer.parseInt(lines.get(1));
										cusage = Integer.parseInt(lines.get(0));
										mfreq = Integer.parseInt(lines.get(3));
										musage = Integer.parseInt(lines.get(2));
										pwr = Float.parseFloat(lines.get(5));
									} catch (Exception ex) {
										uiST_State.setText("Input error: "+lines);
										throw ex;
									}

									coreRecords.ringAddLast((cfreq << 7) | cusage);
									memRecords.ringAddLast((mfreq << 7) | musage);
									powerRecords.ringAddLast(pwr);

									block:
									if (uiST_Core.isSelected()) {
										int cUsageUB = ((int) uiST_TargetCoreUB.getValue());
										int cUsageLB = ((int) uiST_TargetCoreLB.getValue());
										int bestFreq = cusage * cfreq / ((int) uiST_TargetCore.getValue());
										if (cusage < cUsageLB) {
											if (++prevFreq[2] < ((int) uiST_DecrCount.getValue())) break block;

											bestFreq = alignFreq(bestFreq, myCard.coreFreq);
											int bestUsage = cusage * cfreq / bestFreq;
											if (bestUsage > cUsageUB) break block;

											int minAutoFreq = (int) uiMinCore.getValue();
											if (bestFreq <= minAutoFreq) {
												if (setClock(true, minAutoFreq, false)) {
													uiST_State.setText("R Core "+cfreq+"MHz@"+cusage+"% => 0~"+minAutoFreq+"MHz");
												}
											} else {
												if (setClock(true, bestFreq, true)) {
													uiST_State.setText("↓ Core "+cfreq+"MHz@"+cusage+"% => "+bestFreq+"MHz@"+ bestUsage +"%");
												}
											}
										} else {
											prevFreq[2] = 0;
											if (cusage > cUsageUB) {
												bestFreq = alignFreq(bestFreq, myCard.coreFreq);

												int maxCore = (int) uiMaxCore.getValue();
												if (bestFreq > maxCore) bestFreq = maxCore;

												int bestUsage = cusage * cfreq / bestFreq;
												if (bestUsage < cUsageLB) break block;

												if (setClock(true, bestFreq, true)) {
													uiST_State.setText("↑ Core "+cfreq+"MHz@"+cusage+"% => "+bestFreq+"MHz@"+ bestUsage +"%");
												}
											}
										}
									}

									block:
									if (uiST_Memory.isSelected()) {
										int mUsageUB = ((int) uiST_TargetMemUB.getValue());
										int mUsageLB = ((int) uiST_TargetMemLB.getValue());
										int bestFreq = musage * mfreq / (int) uiST_TargetMem.getValue();
										if (musage < mUsageLB) {
											if (++prevFreq[3] < ((int) uiST_DecrCount.getValue())) break block;

											bestFreq = alignFreq(bestFreq, myCard.memFreq);

											// y = 7.3435x-0.154R2 = 0.7056
											int bestUsage = (int) (musage * mfreq / bestFreq * Math.pow(7.3435*mfreq, -0.154));
											if (bestUsage > mUsageUB) break block;

											int minAutoFreq = ((CMBoxValue) uiMinMem.getSelectedItem()).value;
											if (bestFreq <= minAutoFreq) {
												if (setClock(false, minAutoFreq+1, false)) {
													uiST_State.setText("R Mem "+mfreq+"MHz@"+musage+"% => 0~"+minAutoFreq+"MHz");
												}
											} else {
												if (setClock(false, bestFreq, true)) {
													uiST_State.setText("↓ Mem "+mfreq+"MHz@"+musage+"% => "+bestFreq+"MHz@"+bestUsage+"%");
												}
											}
										} else {
											prevFreq[3] = 0;
											if (musage > mUsageUB) {
												bestFreq = alignFreq(bestFreq, myCard.memFreq);
												int maxMem = ((CMBoxValue) uiMaxMem.getSelectedItem()).value;
												if (bestFreq > maxMem) bestFreq = maxMem;

												int bestUsage = musage * mfreq / bestFreq;
												//if (bestUsage < mUsageLB) break block;

												if (setClock(false, bestFreq, true)) {
													uiST_State.setText("↑ Mem "+mfreq+"MHz@"+musage+"% => "+bestFreq+"MHz@"+bestUsage+"%");
												}
											}
										}
									}

									if (System.currentTimeMillis() - prevUpdate > 500) {
										uiST_InstantInfo.setText("Core:"+cfreq+"MHz@"+cusage+"%  Mem:"+mfreq+"MHz@"+musage+"%  Temp="+lines.get(4)+"℃ Pwr="+lines.get(5)+"W");
										if (uiST_paint.isSelected()) {
											graphCore.repaint();
											graphMem.repaint();
											graphPwr.repaint();
										}
										prevUpdate = System.currentTimeMillis();
									}
								});

								rIndex = wIndex;
							}

							wIndex++;
						}

						System.arraycopy(buf, rIndex, buf, 0, wIndex - rIndex);
						wIndex -= rIndex;
						rIndex = 0;
					}
				} catch (Exception ex) {
					p1.destroy();
					throw ex;
				}
				System.out.println("进程已终止:"+p1.exitValue());
			});
		});

		if (!VMUtil.isRoot()) {
			GuiUtil.removeComponent(uiPanelControl);
			uiST_Memory.setSelected(false);
			uiST_Core.setSelected(false);
			uiST_State.setText("没有管理员权限，工作在只读模式下");
			repaint();
		}
	}

	private static final int[] prevFreq = new int[6];
	private boolean setClock(boolean isGraphicClock, int frequency, boolean isLocked) {
		if (frequency == prevFreq[isGraphicClock ? 1 : 0]) return false;
		prevFreq[isGraphicClock ? 1 : 0] = frequency;

		List<String> args = Arrays.asList(new String[3]);
		args.set(0, "nvidia-smi");
		args.set(1, isGraphicClock ? "-lgc" : "-lmc");
		args.set(2, isLocked ? Integer.toString(frequency) : "0,"+frequency);

		try {
			Process p = new ProcessBuilder().command(args).start();
			if (0 == p.waitFor()) {
				String buf = IOUtil.read(TextReader.auto(p.getInputStream()));
				if (!buf.contains("locked")) return true;
			}
			String read = IOUtil.read(TextReader.auto(p.getErrorStream()));
			System.out.println(read);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (isGraphicClock) {
			TaskPool.Common().submit(() -> {
				JOptionPane.showMessageDialog(this, "该显卡不支持或没有权限调节核心频率");
			});
			uiST_Core.setSelected(false);
			uiST_Core.setEnabled(false);
		} else {
			TaskPool.Common().submit(() -> {
				JOptionPane.showMessageDialog(this, "该显卡不支持或没有权限调节显存频率");
			});
			uiST_Memory.setSelected(false);
			uiST_Memory.setEnabled(false);
		}
		return false;
	}

	private static int alignFreq(int freq, int[] arr) {
		freq = Arrays.binarySearch(arr, freq);
		if (freq < 0) freq = -freq - 1;
		if (freq > arr.length-1) freq = arr.length-1;
		return arr[freq];
	}

	private static SpinnerNumberModel createMyModel(int v) { return new SpinnerNumberModel(v, 5, 95, 1); }

	private static boolean query(Consumer<List<String>> csvCallback, String... cmd) {
		SimpleList<String> args = new SimpleList<>();
		args.add("nvidia-smi");
		args.addAll(cmd);
		args.add("--format=csv,noheader,nounits");
		ProcessBuilder pb = new ProcessBuilder().command(args).redirectOutput(ProcessBuilder.Redirect.PIPE);
		try {
			Process p = pb.start();
			try (TextReader stdout = TextReader.auto(p.getInputStream())) {
				new CsvParser().forEachLine(stdout, (rowId, lines) -> {
					for (int i = 0; i < lines.size(); i++) {
						lines.set(i, lines.get(i).trim());
					}
					csvCallback.accept(lines);
				});
				int exitCode = p.waitFor();
				return exitCode == 0;
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        scrollPane1 = new JScrollPane();
        uiCardList = new JList<>();
        var label7 = new JLabel();
        uiST_Toggle = new JButton();
        uiCheckInterval = new JSpinner();
        uiST_paint = new JCheckBox();
        uiPanelControl = new JPanel();
        uiST_Core = new JCheckBox();
        uiST_Memory = new JCheckBox();
        var label9 = new JLabel();
        uiST_TargetCore = new JSpinner();
        var label10 = new JLabel();
        uiST_TargetCoreLB = new JSpinner();
        var label8 = new JLabel();
        uiST_TargetCoreUB = new JSpinner();
        var label1 = new JLabel();
        uiMinCore = new JSpinner();
        var label2 = new JLabel();
        uiMinMem = new JComboBox<>();
        var label5 = new JLabel();
        uiMaxCore = new JSpinner();
        var label6 = new JLabel();
        uiMaxMem = new JComboBox<>();
        var label14 = new JLabel();
        uiST_DecrCount = new JSpinner();
        var label12 = new JLabel();
        uiST_TargetMem = new JSpinner();
        var label11 = new JLabel();
        uiST_TargetMemUB = new JSpinner();
        var label13 = new JLabel();
        uiST_TargetMemLB = new JSpinner();
        var label3 = new JLabel();
        uiPowerLimit = new JSpinner();
        var label4 = new JLabel();
        uiTempLimit = new JSpinner();
        uiApplyStaticCfg = new JButton();
        uiST_State = new JLabel();
        uiST_InstantInfo = new JLabel();
        var label15 = new JLabel();
        uiGraphCore = new JPanel();
        var label16 = new JLabel();
        uiGraphMem = new JPanel();
        uiGraphPower = new JPanel();

        //======== this ========
        setTitle("CardSleep 1.2.0");
        var contentPane = getContentPane();
        contentPane.setLayout(null);

        //======== scrollPane1 ========
        {
            scrollPane1.setViewportView(uiCardList);
        }
        contentPane.add(scrollPane1);
        scrollPane1.setBounds(5, 5, 385, 225);

        //---- label7 ----
        label7.setText("\u68c0\u6d4b\u8f6e\u8be2 (ms)");
        contentPane.add(label7);
        label7.setBounds(new Rectangle(new Point(185, 160), label7.getPreferredSize()));

        //---- uiST_Toggle ----
        uiST_Toggle.setText("\u5f00\u59cb");
        uiST_Toggle.setMargin(new Insets(2, 10, 2, 10));
        uiST_Toggle.setEnabled(false);
        contentPane.add(uiST_Toggle);
        uiST_Toggle.setBounds(new Rectangle(new Point(250, 180), uiST_Toggle.getPreferredSize()));
        contentPane.add(uiCheckInterval);
        uiCheckInterval.setBounds(265, 155, 100, uiCheckInterval.getPreferredSize().height);

        //---- uiST_paint ----
        uiST_paint.setText("\u7ed8\u56fe");
        uiST_paint.setSelected(true);
        contentPane.add(uiST_paint);
        uiST_paint.setBounds(new Rectangle(new Point(200, 180), uiST_paint.getPreferredSize()));

        //======== uiPanelControl ========
        {
            uiPanelControl.setLayout(null);

            //---- uiST_Core ----
            uiST_Core.setText("\u7ba1\u7406\u6838\u5fc3");
            uiST_Core.setSelected(true);
            uiPanelControl.add(uiST_Core);
            uiST_Core.setBounds(new Rectangle(new Point(10, 0), uiST_Core.getPreferredSize()));

            //---- uiST_Memory ----
            uiST_Memory.setText("\u7ba1\u7406\u663e\u5b58");
            uiST_Memory.setSelected(true);
            uiPanelControl.add(uiST_Memory);
            uiST_Memory.setBounds(new Rectangle(new Point(90, 0), uiST_Memory.getPreferredSize()));

            //---- label9 ----
            label9.setText("\u6838\u5fc3\u5360\u7528\u9884\u671f");
            uiPanelControl.add(label9);
            label9.setBounds(new Rectangle(new Point(0, 28), label9.getPreferredSize()));
            uiPanelControl.add(uiST_TargetCore);
            uiST_TargetCore.setBounds(75, 25, 100, uiST_TargetCore.getPreferredSize().height);

            //---- label10 ----
            label10.setText("\u6838\u5fc3\u5360\u7528\u4e0b\u754c");
            uiPanelControl.add(label10);
            label10.setBounds(new Rectangle(new Point(0, 77), label10.getPreferredSize()));
            uiPanelControl.add(uiST_TargetCoreLB);
            uiST_TargetCoreLB.setBounds(75, 75, 100, uiST_TargetCoreLB.getPreferredSize().height);

            //---- label8 ----
            label8.setText("\u6838\u5fc3\u5360\u7528\u4e0a\u754c");
            uiPanelControl.add(label8);
            label8.setBounds(new Rectangle(new Point(0, 52), label8.getPreferredSize()));
            uiPanelControl.add(uiST_TargetCoreUB);
            uiST_TargetCoreUB.setBounds(75, 50, 100, uiST_TargetCoreUB.getPreferredSize().height);

            //---- label1 ----
            label1.setText("\u7a81\u53d1\u6838\u5fc3\u9891\u7387");
            uiPanelControl.add(label1);
            label1.setBounds(new Rectangle(new Point(0, 103), label1.getPreferredSize()));
            uiPanelControl.add(uiMinCore);
            uiMinCore.setBounds(75, 100, 100, uiMinCore.getPreferredSize().height);

            //---- label2 ----
            label2.setText("\u7a81\u53d1\u663e\u5b58\u9891\u7387");
            uiPanelControl.add(label2);
            label2.setBounds(new Rectangle(new Point(0, 128), label2.getPreferredSize()));
            uiPanelControl.add(uiMinMem);
            uiMinMem.setBounds(75, 125, 100, uiMinMem.getPreferredSize().height);

            //---- label5 ----
            label5.setText("\u6700\u5927\u6838\u5fc3\u9891\u7387");
            uiPanelControl.add(label5);
            label5.setBounds(new Rectangle(new Point(0, 152), label5.getPreferredSize()));
            uiPanelControl.add(uiMaxCore);
            uiMaxCore.setBounds(75, 150, 100, uiMaxCore.getPreferredSize().height);

            //---- label6 ----
            label6.setText("\u6700\u5927\u663e\u5b58\u9891\u7387");
            uiPanelControl.add(label6);
            label6.setBounds(new Rectangle(new Point(0, 177), label6.getPreferredSize()));
            uiPanelControl.add(uiMaxMem);
            uiMaxMem.setBounds(75, 175, 100, uiMaxMem.getPreferredSize().height);

            //---- label14 ----
            label14.setText("\u964d\u9891\u8fde\u7eed\u8ba1\u6570");
            uiPanelControl.add(label14);
            label14.setBounds(new Rectangle(new Point(185, 3), label14.getPreferredSize()));
            uiPanelControl.add(uiST_DecrCount);
            uiST_DecrCount.setBounds(260, 0, 100, uiST_DecrCount.getPreferredSize().height);

            //---- label12 ----
            label12.setText("\u663e\u5b58\u5360\u7528\u9884\u671f");
            uiPanelControl.add(label12);
            label12.setBounds(new Rectangle(new Point(185, 28), label12.getPreferredSize()));
            uiPanelControl.add(uiST_TargetMem);
            uiST_TargetMem.setBounds(260, 25, 100, uiST_TargetMem.getPreferredSize().height);

            //---- label11 ----
            label11.setText("\u663e\u5b58\u5360\u7528\u4e0a\u754c");
            uiPanelControl.add(label11);
            label11.setBounds(new Rectangle(new Point(185, 52), label11.getPreferredSize()));
            uiPanelControl.add(uiST_TargetMemUB);
            uiST_TargetMemUB.setBounds(260, 50, 100, uiST_TargetMemUB.getPreferredSize().height);

            //---- label13 ----
            label13.setText("\u663e\u5b58\u5360\u7528\u4e0b\u754c");
            uiPanelControl.add(label13);
            label13.setBounds(new Rectangle(new Point(185, 77), label13.getPreferredSize()));
            uiPanelControl.add(uiST_TargetMemLB);
            uiST_TargetMemLB.setBounds(260, 75, 100, uiST_TargetMemLB.getPreferredSize().height);

            //---- label3 ----
            label3.setText("\u529f\u8017\u5899 (W)");
            uiPanelControl.add(label3);
            label3.setBounds(new Rectangle(new Point(195, 102), label3.getPreferredSize()));
            uiPanelControl.add(uiPowerLimit);
            uiPowerLimit.setBounds(260, 100, 100, uiPowerLimit.getPreferredSize().height);

            //---- label4 ----
            label4.setText("\u6e29\u5ea6\u5899 (\u2103)");
            uiPanelControl.add(label4);
            label4.setBounds(new Rectangle(new Point(190, 129), label4.getPreferredSize()));
            uiPanelControl.add(uiTempLimit);
            uiTempLimit.setBounds(260, 125, 100, uiTempLimit.getPreferredSize().height);

            //---- uiApplyStaticCfg ----
            uiApplyStaticCfg.setText("\u9759\u6001\u5e94\u7528");
            uiApplyStaticCfg.setMargin(new Insets(2, 4, 2, 4));
            uiPanelControl.add(uiApplyStaticCfg);
            uiApplyStaticCfg.setBounds(new Rectangle(new Point(300, 175), uiApplyStaticCfg.getPreferredSize()));
        }
        contentPane.add(uiPanelControl);
        uiPanelControl.setBounds(5, 5, 385, 225);

        //---- uiST_State ----
        uiST_State.setText("state");
        contentPane.add(uiST_State);
        uiST_State.setBounds(5, 235, 270, uiST_State.getPreferredSize().height);

        //---- uiST_InstantInfo ----
        uiST_InstantInfo.setText("statistic");
        contentPane.add(uiST_InstantInfo);
        uiST_InstantInfo.setBounds(5, 250, 395, uiST_InstantInfo.getPreferredSize().height);

        //---- label15 ----
        label15.setText("\u56fe\u8868: \u6838\u5fc3\u9891\u7387\u3001\u5360\u7528\u7387");
        contentPane.add(label15);
        label15.setBounds(new Rectangle(new Point(5, 265), label15.getPreferredSize()));

        //======== uiGraphCore ========
        {
            uiGraphCore.setLayout(null);
        }
        contentPane.add(uiGraphCore);
        uiGraphCore.setBounds(0, 280, 380, 100);

        //---- label16 ----
        label16.setText("\u56fe\u8868: \u663e\u5b58\u9891\u7387\u3001\u5360\u7528\u7387");
        contentPane.add(label16);
        label16.setBounds(5, 380, 180, 13);

        //======== uiGraphMem ========
        {
            uiGraphMem.setLayout(null);
        }
        contentPane.add(uiGraphMem);
        uiGraphMem.setBounds(0, 395, 380, 100);

        //======== uiGraphPower ========
        {
            uiGraphPower.setLayout(null);
        }
        contentPane.add(uiGraphPower);
        uiGraphPower.setBounds(0, 510, 380, 100);

        contentPane.setPreferredSize(new Dimension(400, 620));
        pack();
        setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    private JScrollPane scrollPane1;
    private JList<GraphicCard> uiCardList;
    private JButton uiST_Toggle;
    private JSpinner uiCheckInterval;
    private JCheckBox uiST_paint;
    private JPanel uiPanelControl;
    private JCheckBox uiST_Core;
    private JCheckBox uiST_Memory;
    private JSpinner uiST_TargetCore;
    private JSpinner uiST_TargetCoreLB;
    private JSpinner uiST_TargetCoreUB;
    private JSpinner uiMinCore;
    private JComboBox<CMBoxValue> uiMinMem;
    private JSpinner uiMaxCore;
    private JComboBox<CMBoxValue> uiMaxMem;
    private JSpinner uiST_DecrCount;
    private JSpinner uiST_TargetMem;
    private JSpinner uiST_TargetMemUB;
    private JSpinner uiST_TargetMemLB;
    private JSpinner uiPowerLimit;
    private JSpinner uiTempLimit;
    private JButton uiApplyStaticCfg;
    private JLabel uiST_State;
    private JLabel uiST_InstantInfo;
    private JPanel uiGraphCore;
    private JPanel uiGraphMem;
    private JPanel uiGraphPower;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}