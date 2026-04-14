package com.hayden.utilitymodule;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class CombinationUtilities {


    private <V> V[] permutations(int k, V[] arr, List<V[]> combos, Supplier<V[]> factory, BiFunction<V[],V[],V[]> copier)
    {


        var toCopy = factory.get();
        toCopy = copier.apply(toCopy, arr);

        if(k == 1)
            return toCopy;

        combos.add(permutations(k-1, toCopy, combos, factory, copier));
        for(int i=0; i<k-1; ++i){
            if(k % 2 == 0){
                V prev = toCopy[k-1];
                toCopy[k-1] = toCopy[i];
                toCopy[i] = prev;
            } else {
                V prev = toCopy[0];
                toCopy[0] = toCopy[k-1];
                toCopy[k-1] = prev;
            }
            combos.add(permutations(k-1, toCopy, combos, factory, copier));
        }
        return toCopy;
    }

}
