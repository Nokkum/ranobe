package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

public class WtrLabReaderEnvelopeTest {
    @Test
    public void relativeContentUrlResolvesAgainstTheSite() {
        assertEquals("https://wtr-lab.com/api/reader/content?id=7",
                WtrLab.resolveContentUrl("/api/reader/content?id=7"));
        assertEquals("https://wtr-lab.com/content/7", WtrLab.resolveContentUrl("content/7"));
    }

    @Test
    public void httpsAndProtocolRelativeUrlsAreKept() {
        assertEquals("https://cdn.example.net/c/7.json", WtrLab.resolveContentUrl("https://cdn.example.net/c/7.json"));
        assertEquals("https://cdn.example.net/c/7.json", WtrLab.resolveContentUrl("//cdn.example.net/c/7.json"));
    }

    @Test
    public void cleartextOrUnexpectedSchemesAreRejected() {
        assertNull(WtrLab.resolveContentUrl("http://wtr-lab.com/c/7"));
        assertNull(WtrLab.resolveContentUrl("ftp://example.net/c/7"));
        assertNull(WtrLab.resolveContentUrl(""));
        assertNull(WtrLab.resolveContentUrl(null));
    }

    @Test
    public void newEnvelopeHasNoInlineTextButExposesTheContentUrl() throws Exception {
        JSONObject envelope = new JSONObject()
                .put("success", true)
                .put("chapter", new JSONObject().put("title", "Chapter 2").put("locked", false))
                .put("tasks", new JSONArray())
                .put("content_url", "/api/reader/content?id=7");

        assertEquals("", WtrLab.payloadContent(envelope));
        assertEquals("https://wtr-lab.com/api/reader/content?id=7", WtrLab.contentUrl(envelope));
    }

    @Test
    public void payloadAtContentUrlIsReadWithGlossary() throws Exception {
        JSONObject payload = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("data", new JSONObject()
                                .put("body", new JSONArray().put("※0⛬ arrived.").put("Second paragraph."))
                                .put("glossary_data", new JSONObject()
                                        .put("terms", new JSONArray().put("The Archivist")))));

        assertEquals("The Archivist arrived.\n\nSecond paragraph.", WtrLab.payloadContent(payload));
    }

    @Test
    public void oldInlineShapeStillWorks() throws Exception {
        JSONObject response = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("data", new JSONObject().put("body", new JSONArray().put("One").put("Two"))));

        assertEquals("One\n\nTwo", WtrLab.payloadContent(response));
    }

    @Test
    public void placeholderInANovelTitleShowsTheDisplayedText() {
        assertEquals("Medival Monster Slayer Saga",
                WtrLab.replacePlaceholders("Medival %{Monster Slayer Saga|V2l0Y2hlcg}"));
    }

    @Test
    public void severalPlaceholdersAndPipesInsideTheShownTextAreHandled() {
        assertEquals("A B | C and D",
                WtrLab.replacePlaceholders("%{A B | C|QQ==} and %{D|RA==}"));
    }

    @Test
    public void textWithoutAPlaceholderIsLeftAlone() {
        assertEquals("100% {not a placeholder | really}",
                WtrLab.replacePlaceholders("100% {not a placeholder | really}"));
        assertEquals("%{no separator}", WtrLab.replacePlaceholders("%{no separator}"));
        assertEquals("", WtrLab.replacePlaceholders(""));
        assertNull(WtrLab.replacePlaceholders(null));
    }

    @Test
    public void lockedChapterWithNoContentAsksForSignIn() throws Exception {
        JSONObject envelope = new JSONObject()
                .put("success", true)
                .put("chapter", new JSONObject().put("locked", true));

        IOException error = WtrLab.noContent(envelope);
        assertTrue(WtrLab.isSignInRequired(error.getMessage()));
    }

    @Test
    public void missingContentIsReportedWithTheResponseFields() throws Exception {
        JSONObject envelope = new JSONObject().put("success", true).put("tasks", new JSONArray());

        IOException error = WtrLab.noContent(envelope);
        assertFalse(WtrLab.isSignInRequired(error.getMessage()));
        assertTrue(error.getMessage().contains("no chapter content"));
        assertTrue(error.getMessage().contains("tasks"));
    }
}
