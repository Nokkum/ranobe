package org.ranobe.ranobe.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.ranobe.ranobe.models.Novel;

public class ExportJobTest {
    private static Novel novel() {
        Novel novel = new Novel("https://example.com/book", 7);
        novel.name = "A Book";
        return novel;
    }

    @Test
    public void downloadedOnlySurvivesBeingSavedAndRestored() throws Exception {
        ExportJob job = new ExportJob(novel(), Float.NaN, Float.NaN, true);

        ExportJob back = ExportJob.fromJson(job.toJson());

        assertTrue(back.downloadedOnly);
        assertEquals(job.id, back.id);
        // it is a scope of its own, not a range
        assertFalse(back.hasRange());
    }

    @Test
    public void anOrdinaryExportIsNotDownloadedOnly() throws Exception {
        ExportJob job = new ExportJob(novel(), 3f, 9f);

        assertFalse(job.downloadedOnly);
        ExportJob back = ExportJob.fromJson(job.toJson());
        assertFalse(back.downloadedOnly);
        assertEquals(3f, back.from, 0f);
        assertEquals(9f, back.to, 0f);
    }

    // exports queued by an older version of the app have no such key and must load as the ordinary exports they were
    @Test
    public void jobsSavedBeforeTheOptionExistedLoadAsOrdinaryExports() throws Exception {
        JSONObject saved = new ExportJob(novel(), 1f, 5f).toJson();
        saved.remove("downloadedOnly");

        ExportJob back = ExportJob.fromJson(saved);

        assertFalse(back.downloadedOnly);
        assertEquals(1f, back.from, 0f);
        assertEquals(5f, back.to, 0f);
    }
}
