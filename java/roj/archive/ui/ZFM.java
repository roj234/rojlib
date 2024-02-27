/*
 * Created by JFormDesigner on Wed Apr 24 06:59:16 CST 2024
 */

package roj.archive.ui;

import roj.ui.CMBoxValue;
import roj.ui.GuiUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Roj234
 */
public class ZFM extends JFrame {
    public static void main(String[] args) throws Exception {
        GuiUtil.systemLook();
        ZFM f = new ZFM();

        f.pack();
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setVisible(true);
    }

	public ZFM() {
		initComponents();
	}

    private void createUIComponents() {
        // TODO: add custom component creation code here
    }

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        var menuBar = new JMenuBar();
        var menuFile = new JMenu();
        btnOpen = new JMenuItem();
        btnOpenNew = new JMenuItem();
        btnOpenSelect = new JMenuItem();
        btnSave = new JMenuItem();
        btnExit = new JMenuItem();
        var menuEdit = new JMenu();
        btnRename = new JMenuItem();
        btnRepath = new JMenuItem();
        btnCopy = new JMenuItem();
        btnLink = new JMenuItem();
        btnDelete = new JMenuItem();
        btnSplit = new JMenuItem();
        btnComment = new JMenuItem();
        btnEncrypt = new JMenuItem();
        var menuView = new JMenu();
        btnSelAll = new JMenuItem();
        btnSelNone = new JMenuItem();
        btnSelRev = new JMenuItem();
        btnSelRegex = new JMenuItem();
        var menuAbout = new JMenu();
        btnOptions = new JMenuItem();
        btnBenchmark = new JMenuItem();
        btnHelp = new JMenuItem();
        btnAbout = new JMenuItem();
        var mainPanel = new JPanel();
        var btnPanel = new JPanel();
        btnAdd = new JButton();
        btnExtract = new JButton();
        btnTest = new JButton();
        btnDelete2 = new JButton();
        btnInfo = new JButton();
        var treePanel = new JScrollPane();
        uiTable = new JTable();
        winAbout = new JDialog();
        uiLogo = new JPanel();
        var textAbout = new JTextPane();
        btnCloseAbout = new JButton();
        btnGithub = new JButton();
        winEncrypt = new JDialog();
        var label2 = new JLabel();
        uiOldPass = new JTextField();
        var label3 = new JLabel();
        uiNewPass = new JTextField();
        var label4 = new JLabel();
        uiZipMethod = new JComboBox<>();
        var label5 = new JLabel();
        ui7zMethod1 = new JSpinner();
        ui7zMethod2 = new JSpinner();
        uiDecrypt = new JCheckBox();
        btnCloseEncrypt = new JButton();
        winExtract = new JDialog();
        uiExtractPath = new JTextField();
        uiSelectPath = new JButton();
        uiExtractInfo = new JTextPane();
        var label1 = new JLabel();
        uiThreads = new JSpinner();
        uiExtract = new JButton();
        winGenericText = new JDialog();
        var scrollPane1 = new JScrollPane();
        uiGenericText = new JTextPane();
        btnOkText = new JButton();
        btnCloseText = new JButton();
        winSplit = new JDialog();
        btnMerge = new JButton();
        var label6 = new JLabel();
        uiSplitSizes = new JTextField();
        btnSplit2 = new JButton();
        winSave = new Window(null);
        var label7 = new JLabel();
        uiSaveProgress = new JProgressBar();
        uiCloseSave = new JButton();
        dialog1 = new JDialog();
        comboBox1 = new JComboBox<>();
        textField1 = new JTextField();
        textField2 = new JTextField();
        label8 = new JLabel();
        label9 = new JLabel();
        label10 = new JLabel();
        button1 = new JButton();
        textField3 = new JTextField();
        textField4 = new JTextField();
        button2 = new JButton();
        label11 = new JLabel();
        label12 = new JLabel();
        button3 = new JButton();
        button4 = new JButton();

