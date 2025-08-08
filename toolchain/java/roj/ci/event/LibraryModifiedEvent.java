package roj.ci.event;

import org.jetbrains.annotations.Nullable;
import roj.asmx.event.Event;
import roj.ci.Project;

/**
 * @author Roj234
 * @since 2025/2/13 0:30
 */
public class LibraryModifiedEvent extends Event {
	private final Project owner;

	public LibraryModifiedEvent(Project owner) {this.owner = owner;}

	/**
	 * null if Global
	 */
	@Nullable public Project getOwner() {return owner;}
}
