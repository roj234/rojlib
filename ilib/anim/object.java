package ilib.anim;

import ilib.anim.model.IModel;
import ilib.client.RenderUtils;

import java.util.Collections;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/6/13 13:25
 */
public class object {
	IModel model;
	Keyframes keyframes = new Keyframes("");
	Map<String, object> children = Collections.emptyMap();


	public void render(double ticks) {
		RenderUtils.loadMatrix(keyframes.interpolate(ticks));
		model.render(ticks);
		for (int i = 0; i < children.size(); i++) {
			children.get(i).render(ticks);
		}
	}
}
