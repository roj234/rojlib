package roj.text.logging.c;

import roj.text.CharList;

import java.util.Map;
import java.util.function.BiConsumer;

public interface LogComponent extends BiConsumer<Map<String, Object>, CharList> {}
