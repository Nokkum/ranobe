package org.ranobe.ranobe.models;


import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import org.ranobe.ranobe.util.SourceUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
public class Novel implements Parcelable {
    public static final Creator<Novel> CREATOR = new Creator<Novel>() {
        @Override
        public Novel createFromParcel(Parcel in) {
            return new Novel(in);
        }

        @Override
        public Novel[] newArray(int size) {
            return new Novel[size];
        }
    };
    @PrimaryKey
    public long id;
    public int sourceId;
    public String name;
    public String cover;
    public String url;
    public String status;
    public String summary;
    public List<String> alternateNames;
    public List<String> authors;
    public List<String> genres;
    public float rating;
    public int year;
    public int lastKnownChapterCount;
    // How many chapters a source's search results say this novel has; 0 when they don't say. Only the Chapter
    // Range filter reads it, so it isn't saved or passed on. (lastKnownChapterCount is the library's baseline
    // for "new chapters" and must stay an exact count, which a search result's figure often isn't.)
    @Ignore
    public int searchChapterCount;
    // other novels the source suggests on this one's page, filled by Source.details(); saved with the cached
    // details as json (see NovelMapper), not as part of the library entry
    @Ignore
    public List<Novel> recommendations = new ArrayList<>();
    // genre name -> the source's page listing that genre, filled by sources that have such pages. Nothing in this
    // app opens them yet (a genre chip has no tap action), so it is neither saved nor passed on.
    @Ignore
    public Map<String, String> genreLinks = new LinkedHashMap<>();

    public Novel(String url) {
        this.id = SourceUtils.generateId(url);
        this.url = url;
    }

    @Ignore
    public Novel(String url, int sourceId) {
        this.id = SourceUtils.generateId(url);
        this.url = url;
        this.sourceId = sourceId;
    }

    protected Novel(Parcel in) {
        id = in.readLong();
        sourceId = in.readInt();
        name = in.readString();
        cover = in.readString();
        url = in.readString();
        status = in.readString();
        summary = in.readString();
        alternateNames = in.createStringArrayList();
        authors = in.createStringArrayList();
        genres = in.createStringArrayList();
        rating = in.readFloat();
        year = in.readInt();
        lastKnownChapterCount = in.readInt();
    }

    @NonNull
    @Override
    public String toString() {
        return "Novel{" +
                "id=" + id +
                ", sourceId=" + sourceId +
                ", name='" + name + '\'' +
                ", cover='" + cover + '\'' +
                ", url='" + url + '\'' +
                ", status='" + status + '\'' +
                ", summary='" + summary + '\'' +
                ", alternateNames=" + alternateNames +
                ", authors=" + authors +
                ", genres=" + genres +
                ", rating=" + rating +
                ", year=" + year +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeLong(id);
        parcel.writeInt(sourceId);
        parcel.writeString(name);
        parcel.writeString(cover);
        parcel.writeString(url);
        parcel.writeString(status);
        parcel.writeString(summary);
        parcel.writeStringList(alternateNames);
        parcel.writeStringList(authors);
        parcel.writeStringList(genres);
        parcel.writeFloat(rating);
        parcel.writeInt(year);
        parcel.writeInt(lastKnownChapterCount);
    }
}
