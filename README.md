# ScapeMar

ScapeMar client downloads for world 255. The server is already hosted. [Choose your download](https://oshabana.github.io/scapemar-client/).

## Download

Choose your computer from the [latest release](https://github.com/oshabana/scapemar-client/releases/latest):

| Computer | File | Then |
| --- | --- | --- |
| Mac | `ScapeMar-macos.dmg` | Drag **ScapeMar** to Applications and open it |
| Windows | `ScapeMar-windows-setup.exe` | Run it; ScapeMar opens and gets Start menu and desktop shortcuts |
| Linux | `ScapeMar-linux.zip` | Extract it and open `Launch ScapeMar.sh` |

Java is included in every download; there is nothing else to install. `ScapeMar-windows.zip` is a no-install Windows copy: extract it and open `Launch ScapeMar.bat`.

The downloads are not signed with a paid developer certificate, so the first launch shows a warning. On Mac, approve the network prompt; if macOS blocks the app, open **System Settings → Privacy & Security** and click **Open Anyway**. On Windows, click **More info**, then **Run anyway**.

The launcher installs the public server connection and selects **Quest Helper** and **117 HD** for RuneLite's Plugin Hub. RSProx then opens: select **ScapeMar**, choose **RuneLite**, and click **Launch**. On the login screen, click **New User** to pick a username and password (typed twice), or **Existing User** to sign in.

If either plugin does not appear, open RuneLite's wrench icon, open **Plugin Hub**, search for **Quest Helper** or **117 HD**, and click **Install**. The plugins come from RuneLite's Plugin Hub and receive updates there.

The bundle contains the [Eclipse Temurin Java 21 runtime](https://adoptium.net/), the [official RSProx launcher](https://github.com/blurite/rsprox/releases/tag/v1.0), the public connection profile, and our setup launcher. It contains no server code, account credentials, or modified RuneLite binary. The [release checksums](https://github.com/oshabana/scapemar-client/releases/latest) let you check the downloaded zips. ScapeMar is unofficial and is not affiliated with Jagex, RuneLite, or RSProx.

## If you already use RSProx

The launcher replaces any existing RSProx target file with ScapeMar's and saves your old one as `proxy-targets.yaml.scapemar-backup` in the same folder. To keep your own targets, import this URL in RSProx instead:

`https://raw.githubusercontent.com/oshabana/scapemar-client/main/proxy-targets.yaml`

On Mac, the first custom target needs `127.0.255.3` on `lo0`. The bundle adds it when needed. If you have multiple custom targets, follow the [RSProx group ID instructions](https://github.com/blurite/rsprox#macos-support-osrs).

## Maintainer

Run `./build-bundles.sh` on a Mac with a JDK (21 or newer) and `makensis` (`brew install makensis`) to make the four release files. It downloads the Temurin Java 21 runtimes, checks them against Adoptium's checksums, and caches them in `dist/runtimes`. `./build-login-plugin.sh` builds only the login plugin into `dist/ScapeMar-Login.jar`, where the game server's local `play.sh` picks it up. It also verifies the official RSProx v1.0 launcher SHA-256 before packaging. The public address and login modulus live in `proxy-targets.yaml`; rebuild and republish when they change.
