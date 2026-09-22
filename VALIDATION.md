# Validation РІР‚вЂќ 2026-09-19

## Completed checks

- Original ZIP and extracted InMobi Android AAR match the SHA-256 values in `sdk/checksums.json`.
- The packaged `classes.jar` and all 68 Android resource files match the supplied AAR byte for byte.
- Host-JVM tests compile the production bridge against the real SDK models: unknown versus false, partial vendor choice, Additional Consent, numeric US opt-outs, Google Basic Consent, current IAB storage filtering, 400 concurrent callbacks, error forwarding and shutdown behavior pass.
- Startup-order tests exercise the production provider and bridge with lifecycle/SDK-startup stand-ins: SDK registration precedes Activity start, missing configuration remains dormant, and Lua attachment does not repeat SDK initialization.
- Windows demonstration builds and executes using stable Defold 1.13.1. Native module availability, unsupported-platform results and invalid Lua argument checks pass (`CMP_DESKTOP_TESTS_PASSED`, process exit 0).
- Stable Defold 1.13.1 Android debug and release APK/AAB builds pass for ARM64 and ARMv7.
- Separate Defold 1.14.0 alpha R8 release builds pass for both Android architectures. Build logs confirm R8 execution and provide `mapping.txt`; the optimized APK retains the Java bridge, SDK classes and reflected model getters.
- APK inspection confirms both native ABIs, CMP layout and activity, expected network permissions, and absence of the source SDK ZIP from the app payload.

Build reports/logs are in `.cache/`. Bundles are in `bundles/`. The demo uses a locally generated debug signing key; these are test artifacts, not a production store release.

## Version distinction

Stable baseline: **Defold 1.13.1**, engine `574678c7d44be490d874fbed2d0ae6211feec4d9`.

R8 validation only: **Defold 1.14.0 alpha**, engine `9ca5465caa34c4872c3dcad260fbed3ea35f5c6a`. The stable Bob does not implement the newer `android.r8_keep_rules` setting. The explicit `--r8` build uses the alpha engine and separate output directories; it does not replace the stable baseline.

## Android device smoke check

Installed the stable debug APK with `adb install -r` on a JamboPhone JP1 running Android 13 (720 x 1600). The standalone package `com.indiesoftby.inmobicmpdemo` launches and reports SDK 2.4.3 through the native bridge. Both form buttons return `not_ready` with the empty p-code, and the status button reports `supported=true`, `initialized=false`, `loaded=false` and empty consent storage. Returning from Home resumes the demo. No fatal exception was observed in the captured app-process log. Defold emits a recurring `DLIB SOCKET: Unknown result code 19` diagnostic; it does not prevent these smoke checks and has not been investigated as a CMP network failure (CMP initialization was dormant).

App-process logs and screenshots are saved locally in `.cache/device/`.

After the user supplied the account p-code and registered package, built a configured debug APK. With explicit user authorization, uninstalled the existing Play-installed game and installed this demo using the registered package ID (the uninstall also removed that game's local data). Configuration is in ignored `local.project`.

Live SDK configuration loaded: `gdpr_applies=true`, `us_regulation_applies=false`. GDPR opened automatically on the Belarus device. The first screen displayed AGREE and More Options; the latter opened purposes with consent switches off and SAVE & EXIT. Saving without granting purpose consent emitted GDPR, non-IAB and Additional Consent callbacks and populated standard TCF preferences. Restarting the process retained the same TC string and produced вЂњGDPR is applicable but no need to re-trigger the screenвЂќ. The manual US API produced HIDDEN / вЂњUS regulations not applicableвЂќ; no US form was displayed.

The portal logo produced `FAILED_LOGO_DOWNLOAD`; consent UI still worked. The first-screen theme has no separate reject button and lists 1738 partners; review the portal theme and actual vendor selection before game integration. A Defold sound permission diagnostic was also observed without a fatal crash; no phone permission was added.

Device testing exposed a stale VISIBLE event after the SDK activity closed. The bridge now allows another form request when the host has regained window focus, even if the SDK omitted HIDDEN. A host regression test covers busy while unfocused, reopening after focus returns, and duplicate pending requests. Bob settings are staged in the ignored cache to avoid duplicate-resource errors when local.project is at the project root.

## Remaining live checks

US form rendering in an applicable region, offline SDK behavior, accept-all and individual vendor choices, and release/R8 UI behavior remain unverified. The real-device checks above used the stable debug build. Initial release/R8 build checks predate the window-focus fix.

The Java host tests use Android scheduling/preferences stand-ins; they do not establish live SDK behavior. The device scenarios are listed in `tests/README.md`.

Yandex Boost and its adapters are intentionally not installed in this standalone project. TCF/Additional Consent compatibility follows the documented storage interfaces; full mediation behavior requires a later integration test in the consuming game.

The fixed debug APK was rebuilt and installed with `adb install -r`. On the same process: manually opened GDPR, saved with purpose consent off, requested US (received not applicable rather than stale busy), and opened GDPR again successfully. Evidence: `.cache/device/fixed-reopening.log` and `.png`. The GDPR form was left open for inspection.

## Integration fixes backported on 2026-09-22

Ported the consuming game's native flow readiness/lifecycle tracking, applicable GPP opt-out decoding and preservation of startup events until a Lua listener attaches. Updated the public Lua annotations and API documentation.

- `python tools/test_bootstrap.py` passes the existing bridge and startup suites plus the imported `CmpFlowTest` lifecycle/GPP regression suite.
- `python tools/build.py android --variant debug` passes on Defold 1.13.1 for ARM64 and ARMv7.
- No new device, release or R8 run was performed for this backport. Host tests use lifecycle stand-ins and do not verify live consent forms.
