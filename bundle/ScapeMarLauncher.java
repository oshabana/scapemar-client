import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

public class ScapeMarLauncher {
    private static final String PLUGIN_KEY = "runelite.externalPlugins";
    private static final String LOGIN_PLUGIN = "ScapeMar-Login.jar";
    private static final String UPDATE_URL =
        "https://github.com/oshabana/scapemar-client/releases/latest/download/";
    private static final Pattern PROFILE = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(-?\\d+)");
    private static final Pattern ACTIVE = Pattern.compile("\"active\"\\s*:\\s*true");

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (Exception e) {
            if (GraphicsEnvironment.isHeadless()) {
                throw e;
            }
            JOptionPane.showMessageDialog(null, e.getMessage(), "ScapeMar could not start",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Path bundle = Path.of(System.getProperty("user.dir"));
        if (!Files.isRegularFile(bundle.resolve("proxy-targets.yaml"))) {
            throw new IOException("Run this from the extracted ScapeMar bundle.");
        }
        Path home = Path.of(System.getProperty("user.home"));
        JWindow splash = showSplash("Checking for updates...");
        Path assets;
        try {
            assets = checkForUpdates(bundle, home.resolve(".scapemar/update"));
        } finally {
            if (splash != null) {
                splash.dispose();
            }
        }
        installTarget(assets.resolve("proxy-targets.yaml"), home.resolve(".rsprox/proxy-targets.yaml"));
        installPlugins(home.resolve(".runelite"));
        installLoginPlugin(assets.resolve(LOGIN_PLUGIN), home.resolve(".rlcustom/sideloaded-plugins"));
        if (args.length > 0 && args[0].equals("--setup-only")) {
            System.out.println("ScapeMar connection and plugins configured.");
            return;
        }
        Path launcher = bundle.resolve("rsprox-launcher.jar");
        if (!Files.isRegularFile(launcher)) {
            throw new IOException("rsprox-launcher.jar is missing from this bundle.");
        }
        String java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "javaw.exe" : "java")
            .toString();
        ProcessBuilder game = new ProcessBuilder(java, "-jar", launcher.toString()).inheritIO();
        Path auto = bundle.resolve("ScapeMar-Auto.jar");
        if (Files.isRegularFile(auto)) {
            game.environment().put("JAVA_TOOL_OPTIONS", "-javaagent:" + auto);
        }
        game.start().waitFor();
    }

