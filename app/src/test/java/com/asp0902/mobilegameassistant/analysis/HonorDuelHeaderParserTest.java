package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class HonorDuelHeaderParserTest {
    @Test
    public void parsesOnlyVisibleHeaderEvidence() {
        // allText is always blocks.joinToString(" "), so the header text must appear as its own
        // block (top strip, centerY well under the .28 first shop row) for the parser to see it.
        java.util.List<OcrBlock> blocks = Arrays.asList(
                new OcrBlock("목표: 9회 승리", .35f, .04f, .55f, .06f),
                new OcrBlock("49", .82f, .12f, .86f, .16f),
                new OcrBlock("3", .80f, .22f, .82f, .24f),
                new OcrBlock("마이다스의 재물", .08f, .70f, .30f, .74f),
                new OcrBlock("4/24", .08f, .70f, .14f, .74f));
        String allText = blocks.stream().map(OcrBlock::getText).reduce((a, b) -> a + " " + b).orElse("");
        HonorDuelHeader header = HonorDuelHeaderParser.INSTANCE.parse(blocks, allText, 2);

        assertEquals(Integer.valueOf(49), header.getCurrency());
        assertEquals(Integer.valueOf(3), header.getRefreshCost());
        assertEquals(Integer.valueOf(9), header.getTargetWins());
        assertEquals("마이다스의 재물", header.getArtifactName());
        assertEquals(0f, header.getConfidence().get(HeaderField.HP), 0f);
    }
}
