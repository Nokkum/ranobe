package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.ReadHistory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BackupCodecTest {

    private static Novel novel(String url) {
        Novel novel = new Novel(url);
        novel.sourceId = 17;
        novel.name = "Lord of the Mysteries \u201cLOTM\u201d";
        novel.cover = "https://example.org/cover.jpg";
        novel.status = "Completed";
        novel.summary = "Line one\nLine \"two\" with a backslash \\ and \u7231\u6f5c\u6c34\u7684\u4e4c\u8d3c";
        novel.alternateNames = Arrays.asList("LOTM", "\u8bf8\u754f\u7684\u9ed1\u6697");
        novel.authors = Arrays.asList("Cuttlefish That Loves Diving", "\u7231\u6f5c\u6c34\u7684\u4e4c\u8d3c");
        novel.genres = Arrays.asList("Fantasy", "Mystery");
        novel.rating = 4.3f;
        novel.year = 2018;
        novel.lastKnownChapterCount = 1432;
        return novel;
    }

    private static ReadHistory entry(String url, String novelUrl, long timestamp) {
        ReadHistory entry = new ReadHistory();
        entry.url = url;
        entry.novelUrl = novelUrl;
        entry.novelName = "Lord of the Mysteries";
        entry.cover = "https://example.org/cover.jpg";
        entry.sourceId = 17;
        entry.name = "Chapter 12: Crimson";
        entry.updated = "2026-01-02";
        entry.id = 12.5f;
        entry.position = 42;
        entry.readerOffset = 318;
        entry.timestamp = timestamp;
        entry.content = "the full chapter text that must not be exported";
        return entry;
    }

    private static BackupCodec.BackupException.Reason reasonOfLibrary(String text) {
        try {
            BackupCodec.parseLibrary(text);
        } catch (BackupCodec.BackupException e) {
            return e.reason;
        }
        fail("expected the file to be refused");
        return null;
    }

    private static BackupCodec.BackupException refusedHistory(String text) {
        try {
            BackupCodec.parseHistory(text);
        } catch (BackupCodec.BackupException e) {
            return e;
        }
        fail("expected the file to be refused");
        return null;
    }

    // ---- library ----

    @Test
    public void library_roundTripKeepsEveryField() throws Exception {
        Novel original = novel("https://example.org/novel/lotm/");
        String json = BackupCodec.libraryToJson(Collections.singletonList(original), 1760000000000L);

        BackupCodec.Parsed<Novel> parsed = BackupCodec.parseLibrary(json);

        assertEquals(0, parsed.invalid);
        assertEquals(1, parsed.items.size());
        Novel back = parsed.items.get(0);
        assertEquals(original.url, back.url);
        assertEquals(original.id, back.id);
        assertEquals(17, back.sourceId);
        assertEquals(original.name, back.name);
        assertEquals(original.cover, back.cover);
        assertEquals(original.status, back.status);
        assertEquals(original.summary, back.summary);
        assertEquals(original.alternateNames, back.alternateNames);
        assertEquals(original.authors, back.authors);
        assertEquals(original.genres, back.genres);
        assertEquals(4.3f, back.rating, 0f);
        assertEquals(2018, back.year);
        assertEquals(1432, back.lastKnownChapterCount);
    }

    @Test
    public void library_ratingIsWrittenWithoutBinaryNoise() throws Exception {
        String json = BackupCodec.libraryToJson(Collections.singletonList(novel("https://example.org/a")), 1L);
        assertTrue(json.contains("4.3"));
        assertFalse(json.contains("4.30000"));
    }

    @Test
    public void library_nonFiniteRatingIsWrittenAsZero() throws Exception {
        Novel odd = novel("https://example.org/a");
        odd.rating = Float.NaN;
        Novel infinite = novel("https://example.org/b");
        infinite.rating = Float.POSITIVE_INFINITY;

        String json = BackupCodec.libraryToJson(Arrays.asList(odd, infinite), 1L);

        BackupCodec.Parsed<Novel> parsed = BackupCodec.parseLibrary(json);
        assertEquals(0f, parsed.items.get(0).rating, 0f);
        assertEquals(0f, parsed.items.get(1).rating, 0f);
    }

    @Test
    public void library_nullFieldsAndListsSurvive() throws Exception {
        Novel bare = new Novel("https://example.org/bare");

        BackupCodec.Parsed<Novel> parsed = BackupCodec.parseLibrary(
                BackupCodec.libraryToJson(Collections.singletonList(bare), 1L));

        Novel back = parsed.items.get(0);
        assertNull(back.name);
        assertNull(back.summary);
        assertTrue(back.authors.isEmpty());
        assertTrue(back.genres.isEmpty());
    }

    @Test
    public void library_emptyLibraryIsAValidBackup() throws Exception {
        String json = BackupCodec.libraryToJson(new ArrayList<Novel>(), 1L);

        BackupCodec.Parsed<Novel> parsed = BackupCodec.parseLibrary(json);

        assertEquals(0, parsed.items.size());
        assertEquals(0, parsed.invalid);
    }

    @Test
    public void library_idComesFromTheUrlNotTheFile() throws Exception {
        String json = "{\"app\":\"ranobe\",\"type\":\"library\",\"version\":1,"
                + "\"novels\":[{\"id\":12345,\"url\":\"https://example.org/x\",\"name\":\"X\"}]}";

        Novel novel = BackupCodec.parseLibrary(json).items.get(0);

        assertEquals(SourceUtils.generateId("https://example.org/x").longValue(), novel.id);
    }

    @Test
    public void library_entriesWithoutAUrlAreCountedAsInvalid() throws Exception {
        String json = "{\"app\":\"ranobe\",\"type\":\"library\",\"version\":1,\"novels\":["
                + "{\"name\":\"no url\"},"
                + "{\"url\":\"\"},"
                + "{\"url\":null},"
                + "\"just a string\","
                + "42,"
                + "{\"url\":\"https://example.org/ok\",\"name\":\"ok\"}]}";

        BackupCodec.Parsed<Novel> parsed = BackupCodec.parseLibrary(json);

        assertEquals(1, parsed.items.size());
        assertEquals("https://example.org/ok", parsed.items.get(0).url);
        assertEquals(5, parsed.invalid);
    }

    @Test
    public void library_jsonNullIsNullNotTheWordNull() throws Exception {
        String json = "{\"app\":\"ranobe\",\"type\":\"library\",\"version\":1,"
                + "\"novels\":[{\"url\":\"https://example.org/x\",\"name\":null,\"summary\":null,"
                + "\"authors\":[null,\"A\"]}]}";

        Novel novel = BackupCodec.parseLibrary(json).items.get(0);

        assertNull(novel.name);
        assertNull(novel.summary);
        assertEquals(Collections.singletonList("A"), novel.authors);
    }

    @Test
    public void library_listItemsAreTidied() throws Exception {
        // the database stores a list as tab-separated text, so a tab inside a name would split it
        String json = "{\"app\":\"ranobe\",\"type\":\"library\",\"version\":1,"
                + "\"novels\":[{\"url\":\"https://example.org/x\","
                + "\"genres\":[\" Fantasy \",\"\",\"   \",\"Sci\\tFi\"]}]}";

        Novel novel = BackupCodec.parseLibrary(json).items.get(0);

        assertEquals(Arrays.asList("Fantasy", "Sci Fi"), novel.genres);
    }

    @Test
    public void library_missingNumbersDefaultToZero() throws Exception {
        String json = "{\"app\":\"ranobe\",\"type\":\"library\",\"version\":1,"
                + "\"novels\":[{\"url\":\"https://example.org/x\"}]}";

        Novel novel = BackupCodec.parseLibrary(json).items.get(0);

        assertEquals(0, novel.sourceId);
        assertEquals(0f, novel.rating, 0f);
        assertEquals(0, novel.year);
        assertEquals(0, novel.lastKnownChapterCount);
    }

    // ---- history ----

    @Test
    public void history_roundTripKeepsEveryField() throws Exception {
        ReadHistory original = entry("https://example.org/novel/lotm/chapter-12", "https://example.org/novel/lotm/", 1760000000123L);

        BackupCodec.Parsed<ReadHistory> parsed = BackupCodec.parseHistory(
                BackupCodec.historyToJson(Collections.singletonList(original), 1760000000000L));

        assertEquals(0, parsed.invalid);
        assertEquals(1, parsed.items.size());
        ReadHistory back = parsed.items.get(0);
        assertEquals(original.url, back.url);
        assertEquals(original.novelUrl, back.novelUrl);
        assertEquals(original.novelName, back.novelName);
        assertEquals(original.cover, back.cover);
        assertEquals(17, back.sourceId);
        assertEquals(original.name, back.name);
        assertEquals(original.updated, back.updated);
        assertEquals(12.5f, back.id, 0f);
        assertEquals(42, back.position);
        assertEquals(318, back.readerOffset);
        assertEquals(1760000000123L, back.timestamp);
    }

    @Test
    public void history_savedChapterTextIsNotExported() throws Exception {
        ReadHistory original = entry("https://example.org/c1", "https://example.org/n", 1L);

        String json = BackupCodec.historyToJson(Collections.singletonList(original), 1L);

        assertFalse(json.contains("content"));
        assertFalse(json.contains("must not be exported"));
        assertNull(BackupCodec.parseHistory(json).items.get(0).content);
    }

    @Test
    public void history_entriesNeedBothTheChapterAndTheNovel() throws Exception {
        String json = "{\"app\":\"ranobe\",\"type\":\"history\",\"version\":1,\"entries\":["
                + "{\"url\":\"https://example.org/c1\"},"
                + "{\"novelUrl\":\"https://example.org/n\"},"
                + "{\"url\":\"https://example.org/c2\",\"novelUrl\":\"https://example.org/n\"}]}";

        BackupCodec.Parsed<ReadHistory> parsed = BackupCodec.parseHistory(json);

        assertEquals(1, parsed.items.size());
        assertEquals("https://example.org/c2", parsed.items.get(0).url);
        assertEquals(2, parsed.invalid);
    }

    // ---- the header ----

    @Test
    public void header_textThatIsNotJsonIsNotABackup() {
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP, reasonOfLibrary("hello"));
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP, reasonOfLibrary(""));
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP, reasonOfLibrary(null));
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP, reasonOfLibrary("[1,2,3]"));
    }

    @Test
    public void header_jsonFromAnotherAppIsNotABackup() {
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP,
                reasonOfLibrary("{\"app\":\"mihon\",\"type\":\"library\",\"version\":1,\"novels\":[]}"));
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP,
                reasonOfLibrary("{\"novels\":[]}"));
    }

    @Test
    public void header_missingOrUnknownTypeIsNotABackup() {
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP,
                reasonOfLibrary("{\"app\":\"ranobe\",\"version\":1,\"novels\":[]}"));
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP,
                reasonOfLibrary("{\"app\":\"ranobe\",\"type\":\"settings\",\"version\":1,\"novels\":[]}"));
    }

    @Test
    public void header_missingVersionIsNotABackup() {
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP,
                reasonOfLibrary("{\"app\":\"ranobe\",\"type\":\"library\",\"novels\":[]}"));
    }

    @Test
    public void header_aNewerVersionIsRefusedAsSuch() {
        assertEquals(BackupCodec.BackupException.Reason.NEWER_VERSION,
                reasonOfLibrary("{\"app\":\"ranobe\",\"type\":\"library\",\"version\":99,\"novels\":[]}"));
    }

    @Test
    public void header_aMissingEntryListIsNotABackup() {
        assertEquals(BackupCodec.BackupException.Reason.NOT_BACKUP,
                reasonOfLibrary("{\"app\":\"ranobe\",\"type\":\"library\",\"version\":1}"));
    }

    @Test
    public void header_theWrongKindOfBackupSaysWhatItIs() throws Exception {
        String library = BackupCodec.libraryToJson(Collections.singletonList(novel("https://example.org/a")), 1L);
        String history = BackupCodec.historyToJson(
                Collections.singletonList(entry("https://example.org/c", "https://example.org/n", 1L)), 1L);

        BackupCodec.BackupException asHistory = refusedHistory(library);
        assertEquals(BackupCodec.BackupException.Reason.WRONG_TYPE, asHistory.reason);
        assertEquals(BackupCodec.TYPE_LIBRARY, asHistory.foundType);

        try {
            BackupCodec.parseLibrary(history);
            fail("a history file was accepted as a library");
        } catch (BackupCodec.BackupException e) {
            assertEquals(BackupCodec.BackupException.Reason.WRONG_TYPE, e.reason);
            assertEquals(BackupCodec.TYPE_HISTORY, e.foundType);
        }
    }

    // ---- reading the file ----

    @Test
    public void readText_dropsAByteOrderMark() throws Exception {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(body, 0, bytes, bom.length, body.length);

        assertEquals("{\"a\":1}", BackupCodec.readText(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void readText_decodesUtf8() throws Exception {
        String text = "\u7231\u6f5c\u6c34\u7684\u4e4c\u8d3c \u2014 caf\u00e9";

        assertEquals(text, BackupCodec.readText(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void readText_refusesAFileThatIsTooLarge() throws Exception {
        // endless zeros; the reader must give up at the limit instead of buffering forever
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 0;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                return length;
            }
        };

        try {
            BackupCodec.readText(endless);
            fail("an endless stream was read");
        } catch (BackupCodec.BackupException e) {
            assertEquals(BackupCodec.BackupException.Reason.TOO_LARGE, e.reason);
        }
    }
}
