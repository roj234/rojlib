package ilib.client.event;


import net.minecraftforge.fml.common.eventhandler.Event;

public class RenderStageEvent extends Event {
	public final Stage stage;

	public RenderStageEvent(Stage stage) {this.stage = stage;}

	public enum Stage {
		OPAQUE, TRANSLUCENT, OPAQUE_END, TRANSLUCENT_END;

		public boolean beginOrEnd() {
			return ordinal() <= 1;
		}
	}
}
