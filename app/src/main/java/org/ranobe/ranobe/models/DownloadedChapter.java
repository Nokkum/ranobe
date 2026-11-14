package org.ranobe.ranobe.models;

import androidx.annotation.NonNull;

/**
 * What a list of downloaded chapters needs of each one. Not the text: all of a long novel's chapters together
 * can be tens of megabytes, which a list has no use for.
 */
public class DownloadedChapter {
    @NonNull
    public String url = "";
    public String name;
    public float id;
}
