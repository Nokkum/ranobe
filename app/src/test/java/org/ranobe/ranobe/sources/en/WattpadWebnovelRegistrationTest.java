package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.DataSource;
import org.ranobe.ranobe.sources.SourceManager;

public class WattpadWebnovelRegistrationTest {
    @Test
    public void wattpadIsRegisteredUnderItsOwnId() {
        assertEquals(23, Wattpad.SOURCE_ID);
        assertEquals(Wattpad.class, SourceManager.getSources().get(23));
        DataSource data = new Wattpad().metadata();
        assertEquals(23, data.sourceId);
        assertEquals("Wattpad", data.name);
        assertTrue(data.isNew);
    }

    @Test
    public void webnovelIsRegisteredUnderItsOwnId() {
        assertEquals(24, Webnovel.SOURCE_ID);
        assertEquals(Webnovel.class, SourceManager.getSources().get(24));
        DataSource data = new Webnovel().metadata();
        assertEquals(24, data.sourceId);
        assertEquals("Webnovel", data.name);
        assertTrue(data.isNew);
    }

    // 21 and 22 belong to WTR-LAB and Divine Dao Library; a clash would send novels saved under those ids to the wrong source
    @Test
    public void newIdsDoNotShadowEarlierSources() {
        assertNotEquals(Wattpad.class, SourceManager.getSources().get(21));
        assertNotEquals(Webnovel.class, SourceManager.getSources().get(22));
        assertEquals(WtrLab.class, SourceManager.getSources().get(21));
        assertEquals(DivineDaoLibrary.class, SourceManager.getSources().get(22));
    }
}
