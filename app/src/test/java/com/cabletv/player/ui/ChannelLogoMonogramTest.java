package com.cabletv.player.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChannelLogoMonogramTest {

    @Test
    public void latinNameTakesLeadingAlnumRun() {
        assertEquals("CCTV1", ChannelLogoView.monogram("CCTV-1综合"));
        assertEquals("CCTV5", ChannelLogoView.monogram("CCTV-5+体育赛事"));
        assertEquals("HBO", ChannelLogoView.monogram("hbo"));
    }

    @Test
    public void latinRunIsCappedAtFive() {
        assertEquals("PHOEN", ChannelLogoView.monogram("Phoenix TV"));
    }

    @Test
    public void chineseNameTakesFirstTwoChars() {
        assertEquals("湖南", ChannelLogoView.monogram("湖南卫视"));
        assertEquals("重庆", ChannelLogoView.monogram(" 重庆-新闻 "));
        assertEquals("凤", ChannelLogoView.monogram("凤"));
    }

    @Test
    public void emptyNamesGiveEmpty() {
        assertEquals("", ChannelLogoView.monogram(null));
        assertEquals("", ChannelLogoView.monogram(" - "));
    }
}
