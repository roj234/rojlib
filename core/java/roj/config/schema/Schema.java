package roj.config.schema;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.Generic;
import roj.compiler.LambdaLinker;
import roj.config.mapper.Name;
import roj.config.mapper.Optional;
import roj.config.node.ConfigValue;
import roj.config.node.Type;
import roj.reflect.Reflection;

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
	public String constraints;
	private transient Predicate<ConfigValue> constraintsImpl;
	@Optional(write = Optional.WriteMode.NON_BLANK)
	public Map<String, Schema> properties = Collections.emptyMap();
	public Schema children;
	public Boolean additionalProperties;

	public void checkArgument() {
		if (value != null && !value.mayCastTo(type)) {
			throw new IllegalArgumentException("Default value "+value+" is not type "+type);
		}
		if ((min != null || max != null) && !type.isNumber()) {
			throw new IllegalArgumentException("Only numeric types can have min/max");
		}
		if (pattern != null && type != Type.STRING && !type.isNumber()) {
			throw new IllegalArgumentException("Only string/numeric types can have pattern");
		}
		if ((properties != null && !properties.isEmpty() || additionalProperties != null) && type != Type.MAP) {
			throw new IllegalArgumentException("Only map type can have properties");
		}
		if (children != null && !type.isContainer()) {
			throw new IllegalArgumentException("Only container types can have children");
		}
		if (constraints != null) {
			LambdaLinker lambdaLinker = new LambdaLinker();
			constraintsImpl = lambdaLinker.linkLambda("roj/config/schema/Constraints$"+Reflection.uniqueId(), Generic.generic("java/util/function/Predicate", roj.asm.type.Type.klass("roj/config/node/ConfigValue")), constraints, "value");
		}

		if (properties != null) {
			for (var schema : properties.values()) schema.checkArgument();
		}
		if (children != null) children.checkArgument();
	}

	@Nullable
	public ConfigValue validate(ConfigValue value) {
		if (value == null) {
			if (required == Boolean.TRUE || (this.value == null && required != Boolean.FALSE)) {
				throw new SchemaViolationException("Required value is null");
			} else {
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
		if (constraintsImpl != null && !constraintsImpl.test(value))
			throw new SchemaViolationException("Constraint "+constraints+" is not met");
		if (properties != null && !properties.isEmpty()) {
			var map = value.asMap().raw();
			for (var entry : properties.entrySet()) {
				Schema schema = entry.getValue();
				String name = entry.getKey();
				try {
					ConfigValue newValue = schema.validate(map.get(name));
					if (newValue != null) map.put(name, newValue);
				} catch (SchemaViolationException e) {
					throw e.addPath("."+name);
				}
			}
		}
		if (children != null) {
			if (value.mayCastTo(Type.MAP)) {
				var map = value.asMap().raw();
				for (var entry : map.entrySet()) {
					try {
						ConfigValue newValue = children.validate(entry.getValue());
						if (newValue != null) entry.setValue(newValue);
					} catch (SchemaViolationException e) {
						throw e.addPath("."+entry.getKey());
					}
				}
			} else {
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
		}

		return null;
	}
}
