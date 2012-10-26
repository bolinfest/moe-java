// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.dvcs.git;

import static org.easymock.EasyMock.expect;

import com.google.common.base.Joiner;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.moe.client.CommandRunner.CommandException;
import com.google.devtools.moe.client.MoeProblem;
import com.google.devtools.moe.client.database.Equivalence;
import com.google.devtools.moe.client.database.EquivalenceMatcher;
import com.google.devtools.moe.client.database.FileDb;
import com.google.devtools.moe.client.project.RepositoryConfig;
import com.google.devtools.moe.client.repositories.Revision;
import com.google.devtools.moe.client.repositories.RevisionMatcher;
import com.google.devtools.moe.client.repositories.RevisionMetadata;
import com.google.devtools.moe.client.testing.AppContextForTesting;
import com.google.devtools.moe.client.testing.DummyDb;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;
import org.easymock.IMocksControl;

import java.util.Set;

/**
 * Unit tests for GitRevisionHistory.
 */
public class GitRevisionHistoryTest extends TestCase {

  private static final String LOG_FORMAT_COMMIT_ID = "%H";
  private static final Joiner METADATA_JOINER = Joiner.on(GitRevisionHistory.LOG_DELIMITER); 
  private static final String LOG_FORMAT_ALL_METADATA =
      METADATA_JOINER.join("%H", "%an", "%ad", "%P", "%B");
  
  private IMocksControl control;
  private String repositoryName = "mockrepo";
  private RepositoryConfig repositoryConfig;
  private String localCloneTempDir = "/tmp/git_tipclone_mockrepo_12345";

  @Override public void setUp() {
    AppContextForTesting.initForTest();
    control = EasyMock.createControl();
    repositoryConfig = control.createMock(RepositoryConfig.class);
    expect(repositoryConfig.getUrl()).andReturn(localCloneTempDir).anyTimes();
  }

  @Override public void tearDown() {
    control = null;
  }

  private GitClonedRepository mockClonedRepo(String repoName) {
    GitClonedRepository mockRepo = control.createMock(GitClonedRepository.class);
    expect(mockRepo.getRepositoryName()).andReturn(repoName).anyTimes();
    return mockRepo;
  }
  
  private IExpectationSetters<String> expectLogCommand(
      GitClonedRepository mockRepo, String logFormat, String revName) throws CommandException {
    return expect(mockRepo.runGitCommand(
        "log",
        "--max-count=1",
        "--format=" + logFormat,
        revName));
  }
  
  public void testFindHighestRevision() throws Exception {
    GitClonedRepository mockRepo = mockClonedRepo(repositoryName);

    expectLogCommand(mockRepo, LOG_FORMAT_COMMIT_ID, "HEAD").andReturn("mockHashID");

    control.replay();

    GitRevisionHistory rh = new GitRevisionHistory(Suppliers.ofInstance(mockRepo));
    Revision rev = rh.findHighestRevision(null);
    assertEquals(repositoryName, rev.repositoryName);
    assertEquals("mockHashID", rev.revId);

    control.verify();
  }

  public void testFindHighestRevision_nonExistentHashThrows() throws Exception {
    GitClonedRepository mockRepo = mockClonedRepo(repositoryName);

    expectLogCommand(mockRepo, LOG_FORMAT_COMMIT_ID, "bogusHash")
        .andThrow(
            new CommandException(
                "git",
                ImmutableList.<String>of("mock args"),
                "mock stdout",
                "mock stderr: unknown revision",
                1));

    control.replay();

    try {
      GitRevisionHistory rh = new GitRevisionHistory(Suppliers.ofInstance(mockRepo));
      Revision rev = rh.findHighestRevision("bogusHash");
      fail("'git log' didn't fail on bogus hash ID");
    } catch (MoeProblem expected) {}

    control.verify();
  }

  public void testGetMetadata() throws Exception {
    GitClonedRepository mockRepo = mockClonedRepo(repositoryName);

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "f00d").andReturn(METADATA_JOINER.join(
        "f00d", "foo@google.com", "date", "d34d b33f", "description\n"));

    control.replay();

    GitRevisionHistory rh = new GitRevisionHistory(Suppliers.ofInstance(mockRepo));
    RevisionMetadata result = rh.getMetadata(new Revision("f00d", "mockrepo"));
    assertEquals("f00d", result.id);
    assertEquals("foo@google.com", result.author);
    assertEquals("date", result.date);
    assertEquals("description\n", result.description);
    assertEquals(ImmutableList.of(new Revision("d34d", repositoryName),
                                  new Revision("b33f", repositoryName)),
                 result.parents);

