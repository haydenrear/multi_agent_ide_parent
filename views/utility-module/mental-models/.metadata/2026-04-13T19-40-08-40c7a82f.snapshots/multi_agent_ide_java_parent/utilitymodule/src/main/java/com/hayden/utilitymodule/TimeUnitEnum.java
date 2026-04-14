package com.hayden.utilitymodule;

import lombok.Getter;
import org.eclipse.collections.impl.map.primitive.LongKeysMap;
import reactor.core.publisher.Flux;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.NavigableSet;
import java.util.function.Predicate;

@Getter
public enum TimeUnitEnum implements Comparable<TimeUnitEnum> {

    SECOND("1Sec",1000, ChronoUnit.SECONDS),
    ONEMINUTE("1Min",60000, ChronoUnit.MINUTES),
    FIVEMINUTE("5Min",300000, ChronoUnit.MINUTES),
    TENMINUTE("10Min",600000, ChronoUnit.MINUTES),
    FIFTEENMINUTE("15Min",900000, ChronoUnit.MINUTES),
    ONEHOUR("1Hour", 3.6e+6, ChronoUnit.HOURS),
    SIXHOUR("SixHour", 2.16e+7, ChronoUnit.HOURS),
    TWELVEHOUR("12Hour", 4.32e+7, ChronoUnit.HALF_DAYS),
    DAY("1Day", 8.64e+7, ChronoUnit.DAYS),
    WEEK("Wk", 6.048e+8, ChronoUnit.WEEKS),
    MONTH("Mon", 2.628e+9, ChronoUnit.MONTHS),
    YEAR("Year", 3.154e+10, ChronoUnit.YEARS),
    DECADE("Decade", 3.154e+11, ChronoUnit.DECADES);

    public String value;
    public double ms;
    public ChronoUnit chronoUnit;

    TimeUnitEnum(String value, double ms, ChronoUnit chronoUnit){
        this.value = value;
        this.ms = ms;
        this.chronoUnit = chronoUnit;
    }

    public static TimeUnitEnum getTimeUnit(NavigableSet<Long> dates) {

        Long lastEntry = dates.last();
        Long nextToLast = dates.lower(lastEntry);

        if(lastEntry == null || nextToLast == null)
            return TimeUnitEnum.DAY;

        double length = lastEntry - nextToLast;

        return getTimeUnitEnum(length);
    }


    public static TimeUnitEnum getTimeUnitEnum(double dateDelta) {
        return Flux.fromIterable(TimeConverter.entrySet())
                .filter(entry -> entry.getValue().test(dateDelta))
                .map(Map.Entry::getKey)
                .single()
                .toFuture()
                .join();
    }

    public static final Map<TimeUnitEnum, Predicate<Double>> TimeConverter = Map.of(
            TimeUnitEnum.SECOND, length -> length <= 1000,
            TimeUnitEnum.ONEMINUTE, length -> length>1000 & length<=60000,
//            TimeUnitEnum.FIVEMINUTE, length -> length > 300000 && length <= 600000,
//            TimeUnitEnum.TENMINUTE, length -> length > 600000 && length <=900000,
            TimeUnitEnum.FIFTEENMINUTE, length -> length > 900000 && length <= 3.6e+6,
            TimeUnitEnum.ONEHOUR, length -> length > 3.6e+6 && length <= 2.16e+7,
//            TimeUnitEnum.SIXHOUR, length -> length > 2.16e+7 && length <= 4.32e+7,
            TimeUnitEnum.TWELVEHOUR, length -> length > 4.32e+7 && length <= 8.64e+7,
            TimeUnitEnum.DAY, length -> length > 8.64e+7 && length <= 6.048e+8,
            TimeUnitEnum.WEEK, length -> length > 6.048e+8 && length <= 2.628e+9,
            TimeUnitEnum.MONTH, length -> length > 2.628e+9 && length <= 3.154e+10,
            TimeUnitEnum.YEAR, length -> length > 3.154e+10 && length <= 3.154e+11,
            TimeUnitEnum.DECADE, length -> length > 3.154e+11
    );

}
