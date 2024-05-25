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
import roj.io.IOUtil;
import roj.platform.Plugin;
import roj.platform.SimplePlugin;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.ui.CMBoxValue;
import roj.ui.DoubleClickHelper;
import roj.ui.GuiUtil;
import roj.ui.OnChangeHelper;
import roj.util.ByteList;
import roj.util.VMUtil;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
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
		Runtime.getRuntime().addShutdownHook(new Thread(p::onDisable));
	}
	@SimplePlugin(id = "card_sleep", version = "1.1", desc = "让你的显卡更凉快")
	public static class PluginHandler extends Plugin {
		private CardSleep instance;

		@Override
		protected void onLoad() {
			if (!VMUtil.isRoot()) throw new RuntimeException("本程序需要管理员权限");
			GuiUtil.systemLook();
		}

		@Override
		protected void onEnable() {
			assert instance == null;

			monitorRead = new TaskExecutor();
			monitorRead.setName("ProcessRead");
			monitorRead.setDaemon(true);
			monitorRead.start();

			CardSleep f = instance = new CardSleep();
			f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			f.setVisible(true);
		}

		@Override
		protected void onDisable() {
			Process p = monitor;
			if (p != null) p.destroy();

			monitorRead.shutdown();
			monitorRead = null;

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

	private static RingBuffer<Integer> coreRecords, memRecords;

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
				tMax = Integer.parseInt(desc.get(5));
			} catch (Exception ignored) {}
			pMin = Float.parseFloat(desc.get(6));
			pMax = Float.parseFloat(desc.get(7));
			longDesc = name+" ("+desc.get(2)+" PCIe "+desc.get(3)+".0x"+desc.get(4)+") \n" +
				"TMax="+desc.get(5)+",PL="+pMin+"-"+pMax+"W,Core="+desc.get(8)+"MHz,Mem="+desc.get(9)+"MHz";
		}

		@Override
		public String toString() { return name; }

		public void addFreqGroup(int memFreq, int coreFreq) {

		}
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
		query(strings -> cards.addElement(new GraphicCard(strings)), "--query-gpu=gpu_uuid,name,pci.bus_id,pcie.link.gen.max,pcie.link.width.max,temperature.gpu.tlimit,power.min_limit,power.max_limit,clocks.max.graphics,clocks.max.memory");
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

		Container pane = getContentPane();

		JComponent graphCore = new MyGraph(coreRecords);
		graphCore.setBounds(uiGraphCore.getBounds());
		pane.remove(uiGraphCore);
		pane.add(graphCore);

		JComponent graphMem = new MyGraph(memRecords);
		graphMem.setBounds(uiGraphMem.getBounds());
		pane.remove(uiGraphMem);
		pane.add(graphMem);

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

			monitorRead.pushTask(() -> {
				CsvParser cp = new CsvParser();
				byte[] buf = new byte[256];
				int rIndex = 0, wIndex = 0;

				try (InputStream in = p1.getInputStream()) {
					while (true) {
						int r = in.read(buf, wIndex, buf.length - wIndex);
						if (r < 0) break;

						for (int i = 0; i < r; i++) {
							if (buf[wIndex] == '\n') {
								cp.forEachLine(ByteList.wrap(buf, rIndex, wIndex-rIndex), lines -> {
									for (int j = 0; j < lines.size(); j++) lines.set(j, lines.get(j).trim());

									int cfreq, cusage, mfreq, musage;
									try {
										cfreq = Integer.parseInt(lines.get(1));
										cusage = Integer.parseInt(lines.get(0));
										mfreq = Integer.parseInt(lines.get(3));
										musage = Integer.parseInt(lines.get(2));
									} catch (Exception ex) {
										uiST_State.setText("Input error: "+lines);
										throw ex;
									}

									coreRecords.ringAddLast((cfreq << 7) | cusage);
									memRecords.ringAddLast((mfreq << 7) | musage);

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

									block:
									if (uiMakeUsageCsv.isSelected()) {
										int freq = myCard.coreFreq[prevFreq[5]];
										float avgUsage;

										if (++prevFreq[4] == 5) {
											avgUsage = slidingAvg(coreRecords, 5);
											if (avgUsage < 100) break block;
										} else if (prevFreq[4] == 50) {
											prevFreq[4] = 0;
											avgUsage = slidingAvg(coreRecords, 50);
										} else break block;

										System.out.println(freq+","+avgUsage);
										if (prevFreq[5] == myCard.coreFreq.length-1) {
											setClock(true, 0, true);
											setClock(false, 0, true);
											uiST_Toggle.doClick();
											return;
										}
										setClock(true, myCard.coreFreq[++prevFreq[5]], true);
									}

									if (System.currentTimeMillis() - prevUpdate > 500) {
										uiST_InstantInfo.setText("Core:"+cfreq+"MHz@"+cusage+"%  Mem:"+mfreq+"MHz@"+musage+"%  Temp="+lines.get(4)+"℃ Pwr="+lines.get(5)+"W");
										if (uiST_paint.isSelected()) {
											graphCore.repaint();
											graphMem.repaint();
										}
										prevUpdate = System.currentTimeMillis();
									}
								});

								//processLine(buf, rIndex, wIndex);
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

		uiMakeUsageCsv.addActionListener(e -> {
			uiST_Core.setSelected(false);
			uiST_Memory.setSelected(false);
			uiMakeUsageCsv.setEnabled(false);
			setClock(false, 99999, true);
			setClock(true, myCard.coreFreq[0], false);
			System.out.println("freq,usage");
		});
	}

	private static float slidingAvg(RingBuffer<Integer> rb, int len) {
		Iterator<Integer> itr = rb.descendingIterator();
		if (!itr.hasNext()) return Float.NaN;
		int val = itr.next();

		int freqSlot = val >>> 7;
		int sum = val & 127;

		for (int j = 1; j < len; j++) {
			if (!itr.hasNext()) {
				len = j;
				break;
			}

			val = itr.next();
			if (val >>> 7 != freqSlot) {
				len = j;
				break;
			}

			sum += val & 127;
		}
		return (float) sum / len;
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
			TaskPool.Common().pushTask(() -> {
				JOptionPane.showMessageDialog(this, "该显卡不支持或没有权限调节核心频率");
			});
			uiST_Core.setSelected(false);
			uiST_Core.setEnabled(false);
		} else {
			TaskPool.Common().pushTask(() -> {
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
				new CsvParser().forEachLine(stdout, lines -> {
					for (int i = 0; i < lines.size(); i++) {
						lines.set(i, lines.get(i).trim());
					}
					csvCallback.accept(lines);
				});
				int exitCode = p.waitFor();
				return exitCode == 0;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		scrollPane1 = new JScrollPane();
		uiCardList = new JList<>();
		var label3 = new JLabel();
		uiPowerLimit = new JSpinner();
		var label4 = new JLabel();
		uiTempLimit = new JSpinner();
		var label5 = new JLabel();
		uiMaxCore = new JSpinner();
		var label6 = new JLabel();
		uiMaxMem = new JComboBox<>();
		uiApplyStaticCfg = new JButton();
		var label1 = new JLabel();
		uiMinCore = new JSpinner();
		var label2 = new JLabel();
		uiMinMem = new JComboBox<>();
		var label7 = new JLabel();
		uiCheckInterval = new JSpinner();
		var label9 = new JLabel();
		uiST_TargetCore = new JSpinner();
		var label8 = new JLabel();
		uiST_TargetCoreUB = new JSpinner();
		var label10 = new JLabel();
		uiST_TargetCoreLB = new JSpinner();
		var label12 = new JLabel();
		uiST_TargetMem = new JSpinner();
		var label11 = new JLabel();
		uiST_TargetMemUB = new JSpinner();
		var label13 = new JLabel();
		uiST_TargetMemLB = new JSpinner();
		uiST_Core = new JCheckBox();
		uiST_Memory = new JCheckBox();
		uiST_Toggle = new JButton();
		uiST_paint = new JCheckBox();
		uiST_InstantInfo = new JLabel();
		uiGraphCore = new JPanel();
		uiGraphMem = new JPanel();
		var label15 = new JLabel();
		var label16 = new JLabel();
		uiST_State = new JLabel();
		uiST_DecrCount = new JSpinner();
		var label14 = new JLabel();
		uiMakeUsageCsv = new JCheckBox();

		//======== this ========
		setTitle("CardSleep 1.1.1");
		var contentPane = getContentPane();
		contentPane.setLayout(null);

		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(uiCardList);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(5, 5, 190, 225);

		//---- label3 ----
		label3.setText("\u529f\u8017\u5899 (W)");
		contentPane.add(label3);
		label3.setBounds(new Rectangle(new Point(210, 18), label3.getPreferredSize()));

		//---- uiPowerLimit ----
		uiPowerLimit.setEnabled(false);
		contentPane.add(uiPowerLimit);
		uiPowerLimit.setBounds(275, 15, 100, uiPowerLimit.getPreferredSize().height);

		//---- label4 ----
		label4.setText("\u6e29\u5ea6\u5899 (\u2103)");
		contentPane.add(label4);
		label4.setBounds(new Rectangle(new Point(205, 44), label4.getPreferredSize()));

		//---- uiTempLimit ----
		uiTempLimit.setEnabled(false);
		contentPane.add(uiTempLimit);
		uiTempLimit.setBounds(275, 40, 100, uiTempLimit.getPreferredSize().height);

		//---- label5 ----
		label5.setText("\u6700\u5927\u6838\u5fc3\u9891\u7387");
		contentPane.add(label5);
		label5.setBounds(new Rectangle(new Point(200, 67), label5.getPreferredSize()));

		//---- uiMaxCore ----
		uiMaxCore.setEnabled(false);
		contentPane.add(uiMaxCore);
		uiMaxCore.setBounds(275, 65, 100, uiMaxCore.getPreferredSize().height);

		//---- label6 ----
		label6.setText("\u6700\u5927\u663e\u5b58\u9891\u7387");
		contentPane.add(label6);
		label6.setBounds(new Rectangle(new Point(200, 92), label6.getPreferredSize()));

		//---- uiMaxMem ----
		uiMaxMem.setEnabled(false);
		contentPane.add(uiMaxMem);
		uiMaxMem.setBounds(275, 90, 100, uiMaxMem.getPreferredSize().height);

		//---- uiApplyStaticCfg ----
		uiApplyStaticCfg.setText("\u9759\u6001\u5e94\u7528");
		uiApplyStaticCfg.setMargin(new Insets(2, 4, 2, 4));
		uiApplyStaticCfg.setEnabled(false);
		contentPane.add(uiApplyStaticCfg);
		uiApplyStaticCfg.setBounds(new Rectangle(new Point(315, 115), uiApplyStaticCfg.getPreferredSize()));

		//---- label1 ----
		label1.setText("\u7a81\u53d1\u6838\u5fc3\u9891\u7387");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(200, 163), label1.getPreferredSize()));

		//---- uiMinCore ----
		uiMinCore.setEnabled(false);
		contentPane.add(uiMinCore);
		uiMinCore.setBounds(275, 160, 100, uiMinCore.getPreferredSize().height);

		//---- label2 ----
		label2.setText("\u7a81\u53d1\u663e\u5b58\u9891\u7387");
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(200, 188), label2.getPreferredSize()));

		//---- uiMinMem ----
		uiMinMem.setEnabled(false);
		contentPane.add(uiMinMem);
		uiMinMem.setBounds(275, 185, 100, uiMinMem.getPreferredSize().height);

		//---- label7 ----
		label7.setText("\u68c0\u6d4b\u8f6e\u8be2 (ms)");
		contentPane.add(label7);
		label7.setBounds(new Rectangle(new Point(195, 214), label7.getPreferredSize()));
		contentPane.add(uiCheckInterval);
		uiCheckInterval.setBounds(275, 210, 100, uiCheckInterval.getPreferredSize().height);

		//---- label9 ----
		label9.setText("\u6838\u5fc3\u5360\u7528\u9884\u671f");
		contentPane.add(label9);
		label9.setBounds(new Rectangle(new Point(15, 264), label9.getPreferredSize()));
		contentPane.add(uiST_TargetCore);
		uiST_TargetCore.setBounds(90, 260, 100, uiST_TargetCore.getPreferredSize().height);

		//---- label8 ----
		label8.setText("\u6838\u5fc3\u5360\u7528\u4e0a\u754c");
		contentPane.add(label8);
		label8.setBounds(new Rectangle(new Point(15, 288), label8.getPreferredSize()));
		contentPane.add(uiST_TargetCoreUB);
		uiST_TargetCoreUB.setBounds(90, 285, 100, uiST_TargetCoreUB.getPreferredSize().height);

		//---- label10 ----
		label10.setText("\u6838\u5fc3\u5360\u7528\u4e0b\u754c");
		contentPane.add(label10);
		label10.setBounds(new Rectangle(new Point(15, 313), label10.getPreferredSize()));
		contentPane.add(uiST_TargetCoreLB);
		uiST_TargetCoreLB.setBounds(90, 310, 100, uiST_TargetCoreLB.getPreferredSize().height);

		//---- label12 ----
		label12.setText("\u663e\u5b58\u5360\u7528\u9884\u671f");
		contentPane.add(label12);
		label12.setBounds(new Rectangle(new Point(200, 264), label12.getPreferredSize()));
		contentPane.add(uiST_TargetMem);
		uiST_TargetMem.setBounds(275, 260, 100, uiST_TargetMem.getPreferredSize().height);

		//---- label11 ----
		label11.setText("\u663e\u5b58\u5360\u7528\u4e0a\u754c");
		contentPane.add(label11);
		label11.setBounds(new Rectangle(new Point(200, 288), label11.getPreferredSize()));
		contentPane.add(uiST_TargetMemUB);
		uiST_TargetMemUB.setBounds(275, 285, 100, uiST_TargetMemUB.getPreferredSize().height);

		//---- label13 ----
		label13.setText("\u663e\u5b58\u5360\u7528\u4e0b\u754c");
		contentPane.add(label13);
		label13.setBounds(new Rectangle(new Point(200, 313), label13.getPreferredSize()));
		contentPane.add(uiST_TargetMemLB);
		uiST_TargetMemLB.setBounds(275, 310, 100, uiST_TargetMemLB.getPreferredSize().height);

		//---- uiST_Core ----
		uiST_Core.setText("\u7ba1\u7406\u6838\u5fc3");
		uiST_Core.setSelected(true);
		contentPane.add(uiST_Core);
		uiST_Core.setBounds(new Rectangle(new Point(25, 235), uiST_Core.getPreferredSize()));

		//---- uiST_Memory ----
		uiST_Memory.setText("\u7ba1\u7406\u663e\u5b58");
		uiST_Memory.setSelected(true);
		contentPane.add(uiST_Memory);
		uiST_Memory.setBounds(new Rectangle(new Point(105, 235), uiST_Memory.getPreferredSize()));

		//---- uiST_Toggle ----
		uiST_Toggle.setText("\u5f00\u59cb");
		uiST_Toggle.setMargin(new Insets(2, 10, 2, 10));
		uiST_Toggle.setEnabled(false);
		contentPane.add(uiST_Toggle);
		uiST_Toggle.setBounds(new Rectangle(new Point(325, 335), uiST_Toggle.getPreferredSize()));

		//---- uiST_paint ----
		uiST_paint.setText("\u7ed8\u56fe");
		uiST_paint.setSelected(true);
		contentPane.add(uiST_paint);
		uiST_paint.setBounds(new Rectangle(new Point(275, 335), uiST_paint.getPreferredSize()));

		//---- uiST_InstantInfo ----
		uiST_InstantInfo.setText("statistic");
		contentPane.add(uiST_InstantInfo);
		uiST_InstantInfo.setBounds(5, 355, 395, uiST_InstantInfo.getPreferredSize().height);

		//======== uiGraphCore ========
		{
			uiGraphCore.setLayout(null);
		}
		contentPane.add(uiGraphCore);
		uiGraphCore.setBounds(5, 395, 380, 100);

		//======== uiGraphMem ========
		{
			uiGraphMem.setLayout(null);
		}
		contentPane.add(uiGraphMem);
		uiGraphMem.setBounds(5, 520, 380, 100);

		//---- label15 ----
		label15.setText("\u6838\u5fc3\u9891\u7387\u3001\u5360\u7528\u7387\u968f\u65f6\u95f4\u53d8\u5316\u56fe\u793a");
		contentPane.add(label15);
		label15.setBounds(new Rectangle(new Point(5, 375), label15.getPreferredSize()));

		//---- label16 ----
		label16.setText("\u663e\u5b58\u9891\u7387\u3001\u5360\u7528\u7387\u968f\u65f6\u95f4\u53d8\u5316\u56fe\u793a");
		contentPane.add(label16);
		label16.setBounds(5, 500, 180, 13);

		//---- uiST_State ----
		uiST_State.setText("state");
		contentPane.add(uiST_State);
		uiST_State.setBounds(5, 340, 270, uiST_State.getPreferredSize().height);
		contentPane.add(uiST_DecrCount);
		uiST_DecrCount.setBounds(275, 235, 100, uiST_DecrCount.getPreferredSize().height);

		//---- label14 ----
		label14.setText("\u964d\u9891\u8fde\u7eed\u8ba1\u6570");
		contentPane.add(label14);
		label14.setBounds(new Rectangle(new Point(200, 239), label14.getPreferredSize()));

		//---- uiMakeUsageCsv ----
		uiMakeUsageCsv.setText("csv freq/usage");
		uiMakeUsageCsv.setEnabled(false);
		contentPane.add(uiMakeUsageCsv);
		uiMakeUsageCsv.setBounds(new Rectangle(new Point(205, 115), uiMakeUsageCsv.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(395, 630));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JScrollPane scrollPane1;
	private JList<GraphicCard> uiCardList;
	private JSpinner uiPowerLimit;
	private JSpinner uiTempLimit;
	private JSpinner uiMaxCore;
	private JComboBox<CMBoxValue> uiMaxMem;
	private JButton uiApplyStaticCfg;
	private JSpinner uiMinCore;
	private JComboBox<CMBoxValue> uiMinMem;
	private JSpinner uiCheckInterval;
	private JSpinner uiST_TargetCore;
	private JSpinner uiST_TargetCoreUB;
	private JSpinner uiST_TargetCoreLB;
	private JSpinner uiST_TargetMem;
	private JSpinner uiST_TargetMemUB;
	private JSpinner uiST_TargetMemLB;
	private JCheckBox uiST_Core;
	private JCheckBox uiST_Memory;
	private JButton uiST_Toggle;
	private JCheckBox uiST_paint;
	private JLabel uiST_InstantInfo;
	private JPanel uiGraphCore;
	private JPanel uiGraphMem;
	private JLabel uiST_State;
	private JSpinner uiST_DecrCount;
	private JCheckBox uiMakeUsageCsv;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}