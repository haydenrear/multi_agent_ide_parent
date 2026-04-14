package com.hayden.utilitymodule.reflection;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PathUtil {

    public static String fromPackage(String packageName) {
        return packageName.replace(".", "/");
    }

}
