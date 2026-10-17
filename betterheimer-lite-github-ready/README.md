# BetterHeimer Lite — Fabric 26.3

A client-side Fabric mod inspired by IP-range Minecraft server finders. It checks Minecraft Java status responses from a **private or explicitly authorized local IPv4 range**, then adds responsive servers to Minecraft's normal Multiplayer server list (`servers.dat`). No mod is required on the target servers.

## In-game controls

The main menu includes two buttons:

- **BH Scan** — opens the scanner screen and starts a scan using the current saved settings. No world needs to be loaded.
- **BH Config** — opens the configuration and scan screen from the main menu.

The BetterHeimer Lite screen lets you edit the IPv4 CIDR range, port, timeout, launch delay, max host count, and parallel worker count (1–16). A live progress bar shows percentage, hosts checked, servers found, and servers newly saved. Use **Save** to persist settings, **Start scan** to save and scan, **Cancel** to request cancellation, and **Done** to return to the previous screen.

Commands remain available while in a world:

- `/betterheimer scan` — start a scan using saved settings.
- `/betterheimer cancel` — stop after the current network attempt finishes.
- `/betterheimer reload` — reload settings from the config file.

Settings are stored at `config/betterheimerlite.json` within the active Minecraft instance, but normal use does not require editing that file manually.

## Safety and scope

- Public IPv4 CIDRs are rejected in code. Supported network classes are RFC1918 private ranges (`10/8`, `172.16/12`, `192.168/16`), loopback (`127/8`), and link-local (`169.254/16`).
- IPv4 CIDR notation is required; hostnames and IPv6 are not supported.
- Uses a bounded worker pool (1–16 simultaneous status checks); default is 8 workers. Launch delay can be configured from 0–5000 ms to tune connection rate.
- Hard cap: 4,096 usable host addresses per scan. `maxHosts` can lower the cap, not raise the hard limit.
- The status parser skips the response's `players` object and does not retain player sample names. There is no login, offline-mode, or authentication testing.
- Each newly discovered server is added to and saved in the vanilla server list immediately; already-listed addresses are not duplicated.

Only scan networks you own or have permission to test.

## Build and install

1. Build with Java 25 and a compatible Gradle installation (Gradle 9.6.0 is recommended for Minecraft 26.3).
2. Run `gradle build` from this folder.
3. Copy `build/libs/betterheimer-lite-1.1.0.jar` into the Fabric instance's `mods` folder.
4. Install Fabric Loader for Minecraft 26.3 and Fabric API `0.163.0+26.3`.
5. Launch Minecraft. Open **BH Config** on the title screen to set your CIDR, or choose **BH Scan** for a one-click scan using saved settings.

Example config (normally edited in-game):

```json
{
  "range": "192.168.1.0/24",
  "port": 25565,
  "timeoutMs": 700,
  "delayMs": 25,
  "maxHosts": 1024,
  "parallelism": 8
}
```

A private `/20` contains up to 4,094 usable host addresses, so set `maxHosts` to `4096` to allow that size. Use only on networks you own or are authorized to test. The worker count is capped at 16 and the scan size at 4,096 hosts.

## Build requirements and verification

- Minecraft Java 26.3
- Fabric Loader 0.19.5+
- Fabric API `0.163.0+26.3`
- Java 25
- Gradle 9.6.0 recommended

Output: `build/libs/betterheimer-lite-1.1.0.jar`.

This authoring environment only has Java 21 and no Gradle installation, so the project has not been compiled or run in Minecraft here. The first local/CI build is still required to verify compilation against the installed 26.3 toolchain.
