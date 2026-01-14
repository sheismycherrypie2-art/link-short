package org.example.shorter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        AppConfig cfg = new ConfigLoader().load("application.properties");

        UUID userId =
            new UserIdProvider(Path.of(System.getProperty("user.home"), ".shortener-uuid"))
                .getOrCreate();

        LinkRepository repo = new LinkRepository(cfg.dbPath);
        LinkService service =
            new LinkService(repo, cfg.ttl, cfg.defaultLimit, cfg.codeLength, cfg.openBrowser);

        startCleanup(repo, cfg);

        printHelp(userId, cfg);

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String line;
                try {
                    line = sc.nextLine();
                } catch (Exception e) {
                    break;
                }

                line = line.trim();
                if (line.isEmpty()) continue;

                List<String> parts = splitArgsWithQuotes(line);
                String cmd = parts.get(0).toLowerCase();

                try {
                    switch (cmd) {
                        case "help" -> printHelp(userId, cfg);
                        case "exit", "quit" -> {
                            return;
                        }
                        case "uuid" -> System.out.println(userId);

                        case "create" -> {
                            if (parts.size() < 2) {
                                System.out.println("Usage: create <url> [limit]");
                                break;
                            }
                            String url = parts.get(1);
                            Integer limit = (parts.size() >= 3) ? parseInt(parts.get(2)) : null;
                            Link link = service.create(userId, url, limit);
                            System.out.println("code: " + link.code);
                            System.out.println("limit: " + link.maxClicks);
                            System.out.println("ttl: " + cfg.ttl);
                        }

                        case "open" -> {
                            if (parts.size() < 2) {
                                System.out.println("Usage: open <code>");
                                break;
                            }
                            System.out.println(service.open(parts.get(1)));
                        }

                        case "list" -> System.out.print(service.list(userId));

                        case "info" -> {
                            if (parts.size() < 2) {
                                System.out.println("Usage: info <code>");
                                break;
                            }
                            System.out.println(service.info(userId, parts.get(1)));
                        }

                        case "set-limit" -> {
                            if (parts.size() < 3) {
                                System.out.println("Usage: set-limit <code> <limit>");
                                break;
                            }
                            String code = parts.get(1);
                            int limit = parseInt(parts.get(2));
                            System.out.println(service.setLimit(userId, code, limit));
                        }

                        case "delete" -> {
                            if (parts.size() < 2) {
                                System.out.println("Usage: delete <code>");
                                break;
                            }
                            System.out.println(service.delete(userId, parts.get(1)));
                        }

                        default -> System.out.println("Unknown command. Type: help");
                    }
                } catch (Exception e) {
                    System.out.println("ERROR: " + e.getMessage());
                }
            }
        }
    }

    private static void startCleanup(LinkRepository repo, AppConfig cfg) {
        var ses =
            Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("cleanup-thread");
                    return t;
                });

        long ms = cfg.cleanupPeriod.toMillis();
        ses.scheduleAtFixedRate(
            () -> {
                try {
                    int deleted = repo.deleteExpired(System.currentTimeMillis());
                    if (deleted > 0) {
                        System.out.println("[notify] deleted expired links: " + deleted);
                    }
                } catch (Exception ignored) {
                }
            },
            ms,
            ms,
            TimeUnit.MILLISECONDS);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid number");
        }
    }

    private static void printHelp(UUID userId, AppConfig cfg) {
        System.out.println("user: " + userId);
        System.out.println("commands:");
        System.out.println("  create <url> [limit]");
        System.out.println("  open <code>");
        System.out.println("  list");
        System.out.println("  info <code>");
        System.out.println("  set-limit <code> <limit>");
        System.out.println("  delete <code>");
        System.out.println("  uuid");
        System.out.println("  help");
        System.out.println("  exit");
        System.out.println("config:");
        System.out.println("  ttl=" + cfg.ttl + ", cleanup=" + cfg.cleanupPeriod + ", default.limit=" + cfg.defaultLimit);
        System.out.println("examples:");
        System.out.println("  create https://www.google.com/ 3");
        System.out.println("  create \"https://www.google.com/\" 5");
        System.out.println("  open Ab3xK91Q");
    }

    private static List<String> splitArgsWithQuotes(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(ch) && !inQuotes) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }

            cur.append(ch);
        }

        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