        //======== this ========
        setTitle("Roj234's ZFM 0.2");
        var contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== menuBar ========
        {

            //======== menuFile ========
            {
                menuFile.setText("\u6587\u4ef6");

                //---- btnOpen ----
                btnOpen.setText("\u6253\u5f00");
                menuFile.add(btnOpen);

                //---- btnOpenNew ----
                btnOpenNew.setText("\u65b0\u7a97\u53e3\u6253\u5f00");
                menuFile.add(btnOpenNew);

                //---- btnOpenSelect ----
                btnOpenSelect.setText("\u6253\u5f00\u9009\u4e2d\u9879");
                menuFile.add(btnOpenSelect);
                menuFile.addSeparator();

                //---- btnSave ----
                btnSave.setText("\u4fdd\u5b58");
                menuFile.add(btnSave);

                //---- btnExit ----
                btnExit.setText("\u9000\u51fa");
                menuFile.add(btnExit);
            }
            menuBar.add(menuFile);

            //======== menuEdit ========
            {
                menuEdit.setText("\u7f16\u8f91");

                //---- btnRename ----
                btnRename.setText("\u91cd\u547d\u540d");
                menuEdit.add(btnRename);

                //---- btnRepath ----
                btnRepath.setText("\u79fb\u52a8\u5230");
                menuEdit.add(btnRepath);

                //---- btnCopy ----
                btnCopy.setText("\u590d\u5236\u5230");
                menuEdit.add(btnCopy);

                //---- btnLink ----
                btnLink.setText("\u94fe\u63a5");
                menuEdit.add(btnLink);

                //---- btnDelete ----
                btnDelete.setText("\u5220\u9664");
                menuEdit.add(btnDelete);
                menuEdit.addSeparator();

                //---- btnSplit ----
                btnSplit.setText("\u5206\u5377");
                menuEdit.add(btnSplit);

                //---- btnComment ----
                btnComment.setText("\u6ce8\u91ca");
                menuEdit.add(btnComment);

                //---- btnEncrypt ----
                btnEncrypt.setText("\u52a0\u5bc6");
                menuEdit.add(btnEncrypt);
            }
            menuBar.add(menuEdit);

            //======== menuView ========
            {
                menuView.setText("\u67e5\u770b");

                //---- btnSelAll ----
                btnSelAll.setText("\u5168\u9009");
                menuView.add(btnSelAll);

                //---- btnSelNone ----
                btnSelNone.setText("\u5168\u4e0d\u9009");
                menuView.add(btnSelNone);

                //---- btnSelRev ----
                btnSelRev.setText("\u53cd\u9009");
                menuView.add(btnSelRev);
                menuView.addSeparator();

                //---- btnSelRegex ----
                btnSelRegex.setText("\u6b63\u5219\u9009\u62e9");
                menuView.add(btnSelRegex);
            }
            menuBar.add(menuView);

            //======== menuAbout ========
            {
                menuAbout.setText("\u5173\u4e8e");

                //---- btnOptions ----
                btnOptions.setText("\u9996\u9009\u9879");
                menuAbout.add(btnOptions);

                //---- btnBenchmark ----
                btnBenchmark.setText("\u57fa\u51c6\u6d4b\u8bd5");
                menuAbout.add(btnBenchmark);
                menuAbout.addSeparator();

                //---- btnHelp ----
                btnHelp.setText("\u5e2e\u52a9");
                menuAbout.add(btnHelp);

                //---- btnAbout ----
                btnAbout.setText("\u5173\u4e8eZFM");
                menuAbout.add(btnAbout);
            }
            menuBar.add(menuAbout);
        }
        contentPane.add(menuBar, BorderLayout.NORTH);

