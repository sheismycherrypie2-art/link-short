package org.example.shorter;

import java.time.Duration;

public class AppConfig {
  public final Duration ttl;
  public final Duration cleanupPeriod;
  public final String dbPath;
  public final int defaultLimit;
  public final int codeLength;
  public final boolean openBrowser;

  public AppConfig(
      Duration ttl,
      Duration cleanupPeriod,
      String dbPath,
      int defaultLimit,
      int codeLength,
      boolean openBrowser) {
    this.ttl = ttl;
    this.cleanupPeriod = cleanupPeriod;
    this.dbPath = dbPath;
    this.defaultLimit = defaultLimit;
    this.codeLength = codeLength;
    this.openBrowser = openBrowser;
  }
}
