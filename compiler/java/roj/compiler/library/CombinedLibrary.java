package roj.compiler.library;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.collect.ArrayList;
import roj.collect.HashMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2025/10/30 18:47
 */
public class CombinedLibrary implements Library {
	private final HashMap<String, Library> indexedContent = new HashMap<>();
	private final List<Library> libraries = new ArrayList<>(), unenumerableLibraries = new ArrayList<>();

	@Override public Collection<String> indexedContent() {return indexedContent.keySet();}
	@Override public void exportedContent(@Nullable String moduleName, Consumer<String> callback) {
		libraries.forEach(library -> library.exportedContent(moduleName, callback));
	}

	public void addLibrary(Library library) {
		var content = library.indexedContent();
		if (content.isEmpty()) {
			unenumerableLibraries.add(library);
		} else {
			for (String className : content)
				indexedContent.put(className, library);
		}
		libraries.add(library);
	}

	@Override public ClassNode get(CharSequence name) {
		var owner = indexedContent.get(name);
		if (owner != null) return owner.get(name);

		for (Library library : unenumerableLibraries) {
			var node = library.get(name);
			if (node != null) return node;
		}

		return null;
	}

	@Override public InputStream getResource(CharSequence name) throws IOException {
		var owner = indexedContent.get(name);
		if (owner != null) return owner.getResource(name);

		for (Library library : unenumerableLibraries) {
			var in = library.getResource(name);
			if (in != null) return in;
		}

		return null;
	}

	@Override public void close() throws Exception {
		for (var library : libraries) library.close();
		libraries.clear();
		unenumerableLibraries.clear();
		indexedContent.clear();
	}
}