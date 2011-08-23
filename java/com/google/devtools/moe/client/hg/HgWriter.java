// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.devtools.moe.client.hg;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.CommandRunner.CommandException;
import com.google.devtools.moe.client.FileSystem;
import com.google.devtools.moe.client.MoeProblem;
import com.google.devtools.moe.client.Utils;
import com.google.devtools.moe.client.codebase.Codebase;
import com.google.devtools.moe.client.repositories.RevisionMetadata;
import com.google.devtools.moe.client.writer.DraftRevision;
import com.google.devtools.moe.client.writer.Writer;
import com.google.devtools.moe.client.writer.WritingError;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Writer implementation for Hg. Construct it with an HgClonedRepository at some revision.
 * putCodebase() will modify that clone per the given Codebase (which could be from any repo, Hg or
 * not).
 *
 */
public class HgWriter implements Writer {

  private final Supplier<HgClonedRepository> revCloneSupplier;
  private final String projectSpace;

  HgWriter(Supplier<HgClonedRepository> revCloneSupplier, String projectSpace) {
    this.revCloneSupplier = revCloneSupplier;
    this.projectSpace = projectSpace;
  }

  @Override
  public File getRoot() {
    return revCloneSupplier.get().getLocalTempDir();
  }

  @Override
  public DraftRevision putCodebase(Codebase c) throws WritingError {
    c.checkProjectSpace(projectSpace);
    Set<String> codebaseFiles = c.getRelativeFilenames();
    Set<String> writerRepoFiles = Utils.filterByRegEx(
        Utils.makeFilenamesRelative(
            AppContext.RUN.fileSystem.findFiles(getRoot()),
            getRoot()),
        // Filter out paths and files that start with '.hg'.
        "^\\.hg.*");

    Set<String> union = Sets.union(codebaseFiles, writerRepoFiles);

    for (String filename : union) {
      try {
        putFile(filename, c);
      } catch (CommandException e) {
        throw new MoeProblem("problem occurred while running hg: " + e.stderr);
      }
    }

    return new HgDraftRevision(revCloneSupplier);
  }

  @Override
  public DraftRevision putCodebase(Codebase c, RevisionMetadata rm) throws WritingError {
    DraftRevision dr = putCodebase(c);
    // Generate a shell script to commit repo with description and author
    String message = String.format("hg commit -m \"%s\" -u \"%s\"\nhg push",
                                   rm.description, rm.author);
    Utils.makeShellScript(message,
        revCloneSupplier.get().getLocalTempDir().getAbsolutePath() + "/hg_commit.sh");

    AppContext.RUN.ui.info(String.format("To commit, run: cd %s && ./hg_commit.sh && cd -",
        revCloneSupplier.get().getLocalTempDir().getAbsolutePath()));
    return dr;
  }

  private void putFile(String relativeFilename, Codebase c) throws CommandException {
    FileSystem fs = AppContext.RUN.fileSystem;
    File src = c.getFile(relativeFilename);
    File dest = new File(getRoot().getAbsolutePath(), relativeFilename);
    boolean srcExists = fs.exists(src);
    boolean destExists = fs.exists(dest);

    if (!srcExists && !destExists) {
      throw new MoeProblem(
          String.format("Neither src nor dests exists. Unreachable code:\n%s\n%s\n%s",
                        relativeFilename, src, dest));
    }

    if (!srcExists) {
      HgRepository.runHgCommand(
          ImmutableList.of("rm", relativeFilename),
          getRoot().getAbsolutePath());
      return;
    }

    try {
      fs.makeDirsForFile(dest);
      fs.copyFile(src, dest);
    } catch (IOException e) {
      throw new MoeProblem(e.getMessage());
    }

    if (!destExists) {
      HgRepository.runHgCommand(
          ImmutableList.of("add", relativeFilename),
          getRoot().getAbsolutePath());
    }
  }
}
