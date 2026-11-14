package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NovelPingWatermarkTest {
    private static String clean(String text) {
        return NovelPing.withoutWatermark(text);
    }

    // the line from the chapter
    @Test
    public void theHostedAtLineGoes() {
        assertEquals("Wang Teng smiled faintly.",
                clean("Wang Teng smiled faintly. This story is hosted at novelping(.)com."));
    }

    // a paragraph that was nothing else must not leave a gap
    @Test
    public void aParagraphOfNothingElseIsDropped() {
        assertEquals("First.\n\n\nSecond.",
                clean("First.\n\n\nThis story is hosted at novelping(.)com.\n\n\nSecond."));
    }

    @Test
    public void betweenTwoSentencesTheOtherTwoStay() {
        assertEquals("One. Two.", clean("One. This story is hosted at novelping(.)com. Two."));
    }

    @Test
    public void theOtherWaysOfHidingTheDotAreCaughtToo() {
        assertEquals("Done.", clean("Done. This story is hosted at novelping[.]com."));
        assertEquals("Done.", clean("Done. This story is hosted at novelping.com."));
        assertEquals("Done.", clean("Done. This story is hosted at NovelPing dot com."));
        assertEquals("Done.", clean("Done. This story is hosted at novelping(dot)com."));
        assertEquals("Done.", clean("Done. This story is hosted at novel ping(.)com."));
    }

    @Test
    public void otherAdvertWordingsGoWithTheirWholeSentence() {
        assertEquals("He left the room.", clean("He left the room. Read the latest chapters at novelping(.)com"));
        assertEquals("He left the room.", clean("He left the room. Visit novelping(.)com for the best reading experience!"));
        assertEquals("He left the room.", clean("He left the room. The original is on novelping(.)com."));
    }

    // the known phrase also goes when the sentence before it was left without a full stop
    @Test
    public void theKnownPhraseGoesEvenWithoutAFullStopBeforeIt() {
        assertEquals("Wang Teng smiled faintly", clean("Wang Teng smiled faintly This story is hosted at novelping(.)com."));
    }

    // an advert must never take the story's own sentences with it
    @Test
    public void thePrecedingSentenceIsNeverTouched() {
        assertEquals("Wang Teng smiled faintly.", clean("Wang Teng smiled faintly. Read at novelping(.)com"));
    }

    @Test
    public void everyAdvertInAChapterGoes() {
        assertEquals("A.\n\n\nB.\n\n\nC.", clean("A. This story is hosted at novelping(.)com.\n\n\nB.\n\n\nC. Read at novelping(.)com"));
    }

    // the text as chapter() builds it: a space before each paragraph break, and the advert as the last paragraph too
    @Test
    public void theShapeChapterBuildsIsHandled() {
        String built = "Wang Teng smiled faintly. This story is hosted at novelping(.)com. \n\n\nHe nodded. \n\n\n"
                + "This story is hosted at novelping(.)com.";
        assertEquals("Wang Teng smiled faintly.\n\n\nHe nodded.", clean(built));
    }

    @Test
    public void ordinaryTextIsLeftAlone() {
        String text = "He said \"ping\" and left. The novel ended. They played ping pong.\n\n\nA second paragraph.";
        assertEquals(text, clean(text));
        assertEquals("The company dot com boom.", clean("The company dot com boom."));
    }

    @Test
    public void emptyAndNullAreFine() {
        assertEquals("", clean(""));
        assertEquals(null, clean(null));
    }
}
