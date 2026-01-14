package org.example.shorter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class ConfigLoaderTest {

  @Test
  void defaultsWhenMissing() {
    AppConfig cfg = new ConfigLoader().load("missing.properties");
    assertThat(cfg.ttl.toMinutes()).isEqualTo(1440);
    assertThat(cfg.cleanupPeriod.getSeconds()).isEqualTo(30);
    assertThat(cfg.defaultLimit).isEqualTo(5);
    assertThat(cfg.codeLength).isEqualTo(8);
  }

  @Test
  void loadsFromFile() throws Exception {
    Path tmp = Files.createTempFile("app", ".properties");
    Files.writeString(
        tmp,
        """
            ttl.minutes=10
            cleanup.seconds=2
            db.path=test.db
            default.limit=7
            code.length=9
            browser.open=false
            """);
    AppConfig cfg = new ConfigLoader().load(tmp.toString());
    assertThat(cfg.ttl.toMinutes()).isEqualTo(10);
    assertThat(cfg.cleanupPeriod.getSeconds()).isEqualTo(2);
    assertThat(cfg.dbPath).isEqualTo("test.db");
    assertThat(cfg.defaultLimit).isEqualTo(7);
    assertThat(cfg.codeLength).isEqualTo(9);
    assertThat(cfg.openBrowser).isFalse();
  }
}
