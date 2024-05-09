package com.nanolaba.logging;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SimpleConsoleLogger implements ILogger {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    @Override
    public void log(LogEntry entry) {
        LogEntry.LogEntryLevel level = entry.getLevel();
        Class aClass = entry.getSourceClass();
        Object message = entry.getMessage();
        Object[] args = entry.getArgs();
        Throwable throwable = entry.getThrowable();

        PrintStream out = level == LogEntry.LogEntryLevel.ERROR ? System.err : System.out;

        out.print(level);
        if (dateFormat != null) {
            out.print(" ");
            out.print(dateFormat.format(new Date()));
        }
        if (aClass != null) {
            out.print(" [");
            out.print(aClass.getSimpleName());
            out.print("]");
        }
        if (message != null) {
            out.print(" ");
            if (args == null || args.length == 0) {
                out.print(message);
            } else {
                boolean arg = false;
                int argNumber = 0;
                for (char c : message.toString().toCharArray()) {
                    if (c == '{') {
                        arg = true;
                        out.print(args[argNumber++]);
                    } else if (c == '}') {
                        arg = false;
                    } else if (!arg) {
                        out.print(c);
                    }
                }
            }
        }
        if (throwable != null) {
            out.print(" ");
            throwable.printStackTrace(out);
        } else {
            out.println();
        }
    }

    public SimpleDateFormat getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(SimpleDateFormat dateFormat) {
        this.dateFormat = dateFormat;
    }
}
