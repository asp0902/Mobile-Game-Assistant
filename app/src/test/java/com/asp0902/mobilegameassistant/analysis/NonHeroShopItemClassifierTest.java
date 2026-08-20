package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NonHeroShopItemClassifierTest {
    @Test
    public void classifiesOnlyMultiSignalArtifactExperience() {
        NonHeroItemClassification result = NonHeroShopItemClassifier.INSTANCE.classify(
                "+4", new SlotVisualEvidence(.02f, false));

        assertEquals(ShopItemType.ARTIFACT_XP, result.getType());
        assertEquals(4, result.getArtifactXpAmount().intValue());
    }

    @Test
    public void classifiesNamedEquipmentAndFactionPackWithoutPrice() {
        assertEquals(ShopItemType.EQUIPMENT, NonHeroShopItemClassifier.INSTANCE.classify("간이 활").getType());
        assertEquals(ShopItemType.FACTION_HERO_PACK,
                NonHeroShopItemClassifier.INSTANCE.classify("랜덤 레오프론 영웅").getType());
    }

    @Test
    public void keepsHeroPortraitUnknown() {
        assertEquals(ShopItemType.UNKNOWN,
                NonHeroShopItemClassifier.INSTANCE.classify("발렌", new SlotVisualEvidence(0f, true)).getType());
    }
}
