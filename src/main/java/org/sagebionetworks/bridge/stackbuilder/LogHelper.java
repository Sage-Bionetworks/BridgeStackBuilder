package org.sagebionetworks.bridge.stackbuilder;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class LogHelper {
    private static final DateTimeZone LOCAL_TIME_ZONE = DateTimeZone.forID("America/Los_Angeles");

    public static void logError(String msg) {
        System.err.print("ERROR [");
        System.err.print(DateTime.now(LOCAL_TIME_ZONE));
        System.err.print(']');
        System.err.println(msg);
    }

    public static void logError(String msg, Throwable ex) {
        System.err.print("ERROR [");
        System.err.print(DateTime.now(LOCAL_TIME_ZONE));
        System.err.print(']');
        System.err.println(msg);
        ex.printStackTrace();
    }

    public static void logInfo(String msg) {
        System.err.print("INFO [");
        System.out.print(DateTime.now(LOCAL_TIME_ZONE));
        System.out.print(']');
        System.out.println(msg);
    }

    public static void logWarning(String msg) {
        System.err.print("WARN [");
        System.out.print(DateTime.now(LOCAL_TIME_ZONE));
        System.out.print(']');
        System.out.println(msg);
    }
}
