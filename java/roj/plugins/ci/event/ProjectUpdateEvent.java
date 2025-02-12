package roj.plugins.ci.event;

import roj.asmx.event.Event;
import roj.plugins.ci.Project;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2025/2/13 0013 0:30
 */
public class ProjectUpdateEvent extends Event {
	private final Project project;
	private final CharList libraries;

	public ProjectUpdateEvent(Project project, CharList libraries) {
		this.project = project;
		this.libraries = libraries;
	}

	public Project getProject() {return project;}

	public void add(String name) {libraries.append("<root url=\"jar://$MODULE_DIR$/../data/").append(name).append("!/\" />\n");}
}
