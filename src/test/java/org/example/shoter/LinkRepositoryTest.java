package org.example.shorter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

public class LinkRepositoryTest {

    @Test
    void insertAndFind() throws Exception {
        String db = Files.createTempFile("db", ".sqlite").toString();
        LinkRepository repo = new LinkRepository(db);

        Link l = new Link();
        l.userUuid = "u";
        l.code = "abc";
        l.originalUrl = "https://example.com";
        l.createdAtMs = 1;
        l.expiresAtMs = System.currentTimeMillis() + 100000;
        l.maxClicks = 2;
        l.clicks = 0;
        l.active = 1;
        repo.insert(l);

        assertThat(repo.findByCode("abc")).isPresent();
    }

    @Test
    void consumeClickAtomically() throws Exception {
        String db = Files.createTempFile("db", ".sqlite").toString();
        LinkRepository repo = new LinkRepository(db);

        Link l = new Link();
        l.userUuid = "u";
        l.code = "abc";
        l.originalUrl = "https://example.com";
        l.createdAtMs = 1;
        l.expiresAtMs = System.currentTimeMillis() + 100000;
        l.maxClicks = 2;
        l.clicks = 0;
        l.active = 1;
        repo.insert(l);

        assertThat(repo.consumeClick("abc", System.currentTimeMillis())).isPresent();
        assertThat(repo.consumeClick("abc", System.currentTimeMillis())).isPresent();
        assertThat(repo.consumeClick("abc", System.currentTimeMillis())).isEmpty();

        Link after = repo.findByCode("abc").get();
        assertThat(after.clicks).isEqualTo(2);
        assertThat(after.isActive()).isFalse();
    }

    @Test
    void deleteExpiredWorks() throws Exception {
        String db = Files.createTempFile("db", ".sqlite").toString();
        LinkRepository repo = new LinkRepository(db);

        Link l = new Link();
        l.userUuid = "u";
        l.code = "abc";
        l.originalUrl = "https://example.com";
        l.createdAtMs = 1;
        l.expiresAtMs = System.currentTimeMillis() - 1;
        l.maxClicks = 2;
        l.clicks = 0;
        l.active = 1;
        repo.insert(l);

        int deleted = repo.deleteExpired(System.currentTimeMillis());
        assertThat(deleted).isEqualTo(1);
        assertThat(repo.findByCode("abc")).isEmpty();
    }
}
