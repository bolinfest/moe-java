// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.devtools.moe.client.svn;

import com.google.common.collect.ImmutableList;
import com.google.devtools.moe.client.CommandRunner.CommandException;
import com.google.devtools.moe.client.MoeProblem;
import com.google.devtools.moe.client.repositories.Revision;
import com.google.devtools.moe.client.repositories.RevisionHistory;
import com.google.devtools.moe.client.repositories.RevisionMetadata;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 *
 * @author dbentley@google.com (Daniel Bentley)
 */
public class SvnRevisionHistory implements RevisionHistory {

  private String name;
  private String url;

  public SvnRevisionHistory(String name, String url) {
    this.name = name;
    this.url = url;
  }

  public Revision findHighestRevision(String revId) {
    if (revId == null || revId.isEmpty()) {
      revId = "HEAD";
    }
    ImmutableList<String> args = ImmutableList.of("log", "--xml", "-l", "1", "-r",
        revId + ":1", url);

    String log;
    try {
      log = SvnRepository.runSvnCommand(args, "");
    } catch (CommandException e) {
      throw new MoeProblem(
          String.format("Failed svn run: %s %d %s %s", args.toString(), e.returnStatus,
              e.stdout, e.stderr));
    }

    List<Revision> revisions = parseRevisions(log, name);
    // TODO(dbentley): we should log when the Revision's revId is different than
    // what was passed in, as this is often suprising to users.
    return revisions.get(0);
  }

  /**
   * Parse the output of svn log into Revisions.
   *
   * @param log  the output of svn to parse
   * @param repositoryName  the name of the repository being parsed
   */
  public static List<Revision> parseRevisions(String log, String repositoryName) {
    try {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
          new InputSource(new StringReader(log)));
      NodeList nl = doc.getElementsByTagName("logentry");
      ImmutableList.Builder<Revision> resultBuilder = ImmutableList.builder();
      for (int i = 0; i < nl.getLength(); i++) {
        String revId = nl.item(i).getAttributes().getNamedItem("revision").getNodeValue();
        resultBuilder.add(new Revision(revId, repositoryName));
      }
      return resultBuilder.build();
    } catch (Exception e) {
      throw new MoeProblem("Could not parse xml log: " + log + e.getMessage());
    }
  }

  /**
   * Read the metadata for a given revision in the same repository
   *
   * @param revision  the revision to get metadata for
   */
  public RevisionMetadata getMetadata(Revision revision) throws MoeProblem {
    if (!name.equals(revision.repositoryName)) {
      throw new MoeProblem(
          String.format("Could not get metadata: Revision %s is in repository %s instead of %s",
                        revision.revId, revision.repositoryName, name));
    }
    // svn log command for output in xml format for 2 log entries, for revision and its parent
    ImmutableList<String> args = ImmutableList.of("log", "--xml", "-l", "2", "-r",
        revision.revId + ":1", url);
    String log;
    try {
      log = SvnRepository.runSvnCommand(args, "");
    } catch (CommandException e) {
      throw new MoeProblem(
          String.format("Failed svn run: %s %d %s %s", args.toString(), e.returnStatus,
              e.stdout, e.stderr));
    }
    List<RevisionMetadata> metadata = parseMetadata(log);
    return metadata.get(0);
  }

  /**
   * Parse the output of svn log into Metadata
   *
   * @param log  the output of svn to parse
   */
  public List<RevisionMetadata> parseMetadata(String log) {
    try {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
          new InputSource(new StringReader(log)));
      NodeList nl = doc.getElementsByTagName("logentry");
      ImmutableList.Builder<RevisionMetadata> resultBuilder = ImmutableList.builder();
      String revId = nl.item(0).getAttributes().getNamedItem("revision").getNodeValue();
      for (int i = 0; i < nl.getLength() - 1; i++) {
        String parentId = nl.item(i + 1).getAttributes().getNamedItem("revision").getNodeValue();
        NodeList nlEntries = nl.item(i).getChildNodes();
        resultBuilder.add(parseMetadataNodeList(revId, nlEntries,
                                                ImmutableList.of(new Revision(parentId, name))));
        revId = parentId;
      }
      // last revision has no parent
      NodeList nlEntries = nl.item(nl.getLength() - 1).getChildNodes();
      resultBuilder.add(parseMetadataNodeList(revId, nlEntries, ImmutableList.<Revision>of()));
      return resultBuilder.build();
    } catch (Exception e) {
      throw new MoeProblem("Could not parse xml log: " + log + e.getMessage());
    }
  }
    /**
     * Helper function for parseMetadata
     */
  private RevisionMetadata parseMetadataNodeList(String revId, NodeList nlEntries,
                                                 ImmutableList<Revision> parents) {
    if (nlEntries.getLength() != 0) {
      String author = nlEntries.item(0).getTextContent();
      String date = nlEntries.item(1).getTextContent();
      String description = nlEntries.item(2).getTextContent();
      return new RevisionMetadata(revId, author, date, description, parents);
    } else {
      return new RevisionMetadata(revId, "", "", "", parents);
    }
  }
}
