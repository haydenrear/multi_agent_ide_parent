package com.hayden.utilitymodule;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionFunctions {

    public static <V> List<V> toList(Iterable<V> iterable) {
        return Lists.newArrayList(iterable);
    }

    public static <V> List<V> toList(Iterator<V> iterable) {
        return Lists.newArrayList(iterable);
    }

    public static <V extends Collection> Collection<?> flattenCollection(V v){
        List<Object> collect = (List<Object>) streamFromCollection(v)
                .map(f -> {
                    if (f instanceof Collection c) {
                        return flattenCollection(c);
                    } else return f;
                })
                .collect(Collectors.toList());
        return collect;
    }

    private static <T extends Collection<U>, U> Stream<U> streamFromCollection(T t) {
        return t.stream();
    }


    public static void CopyNestedList(List old, List newList) {
        if(old == null)
            return;
        for(var t : old){
            if(t instanceof List lst){
                List newInnerList = new ArrayList();
                CopyNestedList(lst, newInnerList);
                newList.add(newInnerList);
            }
            else {
                newList.add(t);
            }
        }
    }

}
