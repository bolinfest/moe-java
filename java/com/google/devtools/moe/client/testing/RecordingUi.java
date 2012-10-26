// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.testing;

import com.google.devtools.moe.client.SystemUi;
import com.google.devtools.moe.client.Ui;

/**
 * Ui that records what was Info'ed
 *
 * @author dbentley@google.com (Daniel Bentley)
 */
public class RecordingUi extends SystemUi {

  public String lastInfo;
  public String lastError;
  public String lastTaskResult;

  public RecordingUi() {
    lastInfo = null;
    lastError = null;
  }

  @Override
  public void info(String msg) {
    lastInfo = msg;
    super.info(msg);
  }

  @Override
  public void error(String msg) {
    lastError = msg;
    super.error(msg);
  }

  @Override
  public void popTask(Ui.Task task, String result) {
    lastTaskResult = result;
    super.popTask(task, result);
  }
}
