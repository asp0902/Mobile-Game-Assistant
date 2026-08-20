package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class HonorDuelHeaderParserTest {
    @Test
    public void parsesOnlyVisibleHeaderEvidence() {
        HonorDuelHeader header = HonorDuelHeaderParser.INSTANCE.parse(
                Arrays.asList(
                        new OcrBlock("49", .82f, .12f, .86f, .16f),
                        new OcrBlock("3", .80f, .25f, .82f, .28f),
                        new OcrBlock("4/24", .08f, .70f, .14f, .74f)),
                "목표: 9회 승리 마이다스의 재물",
                2);

        assertEquals(Integer.valueOf(49), header.getCurrency());
        assertEquals(Integer.valueOf(3), header.getRefreshCost());
        assertEquals(Integer.valueOf(9), header.getTargetWins());
        assertEquals("마이다스의 재물", header.getArtifactName());
        assertEquals(0f, header.getConfidence().get(HeaderField.HP), 0f);
    }
}
