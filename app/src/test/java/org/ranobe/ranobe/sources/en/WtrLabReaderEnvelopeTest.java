package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.ranobe.ranobe.sources.ChallengeRequiredException;
import org.ranobe.ranobe.sources.ChapterLockedException;

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
    public void lockedChapterWithNoContentIsReportedAsLockedNotAsSignIn() throws Exception {
        JSONObject envelope = new JSONObject()
                .put("success", true)
                .put("chapter", new JSONObject().put("locked", true));

        IOException error = WtrLab.noContent(envelope);
        assertTrue(error instanceof ChapterLockedException);
        assertTrue(ChapterLockedException.isLocked(error.getMessage()));
        assertFalse(WtrLab.isSignInRequired(error.getMessage()));
    }

    @Test
    public void turnstileDemandFromTheServerIsRecognised() throws Exception {
        JSONObject reply = new JSONObject("{\"success\":false,\"requireTurnstile\":true,"
                + "\"message\":\"Please complete the Turnstile challenge to continue reading\","
                + "\"threshold\":30,\"count\":99}");

        assertTrue(WtrLab.needsChallenge(reply));
        assertEquals("Please complete the Turnstile challenge to continue reading", WtrLab.failureMessage(reply));
        assertFalse(WtrLab.needsChallenge(new JSONObject("{\"success\":false}")));
        assertFalse(WtrLab.needsChallenge(new JSONObject("{\"success\":false,\"requireTurnstile\":false}")));
        assertFalse(WtrLab.needsChallenge(null));
    }

    @Test
    public void challengeErrorIsToldApartFromSignInAndLocked() {
        String message = new ChallengeRequiredException("Please complete the check").getMessage();

        assertTrue(ChallengeRequiredException.isChallengeRequired(message));
        assertFalse(WtrLab.isSignInRequired(message));
        assertFalse(ChapterLockedException.isLocked(message));
        assertFalse(ChallengeRequiredException.isChallengeRequired("WTR-LAB sign-in required to read this chapter."));
        assertFalse(ChallengeRequiredException.isChallengeRequired(null));
    }

    @Test
    public void wtrLabAsksForASteadyPaceDuringBulkDownloads() {
        assertTrue(new WtrLab().requestGapMillis() > 0);
    }

    @Test
    public void sessionReplyTellsSignedInFromSignedOut() {
        assertEquals(Boolean.TRUE, WtrLab.signedOutFromSessionBody("null"));
        assertEquals(Boolean.TRUE, WtrLab.signedOutFromSessionBody(" null \n"));
        assertEquals(Boolean.TRUE, WtrLab.signedOutFromSessionBody("{}"));
        assertEquals(Boolean.FALSE, WtrLab.signedOutFromSessionBody("{\"session\":{\"id\":\"1\"},\"user\":{\"id\":\"2\"}}"));
        assertEquals(Boolean.FALSE, WtrLab.signedOutFromSessionBody("{\"user\":{\"id\":\"2\"}}"));
    }

    @Test
    public void sessionUserNameNeverFallsBackToTheEmailAddress() {
        assertEquals("Mira", WtrLab.sessionUserName("{\"user\":{\"user_name\":\"Mira\",\"email\":\"m@example.com\"}}"));
        assertEquals("Mira K", WtrLab.sessionUserName("{\"user\":{\"name\":\"Mira K\"}}"));
        assertEquals("", WtrLab.sessionUserName("{\"user\":{\"email\":\"m@example.com\"}}"));
        assertEquals("", WtrLab.sessionUserName("null"));
        assertEquals("", WtrLab.sessionUserName(null));
    }

    @Test
    public void unreadableSessionReplyIsNotTreatedAsSignedOut() {
        assertNull(WtrLab.signedOutFromSessionBody(null));
        assertNull(WtrLab.signedOutFromSessionBody(""));
        assertNull(WtrLab.signedOutFromSessionBody("<html>Just a moment...</html>"));
        assertNull(WtrLab.signedOutFromSessionBody("[1,2]"));
    }

    @Test
    public void failureMessageReadsTheUsualFieldsAndSkipsEmptyOnes() throws Exception {
        assertEquals("need login", WtrLab.failureMessage(new JSONObject().put("message", "need login")));
        assertEquals("boom", WtrLab.failureMessage(new JSONObject().put("error", "").put("msg", "boom")));
        assertEquals("", WtrLab.failureMessage(new JSONObject().put("success", false)));
    }

    @Test
    public void longTextIsAbbreviated() {
        assertEquals("abc", WtrLab.abbreviate("abc", 5));
        assertEquals("abcde…", WtrLab.abbreviate("abcdefgh", 5));
    }

    @Test
    public void lockedWordInAnApiErrorIsRecognised() {
        assertTrue(WtrLab.isLockedMessage("chapter_locked"));
        assertTrue(WtrLab.isLockedMessage("This chapter is Locked"));
        assertTrue(WtrLab.isLockedMessage("unlock_required"));
        assertFalse(WtrLab.isLockedMessage("Access blocked by security check"));
        assertFalse(WtrLab.isLockedMessage("clock skew"));
        assertFalse(WtrLab.isLockedMessage("need_login"));
        assertFalse(WtrLab.isLockedMessage(""));
        assertFalse(WtrLab.isLockedMessage(null));
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
