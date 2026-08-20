package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.List;
import kotlin.Pair;
import org.junit.Test;

public class HeroIdentityResolverTest {
    private final List<HeroReference> heroes = Arrays.asList(
            new HeroReference("valen", "발렌", "레오프론", null),
            new HeroReference("perseus", "페르세우스", "레오프론", null),
            new HeroReference("guinness", "귀네스", "레오프론", null),
            new HeroReference("tiloa", "틸로아", "와일더스", null));

    @Test
    public void keepsValenSeparateFromPerseus() {
        HeroRecognitionResult result = HeroIdentityResolver.INSTANCE.resolveText("발렌 레오프론", heroes);
        assertEquals("valen", result.getHeroId());
        assertNotEquals("perseus", result.getHeroId());
    }

    @Test
    public void keepsGuinnessSeparateFromValen() {
        HeroRecognitionResult result = HeroIdentityResolver.INSTANCE.resolveText("귀네스 레오프론", heroes);
        assertEquals("guinness", result.getHeroId());
        assertNotEquals("valen", result.getHeroId());
    }

    @Test
    public void identifiesTiloaFromConfirmedName() {
        assertEquals("tiloa", HeroIdentityResolver.INSTANCE.resolveText("틸로아 와일더스", heroes).getHeroId());
    }

    @Test
    public void keepsClosePortraitScoresUnconfirmed() {
        HeroRecognitionResult result = HeroIdentityResolver.INSTANCE.resolveScores(Arrays.asList(
                new Pair<>(heroes.get(0), 0.86f),
                new Pair<>(heroes.get(1), 0.84f)
        ));

        assertEquals(HeroRecognitionStatus.NEEDS_CONFIRMATION, result.getStatus());
        assertEquals("valen", result.getHeroId());
    }
}
