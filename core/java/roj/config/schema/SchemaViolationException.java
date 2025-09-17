package roj.config.schema;

import roj.text.CharList;

/**
 * @author Roj234
 * @since 2025/09/17 15:52
 */
public class SchemaViolationException extends RuntimeException {
	private final CharList paths = new CharList();

	public SchemaViolationException(String message) {
		super(message);
	}

	public SchemaViolationException addPath(String path) {
		this.paths.insert(0, path);
		return this;
	}

	@Override
	public String toString() {
		return "Schema violation: " + getMessage() + "\nAt: $" + paths;
	}
}
