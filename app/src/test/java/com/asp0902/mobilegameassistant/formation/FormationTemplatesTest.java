package com.asp0902.mobilegameassistant.formation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class FormationTemplatesTest {
    @Test
    public void templatesHaveUniqueNormalizedTiles() {
        for (FormationTemplateId id : FormationTemplateId.values()) {
            FormationTemplate template = FormationTemplates.INSTANCE.fromId(id);
            Set<String> ids = new HashSet<>();
            for (FormationTile tile : template.getTiles()) {
                assertTrue(ids.add(tile.getId()));
                assertTrue(tile.getX() >= 0f && tile.getX() <= 1f);
                assertTrue(tile.getY() >= 0f && tile.getY() <= 1f);
            }
            assertTrue(template.getTiles().size() > 0);
            assertEquals(id, template.getId());
        }
    }
}
