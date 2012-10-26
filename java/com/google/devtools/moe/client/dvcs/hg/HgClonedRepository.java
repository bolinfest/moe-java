// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.dvcs.hg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.CommandRunner.CommandException;
import com.google.devtools.moe.client.FileSystem.Lifetime;
import com.google.devtools.moe.client.Lifetimes;
import com.google.devtools.moe.client.MoeProblem;
import com.google.devtools.moe.client.codebase.LocalClone;
import com.google.devtools.moe.client.project.RepositoryConfig;

import java.io.File;
import java.io.IOException;

/**
 * Hg implementation of LocalClone, i.e. an 'hg clone' to local disk.
 *
 */
public class HgClonedRepository implements LocalClone {

  private final String repositoryName;
  private final RepositoryConfig repositoryConfig;

  /**
   * The location to clone from. If snapshotting a locally modified Writer, this will <em>not</em>
   * be the same as repositoryConfig.getUrl(). Otherwise, it will.
   */
  private final String repositoryUrl;
  private File localCloneTempDir;
  private boolean clonedLocally;

  /** The revision of this clone, an Hg changeset ID */
  private String revId;

  public HgClonedRepository(String repositoryName, RepositoryConfig repositoryConfig) {
    this(repositoryName, repositoryConfig, repositoryConfig.getUrl());
  }

  HgClonedRepository(
      String repositoryName, RepositoryConfig repositoryConfig, String repositoryUrl) {
    this.repositoryName = repositoryName;
    this.repositoryConfig = repositoryConfig;
    this.repositoryUrl = repositoryUrl;
    this.clonedLocally = false;
  }

  @Override
  public String getRepositoryName() {
    return repositoryName;
  }

  @Override
  public RepositoryConfig getConfig() {
    return repositoryConfig;
  }

  @Override
  public File getLocalTempDir() {
    Preconditions.checkState(localCloneTempDir != null);
    return localCloneTempDir;
  }

  @Override
  public void cloneLocallyAtHead(Lifetime cloneLifetime) {
    Preconditions.checkState(!clonedLocally);

    String tempDirName = String.format("hg_clone_%s_", repositoryName);
    localCloneTempDir = AppContext.RUN.fileSystem.getTemporaryDirectory(tempDirName, cloneLifetime);

    try {
      HgRepository.runHgCommand(
          ImmutableList.of(
              "clone",
              "--update=" + HgRevisionHistory.DEFAULT_BRANCH,
              repositoryUrl,
              localCloneTempDir.getAbsolutePath()),
          "" /*workingDirectory*/);
      clonedLocally = true;
      revId = HgRevisionHistory.DEFAULT_BRANCH;
    } catch (CommandException e) {
      throw new MoeProblem(
          "Could not clone from hg repo at " + repositoryUrl + ": " + e.stderr);
    }
  }

  @Override
  public void updateToRevision(String revId) {
    Preconditions.checkState(clonedLocally);
    Preconditions.checkState(HgRevisionHistory.DEFAULT_BRANCH.equals(this.revId));
    try {
      runHgCommand("update", revId);
      this.revId = revId;
    } catch (CommandException e) {
      throw new MoeProblem(
          "Could not clone from hg repo at " + localCloneTempDir + ": " + e.stderr);
    }
  }

  @Override
  public File archiveAtRevision(String revId) {
    Preconditions.checkState(clonedLocally);
    if (Strings.isNullOrEmpty(revId)) {
      revId = HgRevisionHistory.DEFAULT_BRANCH;
    }
    File archiveLocation = AppContext.RUN.fileSystem.getTemporaryDirectory(
        String.format("hg_archive_%s_%s_", repositoryName, revId), Lifetimes.currentTask());
    try {
      HgRepository.runHgCommand(
          ImmutableList.of(
              "archive",
              "--rev=" + revId,
              archiveLocation.getAbsolutePath()),
          localCloneTempDir.getAbsolutePath() /*workingDirectory*/);
      clonedLocally = true;

      AppContext.RUN.fileSystem.deleteRecursively(new File(archiveLocation, ".hg_archival.txt"));
    } catch (CommandException e) {
      throw new MoeProblem(
          "Could not archive hg clone at " + localCloneTempDir.getAbsolutePath() + ": " + e.stderr);
    } catch (IOException e) {
      throw new MoeProblem(
          "IOException archiving clone at " + localCloneTempDir.getAbsolutePath() +
          " to revision " + revId + ": " + e);
    }
    return archiveLocation;
  }

  /**
   * Runs an hg command with the given arguments, in this cloned repository's directory.
   *
   * @param args  a list of arguments to the 'hg' command
   * @return the stdout output of the command
   */
  String runHgCommand(String... args) throws CommandException {
    return AppContext.RUN.cmd.runCommand("hg", ImmutableList.copyOf(args),
        getLocalTempDir().getAbsolutePath() /*workingDirectory*/);
  }
}
