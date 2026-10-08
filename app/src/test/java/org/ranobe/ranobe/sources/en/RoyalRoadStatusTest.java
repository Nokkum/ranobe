package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoyalRoadStatusTest {
    @Test
    public void statusesBecomeTheValuesTheSiteFilterTakes() {
        assertEquals("ONGOING", RoyalRoad.statusParam("Ongoing"));
        assertEquals("COMPLETED", RoyalRoad.statusParam("Completed"));
        assertEquals("HIATUS", RoyalRoad.statusParam("Hiatus"));
        assertEquals("DROPPED", RoyalRoad.statusParam("Dropped"));
        assertEquals("DROPPED", RoyalRoad.statusParam("Cancelled"));
    }

    @Test
    public void noKnownStatusMeansNoFilterInTheAddress() {
        assertEquals("", RoyalRoad.statusParam(null));
        assertEquals("", RoyalRoad.statusParam(""));
        assertEquals("", RoyalRoad.statusParam("whatever"));
        assertEquals("", RoyalRoad.statusQuery(null));
        assertEquals("&status=COMPLETED", RoyalRoad.statusQuery("Completed"));
    }

    @Test
    public void royalRoadFiltersStatusItself() {
        RoyalRoad source = new RoyalRoad();

        assertTrue(source.filtersStatusItself());
        assertFalse(source.reportsStatusInResults());
        assertFalse(source.supportsGenreFilter());
    }
}
