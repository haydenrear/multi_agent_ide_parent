package com.hayden.utilitymodule;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class ByteUtility {


    public static List<byte[]> splitByteArrayByByteValue(byte[] toSplitBy, byte[] toSplit) {
        return splitByteArrayByByteValue(toSplitBy, toSplit, false) ;
    }

    public static List<byte[]> splitByteArrayByByteValue(byte[] toSplitBy, byte[] toSplit, boolean include) {
        int counter = 0;
        int i = -1;
        List<byte[]> result = new ArrayList<>();
        while (counter < toSplit.length) {
            if (bytesMatch(toSplitBy, toSplit, counter)) {
                if (i == -1) {
                    i = counter;
                    if (counter != 0) {
                        byte[] out = new byte[counter];
                        System.arraycopy(toSplit, 0, out, 0, counter);
                        result.add(out);
                    }
                } else {
                    if (include) {
                        byte[] out = new byte[counter - i];
                        System.arraycopy(toSplit, i, out, 0, counter - i);
                        result.add(out);
                    } else {
                        byte[] out = new byte[counter - i - toSplitBy.length];
                        System.arraycopy(toSplit, i + toSplitBy.length , out, 0, counter - i - toSplitBy.length);
                        result.add(out);
                    }
                    i = counter;
                }
            }
            counter += 1;
        }

        if (bytesMatch(toSplitBy, toSplit, i == -1 ? 0 : i)) {
            if (i != -1) {
                if (include) {
                    byte[] out = new byte[counter - i];
                    System.arraycopy(toSplit, i, out, 0, counter - i);
                    result.add(out);
                } else if (toSplitBy.length != toSplit.length) {
                    byte[] out = new byte[counter - i - toSplitBy.length];
                    System.arraycopy(toSplit, i + toSplitBy.length , out, 0, counter - i - toSplitBy.length );
                    result.add(out);
                }
            }
        }

        return result;
    }

    private static boolean bytesMatch(byte[] toSplitBy, byte[] toSplit, int counter) {
        for (int j = 0; j < toSplitBy.length; j++) {
            if (toSplit[counter + j] != toSplitBy[j]) return false;
        }
        return true;
    }
}
