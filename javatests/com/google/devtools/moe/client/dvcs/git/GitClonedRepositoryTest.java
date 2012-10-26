// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.dvcs.git;

import static org.easymock.EasyMock.expect;

import com.google.common.collect.ImmutableList;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.CommandRunner;
import com.google.devtools.moe.client.FileSystem;
import com.google.devtools.moe.client.FileSystem.Lifetime;
import com.google.devtools.moe.client.Lifetimes;
import com.google.devtools.moe.client.project.RepositoryConfig;
import com.google.devtools.moe.client.testing.AppContextForTesting;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;

import java.io.File;

import junit.framework.TestCase;

/**
 * Unit tests for GitClonedRepository.
 */
public class GitClonedRepositoryTest extends TestCase {
  
  IMocksControl control;
  FileSystem mockFS;
  CommandRunner cmd;
  RepositoryConfig repositoryConfig;
  
  final String repositoryName = "mockrepo";
  final String repositoryURL = "http://foo/git";
  final String localCloneTempDir = "/tmp/git_clone_mockrepo_12345";
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    AppContextForTesting.initForTest();
    control = EasyMock.createControl();
    repositoryConfig = control.createMock(RepositoryConfig.class);
    expect(repositoryConfig.getUrl()).andReturn(repositoryURL).anyTimes();
    mockFS = control.createMock(FileSystem.class);
    cmd = control.createMock(CommandRunner.class);
    AppContext.RUN.fileSystem = mockFS;
    AppContext.RUN.cmd = cmd;
  }

  private void expectCloneLocally() throws Exception {
    // The Lifetimes of clones in these tests are arbitrary since we're not really creating any
    // temp dirs and we're not testing clean-up.
    expect(mockFS.getTemporaryDirectory(
        EasyMock.eq("git_clone_" + repositoryName + "_"), EasyMock.<Lifetime>anyObject()))
        .andReturn(new File(localCloneTempDir));

    expect(cmd.runCommand(
        "git",
        ImmutableList.<String>of("clone", repositoryURL, localCloneTempDir),
        "" /*workingDirectory*/))
        .andReturn("git clone ok (mock output)");
  }

  public void testCloneLocally() throws Exception {
    expectCloneLocally();

    control.replay();
    GitClonedRepository repo = new GitClonedRepository(repositoryName, repositoryConfig);
    repo.cloneLocallyAtHead(Lifetimes.persistent());
    assertEquals(repositoryName, repo.getRepositoryName());
    assertEquals(repositoryURL, repo.getConfig().getUrl());
    assertEquals(localCloneTempDir, repo.getLocalTempDir().getAbsolutePath());

    try {
      repo.cloneLocallyAtHead(Lifetimes.persistent());
      fail("Re-cloning repo succeeded unexpectedly.");
    } catch (IllegalStateException expected) {}

    control.verify();
  }

  public void testUpdateToRevId_nonHeadRevId() throws Exception {
    String updateRevId = "notHead";
    String headRevId = "head";

    expectCloneLocally();
    
    expect(cmd.runCommand(
        "git",
        ImmutableList.of("show-ref", "--heads", "--hash", GitWriter.DEFAULT_BRANCH_NAME),
        localCloneTempDir)).andReturn(headRevId);

    // Updating to revision other than head, so create a branch.
    expect(cmd.runCommand(
        "git",
        ImmutableList.of("checkout", updateRevId, "-b", "moe_writing_branch_from_" + updateRevId),
        "/tmp/git_clone_mockrepo_12345")).andReturn(headRevId);

    control.replay();
    GitClonedRepository repo = new GitClonedRepository(repositoryName, repositoryConfig);
    repo.cloneLocallyAtHead(Lifetimes.persistent());
    repo.updateToRevision(updateRevId);
    control.verify();
  }
  
  public void testUpdateToRevId_headRevId() throws Exception {
    String updateRevId = "head";
    String headRevId = "head";

    expectCloneLocally();
    
    expect(cmd.runCommand(
        "git",
        ImmutableList.of("show-ref", "--heads", "--hash", GitWriter.DEFAULT_BRANCH_NAME),
        localCloneTempDir)).andReturn(headRevId);

    // No branch creation expected.

    control.replay();
    GitClonedRepository repo = new GitClonedRepository(repositoryName, repositoryConfig);
    repo.cloneLocallyAtHead(Lifetimes.persistent());
    repo.updateToRevision(updateRevId);
    control.verify();
  }
}
