// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.testing;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.database.Db;
import com.google.devtools.moe.client.database.Equivalence;
import com.google.devtools.moe.client.database.SubmittedMigration;
import com.google.devtools.moe.client.repositories.Revision;

import java.util.ArrayList;
import java.util.Set;

/**
 * Db for testing
 *
 */
public class DummyDb implements Db {

  public boolean returnEquivalences;
  public ArrayList<Equivalence> equivalences;
  public ArrayList<SubmittedMigration> migrations;

  public DummyDb(boolean returnEquivalences) {
    this.returnEquivalences = returnEquivalences;
    equivalences = new ArrayList<Equivalence>();
    migrations = new ArrayList<SubmittedMigration>();
  }

  @Override
  public void noteEquivalence(Equivalence equivalence) {
    equivalences.add(equivalence);
  }

  @Override
  public Set<Revision> findEquivalences(Revision revision, String otherRepository) {
    if (!returnEquivalences) {
      return ImmutableSet.of();
    } else {
      return ImmutableSet.of(new Revision("1", otherRepository),
                             new Revision("2", otherRepository));
    }
  }

  @Override
  public boolean noteMigration(SubmittedMigration migration) {
    return !migrations.contains(migration) && migrations.add(migration);
  }

  @Override
  public void writeToLocation(String dbLocation) {
    Joiner j = Joiner.on("\n");
    StringBuilder b = new StringBuilder("Equivalences:\n");
    j.join(b, equivalences);
    b.append("\nMigrations:\n");
    j.join(b, migrations);
    AppContext.RUN.ui.info(b.toString());
  }
}