    private static JWindow showSplash(String text) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        JWindow[] window = new JWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            JLabel label = new JLabel("ScapeMar - " + text, JLabel.CENTER);
            label.setBorder(BorderFactory.createEmptyBorder(24, 48, 24, 48));
            window[0] = new JWindow();
            window[0].add(label);
            window[0].pack();
            window[0].setLocationRelativeTo(null);
            window[0].setAlwaysOnTop(true);
            window[0].setVisible(true);
        });
        return window[0];
    }

    private static Path checkForUpdates(Path bundle, Path updates) {
        try {
            String bundled = Files.readString(bundle.resolve("version.txt")).trim();
            String installed = Files.isRegularFile(updates.resolve("version.txt"))
                ? Files.readString(updates.resolve("version.txt")).trim()
                : bundled;
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(5)).build();
            String manifest = fetch(http, "update.json");
            Matcher version = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(manifest);
            if (version.find() && newer(version.group(1), installed)) {
                Path staging = updates.resolveSibling("update.new");
                deleteTree(staging);
                Files.createDirectories(staging);
                for (String file : new String[] {"proxy-targets.yaml", LOGIN_PLUGIN}) {
                    Matcher hash = Pattern.compile("\"" + Pattern.quote(file) + "\"\\s*:\\s*\"([0-9a-f]{64})\"")
                        .matcher(manifest);
                    byte[] data = fetchBytes(http, file);
                    if (!hash.find() || !sha256(data).equals(hash.group(1))) {
                        throw new IOException(file + " failed its checksum");
                    }
                    Files.write(staging.resolve(file), data);
                }
                Files.writeString(staging.resolve("version.txt"), version.group(1));
                deleteTree(updates);
                Files.move(staging, updates);
                installed = version.group(1);
            }
            return newer(installed, bundled) ? updates : bundle;
        } catch (Exception e) {
            return bundle;
        }
    }

    private static boolean newer(String candidate, String current) {
        String[] a = candidate.split("\\.");
        String[] b = current.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static String fetch(HttpClient http, String file) throws Exception {
        return new String(fetchBytes(http, file), StandardCharsets.UTF_8);
    }

    private static byte[] fetchBytes(HttpClient http, String file) throws Exception {
        HttpResponse<byte[]> response = http.send(
            HttpRequest.newBuilder(URI.create(UPDATE_URL + file)).timeout(Duration.ofSeconds(30)).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException(file + " returned " + response.statusCode());
        }
        return response.body();
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void installTarget(Path source, Path target) throws IOException {
        String bundled = Files.readString(source, StandardCharsets.UTF_8);
        if (Files.exists(target)) {
            if (Files.readString(target, StandardCharsets.UTF_8).equals(bundled)) {
                return;
            }
            Files.copy(target, target.resolveSibling("proxy-targets.yaml.scapemar-backup"),
                StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(target, bundled, StandardCharsets.UTF_8);
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }

    private static void installPlugins(Path runelite) throws IOException {
        Path profiles = runelite.resolve("profiles2/profiles.json");
        Path config = runelite.resolve("settings.properties");
        if (Files.isRegularFile(profiles)) {
            String json = Files.readString(profiles, StandardCharsets.UTF_8);
            List<Path> active = new ArrayList<>();
            Matcher blocks = PROFILE.matcher(json);
            while (blocks.find()) {
                String block = blocks.group(1);
                if (!ACTIVE.matcher(block).find()) {
                    continue;
                }
                Matcher name = NAME.matcher(block);
                Matcher id = ID.matcher(block);
                if (name.find() && id.find()) {
                    String profileName = name.group(1);
                    if (!profileName.equals("$rsprofile") && !profileName.contains("/")
                        && !profileName.contains("\\")) {
                        active.add(runelite.resolve("profiles2/" + profileName + "-" + id.group(1)
                            + ".properties"));
                    }
                }
            }
            if (active.size() != 1) {
                throw new IOException("Could not identify one active RuneLite profile. Install Quest Helper "
                    + "and 117 HD from the Plugin Hub in RuneLite.");
            }
            config = active.get(0);
        }
        Files.createDirectories(config.getParent());
        List<String> lines = Files.exists(config)
            ? new ArrayList<>(Files.readAllLines(config, StandardCharsets.UTF_8))
            : new ArrayList<>();
        Set<String> plugins = new LinkedHashSet<>();
        int settingLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(PLUGIN_KEY + "=")) {
                settingLine = i;
                for (String plugin : line.substring(PLUGIN_KEY.length() + 1).split(",")) {
                    if (!plugin.isBlank()) {
                        plugins.add(plugin.trim());
                    }
                }
            }
        }
        plugins.add("quest-helper");
        plugins.add("117hd");
        String setting = PLUGIN_KEY + "=" + String.join(",", plugins);
        if (settingLine >= 0 && lines.get(settingLine).equals(setting)) {
            return;
        }
        if (settingLine >= 0) {
            lines.set(settingLine, setting);
        } else {
            lines.add(setting);
        }
        if (Files.exists(config)) {
            Files.copy(config, config.resolveSibling(config.getFileName() + ".scapemar-backup"),
                StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(config, lines, StandardCharsets.UTF_8);
    }

    private static void installLoginPlugin(Path source, Path sideloaded) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException(LOGIN_PLUGIN + " is missing from this bundle.");
        }
        Files.createDirectories(sideloaded);
        Files.copy(source, sideloaded.resolve(LOGIN_PLUGIN), StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
