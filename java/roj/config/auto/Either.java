package roj.config.auto;

import org.jetbrains.annotations.NotNull;
import roj.util.Helpers;

import java.util.function.Function;

/**
 * 这是给序列化用的，Left和Right必须是可区分类型(akka. 映射，列表，其它(基本类型和字符串)不放回的三选二)
 * 类型为Either的字段不建议为null
 * 不带泛型参数的Either在序列化时会报错
 * @author Roj234
 * @since 2024/6/21 22:37
 */
public final class Either<Left, Right> {
	private Object data;
	private byte state;

	public Either() {}

	@SuppressWarnings("unchecked")
	public static <L, R> Either<L, R> ofLeft(L right) {return (Either<L, R>) new Either<>().setLeft(right);}
	@SuppressWarnings("unchecked")
	public static <L, R> Either<L, R> ofRight(R right) {return (Either<L, R>) new Either<>().setRight(right);}

    public byte getState() {return state;}
	public boolean isNull() {return state == 0;}
	public boolean isLeft() {return state == 1;}
	public boolean isRight() {return state == 2;}
	@SuppressWarnings("unchecked")
	public Left asLeft() {
		return switch (state) {
			case 0 -> null;
			case 1 -> (Left) data;
			default -> throw new IllegalStateException();
		};
	}
	@SuppressWarnings("unchecked")
	public Right asRight() {
		return switch (state) {
			case 0 -> null;
			case 2 -> (Right) data;
			default -> throw new IllegalStateException();
		};
	}
	public Either<Left, Right> setLeft(@NotNull Left data) {
		this.data = data;
		state = 1;
		return this;
	}
	public Either<Left, Right> setRight(@NotNull Right data) {
		this.data = data;
		state = 2;
		return this;
	}
	public Either<Left, Right> clear() {
		this.data = null;
		state = 0;
		return this;
	}

	@Override
	public String toString() {
		return "Either("+(switch (state) {
			default -> "unknown";
			case 1 -> "left";
			case 2 -> "right";
		})+")<"+data+">";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Either<?, ?> either)) return false;

		return data != null ? data.equals(either.data) : either.data == null;
	}

	@Override
	public int hashCode() {return data != null ? data.hashCode()+1 : 0;}

    public <T> T map(Function<Left, T> leftMapper, Function<Right, T> rightMapper) {
    	if (state == 0) return null;
		return state == 1 ? leftMapper.apply(Helpers.cast(data)) : rightMapper.apply(Helpers.cast(data));
	}
}