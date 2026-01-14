package org.example.shorter;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class LinkServiceTest {

    private LinkService svc(LinkRepository repo, Duration ttl, boolean openBrowser) {
        return new LinkService(repo, ttl, 5, 8, openBrowser);
    }

    @Test
    void createValid() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = svc(repo, Duration.ofMinutes(5), false);

        Link link = s.create(UUID.randomUUID(), "https://example.com", 3);
        assertThat(link.code).hasSize(8);
        assertThat(link.maxClicks).isEqualTo(3);
    }

    @Test
    void createInvalidUrl() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = svc(repo, Duration.ofMinutes(5), false);

        assertThatThrownBy(() -> s.create(UUID.randomUUID(), "not-url", 3))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createUsesDefaultLimit() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = new LinkService(repo, Duration.ofMinutes(5), 7, 8, false);

        Link link = s.create(UUID.randomUUID(), "https://example.com", null);
        assertThat(link.maxClicks).isEqualTo(7);
    }

    @Test
    void openNotFound() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = svc(repo, Duration.ofMinutes(5), false);

        assertThat(s.open("nope")).contains("NOT FOUND");
    }

    @Test
    void openConsumesClicks() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = svc(repo, Duration.ofMinutes(5), false);

        Link link = s.create(UUID.randomUUID(), "https://example.com", 2);
        s.open(link.code);

        Link after = repo.findByCode(link.code).get();
        assertThat(after.clicks).isEqualTo(1);
    }

    @Test
    void openBlocksAfterLimit() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = svc(repo, Duration.ofMinutes(5), false);

        Link link = s.create(UUID.randomUUID(), "https://example.com", 1);
        assertThat(s.open(link.code)).contains("URL:");
        assertThat(s.open(link.code)).contains("LIMIT").contains("DISABLED");
    }

    @Test
    void ttlBlocksOpen() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = svc(repo, Duration.ofMillis(1), false);

        Link link = s.create(UUID.randomUUID(), "https://example.com", 2);
        Thread.sleep(5);

        assertThat(s.open(link.code)).contains("EXPIRED");
    }

    @Test
    void ownerOnlyInfo() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = svc(repo, Duration.ofMinutes(5), false);

        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Link link = s.create(owner, "https://example.com", 2);

        assertThat(s.info(other, link.code)).contains("FORBIDDEN");
        assertThat(s.info(owner, link.code)).contains("code:");
    }

    @Test
    void ownerOnlyDelete() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = svc(repo, Duration.ofMinutes(5), false);

        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Link link = s.create(owner, "https://example.com", 2);

        assertThat(s.delete(other, link.code)).contains("FORBIDDEN");
        assertThat(s.delete(owner, link.code)).isEqualTo("OK");
        assertThat(repo.findByCode(link.code)).isEmpty();
    }

    @Test
    void ownerOnlySetLimit() throws Exception {
        LinkRepository repo = new LinkRepository(Files.createTempFile("db", ".sqlite").toString());
        LinkService s = svc(repo, Duration.ofMinutes(5), false);

        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Link link = s.create(owner, "https://example.com", 2);

        assertThat(s.setLimit(other, link.code, 10)).contains("FORBIDDEN");
        assertThat(s.setLimit(owner, link.code, 10)).isEqualTo("OK");
    }
}
