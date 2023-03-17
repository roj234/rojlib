package ilib.gui.comp;

import ilib.gui.util.ComponentListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class RatioGroup implements ComponentListener {
	protected List<GButton> checkboxes = new ArrayList<>();

	public RatioGroup() {}

	public GButton add(GButton checkbox) {
		checkboxes.add(checkbox);
		checkbox.setListener(this);
		return checkbox;
	}

	@Override
	public void mouseDown(Component c, int mouseX, int mouseY, int button) {
		int i = checkboxes.indexOf(c);
		if (i >= 0) {
			for (int j = 0; j < checkboxes.size(); j++) {
				checkboxes.get(j).setToggled(j == i);
			}
		}
	}

	public void selectFirst() {
		if (!checkboxes.isEmpty()) {
			GButton box = checkboxes.get(0);
			box.setToggled(true);
			box.doAction();
		}
	}
}
