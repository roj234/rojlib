package roj.compiler.library;

import roj.asm.ClassNode;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2025/10/30 18:47
 */
public class CombinedLibrary implements Library {
	private final HashMap<String, Library> libraryByName = new HashMap<>();
	private final List<Library> libraries = new ArrayList<>(), unenumerableLibraries = new ArrayList<>();
	private final Set<String> indexedContent = new HashSet<>();

	@Override public Collection<String> indexedContent() {return indexedContent;}
	@Override public Collection<String> content() {return libraryByName.keySet();}

	public void addLibrary(Library library) {
		var content = library.indexedContent();
		if (content.isEmpty()) {
			unenumerableLibraries.add(library);
		} else {
			for (String className : content)
				libraryByName.put(className, library);
		}
		libraries.add(library);
	}

	@Override public ClassNode get(CharSequence name) {
		var owner = libraryByName.get(name);
		if (owner != null) return owner.get(name);

		for (Library library : unenumerableLibraries) {
			var node = library.get(name);
			if (node != null) return node;
		}

		return null;
	}

	@Override public InputStream getResource(CharSequence name) throws IOException {
		var owner = libraryByName.get(name);
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
		libraryByName.clear();
	}
}