// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.dvcs;

import static org.easymock.EasyMock.expect;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.FileSystem;
import com.google.devtools.moe.client.codebase.Codebase;
import com.google.devtools.moe.client.codebase.LocalClone;
import com.google.devtools.moe.client.project.RepositoryConfig;
import com.google.devtools.moe.client.repositories.Revision;
import com.google.devtools.moe.client.repositories.RevisionHistory;
import com.google.devtools.moe.client.testing.AppContextForTesting;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;

import java.io.File;
import java.util.Collections;

import junit.framework.TestCase;

/**
 * Tests for AbstractDvcsCodebaseCreator, esp. that create() archives at the right revision.
 *
 */
public class AbstractDvcsCodebaseCreatorTest extends TestCase {

  private static final String MOCK_REPO_NAME = "mockrepo";

  private IMocksControl control;
  private LocalClone mockRepo;
  private RevisionHistory mockRevHistory;
  private RepositoryConfig mockRepoConfig;
  private AbstractDvcsCodebaseCreator codebaseCreator;

  @Override protected void setUp() throws Exception {
    super.setUp();

    AppContextForTesting.initForTest();
    control = EasyMock.createControl();
    AppContext.RUN.fileSystem = control.createMock(FileSystem.class);
    mockRepo = control.createMock(LocalClone.class);
    mockRevHistory = control.createMock(RevisionHistory.class);
    mockRepoConfig = control.createMock(RepositoryConfig.class);

    expect(mockRepo.getConfig()).andReturn(mockRepoConfig).anyTimes();
    expect(mockRepoConfig.getIgnoreFileRes()).andReturn(ImmutableList.<String>of());
    expect(mockRepo.getRepositoryName()).andReturn(MOCK_REPO_NAME);

    codebaseCreator = new AbstractDvcsCodebaseCreator(
        Suppliers.ofInstance(mockRepo),
        mockRevHistory,
        "public") {
          @Override protected LocalClone cloneAtLocalRoot(String localroot) {
            throw new UnsupportedOperationException();
          }
    };
  }

  public void testCreate_noGivenRev() throws Exception {
    String archiveTempDir = "/tmp/git_archive_mockrepo_head";
    // Short-circuit Utils.filterFilesByPredicate(ignore_files_re).
    expect(AppContext.RUN.fileSystem.findFiles(new File(archiveTempDir)))
        .andReturn(ImmutableSet.<File>of());

    expect(mockRevHistory.findHighestRevision(null))
        .andReturn(new Revision("mock head changeset ID", MOCK_REPO_NAME));
    expect(mockRepo.archiveAtRevision("mock head changeset ID"))
        .andReturn(new File(archiveTempDir));

    control.replay();

    Codebase codebase = codebaseCreator.create(Collections.<String, String>emptyMap());

    assertEquals(new File(archiveTempDir), codebase.getPath());
    assertEquals("public", codebase.getProjectSpace());
    assertEquals("mockrepo", codebase.getExpression().toString());

    control.verify();
  }

  public void testCreate_givenRev() throws Exception {
    String givenRev = "givenrev";
    String archiveTempDir = "/tmp/git_reclone_mockrepo_head_" + givenRev;
    // Short-circuit Utils.filterFilesByPredicate(ignore_files_re).
    expect(AppContext.RUN.fileSystem.findFiles(new File(archiveTempDir)))
        .andReturn(ImmutableSet.<File>of());

    expect(mockRevHistory.findHighestRevision(givenRev))
        .andReturn(new Revision(givenRev, MOCK_REPO_NAME));
    expect(mockRepo.archiveAtRevision(givenRev)).andReturn(new File(archiveTempDir));

    control.replay();

    Codebase codebase = codebaseCreator.create(ImmutableMap.of("revision", givenRev));

    assertEquals(new File(archiveTempDir), codebase.getPath());
    assertEquals("public", codebase.getProjectSpace());
    assertEquals("mockrepo(revision=" + givenRev + ")", codebase.getExpression().toString());

    control.verify();
  }
}
