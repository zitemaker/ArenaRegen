package com.zitemaker.utils;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public class Helpers {

    public Helpers(){

    }

    public static <E extends Enum<E>, V> Map<E, V> suppliedMap(Class<E> clazz, Function<E, V> mapper) {
        Map<E, V> map = new EnumMap<>(clazz);
        for (E e : clazz.getEnumConstants()) {
            map.put(e, mapper.apply(e));
        }

        return map;
    }
}
