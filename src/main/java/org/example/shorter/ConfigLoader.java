package org.example.shorter;

import java.io.FileInputStream;
import java.time.Duration;
import java.util.Properties;

public class ConfigLoader {

  public AppConfig load(String path) {
    Properties p = new Properties();

    try (FileInputStream in = new FileInputStream(path)) {
      p.load(in);
    } catch (Exception ignored) {

    }

    long ttlMinutes = longProp(p, "ttl.minutes", 1440);
    long cleanupSeconds = longProp(p, "cleanup.seconds", 30);
    String dbPath = strProp(p, "db.path", "shortener.db");
    int defaultLimit = intProp(p, "default.limit", 5);
    int codeLength = intProp(p, "code.length", 8);
    boolean openBrowser = boolProp(p, "browser.open", true);

    return new AppConfig(
        Duration.ofMinutes(ttlMinutes),
        Duration.ofSeconds(cleanupSeconds),
        dbPath,
        defaultLimit,
        codeLength,
        openBrowser);
  }

  private static String strProp(Properties p, String key, String def) {
    String v = p.getProperty(key);
    return (v == null || v.isBlank()) ? def : v.trim();
  }

  private static long longProp(Properties p, String key, long def) {
    try {
      return Long.parseLong(strProp(p, key, String.valueOf(def)));
    } catch (Exception e) {
      return def;
    }
  }

  private static int intProp(Properties p, String key, int def) {
    try {
      return Integer.parseInt(strProp(p, key, String.valueOf(def)));
    } catch (Exception e) {
      return def;
    }
  }

  private static boolean boolProp(Properties p, String key, boolean def) {
    String v = p.getProperty(key);
    if (v == null) return def;
    v = v.trim();
    return v.equalsIgnoreCase("true") || v.equals("1");
  }
}
