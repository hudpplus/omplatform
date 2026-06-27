package com.omplatform.trade.service.atomic;

import java.lang.reflect.Field;

public class TestUtils {
    public static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = null;
            Class<?> c = target.getClass();
            while (c != null) {
                try {
                    f = c.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            if (f == null) throw new RuntimeException("Field not found: " + fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

