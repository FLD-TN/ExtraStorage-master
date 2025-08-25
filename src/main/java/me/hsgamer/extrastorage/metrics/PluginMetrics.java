package me.hsgamer.extrastorage.metrics;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

public class PluginMetrics extends Metrics {
    private static final int PLUGIN_ID = 17928; // ExtraStorage's bStats plugin ID

    public PluginMetrics(me.hsgamer.extrastorage.ExtraStorage plugin) {
        super(plugin, PLUGIN_ID);
    }

    public void addCustomChart(SimplePie chart) {
        super.addCustomChart(chart);
    }
}
