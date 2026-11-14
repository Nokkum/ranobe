package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WtrLabChallengeClearedTest {
    @Test
    public void successfulAnswerWithoutTheDemandMeansThePageWasPassed() {
        assertTrue(WtrLab.isChallengeCleared("{\"success\":true,\"chapter\":{\"title\":\"Chapter 2\"}}"));
        assertTrue(WtrLab.isChallengeCleared("{\"success\":true,\"requireTurnstile\":false}"));
        assertTrue(WtrLab.isChallengeCleared("  \n{\"success\":true}\n"));
    }

    @Test
    public void theDemandMeansTheCheckIsStillOpen() {
        assertFalse(WtrLab.isChallengeCleared("{\"success\":false,\"requireTurnstile\":true}"));
        assertFalse(WtrLab.isChallengeCleared("{\"requireTurnstile\":true}"));
        assertFalse(WtrLab.isChallengeCleared("{\"success\":true,\"requireTurnstile\":true}"));
    }

    @Test
    public void refusalsSayNothingAboutTheCheck() {
        assertFalse(WtrLab.isChallengeCleared("{\"success\":false,\"error\":\"locked\"}"));
        assertFalse(WtrLab.isChallengeCleared("{}"));
    }

    @Test
    public void anythingThatIsNotAJsonObjectIsIgnored() {
        assertFalse(WtrLab.isChallengeCleared(null));
        assertFalse(WtrLab.isChallengeCleared(""));
        assertFalse(WtrLab.isChallengeCleared("null"));
        assertFalse(WtrLab.isChallengeCleared("[]"));
        assertFalse(WtrLab.isChallengeCleared("<html>Just a moment...</html>"));
        assertFalse(WtrLab.isChallengeCleared("{\"success\":true"));
    }
}
