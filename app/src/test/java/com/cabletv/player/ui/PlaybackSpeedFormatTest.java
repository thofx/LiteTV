package com.cabletv.player.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackSpeedFormatTest {

    @Test
    public void formatsKilobytesAndMegabytes() {
        assertEquals("0 KB/s", PlaybackStatusView.formatSpeed(-5));
        assertEquals("512 KB/s", PlaybackStatusView.formatSpeed(512 * 1024));
        assertEquals("2.4 MB/s", PlaybackStatusView.formatSpeed((long) (2.4 * 1024 * 1024)));
    }
}
