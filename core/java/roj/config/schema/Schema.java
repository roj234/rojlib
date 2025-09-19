package roj.config.schema;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.ParameterizedType;
import roj.collect.HashSet;
import roj.compiler.runtime.LambdaCompiler;
import roj.config.mapper.Name;
import roj.config.mapper.Optional;
import roj.config.node.ConfigValue;
import roj.config.node.Type;
import roj.text.ParseException;

import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2025/09/17 15:52
 */
@Optional
public class Schema {
	@Optional(read = Optional.ReadMode.REQUIRED, write = Optional.WriteMode.ALWAYS)
	public Type type;
	public String description;
	/**
	 * 如果省略，那么就是必填字段
	 */
	@Name("default")
	public ConfigValue value;
	public Boolean required;
	public Long min, max;
	public Pattern pattern;
	public String constraints, constraintsJavaScript;
	private transient Predicate<ConfigValue> constraintsImpl;
	@Optional(write = Optional.WriteMode.NON_BLANK)
	public Map<String, Schema> properties = Collections.emptyMap();
	public Schema children;
	public Boolean additionalProperties;

	@SuppressWarnings("unchecked")
	public void validateSelf() {
		if (value != null && !value.mayCastTo(type)) {
			throw new SchemaViolationException("Default value "+value+" is not type "+type);
		}
		if ((min != null || max != null) && !type.isNumber()) {
			throw new SchemaViolationException("Only numeric types can have min/max");
		}
		if (pattern != null && type != Type.STRING && !type.isNumber()) {
			throw new SchemaViolationException("Only string/numeric types can have pattern");
		}
		if ((!properties.isEmpty() || additionalProperties != null) && type != Type.MAP) {
			throw new SchemaViolationException("Only map type can have properties");
		}
		if (children != null && !type.isContainer()) {
			throw new SchemaViolationException("Only container types can have children");
		}
		if (constraints != null) {
			try {
				constraintsImpl = (Predicate<ConfigValue>) LambdaCompiler.getInstance().compile(constraints, "schema", ParameterizedType.parameterized("java/util/function/Predicate", roj.asm.type.Type.klass("roj/config/node/ConfigValue")), "value");
			} catch (ParseException e) {
				throw new IllegalArgumentException("Failed to compile constraint "+constraints, e);
			}
		}

		for (var entry : properties.entrySet()) {
			try {
				entry.getValue().validateSelf();
			} catch (SchemaViolationException e) {
				throw e.addPath(".properties."+entry.getKey());
			}
		}
		if (children != null) {
			try {
				children.validateSelf();
			} catch (SchemaViolationException e) {
				throw e.addPath(".children");
			}
		}
	}

	@Nullable
	public ConfigValue validate(ConfigValue value) {
		if (value == null || value.getType() == Type.NULL) {
			if (required == Boolean.TRUE || (this.value == null && required != Boolean.FALSE)) {
				throw new SchemaViolationException("Required value is null");
			} else {
				if (required == Boolean.FALSE) return null;

				return this.value;
			}
		}

		if (!value.mayCastTo(type)) {
			throw new SchemaViolationException("Value type "+value.getType()+" is not "+type);
		}

		if (min != null && value.asLong() < min)
			throw new SchemaViolationException("Min value "+min+" is "+value);
		if (max != null && value.asLong() > max)
			throw new SchemaViolationException("Max value "+max+" is "+value);
		if (pattern != null && !pattern.matcher(value.asString()).matches())
			throw new SchemaViolationException("Pattern "+pattern+" is not matched");
		if (constraintsImpl != null) {
			boolean ok;
			try {
				 ok = constraintsImpl.test(value);
			} catch (Exception e) {
				throw new SchemaViolationException("Constraint "+constraints+" exception", e);
			}

			if (!ok) throw new SchemaViolationException("Constraint "+constraints+" is not met");
		}

		if (type == Type.MAP) {
			var map = value.asMap().raw();
			int matchCount = 0;

			for (var entry : properties.entrySet()) {
				Schema schema = entry.getValue();
				String name = entry.getKey();
				ConfigValue oldValue = map.get(name);
				if (oldValue != null) matchCount++;
				try {
					ConfigValue newValue = schema.validate(oldValue);
					if (newValue != null) {
						if (oldValue == null) matchCount++;
						map.put(name, newValue);
					}
				} catch (SchemaViolationException e) {
					throw e.addPath("."+name);
				}
			}

			if (additionalProperties == Boolean.FALSE && matchCount < map.size()) {
				var extra = new HashSet<>(map.keySet());
				extra.removeAll(properties.keySet());
				throw new SchemaViolationException("Found additional properties "+extra);
			}

			if (children != null) {
				for (var entry : map.entrySet()) {
					try {
						ConfigValue newValue = children.validate(entry.getValue());
						if (newValue != null) entry.setValue(newValue);
					} catch (SchemaViolationException e) {
						throw e.addPath("."+entry.getKey());
					}
				}
			}
		} else if (children != null) {
			var list = value.asList().raw();
			for (int i = 0; i < list.size(); i++) {
				var childValue = list.get(i);
				try {
					var newValue = children.validate(childValue);
					if (newValue != null) list.set(i, newValue);
				} catch (SchemaViolationException e) {
					throw e.addPath("["+i+"]");
				}
			}
		}

		return null;
	}
}
