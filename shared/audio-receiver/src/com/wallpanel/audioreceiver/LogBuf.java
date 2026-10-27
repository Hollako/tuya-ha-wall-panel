package com.wallpanel.audioreceiver;

import java.io.*;
import java.util.LinkedList;

public class LogBuf {
  private static final int MAX_LINES = 500;
  private final LinkedList<String> lines = new LinkedList<>();
  private final File logFile;
  private PrintWriter fileWriter;

  public LogBuf(File logFile) {
    this.logFile = logFile;
    try {
      fileWriter = new PrintWriter(new FileWriter(logFile, true), true);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public synchronized void append(String line) {
    lines.addLast(line);
    while (lines.size() > MAX_LINES) {
      lines.removeFirst();
    }
    if (fileWriter != null) {
      fileWriter.println(line);
    }
  }

  public synchronized String tail(int n) {
    StringBuilder sb = new StringBuilder();
    int start = Math.max(0, lines.size() - n);
    for (int i = start; i < lines.size(); i++) {
      sb.append(lines.get(i)).append('\n');
    }
    return sb.toString();
  }

  public void close() {
    if (fileWriter != null) {
      fileWriter.close();
    }
  }
}