        //======== mainPanel ========
        {
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

            //======== btnPanel ========
            {
                btnPanel.setLayout(new GridLayout());

                //---- btnAdd ----
                btnAdd.setText("\u6dfb\u52a0");
                btnAdd.setMargin(new Insets(40, 8, 10, 8));
                btnPanel.add(btnAdd);

                //---- btnExtract ----
                btnExtract.setText("\u63d0\u53d6");
                btnExtract.setMargin(new Insets(40, 8, 10, 8));
                btnPanel.add(btnExtract);

                //---- btnTest ----
                btnTest.setText("\u6d4b\u8bd5");
                btnTest.setMargin(new Insets(40, 8, 10, 8));
                btnPanel.add(btnTest);

                //---- btnDelete2 ----
                btnDelete2.setText("\u5220\u9664");
                btnDelete2.setMargin(new Insets(40, 8, 10, 8));
                btnPanel.add(btnDelete2);

                //---- btnInfo ----
                btnInfo.setText("\u4fe1\u606f");
                btnInfo.setMargin(new Insets(40, 8, 10, 8));
                btnPanel.add(btnInfo);
            }
            mainPanel.add(btnPanel);

            //======== treePanel ========
            {
                treePanel.setViewportView(uiTable);
            }
            mainPanel.add(treePanel);
        }
        contentPane.add(mainPanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());

        //======== winAbout ========
        {
            winAbout.setTitle("\u5173\u4e8eZFM");
            var winAboutContentPane = winAbout.getContentPane();
            winAboutContentPane.setLayout(null);

            //======== uiLogo ========
            {
                uiLogo.setLayout(null);
            }
            winAboutContentPane.add(uiLogo);
            uiLogo.setBounds(10, 5, 155, 80);

            //---- textAbout ----
            textAbout.setText("ZFM 0.2\n2024-04-24\nCopyright(c) 2018-2024 Roj234\nZFM\u662f\u4e00\u6b3e\u5f00\u6e90\u8f6f\u4ef6\n\u4f7f\u7528\u5e76\u4fee\u6539\u4e86\u4ee5Public Domain\u5f00\u6e90\u7684LibLZMA");
            winAboutContentPane.add(textAbout);
            textAbout.setBounds(10, 90, 345, 80);

            //---- btnCloseAbout ----
            btnCloseAbout.setText("OK");
            winAboutContentPane.add(btnCloseAbout);
            btnCloseAbout.setBounds(new Rectangle(new Point(15, 175), btnCloseAbout.getPreferredSize()));

            //---- btnGithub ----
            btnGithub.setText("GitHub");
            winAboutContentPane.add(btnGithub);
            btnGithub.setBounds(new Rectangle(new Point(285, 175), btnGithub.getPreferredSize()));

            winAboutContentPane.setPreferredSize(new Dimension(370, 230));
            winAbout.pack();
            winAbout.setLocationRelativeTo(winAbout.getOwner());
        }

        //======== winEncrypt ========
        {
            winEncrypt.setTitle("\u52a0\u5bc6");
            var winEncryptContentPane = winEncrypt.getContentPane();
            winEncryptContentPane.setLayout(null);

            //---- label2 ----
            label2.setText("\u539f\u5bc6\u7801");
            winEncryptContentPane.add(label2);
            label2.setBounds(new Rectangle(new Point(15, 5), label2.getPreferredSize()));
            winEncryptContentPane.add(uiOldPass);
            uiOldPass.setBounds(35, 25, 180, uiOldPass.getPreferredSize().height);

            //---- label3 ----
            label3.setText("\u65b0\u5bc6\u7801");
            winEncryptContentPane.add(label3);
            label3.setBounds(new Rectangle(new Point(15, 50), label3.getPreferredSize()));
            winEncryptContentPane.add(uiNewPass);
            uiNewPass.setBounds(35, 75, 180, uiNewPass.getPreferredSize().height);

            //---- label4 ----
            label4.setText("\u52a0\u5bc6\u65b9\u6cd5(zip)");
            winEncryptContentPane.add(label4);
            label4.setBounds(new Rectangle(new Point(15, 105), label4.getPreferredSize()));
            winEncryptContentPane.add(uiZipMethod);
            uiZipMethod.setBounds(35, 125, 120, uiZipMethod.getPreferredSize().height);

            //---- label5 ----
            label5.setText("7z AES \u5faa\u73af\u5e42\u53ca\u76d0\u957f\u5ea6");
            winEncryptContentPane.add(label5);
            label5.setBounds(new Rectangle(new Point(15, 150), label5.getPreferredSize()));
            winEncryptContentPane.add(ui7zMethod1);
            ui7zMethod1.setBounds(35, 170, 80, ui7zMethod1.getPreferredSize().height);
            winEncryptContentPane.add(ui7zMethod2);
            ui7zMethod2.setBounds(120, 170, 85, ui7zMethod2.getPreferredSize().height);

            //---- uiDecrypt ----
            uiDecrypt.setText("\u53d6\u6d88\u52a0\u5bc6");
            winEncryptContentPane.add(uiDecrypt);
            uiDecrypt.setBounds(new Rectangle(new Point(30, 205), uiDecrypt.getPreferredSize()));

            //---- btnCloseEncrypt ----
            btnCloseEncrypt.setText("\u63d0\u4ea4");
            winEncryptContentPane.add(btnCloseEncrypt);
            btnCloseEncrypt.setBounds(new Rectangle(new Point(105, 205), btnCloseEncrypt.getPreferredSize()));

            winEncryptContentPane.setPreferredSize(new Dimension(240, 270));
            winEncrypt.pack();
            winEncrypt.setLocationRelativeTo(winEncrypt.getOwner());
        }

