// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.dvcs.git;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.CommandRunner.CommandException;
import com.google.devtools.moe.client.MoeProblem;
import com.google.devtools.moe.client.Ui;
import com.google.devtools.moe.client.dvcs.AbstractDvcsWriter;
import com.google.devtools.moe.client.repositories.RevisionMetadata;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Git implementation of {@link AbstractDvcsWriter}. For migrations, local commits are made on a
 * branch from master at last equivalence revision.
 */
public class GitWriter extends AbstractDvcsWriter<GitClonedRepository> {

  /**
   * Date format that Git expects when specifying a date to {@code git commit}.
   */
  private static final SimpleDateFormat GIT_DATE_TIME_FORMAT =
      new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z");

  static final String DEFAULT_BRANCH_NAME = "master";
  
  GitWriter(GitClonedRepository revClone) {
    super(revClone);
  }

  @Override
  protected List<String> getIgnoreFilePatterns() {
    return ImmutableList.<String>builder()
        .addAll(revClone.getConfig().getIgnoreFileRes())
        .add("^\\.git.*")
        .build();    
  }

  @Override
  protected void addFile(String relativeFilename) throws CommandException {
    revClone.runGitCommand("add", relativeFilename);
  }
  
  @Override
  protected void modifyFile(String relativeFilename) throws CommandException {
    // Put the modification in the git index.
    revClone.runGitCommand("add", relativeFilename);
  }
  
  @Override
  protected void removeFile(String relativeFilename) throws CommandException {
    revClone.runGitCommand("rm", relativeFilename);
  }
  
  @Override
  protected void commitChanges(RevisionMetadata rm) throws CommandException {
    List<String> args = Lists.newArrayList("commit", "--all", "--message", rm.description);

    // Preserve the author if the fully-formatted version is available.
    if (rm.fullAuthor != null) {
      args.add("--author");
      args.add(rm.fullAuthor);
    }

    // Preserve the timestamp if the normalized time is available.
    if (rm.normalizedDate != null) {
      args.add("--date");
      args.add(GIT_DATE_TIME_FORMAT.format(rm.normalizedDate));
    }

    revClone.runGitCommand(args);
  }
  
  @Override
  protected boolean hasPendingChanges() {
    // NB(yparghi): There may be a simpler way to do this, e.g. git diff or git commit --dry-run
    // using exit codes, but those appear flaky and this is the only reliable way I've found.
    try {
      return !Strings.isNullOrEmpty(revClone.runGitCommand("status", "--short"));
    } catch (CommandException e) {
      throw new MoeProblem("Error in git status: " + e);
    }
  }

  @Override
  public void printPushMessage() {
    String moeBranchName;
    try {
      moeBranchName = revClone.runGitCommand("rev-parse", "--abbrev-ref", "HEAD").trim();
    } catch (CommandException e) {
      throw new MoeProblem("'git' command error: " + e);
    }

    Ui ui = AppContext.RUN.ui;
    ui.info("=====");
    ui.info("MOE changes have been committed to a clone at " + getRoot());
    if (moeBranchName.equals(DEFAULT_BRANCH_NAME)) {
      ui.info("Changes are on " + DEFAULT_BRANCH_NAME + " branch and are ready to push.");
    } else {
      ui.info("Changes are on a new branch. Rebase or merge these changes back onto ");
      ui.info(DEFAULT_BRANCH_NAME + " to push, e.g.:");
      ui.info("git rebase " + DEFAULT_BRANCH_NAME);
      ui.info("git checkout " + DEFAULT_BRANCH_NAME);
      ui.info("git merge " + moeBranchName);
      ui.info("git push");
    }
    ui.info("=====");
  }
}
