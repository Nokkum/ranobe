package org.ranobe.ranobe.util;

import java.util.Collection;
import java.util.Set;

// What a set of picks in an option row amounts to. Picking nothing narrows nothing, so it is "all"; and
// picking every option narrows nothing either, so that is "all" too.
public final class OptionPicks {
    private OptionPicks() {
    }

    // True when the picks leave the search as wide as "all" / "any": none picked, or every option picked. An id
    // that is no longer an option (a source switched off since) doesn't count against it.
    public static boolean isAll(Set<String> picked, Collection<String> options) {
        return picked.isEmpty() || picked.containsAll(options);
    }
}