        //======== winExtract ========
        {
            winExtract.setTitle("\u63d0\u53d6");
            var winExtractContentPane = winExtract.getContentPane();
            winExtractContentPane.setLayout(null);
            winExtractContentPane.add(uiExtractPath);
            uiExtractPath.setBounds(15, 15, 380, uiExtractPath.getPreferredSize().height);

            //---- uiSelectPath ----
            uiSelectPath.setText("\u2026");
            winExtractContentPane.add(uiSelectPath);
            uiSelectPath.setBounds(new Rectangle(new Point(395, 15), uiSelectPath.getPreferredSize()));
            winExtractContentPane.add(uiExtractInfo);
            uiExtractInfo.setBounds(15, 40, 380, 200);

            //---- label1 ----
            label1.setText("\u591a\u7ebf\u7a0b");
            winExtractContentPane.add(label1);
            label1.setBounds(new Rectangle(new Point(420, 70), label1.getPreferredSize()));
            winExtractContentPane.add(uiThreads);
            uiThreads.setBounds(460, 65, 50, uiThreads.getPreferredSize().height);

            //---- uiExtract ----
            uiExtract.setText("\u63d0\u53d6");
            winExtractContentPane.add(uiExtract);
            uiExtract.setBounds(new Rectangle(new Point(450, 225), uiExtract.getPreferredSize()));

            winExtractContentPane.setPreferredSize(new Dimension(525, 285));
            winExtract.pack();
            winExtract.setLocationRelativeTo(winExtract.getOwner());
        }

        //======== winGenericText ========
        {
            winGenericText.setTitle("\u6d4b\u8bd5\u3001\u4fe1\u606f\u3001\u5e2e\u52a9\u3001\u6ce8\u91ca\u3001\u91cd\u547d\u540d\u3001\u79fb\u52a8\u5230\u3001\u590d\u5236\u5230\u3001\u6b63\u5219\u3001\u94fe\u63a5");
            var winGenericTextContentPane = winGenericText.getContentPane();
            winGenericTextContentPane.setLayout(null);

            //======== scrollPane1 ========
            {
                scrollPane1.setViewportView(uiGenericText);
            }
            winGenericTextContentPane.add(scrollPane1);
            scrollPane1.setBounds(5, 5, 380, 255);

            //---- btnOkText ----
            btnOkText.setText("\u786e\u5b9a");
            winGenericTextContentPane.add(btnOkText);
            btnOkText.setBounds(new Rectangle(new Point(30, 265), btnOkText.getPreferredSize()));

            //---- btnCloseText ----
            btnCloseText.setText("\u53d6\u6d88");
            winGenericTextContentPane.add(btnCloseText);
            btnCloseText.setBounds(new Rectangle(new Point(365, 265), btnCloseText.getPreferredSize()));

            winGenericTextContentPane.setPreferredSize(new Dimension(430, 320));
            winGenericText.pack();
            winGenericText.setLocationRelativeTo(winGenericText.getOwner());
        }

