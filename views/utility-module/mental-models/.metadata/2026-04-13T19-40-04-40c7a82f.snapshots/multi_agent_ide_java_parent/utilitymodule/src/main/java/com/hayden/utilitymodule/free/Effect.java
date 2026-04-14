package com.hayden.utilitymodule.free;

public interface Effect {

    interface WithTimeout extends Effect {

        class TimedOutException extends Throwable {}

        boolean isTimedOut();
    }

}
