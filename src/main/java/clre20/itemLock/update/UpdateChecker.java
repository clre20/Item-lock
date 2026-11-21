package clre20.itemLock.update;

import clre20.itemLock.config.Messages;
import clre20.itemLock.listener.PlayerListener;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * GitHub Releases 版本檢查。
 * <ul>
 *   <li>單一背景執行緒，網路請求不在主執行緒</li>
 *   <li>結果快取 10 分鐘（失敗 1 分鐘），沒有定時輪詢；同時只會有一個請求</li>
 *   <li>比對時去掉 tag 的 v 前綴，逐段比數字</li>
 * </ul>
 */
public final class UpdateChecker {

    public enum Status { NEW, LATEST, DEV, NONE, FAILED }

    public record Result(Status status, String latest, String url, long time) {}

    private static final String DEFAULT_API = "https://api.github.com/repos/clre20/Item-lock/releases/latest";
    private static final long CACHE_MS = 10 * 60 * 1000L;
    private static final long FAIL_CACHE_MS = 60 * 1000L;
    private static final int MAX_BODY = 512 * 1024;

    private final Plugin plugin;
    private final Messages messages;
    private final String current;
    private final String api;
    private Result cached;
    private CompletableFuture<Result> inflight;

    public UpdateChecker(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.current = plugin.getPluginMeta().getVersion();
        // 測試用：-Ditemlock.update-api=<網址>
        this.api = System.getProperty("itemlock.update-api", DEFAULT_API);
    }

    public String getCurrent() {
        return current;
    }

    public synchronized CompletableFuture<Result> check() {
        Result c = cached;
        if (c != null) {
            long age = System.currentTimeMillis() - c.time();
            if (age < (c.status() == Status.FAILED ? FAIL_CACHE_MS : CACHE_MS)) {
                return CompletableFuture.completedFuture(c);
            }
        }
        if (inflight != null) {
            return inflight;
        }
        CompletableFuture<Result> f = CompletableFuture.supplyAsync(this::fetch);
        inflight = f;
        f.whenComplete((r, e) -> {
            synchronized (UpdateChecker.this) {
                cached = r != null ? r : new Result(Status.FAILED, null, null, System.currentTimeMillis());
                inflight = null;
            }
        });
        return f;
    }

    /** 結果回到主執行緒處理；插件已停用則忽略。 */
    private void onMain(Consumer<Result> action) {
        check().thenAccept(r -> {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> action.accept(r));
            }
        });
    }

    public void logOnStartup() {
        onMain(r -> {
            if (r.status() != Status.LATEST) {
                send(Bukkit.getConsoleSender(), r);
            }
        });
    }

    /** /itemlock version */
    public void reply(CommandSender sender) {
        UUID id = sender instanceof Player p ? p.getUniqueId() : null;
        messages.send(sender, "update.checking");
        onMain(r -> {
            CommandSender target = id == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(id);
            if (target != null) {
                send(target, r);
            }
        });
    }

    /** 登入 3 秒後，有新版本才提醒。 */
    public void notifyLater(UUID id) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPlayer(id) == null) {
                return;
            }
            onMain(r -> {
                Player p = Bukkit.getPlayer(id);
                if (r.status() == Status.NEW && p != null && p.hasPermission(PlayerListener.VERSION_PERMISSION)) {
                    messages.send(p, "update.join", "latest", r.latest(), "current", current);
                }
            });
        }, 60L);
    }

    private void send(CommandSender to, Result r) {
        switch (r.status()) {
            case NEW -> messages.send(to, "update.new", "latest", r.latest(), "current", current, "url", r.url());
            case LATEST -> messages.send(to, "update.latest", "current", current);
            case DEV -> messages.send(to, "update.dev", "latest", r.latest(), "current", current);
            case NONE -> messages.send(to, "update.none", "current", current);
            case FAILED -> messages.send(to, "update.failed");
        }
    }

    private Result fetch() {
        long now = System.currentTimeMillis();
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(api).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", plugin.getName() + "/" + current);
            int code = conn.getResponseCode();
            if (code == 404) {
                conn.disconnect();
                return new Result(Status.NONE, null, null, now);
            }
            if (code != 200) {
                conn.disconnect();
                return new Result(Status.FAILED, null, null, now);
            }
            String body;
            try (java.io.InputStream in = conn.getInputStream()) {
                body = new String(in.readNBytes(MAX_BODY), java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonElement tag = json.get("tag_name");
            if (tag == null || tag.isJsonNull()) {
                return new Result(Status.NONE, null, null, now);
            }
            String latest = Version.strip(tag.getAsString());
            JsonElement html = json.get("html_url");
            String url = html != null && !html.isJsonNull() ? html.getAsString() : "";
            int cmp = Version.compare(current, latest);
            Status s = cmp < 0 ? Status.NEW : cmp > 0 ? Status.DEV : Status.LATEST;
            return new Result(s, latest, url, now);
        } catch (IOException | RuntimeException e) {
            return new Result(Status.FAILED, null, null, now);
        }
    }

    public void shutdown() {
        // 無常駐線程，不需關閉
    }
}
