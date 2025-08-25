package me.hsgamer.extrastorage.manager;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.data.log.Log;
import java.io.File;

public class LogManager {
    private final Log log;

    public LogManager(ExtraStorage plugin) {
        this.log = new Log(plugin);
    }

    public void cleanup(long currentTime) {
        if (log.initLogFile()) {
            // Cleanup to be implemented
        }
    }

    public Log getLog() {
        return log;
    }
}
