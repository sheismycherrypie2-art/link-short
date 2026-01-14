package org.example.shorter;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import java.awt.Desktop;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.validator.routines.UrlValidator;

public class LinkService {
    private final LinkRepository repo;
    private final Duration ttl;
    private final int defaultLimit;
    private final int codeLength;
    private final boolean openBrowser;
    private final UrlValidator validator = new UrlValidator(new String[] {"http", "https"});

    public LinkService(
        LinkRepository repo,
        Duration ttl,
        int defaultLimit,
        int codeLength,
        boolean openBrowser) {
        this.repo = repo;
        this.ttl = ttl;
        this.defaultLimit = defaultLimit;
        this.codeLength = codeLength;
        this.openBrowser = openBrowser;
    }

    public Link create(UUID userId, String url, Integer limitOrNull) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("URL is empty");
        if (!validator.isValid(url)) throw new IllegalArgumentException("Invalid URL (http/https)");

        int limit = (limitOrNull == null) ? defaultLimit : limitOrNull;
        if (limit <= 0 || limit > 1_000_000) throw new IllegalArgumentException("Limit 1..1000000");

        long now = System.currentTimeMillis();
        long expires = now + ttl.toMillis();

        // retry on code collision
        for (int attempt = 0; attempt < 10; attempt++) {
            String code =
                NanoIdUtils.randomNanoId(
                    NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                    NanoIdUtils.DEFAULT_ALPHABET,
                    codeLength);

            Link l = new Link();
            l.userUuid = userId.toString();
            l.code = code;
            l.originalUrl = url;
            l.createdAtMs = now;
            l.expiresAtMs = expires;
            l.maxClicks = limit;
            l.clicks = 0;
            l.active = 1;

            try {
                return repo.insert(l);
            } catch (RuntimeException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                if (msg.contains("unique")) continue;
                throw e;
            }
        }

        throw new IllegalStateException("Cannot generate unique code");
    }

    public String open(String code) {
        if (code == null || code.isBlank()) return "Usage: open <code>";

        long now = System.currentTimeMillis();
        Optional<Link> current = repo.findByCode(code);
        if (current.isEmpty()) return "NOT FOUND";

        Link l = current.get();

        if (l.expiresAtMs <= now) return "EXPIRED (TTL). Create a new link.";

        // stable message after limit
        if (l.clicks >= l.maxClicks) return "DISABLED (LIMIT REACHED). Create a new link.";
        if (!l.isActive()) return "DISABLED (LIMIT REACHED). Create a new link.";

        Optional<Link> updated = repo.consumeClick(code, now);
        if (updated.isEmpty()) {
            Optional<Link> again = repo.findByCode(code);
            if (again.isEmpty()) return "NOT FOUND";

            Link a = again.get();
            if (a.expiresAtMs <= now) return "EXPIRED (TTL). Create a new link.";
            if (a.clicks >= a.maxClicks) return "DISABLED (LIMIT REACHED). Create a new link.";
            if (!a.isActive()) return "DISABLED (LIMIT REACHED). Create a new link.";

            return "Cannot consume click";
        }

        Link u = updated.get();

        boolean lastClickUsed = (!u.isActive() || u.clicks >= u.maxClicks);
        String notice = lastClickUsed ? "NOTICE: last allowed click used. Link is now disabled." : null;

        String url = u.originalUrl;

        if (!openBrowser) {
            return (notice == null) ? ("URL: " + url) : ("URL: " + url + "\n" + notice);
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
                return (notice == null) ? ("OPENED: " + url) : ("OPENED: " + url + "\n" + notice);
            } else {
                return (notice == null) ? ("URL: " + url) : ("URL: " + url + "\n" + notice);
            }
        } catch (Exception e) {
            return (notice == null)
                ? ("Cannot open browser. URL: " + url)
                : ("Cannot open browser. URL: " + url + "\n" + notice);
        }
    }

    public String list(UUID userId) {
        var links = repo.listByUser(userId.toString());
        if (links.isEmpty()) return "(empty)";

        StringBuilder sb = new StringBuilder();
        for (var l : links) {
            sb.append("-----\n");
            sb.append("code: ").append(l.code).append("\n");
            sb.append("url : ").append(l.originalUrl).append("\n");
            sb.append("clicks: ").append(l.clicks).append("/").append(l.maxClicks);
            sb.append(" active=").append(l.isActive()).append("\n");
            sb.append("expiresMs: ").append(l.expiresAtMs).append("\n");
        }
        return sb.toString();
    }

    public String info(UUID userId, String code) {
        var opt = repo.findByCode(code);
        if (opt.isEmpty()) return "NOT FOUND";
        Link l = opt.get();
        if (!l.userUuid.equals(userId.toString())) return "FORBIDDEN (not owner)";

        return "code: "
            + l.code
            + "\nurl : "
            + l.originalUrl
            + "\nclicks: "
            + l.clicks
            + "/"
            + l.maxClicks
            + "\nactive: "
            + l.isActive()
            + "\ncreatedMs: "
            + l.createdAtMs
            + "\nexpiresMs: "
            + l.expiresAtMs;
    }

    public String setLimit(UUID userId, String code, int newLimit) {
        if (newLimit <= 0 || newLimit > 1_000_000) return "Limit 1..1000000";
        boolean ok = repo.updateLimit(code, userId.toString(), newLimit);
        return ok ? "OK" : "NOT FOUND or FORBIDDEN";
    }

    public String delete(UUID userId, String code) {
        boolean ok = repo.deleteByCodeAndUser(code, userId.toString());
        return ok ? "OK" : "NOT FOUND or FORBIDDEN";
    }
}
