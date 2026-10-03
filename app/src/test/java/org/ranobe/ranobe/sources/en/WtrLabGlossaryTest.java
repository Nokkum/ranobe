package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class WtrLabGlossaryTest {
    @Test
    public void chapterContentReplacesZeroAndMultiDigitGlossaryMarkers() throws Exception {
        JSONArray terms = new JSONArray();
        terms.put(new JSONObject().put("term", "Mira"));
        for (int i = 1; i < 11; i++) {
            terms.put("Term " + i);
        }
        terms.put("Captain Vela");

        JSONObject readerData = new JSONObject()
                .put("data", new JSONObject()
                        .put("body", new JSONArray()
                                .put("<b>※0⛬</b> waved at ※11⛬.")))
                .put("glossary_data", new JSONObject().put("terms", terms));

        assertEquals("Mira waved at Captain Vela.", WtrLab.chapterContent(readerData));
    }

    @Test
    public void glossaryTermsSentAsArraysUseTheirFirstEntry() throws Exception {
        JSONArray terms = new JSONArray()
                .put(new JSONArray().put("Zhao Lei").put("赵磊"))
                .put(new JSONArray().put("Professor Lin Yu").put("林宇"));
        JSONObject readerData = new JSONObject()
                .put("data", new JSONObject()
                        .put("body", new JSONArray().put("The smile on <b>※0⛬</b> froze at ※1⛬'s \"Wrong\".")))
                .put("glossary_data", new JSONObject().put("terms", terms));

        assertEquals("The smile on Zhao Lei froze at Professor Lin Yu's \"Wrong\".",
                WtrLab.chapterContent(readerData));
    }

    @Test
    public void alternateClosingMarkAndSitePrefixAreAlsoReplaced() throws Exception {
        JSONArray terms = new JSONArray().put(new JSONArray().put("Zhou Hao"));
        JSONObject readerData = new JSONObject()
                .put("data", new JSONObject()
                        .put("body", new JSONArray().put("wtr-lab ※0〓 nodded and ※0〓 left.")))
                .put("glossary_data", new JSONObject().put("terms", terms));

        assertEquals("Zhou Hao nodded and Zhou Hao left.", WtrLab.chapterContent(readerData));
    }

    @Test
    public void missingGlossaryTermsLeaveMarkersUnchangedRatherThanGuessing() throws Exception {
        JSONObject readerData = new JSONObject()
                .put("data", new JSONObject()
                        .put("body", new JSONArray().put("※11⛬ is still unresolved.")));

        assertEquals("※11⛬ is still unresolved.", WtrLab.chapterContent(readerData));
    }

    @Test
    public void chapterContentAlsoReadsGlossaryWhenNestedWithBody() throws Exception {
        JSONObject readerData = new JSONObject()
                .put("data", new JSONObject()
                        .put("body", "Known as ※0⛬.")
                        .put("glossary_data", new JSONObject()
                                .put("terms", new JSONObject().put("0", "the Archivist"))));

        assertEquals("Known as the Archivist.", WtrLab.chapterContent(readerData));
    }

    @Test
    public void chapterContentCanUseResponseLevelGlossary() throws Exception {
        JSONObject readerData = new JSONObject()
                .put("data", new JSONObject().put("body", "※0⛬ arrived."));
        JSONObject glossaryData = new JSONObject()
                .put("terms", new JSONArray().put("the Archivist"));

        assertEquals("the Archivist arrived.", WtrLab.chapterContent(readerData, glossaryData));
    }
}