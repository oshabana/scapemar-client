import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

public class ScapeMarAuto {
    private static final long FIND_TIMEOUT_MS = 180_000;
    private static final long IDLE_EXIT_MS = 20_000;

    public static void premain(String args, Instrumentation instrumentation) {
        Thread thread = new Thread(ScapeMarAuto::run, "scapemar-auto");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        try {
            long deadline = System.currentTimeMillis() + FIND_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                Container bar = findLaunchBar();
                if (bar != null) {
                    SwingUtilities.invokeAndWait(() -> launch(bar));
                    Thread watcher = new Thread(ScapeMarAuto::watchGame, "scapemar-watch");
                    watcher.start();
                    return;
                }
                Thread.sleep(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Container findLaunchBar() {
        for (Window window : Window.getWindows()) {
            Container found = search(window);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Container search(Container parent) {
        if (parent.getClass().getName().endsWith("components.LaunchBar")) {
            return parent;
        }
        for (Component child : parent.getComponents()) {
            if (child instanceof Container container) {
                Container found = search(container);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void launch(Container bar) {
        try {
            for (JComboBox<?> combo : combos(bar, new ArrayList<>())) {
                select(combo, "RuneLite");
                select(combo, "ScapeMar");
            }
            Method launch = bar.getClass().getMethod("launchConfiguration");
            launch.invoke(bar);
            Window window = SwingUtilities.getWindowAncestor(bar);
            if (window instanceof java.awt.Frame frame) {
                frame.setState(java.awt.Frame.ICONIFIED);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<JComboBox<?>> combos(Container parent, List<JComboBox<?>> into) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComboBox<?> combo) {
                into.add(combo);
            } else if (child instanceof Container container) {
                combos(container, into);
            }
        }
        return into;
    }

    private static void select(JComboBox<?> combo, String label) throws ReflectiveOperationException {
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object item = combo.getItemAt(i);
            if (item != null && label.equals(nameOf(item))) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String nameOf(Object item) throws ReflectiveOperationException {
        for (String getter : new String[] {"getName", "getDisplayName"}) {
            try {
                return String.valueOf(item.getClass().getMethod(getter).invoke(item));
            } catch (NoSuchMethodException ignored) {
            }
        }
        return item.toString();
    }

    private static void watchGame() {
        String java = Path.of(System.getProperty("java.home"), "bin").toString();
        long self = ProcessHandle.current().pid();
        boolean seen = false;
        long goneSince = 0;
        try {
            while (true) {
                Thread.sleep(2000);
                boolean running = ProcessHandle.allProcesses().anyMatch(handle -> handle.pid() != self
                    && handle.info().command().filter(command -> command.startsWith(java)).isPresent()
                    && handle.info().arguments().map(args -> String.join(" ", args)
                        .contains("net.runelite.client.RuneLite")).orElse(false));
                if (running) {
                    seen = true;
                    goneSince = 0;
                } else if (seen) {
                    if (goneSince == 0) {
                        goneSince = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - goneSince > IDLE_EXIT_MS) {
                        System.exit(0);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