    control.verify();
  }

  public void testParseMetadata_multiLine() {
    GitRevisionHistory rh = new GitRevisionHistory(Suppliers.ofInstance(
        new GitClonedRepository(repositoryName, repositoryConfig)));
    RevisionMetadata rm = rh.parseMetadata(METADATA_JOINER.join(
            "f00d", "foo@google.com", "date1", "d34d b33f", "desc with \n\nmultiple lines\n"));
    assertEquals("f00d", rm.id);
    assertEquals("foo@google.com", rm.author);
    assertEquals("date1", rm.date);
    assertEquals("desc with \n\nmultiple lines\n", rm.description);
    assertEquals(ImmutableList.of(new Revision("d34d", repositoryName),
                                  new Revision("b33f", repositoryName)),
                 rm.parents);
  }

  /**
   * Mocks most of gh.findHeadRevisions(). Used by both of the next tests.
   *
   * @param mockRepo the mock repository to use
   */
  private void mockBranchCommand(GitClonedRepository mockRepo) throws CommandException {
    expect(mockRepo.runGitCommand("branch")).andReturn("* mockBranchID1\n  mockBranchID2");
    // Now mock getting info on each of the branches.
    expectLogCommand(mockRepo, LOG_FORMAT_COMMIT_ID, "mockBranchID1").andReturn("mockHashID1");
    expectLogCommand(mockRepo, LOG_FORMAT_COMMIT_ID, "mockBranchID2").andReturn("mockHashID2");
  }

  public void testFindHeadRevisions() throws Exception {
    GitClonedRepository mockRepo = mockClonedRepo(repositoryName);
    mockBranchCommand(mockRepo);

    control.replay();

    GitRevisionHistory rh = new GitRevisionHistory(Suppliers.ofInstance(mockRepo));
    ImmutableList<Revision> revs = ImmutableList.copyOf(rh.findHeadRevisions());
    assertEquals(repositoryName, revs.get(0).repositoryName);
    assertEquals("mockHashID1", revs.get(0).revId);
    assertEquals(repositoryName, revs.get(1).repositoryName);
    assertEquals("mockHashID2", revs.get(1).revId);

    control.verify();
  }

  public void testFindNewRevisions_all() throws Exception {
    GitClonedRepository mockRepo = mockClonedRepo(repositoryName);
    mockBranchCommand(mockRepo);
    DummyDb db = new DummyDb(false);

    // Breadth-first search order.
    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "mockHashID1")
        .andReturn(METADATA_JOINER.join(
            "mockHashID1", "uid@google.com", "date", "parent", "description"));

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "mockHashID2")
        .andReturn(METADATA_JOINER.join(
            "mockHashID2", "uid@google.com", "date", "", "description"));

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "parent")
        .andReturn(METADATA_JOINER.join("parent", "uid@google.com", "date", "", "description"));

    control.replay();

    GitRevisionHistory rh = new GitRevisionHistory(Suppliers.ofInstance(mockRepo));
    RevisionMatcher matcher = new EquivalenceMatcher("mockRepo", db);
    ImmutableList<Revision> newRevisions =
        ImmutableList.copyOf(rh.findRevisions(null, matcher));
    assertEquals(3, newRevisions.size());
    assertEquals(repositoryName, newRevisions.get(0).repositoryName);
    assertEquals("mockHashID1", newRevisions.get(0).revId);
    assertEquals(repositoryName, newRevisions.get(1).repositoryName);
    assertEquals("mockHashID2", newRevisions.get(1).revId);
    assertEquals(repositoryName, newRevisions.get(2).repositoryName);
    assertEquals("parent", newRevisions.get(2).revId);

    control.verify();
  }

  public void testFindNewRevisions_pruned() throws Exception {
    GitClonedRepository mockRepo = mockClonedRepo(repositoryName);
    mockBranchCommand(mockRepo);

    // Create a fake db that has equivalences for parent1, so that
    // parent1 isn't included in the output.
    DummyDb db = new DummyDb(true) {
      @Override public Set<Revision> findEquivalences(
          Revision revision, String otherRepository) {
        if (revision.revId.equals("parent1")) {
          return super.findEquivalences(revision, otherRepository);
        } else {
          return ImmutableSet.of();
        }
      }
    };

    // Breadth-first search order.
    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "mockHashID1")
        .andReturn(METADATA_JOINER.join(
            "mockHashID1", "uid@google.com", "date", "parent1", "description"));

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "mockHashID2")
        .andReturn(METADATA_JOINER.join(
            "mockHashID2", "uid@google.com", "date", "", "description"));

    control.replay();

    GitRevisionHistory rh = new GitRevisionHistory(Suppliers.ofInstance(mockRepo));
    RevisionMatcher matcher = new EquivalenceMatcher("mockRepo", db);
    ImmutableList<Revision> newRevisions =
        ImmutableList.copyOf(rh.findRevisions(null, matcher));

    // parent1 should not be traversed.
    assertEquals(2, newRevisions.size());
    assertEquals("mockHashID1", newRevisions.get(0).revId);
    assertEquals("mockHashID2", newRevisions.get(1).revId);

    control.verify();
  }

  /*
   * A database that holds the following equivalences:
   * repo1{1002} == repo2{2}
   */
  private final String testDb1 = "{\"equivalences\":["
      + "{\"rev1\": {\"revId\":\"1002\",\"repositoryName\":\"repo1\"},"
      + " \"rev2\": {\"revId\":\"2\",\"repositoryName\":\"repo2\"}}]}";

  /**
   * A test for finding the last equivalence for the following history starting
   * with repo2{4}:
   *                                         _____
   *                                        |     |
   *                                        |  4  |
   *                                        |_____|
   *                                           |  \
   *                                           |   \
   *                                           |    \
   *                                         __|__   \_____
   *                                        |     |  |     |
   *                                        |  3a |  | 3b  |
   *                                        |_____|  |_____|
   *                                           |     /
   *                                           |    /
   *                                           |   /
   *              ____                       __|__/
   *             |    |                     |     |
   *             |1002|=====================|  2  |
   *             |____|                     |_____|
   *
   *              repo1                      repo2
   *
   * @throws Exception
   */
  public void testFindLastEquivalence() throws Exception {
    GitClonedRepository mockRepo = mockClonedRepo("repo2");

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "4")
        .andReturn(METADATA_JOINER.join("4", "author", "date", "3a 3b", "description"));

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "3a")
        .andReturn(METADATA_JOINER.join("3a", "author", "date", "2", "description"));

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "3b")
        .andReturn(METADATA_JOINER.join("3b", "author", "date", "2", "description"));

    control.replay();

    FileDb database = FileDb.makeDbFromDbText(testDb1);
    EquivalenceMatcher matcher = new EquivalenceMatcher("repo1", database);

    GitRevisionHistory history = new GitRevisionHistory(Suppliers.ofInstance(mockRepo));

    Equivalence actualEq = history.findLastEquivalence(new Revision("4", "repo2"), matcher);
    Equivalence expectedEq = new Equivalence(new Revision("1002", "repo1"),
                                             new Revision("2", "repo2"));
    assertEquals(expectedEq, actualEq);

    control.verify();
  }

  /*
   * A database that holds the following equivalences:
   * repo1{1005} == repo2{5}
   */
  private final String testDb2 = "{\"equivalences\":["
      + "{\"rev1\": {\"revId\":\"1005\",\"repositoryName\":\"repo1\"},"
      + " \"rev2\": {\"revId\":\"5\",\"repositoryName\":\"repo2\"}}]}";

  /**
   * A test for finding the last equivalence for the following history starting
   * with repo2{4}:
   *
   *              ____                       _____
   *             |    |                     |     |
   *             |1005|=====================|  5  |
   *             |____|                     |_____|
   *                                           |
   *                                           |
   *                                           |
   *                                         __|__
   *                                        |     |
   *                                        |  4  |
   *                                        |_____|
   *                                           |  \
   *                                           |   \
   *                                           |    \
   *                                         __|__   \_____
   *                                        |     |  |     |
   *                                        |  3a |  | 3b  |
   *                                        |_____|  |_____|
   *                                           |     /
   *                                           |    /
   *                                           |   /
   *                                         __|__/
   *                                        |     |
   *                                        |  2  |
   *                                        |_____|
   *
   *              repo1                      repo2
   *
   * @throws Exception
   */
  public void testFindLastEquivalenceNull() throws Exception {
    GitClonedRepository mockRepo = mockClonedRepo("repo2");

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "4")
        .andReturn(METADATA_JOINER.join("4", "author", "date", "3a 3b", "description"));

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "3a")
        .andReturn(METADATA_JOINER.join("3a", "author", "date", "2", "description"));

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "3b")
        .andReturn(METADATA_JOINER.join("3b", "author", "date", "2", "description"));

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "2")
        .andReturn(METADATA_JOINER.join("2", "author", "date", "", "description"));

    expectLogCommand(mockRepo, LOG_FORMAT_ALL_METADATA, "2")
        .andReturn(METADATA_JOINER.join("2", "author", "date", "", "description"));

    control.replay();

    FileDb database = FileDb.makeDbFromDbText(testDb2);
    EquivalenceMatcher matcher = new EquivalenceMatcher("repo1", database);
    GitRevisionHistory history = new GitRevisionHistory(Suppliers.ofInstance(mockRepo));
    assertNull(history.findLastEquivalence(new Revision("4", "repo2"), matcher));

    control.verify();
  }
}
