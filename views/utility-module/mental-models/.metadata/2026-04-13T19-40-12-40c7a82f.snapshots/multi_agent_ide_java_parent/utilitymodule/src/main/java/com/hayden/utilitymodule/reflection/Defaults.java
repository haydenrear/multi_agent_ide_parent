package com.hayden.utilitymodule.reflection;

import com.hayden.utilitymodule.result.error.SingleError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public interface Defaults {

    Logger log = LoggerFactory.getLogger(Defaults.class);

    static <T> T setDefaultsFrom(T toSetFor, T toSetFrom) {
        if (toSetFor == null && toSetFrom != null) {
            return toSetFrom;
        }

        if (toSetFor == null || toSetFrom == null) {
            throw new IllegalArgumentException("Both objects must be non-null.");
        }

        // Get the class of toSetFor (or toSetFrom, since they should have the same structure)
        Class<?> clazz = toSetFor.getClass();

        // Iterate over all fields of the class
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);  // Allow access to private fields

            try {
                if (field.isAnnotationPresent(NullHasMeaning.class)) {
                    log.debug("Skipping setting of field {}, null has meaning", field.getName());
                    continue;
                }
                // Check if the value in toSetFor is null and if the field exists in toSetFrom
                Object valueInToSetFor = field.get(toSetFor);
                if (valueInToSetFor != null) {
                    continue;
                }

                Object valueInToSetFrom = field.get(toSetFrom);

                if (valueInToSetFrom != null) {
                    // Set the value from toSetFrom into toSetFor
                    field.set(toSetFor, valueInToSetFrom);
                }
            } catch (IllegalAccessException e) {
                // Handle the case where reflection access fails (e.g., if the field is inaccessible)
                log.error("Unable to access field: {}, {}",
                        field.getName(), SingleError.parseStackTraceToString(e));
                if (log.isDebugEnabled()) {
                    throw new RuntimeException(e);
                }

            }
        }

        return toSetFor;
    }


}
