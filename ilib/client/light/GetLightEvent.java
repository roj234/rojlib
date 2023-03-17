package ilib.client.light;

import roj.collect.SimpleList;

import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/17 3:35
 */
@Cancelable
public class GetLightEvent extends Event {
	public final List<Light> lights;

	public GetLightEvent(SimpleList<Light> lights) {
		this.lights = lights;
	}
}
