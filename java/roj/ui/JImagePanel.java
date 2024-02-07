package roj.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Image panel
 *
 * @author solo6975
 * @since 2021/7/30 20:40
 */
public class JImagePanel extends JComponent {
	public Image img;

	public JImagePanel(Image img) {
		this.img = img;
		setOpaque(true);
	}

	public void paintComponent(Graphics g) {
		super.paintComponents(g);
		g.drawImage(img, 0, 0, this.getWidth(), this.getHeight(), this);
	}
}
