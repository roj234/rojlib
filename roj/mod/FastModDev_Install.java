/*
 * Created by JFormDesigner on Thu Sep 14 15:44:04 CST 2023
 */

package roj.mod;

import javax.swing.*;
import java.awt.*;

/**
 * @author Roj234
 */
public class FastModDev_Install extends JFrame {
	public FastModDev_Install() {
		initComponents();
		tabbedPane1.getModel().setSelectedIndex(1);
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		tabbedPane1 = new JTabbedPane();
		JPanel pMinecraft = new JPanel();
		uiOpenMC = new JButton();
		JLabel label1 = new JLabel();
		uiDownMC = new JButton();
		JScrollPane scrollPane2 = new JScrollPane();
		uiMCList = new JList<>();
		JLabel label3 = new JLabel();
		uiStart = new JButton();
		JPanel pModLoader = new JPanel();
		uiForge = new JRadioButton();
		uiFabric = new JRadioButton();
		uiInstallML = new JButton();
		uiInstalledML = new JCheckBox();
		JPanel pMapping = new JPanel();
		JScrollPane scrollPane1 = new JScrollPane();
		uiMapList = new JList<>();
		uiImportMap = new JButton();
		JLabel label2 = new JLabel();
		uiMapCache = new JCheckBox();
		JPanel pForgeExtra = new JPanel();
		uiMCMcp = new JTextField();
		uiMcp = new JTextField();
		JLabel label4 = new JLabel();
		JLabel label5 = new JLabel();
		JPanel pProject = new JPanel();
		uiNewProject = new JButton();
		internalFrame2 = new JInternalFrame();
		progressBar1 = new JProgressBar();

		//======== this ========
		setTitle("FMD\u521d\u59cb\u5316 - Step 1 / 5");
		Container contentPane = getContentPane();
		contentPane.setLayout(null);

		//======== tabbedPane1 ========
		{

			//======== pMinecraft ========
			{
				pMinecraft.setLayout(null);

				//---- uiOpenMC ----
				uiOpenMC.setText("\u9009\u62e9MC\u5ba2\u6237\u7aef\u76ee\u5f55");
				uiOpenMC.setFont(new Font("\u5b8b\u4f53", Font.PLAIN, 18));
				pMinecraft.add(uiOpenMC);
				uiOpenMC.setBounds(160, 10, 270, 40);

				//---- label1 ----
				label1.setText("\u6216");
				label1.setFont(label1.getFont().deriveFont(label1.getFont().getSize() + 20f));
				pMinecraft.add(label1);
				label1.setBounds(new Rectangle(new Point(420, 155), label1.getPreferredSize()));

				//---- uiDownMC ----
				uiDownMC.setText("\u4e0b\u8f7d\u66f4\u591a\u7248\u672c");
				uiDownMC.setFont(new Font("\u5b8b\u4f53", Font.PLAIN, 24));
				uiDownMC.setMargin(new Insets(0, 0, 0, 0));
				pMinecraft.add(uiDownMC);
				uiDownMC.setBounds(345, 60, 180, 90);

				//======== scrollPane2 ========
				{

					//---- uiMCList ----
					uiMCList.setModel(new AbstractListModel<String>() {
						String[] values = {
							"1.19.2 (1.19.2, fabric)",
							"1.20.1 Arcomua (1.20.1, fabric)",
							"Optifine (1.19.2, forge)"
						};
						@Override
						public int getSize() { return values.length; }
						@Override
						public String getElementAt(int i) { return values[i]; }
					});
					scrollPane2.setViewportView(uiMCList);
				}
				pMinecraft.add(scrollPane2);
				scrollPane2.setBounds(10, 60, 330, 240);

				//---- label3 ----
				label3.setText("\u53ef\u7528\u7248\u672c");
				pMinecraft.add(label3);
				label3.setBounds(new Rectangle(new Point(10, 40), label3.getPreferredSize()));

				//---- uiStart ----
				uiStart.setText("\u4ee5\u9009\u4e2d\u7248\u672c\u5f00\u59cb");
				uiStart.setMargin(new Insets(0, 0, 0, 0));
				uiStart.setFont(new Font("\u5b8b\u4f53", Font.PLAIN, 24));
				pMinecraft.add(uiStart);
				uiStart.setBounds(345, 210, 180, 90);
			}
			tabbedPane1.addTab("\u5f00\u59cb", pMinecraft);

			//======== pModLoader ========
			{
				pModLoader.setLayout(null);

				//---- uiForge ----
				uiForge.setText("Forge");
				pModLoader.add(uiForge);
				uiForge.setBounds(new Rectangle(new Point(45, 20), uiForge.getPreferredSize()));

				//---- uiFabric ----
				uiFabric.setText("Fabric");
				pModLoader.add(uiFabric);
				uiFabric.setBounds(new Rectangle(new Point(100, 20), uiFabric.getPreferredSize()));

				//---- uiInstallML ----
				uiInstallML.setText("\u5b89\u88c5");
				pModLoader.add(uiInstallML);
				uiInstallML.setBounds(new Rectangle(new Point(170, 20), uiInstallML.getPreferredSize()));

				//---- uiInstalledML ----
				uiInstalledML.setText("\u6211\u5df2\u5b89\u88c5\uff01");
				pModLoader.add(uiInstalledML);
				uiInstalledML.setBounds(new Rectangle(new Point(45, 50), uiInstalledML.getPreferredSize()));
			}
			tabbedPane1.addTab("\u6a21\u7ec4\u52a0\u8f7d\u5668(WIP)", pModLoader);

			//======== pMapping ========
			{
				pMapping.setLayout(null);

				//======== scrollPane1 ========
				{
					scrollPane1.setViewportView(uiMapList);
				}
				pMapping.add(scrollPane1);
				scrollPane1.setBounds(15, 35, 275, 180);

				//---- uiImportMap ----
				uiImportMap.setText("\u5bfc\u5165");
				pMapping.add(uiImportMap);
				uiImportMap.setBounds(new Rectangle(new Point(235, 5), uiImportMap.getPreferredSize()));

				//---- label2 ----
				label2.setText("\u53ef\u9009\u7684\u6620\u5c04\u8868\u7c7b\u578b");
				pMapping.add(label2);
				label2.setBounds(new Rectangle(new Point(15, 10), label2.getPreferredSize()));

				//---- uiMapCache ----
				uiMapCache.setText("\u751f\u6210\u7f13\u5b58");
				pMapping.add(uiMapCache);
				uiMapCache.setBounds(new Rectangle(new Point(300, 190), uiMapCache.getPreferredSize()));
			}
			tabbedPane1.addTab("\u6620\u5c04\u8868", pMapping);

			//======== pForgeExtra ========
			{
				pForgeExtra.setLayout(null);
				pForgeExtra.add(uiMCMcp);
				uiMCMcp.setBounds(125, 145, 150, uiMCMcp.getPreferredSize().height);
				pForgeExtra.add(uiMcp);
				uiMcp.setBounds(125, 80, 150, uiMcp.getPreferredSize().height);

				//---- label4 ----
				label4.setText("MCP Version");
				pForgeExtra.add(label4);
				label4.setBounds(new Rectangle(new Point(125, 65), label4.getPreferredSize()));

				//---- label5 ----
				label5.setText("Minecraft Version For MCP");
				pForgeExtra.add(label5);
				label5.setBounds(new Rectangle(new Point(125, 130), label5.getPreferredSize()));
			}
			tabbedPane1.addTab("Forge", pForgeExtra);

			//======== pProject ========
			{
				pProject.setLayout(null);

				//---- uiNewProject ----
				uiNewProject.setText("\u65b0\u5efa\u9879\u76ee");
				pProject.add(uiNewProject);
				uiNewProject.setBounds(new Rectangle(new Point(225, 80), uiNewProject.getPreferredSize()));

				//======== internalFrame2 ========
				{
					internalFrame2.setVisible(true);
					Container internalFrame2ContentPane = internalFrame2.getContentPane();
					internalFrame2ContentPane.setLayout(null);
				}
				pProject.add(internalFrame2);
				internalFrame2.setBounds(25, 50, 180, 170);
			}
			tabbedPane1.addTab("\u65b0\u5efa\u9879\u76ee", pProject);
		}
		contentPane.add(tabbedPane1);
		tabbedPane1.setBounds(10, 25, 535, 335);

		//---- progressBar1 ----
		progressBar1.setValue(30);
		contentPane.add(progressBar1);
		progressBar1.setBounds(10, 5, 535, progressBar1.getPreferredSize().height);

		contentPane.setPreferredSize(new Dimension(560, 400));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JTabbedPane tabbedPane1;
	private JButton uiOpenMC;
	private JButton uiDownMC;
	private JList<String> uiMCList;
	private JButton uiStart;
	private JRadioButton uiForge;
	private JRadioButton uiFabric;
	private JButton uiInstallML;
	private JCheckBox uiInstalledML;
	private JList<String> uiMapList;
	private JButton uiImportMap;
	private JCheckBox uiMapCache;
	private JTextField uiMCMcp;
	private JTextField uiMcp;
	private JButton uiNewProject;
	private JInternalFrame internalFrame2;
	private JProgressBar progressBar1;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
