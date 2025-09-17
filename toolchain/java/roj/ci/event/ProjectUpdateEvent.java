package roj.ci.event;

import roj.ci.Project;
import roj.event.Event;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2025/2/13 0:30
 */
public class ProjectUpdateEvent extends Event {
	private final Project project;
	private final String relativePath;
	private final CharList libraries;

	public ProjectUpdateEvent(Project project, CharList libraries) {
		this.project = project;
		this.libraries = libraries;
		this.relativePath = "../".repeat(2 + project.getName().length() - project.getName().replace("/", "").length());
	}

	public Project getProject() {return project;}

	public void add(String name) {libraries.append("<root url=\"jar://$MODULE_DIR$/").append(relativePath).append("cache/").append(name).append("!/\" />\n");}
}
