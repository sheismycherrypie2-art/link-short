package org.example.shorter;

public class Link {
  public long id;
  public String userUuid;
  public String code;
  public String originalUrl;
  public long createdAtMs;
  public long expiresAtMs;
  public int maxClicks;
  public int clicks;
  public int active; // 1/0

  public boolean isActive() {
    return active == 1;
  }
}
