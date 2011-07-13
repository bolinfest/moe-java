// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.devtools.moe.client.directives;

import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.testing.AppContextForTesting;
import com.google.devtools.moe.client.testing.InMemoryProjectContextFactory;
import com.google.devtools.moe.client.testing.RecordingUi;

import junit.framework.TestCase;

/**
 */
public class RevisionsSinceEquivalenceDirectiveTest extends TestCase {

  @Override
  public void setUp() {
    AppContextForTesting.initForTest();
  }

  public void testPickRevisions() throws Exception {
    ((InMemoryProjectContextFactory) AppContext.RUN.contextFactory).projectConfigs.put(
        "moe_config.txt",
        "{\"name\": \"foo\", \"repositories\": {" +
        "\"internal\": {\"type\": \"dummy\"}}}");
    RevisionsSinceEquivalenceDirective d = new RevisionsSinceEquivalenceDirective();
    d.getFlags().configFilename = "moe_config.txt";
    d.getFlags().dbLocation = "dummy";
    d.getFlags().fromRepository = "internal{1}";
    d.getFlags().toRepository = "public";
    assertEquals(0, d.perform());
    assertEquals("Revisions found: internal{1}",
                 ((RecordingUi) AppContext.RUN.ui).lastInfo);
  }
}