        //======== winSplit ========
        {
            winSplit.setTitle("\u5206\u5377");
            var winSplitContentPane = winSplit.getContentPane();
            winSplitContentPane.setLayout(null);

            //---- btnMerge ----
            btnMerge.setText("\u5408\u5e76");
            winSplitContentPane.add(btnMerge);
            btnMerge.setBounds(new Rectangle(new Point(85, 35), btnMerge.getPreferredSize()));

            //---- label6 ----
            label6.setText("\u7528\u9017\u53f7\u5206\u9694\u591a\u4e2a");
            winSplitContentPane.add(label6);
            label6.setBounds(new Rectangle(new Point(20, 90), label6.getPreferredSize()));
            winSplitContentPane.add(uiSplitSizes);
            uiSplitSizes.setBounds(20, 110, 105, uiSplitSizes.getPreferredSize().height);

            //---- btnSplit2 ----
            btnSplit2.setText("\u62c6\u5206");
            winSplitContentPane.add(btnSplit2);
            btnSplit2.setBounds(new Rectangle(new Point(125, 110), btnSplit2.getPreferredSize()));

            winSplitContentPane.setPreferredSize(new Dimension(230, 190));
            winSplit.pack();
            winSplit.setLocationRelativeTo(winSplit.getOwner());
        }

        //======== winSave ========
        {
            winSave.setLayout(null);

            //---- label7 ----
            label7.setText("\u6b63\u5728\u4fdd\u5b58\u66f4\u6539");
            winSave.add(label7);
            label7.setBounds(new Rectangle(new Point(45, 50), label7.getPreferredSize()));
            winSave.add(uiSaveProgress);
            uiSaveProgress.setBounds(20, 115, 295, uiSaveProgress.getPreferredSize().height);

            //---- uiCloseSave ----
            uiCloseSave.setText("\u53d6\u6d88");
            winSave.add(uiCloseSave);
            uiCloseSave.setBounds(new Rectangle(new Point(280, 195), uiCloseSave.getPreferredSize()));

            winSave.setPreferredSize(new Dimension(345, 225));
            winSave.pack();
            winSave.setLocationRelativeTo(winSave.getOwner());
        }

