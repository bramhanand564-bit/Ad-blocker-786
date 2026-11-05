# Adbloker 786

Android DNS-based ad and tracker domain blocker.

## Current implementation

- Android VpnService exposes a local virtual DNS endpoint.
- Only packets addressed to that DNS endpoint are routed into the TUN interface; ordinary browsing traffic is not sent into the VPN.
- DNS requests are checked against built-in Normal, Advanced and Maximum rules, custom block rules and an allowlist.
- Allowed DNS requests are forwarded through a protected UDP socket to Cloudflare DNS at 1.1.1.1.
- A foreground notification provides a stop action.
- Query and blocked counters are stored locally in SharedPreferences.
- Unit tests cover domain matching, DNS parsing and IPv4/UDP packet checksums.
- No Accessibility service, screen reading, visual ad detection or auto-tapping.

## Limits

DNS filtering only blocks domains that are known and separable at DNS level. It cannot reliably remove first-party/server-side ads, YouTube in-stream ads, ads delivered from the same domain as normal content, or hard-coded IP traffic. Apps that use their own encrypted DNS and Android Private DNS can bypass DNS interception. This is not a full traffic-inspection VPN and does not inspect HTTPS content.

## Build

GitHub Actions runs unit tests and creates a debug APK on pushes to main. Download the artifact named adbloker-786-debug-apk from a successful workflow run.

A real Android device test is required before calling this app production-ready.
