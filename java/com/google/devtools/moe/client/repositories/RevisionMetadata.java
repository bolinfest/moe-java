// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.repositories;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Holds the metadata associated with a Revision.
 *
 */
public class RevisionMetadata {
  public final String id;
  public final String author;
  public final String date;
  public final String description;
  public final List<Revision> parents;

  /**
   * Author identifier that includes name and email address. Git refers to this
   * as the "standard A U Thor <author@example.com[1]> format." A more concrete
   * example would be {@code Michael Bolin <bolinfest@gmail.com>}.
   * <p>
   * May not be available for all repository types.
   */
  @Nullable
  public final String fullAuthor;

  public RevisionMetadata(String id, String author, String date,
      String description, List<Revision> parents) {
    this(id, author, date, description, parents, null /* fullAuthor */ );
  }

  public RevisionMetadata(String id, String author, String date,
                          String description, List<Revision> parents,
                          @Nullable String fullAuthor) {
    this.id = id;
    this.author = author;
    this.date = date;
    this.description = description;
    this.parents = parents;
    this.fullAuthor = fullAuthor;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, author, date, description, parents, fullAuthor);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof RevisionMetadata) {
      RevisionMetadata revisionMetadataObj = (RevisionMetadata) obj;
      return (Objects.equal(id, revisionMetadataObj.id) &&
              Objects.equal(author, revisionMetadataObj.author) &&
              Objects.equal(date, revisionMetadataObj.date) &&
              Objects.equal(description, revisionMetadataObj.description) &&
              Objects.equal(parents, revisionMetadataObj.parents) &&
              Objects.equal(fullAuthor, revisionMetadataObj.fullAuthor));
    }
    return false;
  }

  @Override
  public String toString() {
    return String.format("id: %s\nauthor: %s\ndate: %s\ndescription: %s\nparents: %s\nfullAuthor: %s",
        id, author, date, description, Joiner.on(",").join(parents), fullAuthor);
  }

  /**
   * @return a single RevisionMetadata concatenating the information in the given List
   */
  public static RevisionMetadata concatenate(
      List<RevisionMetadata> rms, @Nullable Revision migrationFromRev) {
    Preconditions.checkArgument(!rms.isEmpty());

    ImmutableList.Builder<String> idBuilder = ImmutableList.builder();
    ImmutableList.Builder<String> authorBuilder = ImmutableList.builder();
    ImmutableList.Builder<String> dateBuilder = ImmutableList.builder();
    ImmutableList.Builder<String> descBuilder = ImmutableList.builder();
    ImmutableList.Builder<Revision> parentBuilder = ImmutableList.builder();

    // It is not practical to concatenate formatted author strings, so
    // select the last non-null fullAuthor from the sequence of revisions.
    String newFullAuthor = null;

    for (RevisionMetadata rm : rms) {
      idBuilder.add(rm.id);
      authorBuilder.add(rm.author);
      dateBuilder.add(rm.date);
      descBuilder.add(rm.description);
      parentBuilder.addAll(rm.parents);
      if (rm.fullAuthor != null) {
        newFullAuthor = rm.fullAuthor;
      }
    }

    if (migrationFromRev != null) {
      descBuilder.add("Created by MOE: http://code.google.com/p/moe-java\n" +
                      "MOE_MIGRATED_REVID=" + migrationFromRev.revId);
    }

    String newId = Joiner.on(", ").join(idBuilder.build());
    String newAuthor = Joiner.on(", ").join(authorBuilder.build());
    String newDate = Joiner.on(", ").join(dateBuilder.build());
    String newDesc = Joiner.on("\n-------------\n").join(descBuilder.build());
    ImmutableList<Revision> newParents = parentBuilder.build();

    return new RevisionMetadata(newId, newAuthor, newDate, newDesc, newParents, newFullAuthor);
  }
}
