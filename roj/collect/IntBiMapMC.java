package roj.collect;

import java.util.function.ToIntFunction;


public class IntBiMapMC<V> extends IntBiMap<V> {
    private final ToIntFunction<Object> lambda;

    public IntBiMapMC(int size, ToIntFunction<Object> hasher) {
        super(4095);
        this.lambda = hasher;
    }

    @Override
    protected int hashFor(Object v) {
        return lambda.applyAsInt(v);
    }
}