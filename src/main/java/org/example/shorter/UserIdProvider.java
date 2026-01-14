package org.example.shorter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class UserIdProvider {
  private final Path file;

  public UserIdProvider(Path file) {
    this.file = file;
  }

  public UUID getOrCreate() {
    try {
      if (Files.exists(file)) {
        String s = Files.readString(file, StandardCharsets.UTF_8).trim();
        return UUID.fromString(s);
      }
      UUID id = UUID.randomUUID();
      Files.writeString(file, id.toString(), StandardCharsets.UTF_8);
      return id;
    } catch (IOException e) {
      return UUID.randomUUID();
    }
  }
}
