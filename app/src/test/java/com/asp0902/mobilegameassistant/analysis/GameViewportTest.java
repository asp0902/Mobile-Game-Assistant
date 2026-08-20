package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GameViewportTest {
    @Test
    public void mapsSlotCoordinatesInsideViewport() {
        GameViewport viewport = new GameViewport(0f, .05f, 1f, 1f);
        NormalizedRect bounds = viewport.toFrame(
                new HonorDuelShopAnalyzer.SlotBounds(.03f, .28f, .25f, .47f, .38f));

        assertEquals(.316f, bounds.getTop(), .001f);
        assertEquals(.4965f, bounds.getBottom(), .001f);
    }
}