        //======== dialog1 ========
        {
            dialog1.setTitle("\u539f\u795e");
            var dialog1ContentPane = dialog1.getContentPane();
            dialog1ContentPane.setLayout(null);
            dialog1ContentPane.add(comboBox1);
            comboBox1.setBounds(65, 85, 130, comboBox1.getPreferredSize().height);
            dialog1ContentPane.add(textField1);
            textField1.setBounds(65, 115, 130, textField1.getPreferredSize().height);
            dialog1ContentPane.add(textField2);
            textField2.setBounds(65, 145, 130, textField2.getPreferredSize().height);

            //---- label8 ----
            label8.setText("\u538b\u7f29\u8d28\u91cf");
            dialog1ContentPane.add(label8);
            label8.setBounds(new Rectangle(new Point(10, 90), label8.getPreferredSize()));

            //---- label9 ----
            label9.setText("\u5206\u5377\u5927\u5c0f");
            dialog1ContentPane.add(label9);
            label9.setBounds(new Rectangle(new Point(10, 120), label9.getPreferredSize()));

            //---- label10 ----
            label10.setText("\u5bc6\u7801");
            dialog1ContentPane.add(label10);
            label10.setBounds(new Rectangle(new Point(10, 150), label10.getPreferredSize()));

            //---- button1 ----
            button1.setText("Waaagh");
            button1.setFont(button1.getFont().deriveFont(28f));
            dialog1ContentPane.add(button1);
            button1.setBounds(new Rectangle(new Point(200, 185), button1.getPreferredSize()));
            dialog1ContentPane.add(textField3);
            textField3.setBounds(65, 15, 265, textField3.getPreferredSize().height);
            dialog1ContentPane.add(textField4);
            textField4.setBounds(65, 50, 265, textField4.getPreferredSize().height);

            //---- button2 ----
            button2.setText("Give me some brain");
            dialog1ContentPane.add(button2);
            button2.setBounds(new Rectangle(new Point(220, 85), button2.getPreferredSize()));

            //---- label11 ----
            label11.setText("from");
            dialog1ContentPane.add(label11);
            label11.setBounds(new Rectangle(new Point(10, 20), label11.getPreferredSize()));

            //---- label12 ----
            label12.setText("to");
            dialog1ContentPane.add(label12);
            label12.setBounds(new Rectangle(new Point(10, 55), label12.getPreferredSize()));

            //---- button3 ----
            button3.setText("\u2026");
            dialog1ContentPane.add(button3);
            button3.setBounds(new Rectangle(new Point(330, 15), button3.getPreferredSize()));

            //---- button4 ----
            button4.setText("\u2026");
            dialog1ContentPane.add(button4);
            button4.setBounds(new Rectangle(new Point(330, 50), button4.getPreferredSize()));

            dialog1ContentPane.setPreferredSize(new Dimension(515, 335));
            dialog1.pack();
            dialog1.setLocationRelativeTo(dialog1.getOwner());
        }
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    private JMenuItem btnOpen;
    private JMenuItem btnOpenNew;
    private JMenuItem btnOpenSelect;
    private JMenuItem btnSave;
    private JMenuItem btnExit;
    private JMenuItem btnRename;
    private JMenuItem btnRepath;
    private JMenuItem btnCopy;
    private JMenuItem btnLink;
    private JMenuItem btnDelete;
    private JMenuItem btnSplit;
    private JMenuItem btnComment;
    private JMenuItem btnEncrypt;
    private JMenuItem btnSelAll;
    private JMenuItem btnSelNone;
    private JMenuItem btnSelRev;
    private JMenuItem btnSelRegex;
    private JMenuItem btnOptions;
    private JMenuItem btnBenchmark;
    private JMenuItem btnHelp;
    private JMenuItem btnAbout;
    private JButton btnAdd;
    private JButton btnExtract;
    private JButton btnTest;
    private JButton btnDelete2;
    private JButton btnInfo;
    private JTable uiTable;
    private JDialog winAbout;
    private JPanel uiLogo;
    private JButton btnCloseAbout;
    private JButton btnGithub;
    private JDialog winEncrypt;
    private JTextField uiOldPass;
    private JTextField uiNewPass;
    private JComboBox<CMBoxValue> uiZipMethod;
    private JSpinner ui7zMethod1;
    private JSpinner ui7zMethod2;
    private JCheckBox uiDecrypt;
    private JButton btnCloseEncrypt;
    private JDialog winExtract;
    private JTextField uiExtractPath;
    private JButton uiSelectPath;
    private JTextPane uiExtractInfo;
    private JSpinner uiThreads;
    private JButton uiExtract;
    private JDialog winGenericText;
    private JTextPane uiGenericText;
    private JButton btnOkText;
    private JButton btnCloseText;
    private JDialog winSplit;
    private JButton btnMerge;
    private JTextField uiSplitSizes;
    private JButton btnSplit2;
    private Window winSave;
    private JProgressBar uiSaveProgress;
    private JButton uiCloseSave;
    private JDialog dialog1;
    private JComboBox<CMBoxValue> comboBox1;
    private JTextField textField1;
    private JTextField textField2;
    private JLabel label8;
    private JLabel label9;
    private JLabel label10;
    private JButton button1;
    private JTextField textField3;
    private JTextField textField4;
    private JButton button2;
    private JLabel label11;
    private JLabel label12;
    private JButton button3;
    private JButton button4;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}