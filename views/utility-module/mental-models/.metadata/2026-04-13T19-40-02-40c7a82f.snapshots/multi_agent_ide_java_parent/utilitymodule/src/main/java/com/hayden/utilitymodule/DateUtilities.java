package com.hayden.utilitymodule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Function;

public class DateUtilities {

    /**
     * returns date from format "yyyy-mm-dd" or December 25th 1980 if thrown.
     * @return
     */
    public static Date ParseDate(String dateFormatyyyydashmmdashdd){
        var ld = LocalDate.parse(dateFormatyyyydashmmdashdd);
        var ldt = ld.atStartOfDay();
        return Date.from(ldt.toInstant(ZoneOffset.UTC));
    }

    public static Map<Date,Double> Collapse(Map<Date,Double> toCollapse, TimeUnitEnum timeUnitEnum, Function<Collection<Double>,Double> conversion) {
        Date date = null;
        List<Double> prevs = new ArrayList<>();
        Map<Date,Double> returnMap =  new HashMap<>();
        for (Map.Entry<Date, Double> d : toCollapse.entrySet()) {
            if (date == null)
                date = d.getKey();
            if(d.getKey().getTime() - date.getTime() > timeUnitEnum.ms) {
                returnMap.put(date, conversion.apply(prevs));
                date = d.getKey();
                prevs.clear();
            } else {
                prevs.add(d.getValue());
            }
        }
        if(prevs.size() != 0) {
            returnMap.put(date, conversion.apply(prevs));
        }
        return returnMap;
    }

    public static Date ParseDate(DateTimeFormatter formatter, String date)
    {
        TemporalAccessor parse = formatter.parse(date);
        return Date.from(Instant.from(parse).atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Date DefaultDate() {
        return Date.from(Instant.ofEpochMilli(346612784000L));
    }


    public static Date DateFromLong(Long epochMilli)
    {
        return Date.from(Instant.ofEpochMilli(epochMilli));
    }

    public static Date RandomDate() {
        Random r = new Random();
        int time = Math.abs((int) Date.from(Instant.now()).getTime());
        return Date.from(
                Instant.ofEpochMilli(
                        r.nextInt(time)
                )
        );
    }

    public static Long RandomEpochMilli() {
        Random r = new Random();
        int time = Math.abs((int) Date.from(Instant.now()).getTime());
        return Instant.ofEpochMilli(
                r.nextInt(time)
        ).toEpochMilli();
    }

    public static Long NowEpochMilli() {
        return new Date().toInstant().toEpochMilli();
    }


}
