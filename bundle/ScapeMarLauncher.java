import java.awt.GraphicsEnvironment;
import java.io.IOException;
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
        installTarget(bundle.resolve("proxy-targets.yaml"), home.resolve(".rsprox/proxy-targets.yaml"));
        installPlugins(home.resolve(".runelite"));
        installLoginPlugin(bundle.resolve(LOGIN_PLUGIN), home.resolve(".rlcustom/sideloaded-plugins"));
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
        new ProcessBuilder(java, "-jar", launcher.toString()).inheritIO().start().waitFor();
    }

    private static void installTarget(Path source, Path target) throws IOException {
        String bundled = Files.readString(source, StandardCharsets.UTF_8);
        if (Files.exists(target)) {
            String existing = Files.readString(target, StandardCharsets.UTF_8);
            if (!existing.equals(bundled)) {
                String previous = bundled.replace("name: ScapeMar", "name: OpenRune Friends");
                if (!existing.equals(previous)) {
                    throw new IOException("An RSProx target file already exists at " + target
                        + ". Move it aside or import the ScapeMar URL in RSProx; your targets were not changed.");
                }
                Files.copy(target, target.resolveSibling("proxy-targets.yaml.scapemar-backup"),
                    StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(target, bundled, StandardCharsets.UTF_8);
            }
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
