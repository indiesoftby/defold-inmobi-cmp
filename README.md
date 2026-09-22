# InMobi CMP for Defold

Android native extension for **InMobi CMP 2.4.3**, using the SDK archive supplied by the developer. Supports GDPR / TCF 2.3, Additional Consent V2 and US Regulations / GPP. No AdMob account, UMP or advertising SDK is required by this extension.

## Project layout and installation

- `inmobi_cmp/`: reusable native extension, Android Java bridge, libraries, resources and LuaCATS definitions.
- `main/`: runnable Defold demonstration.
- `sdk/`: original `InMobiCMPSDKs.zip`, Android AAR, changelog, license notices and SHA-256 checksums.
- `tools/prepare_sdk.py`: reproduces the Defold library/resource layout from the original ZIP.

Open `game.project` in Defold. To use the extension in another project, copy the `inmobi_cmp` folder or publish this project as a Defold library dependency. The library exports only that folder. The original ZIP and demo are not bundled into consuming games. iOS is not implemented; the iOS archive is retained only inside the unmodified source ZIP.

The Android SDK is local. Maven resolves only its support dependencies; do not also add `com.inmobi:inmobicmp` as a Gradle dependency. API 21 is the SDK minimum; the effective device minimum is also constrained by the Defold version used to build the game.

## Existing InMobi account

Use the application already registered in the InMobi CMP portal. Set the policy URL, applicable regulations and actual mediation vendors there. Enable the **TCF storage encoding** for the Yandex integration (TCF or both TCF/GPP), not GPP-only. Enable Google Vendors / Additional Consent when needed by the selected vendors. Use the portal's standard consent UI with accept, reject and manage choices. Layout, language and theme are managed in the portal.

The downloaded archive contains a generic SDK, **not your p-code or app configuration**. Find the p-code in the portal's account profile. The Android package must match the protected app's package in the portal.

For command-line demo builds, create ignored `local.project`:

```ini
[android]
package = YOUR_REGISTERED_ANDROID_PACKAGE

[inmobi_cmp]
p_code = YOUR_P_CODE
```

Pass `--settings local.project` to Bob (or `--settings local.project` to `tools/build.py`). For editor builds, configure those values in `game.project` locally. An empty p-code displays setup instructions instead of starting CMP. The default demo package is `com.indiesoftby.inmobicmpdemo`; change it to match the portal before live tests. Installing a demo with the same package as an existing game can conflict with its signing key: use a test device/profile or register a separate demo property.

## Lua API

The native module is the global `inmobi_cmp`; no `require` is needed. Do not require `inmobi_cmp.api`: it is editor type metadata only. Configure `[inmobi_cmp] p_code` **at build time** in the consuming game's settings as well as passing that same value to `initialize`.

```lua
local function on_cmp_event(self, event, data)
    if event == "error" then
        print(data.code, data.message)
    elseif event == "gdpr_consent" or event == "us_consent" then
        local consent = inmobi_cmp.get_consent()
        -- Pass the applicable signals to your ad integration here.
        -- No universal boolean represents consent to all partners/purposes.
    end
end

function init(self)
    if inmobi_cmp.is_supported() then
        local ok, err = inmobi_cmp.initialize({
            p_code = sys.get_config_string("inmobi_cmp.p_code", ""),
        }, on_cmp_event)
        if not ok then print(err) end
    end
end

function final(self)
    inmobi_cmp.set_listener(nil)
end
```

| Function | Return value |
| --- | --- |
| `is_supported()` | `true` on Android, otherwise `false` |
| `initialize({ p_code = ... }, callback)` | `true` if accepted, otherwise `nil, error` |
| `set_listener(callback_or_nil)` | `true` or `nil, error` |
| `show_gdpr()` | `true` if request accepted, otherwise `nil, error` |
| `show_us_regulations()` | `true` if request accepted, otherwise `nil, error` |
| `get_status()` | Status table or `nil, error` |
| `get_consent()` | Consent snapshot or `nil, error` |
| `get_sdk_version()` | SDK version string or `nil, error` |

An internal, non-exported ContentProvider registers an Android lifecycle callback. With a configured p-code, it starts the SDK synchronously during the first Activity's `onActivityCreated`, **before `onActivityStarted`**, without replacing the game's Application class. This timing matters: InMobi 2.4.3 tracks foreground state through `onActivityStarted`; starting it only from Lua misses the first start and can delay its automatic form until a later foreground transition. SDK automatic display follows the portal's configuration and may precede Lua startup. Events and state are retained in the Java bridge for Lua to consume.

Lua `initialize` attaches the listener to that early-started instance and checks the p-code. The same p-code is idempotent; another p-code is rejected without replacing the existing listener. An optional leading `p-` is removed. The package is taken from the installed application, never overridden only for the consent request. A runtime-only p-code without build-time configuration returns `startup_configuration_required`; it never silently starts too late. Empty build-time configuration keeps the SDK dormant.

Commands returning `true` indicate acceptance, **not consent, completed loading or guaranteed UI display**. Later failures arrive as `error` events. Forms requested before configuration is loaded return `not_ready`. Other bridge errors include `invalid_p_code`, `startup_configuration_required`, `already_initialized`, `busy`, `activity_unavailable`, `bridge_unavailable`, `java_exception` and `unsupported_platform`. SDK errors retain their uppercase enum codes and human-readable messages. A failed network/configuration load is not converted into consent; the SDK controls recovery/cache behavior. A process restart retries SDK initialization.

On unsupported platforms `get_status()` returns `{ supported = false, initialized = false, loaded = false }`; other operations return `nil, "unsupported_platform"`. No consent or success is synthesized.

## Events and data

The callback receives `(self, event, data)` on the **Defold game thread**, never the Java UI thread. Java events use a thread-safe queue. Detach the listener in the script's `final`; replacing/detaching it inside a callback is supported. The update loop waits for a listener before polling events, preserving early startup events until Lua attaches. Consent also remains in SDK storage. There is one listener per engine instance.

| Event | Payload |
| --- | --- |
| `loaded` | SDK PingReturn: `gdpr_applies`, `us_regulation_applies`, `cmp_loaded`, `cmp_status`, `display_status`, version/ID fields |
| `ui_changed` | `display_status`, `display_message`, `regulation_shown`, `gbc_shown` |
| `gdpr_consent` | GDPRData: `tc_string`, `gpp_string`, `purpose`, `vendor`, publisher/feature data |
| `non_iab_consent` | `non_iab_vendor_consents` and SDK non-IAB metadata |
| `additional_consent` | `ac_string` |
| `us_consent` | USRegulationData, including `gpp_string`, notices, opt-outs and MSPA fields |
| `google_basic_consent` | `ad_storage`, `ad_user_data`, `ad_personalization`, `analytics_storage` |
| `region_changed` | Empty table; re-evaluate regional state before ad processing |
| `action` | `action`: SDK button enum; not a substitute for consent data |
| `legacy_ccpa_consent` | `usp_string`; forwarded for compatibility, no deprecated CCPA UI API exposed |
| `error` | `code`, `message` |

SDK public getter names are converted to snake_case; enum values retain SDK names. Unknown/null properties are **omitted**, never changed to `false`. Vendor/purpose IDs are **string keys**, e.g. `data.vendor.consents["123"]`. US numeric values retain the SDK encoding; they are not Lua booleans. `loaded`, `DISMISSED`, and button events do not grant permission to process data.

`get_status()` contains `supported`, `initialized`, `loaded`, and optional last `cmp` (PingReturn) / `ui` (DisplayInfo). `get_consent()` contains optional `gdpr`, `non_iab`, `additional`, `us`, `google_basic`, `legacy_ccpa` model snapshots plus `storage`. Model snapshots describe the latest data available during this process; standard storage is read fresh and survives process restart. Region changes do not make previous model snapshots proof of current applicability.

Android status also exposes `flow_ready`, `form_visible`, `revision` and `failed`. `flow_ready` means the native flow has resolved with no CMP activity or pending form request and no blocking SDK error; it does not grant consent. Continue checking applicability and stored signals. Native activity tracking handles missing HIDDEN callbacks, and closing a form without a choice does not by itself resolve the flow. Region changes invalidate readiness. Logo download failures remain non-blocking.

`get_consent().us_privacy` summarizes only GPP sections listed in `IABGPP_GppSID`: `known` reports whether applicable data was decoded, and `opt_out` combines sale, sharing, targeted-advertising opt-outs and GPC. Missing, malformed or unsupported applicable data leaves `known=false`; do not treat that as an opt-in. The original SDK models and storage remain available.

`storage` exposes only `IABTCF_*`, `IABGPP_*` and `IABUSPrivacy_String` keys with their original names and types. The extension never edits them or clears user consent. Other application preferences are not exposed. The demo logs consent for development; remove such diagnostics from production code.

## Yandex Boost integration quick guide

No advertising SDK is included or initialized in this standalone project.

1. **Configure regions in the InMobi portal.** In the app property's **Regulation Details → Which users you want to ask consent from?**, select the required GDPR audience (EEA, UK and Switzerland) and applicable US regions. To avoid a GDPR prompt in Belarus, exclude Belarus and do not select Worldwide. Leave GDPR in the US disabled for the separate US flow, and enable the automatic US opt-out notice trigger. Select the actual mediation vendors and enable TCF encoding (TCF or TCF/GPP, not GPP-only). See [InMobi property configuration](https://support.inmobi.com/choice/getting-started-cmp/protect-your-properties/protect-an-app).
2. **Start CMP on every launch, for all users.** Set the build-time p-code and call `inmobi_cmp.initialize({ p_code = sys.get_config_string("inmobi_cmp.p_code", "") }, on_cmp_event)` to attach the Lua listener. The extension starts the native SDK early; InMobi decides whether to show a form using portal rules, regional applicability and saved choices. Do not call `show_gdpr()` / `show_us_regulations()` unconditionally at startup or infer geography from the device language.
3. **Gate Boost initialization and ad loading on CMP processing.** Wait until applicability is known and any required choice has been collected or a valid saved choice established. Apply the relevant adapter signals, then initialize Boost and request ads. `loaded` alone is insufficient: it can arrive before the form opens. Missing applicability fields are unknown, not `false`; a closed form is not consent. The consuming game must implement this coordination—this extension has no universal `can_request_ads` API. Gate the game's advertising initialization and preload path.
4. **Use the stored consent signals.** InMobi writes TCF/Additional Consent to default SharedPreferences; [Yandex reads them automatically](https://ads.yandex.com/helpcenter/ru/dev/android/tcf-2-0), so no Lua string copying is required. Check TCF/GPP support and any additional consent/opt-out API requirements for each mediated network. Do not replace vendor/purpose choices or US opt-outs with a blanket `set_user_consent(true)`.
5. **Allow users to revisit their choice.** Connect a privacy settings button to the applicable form after CMP has loaded:

```lua
local state = inmobi_cmp.get_status()
local cmp = state and state.cmp
if state and state.loaded and cmp then
    if cmp.gdpr_applies == true then
        inmobi_cmp.show_gdpr()
    elseif cmp.us_regulation_applies == true then
        inmobi_cmp.show_us_regulations()
    end
end
```

Handle the calls' return values and subsequent callbacks, and re-evaluate adapter signals when choices or regional state change. These manual APIs do not override geography. Also provide a privacy policy link. Child-audience rules and age screening belong to the consuming application; CMP is not an age-verification system. Consent-or-pay and native theme customization are outside this initial wrapper.

## Build and validation

```text
python tools/prepare_sdk.py
python tools/build.py android --variant debug
python tools/build.py android --variant release
python tools/build.py windows --variant debug
python tools/build.py android --variant release --r8
```

The build helper pins stable Defold 1.13.1, downloads Bob into ignored `.cache`, and builds Android ARM64 + ARMv7. It can accept `--settings local.project`. Stable 1.13.1 does not support the newer `android.r8_keep_rules` setting: ordinary release builds use D8. The explicit `--r8` check instead uses pinned Defold 1.14.0 alpha (`9ca5465caa34c4872c3dcad260fbed3ea35f5c6a`) with the built-in engine keep rules. It writes separate `android-release-r8` artifacts and does not change the production baseline. Build reports are saved under `.cache`; output bundles are under `bundles/`.

See `tests/README.md` for automated and device checks and `VALIDATION.md` for the checks actually completed. A successful APK build alone does not prove that the live consent UI works.

## Sources

- [InMobi Android integration](https://support.inmobi.com/choice/implementing-cmp-via-code/mobile-app/android-app-implementation-sdk)
- [Yandex TCF consent](https://ads.yandex.com/helpcenter/ru/dev/android/tcf-2-0)
- [Defold native extensions](https://defold.com/manuals/extensions/)
- Reference architectures: [Defold UMP](https://github.com/tocaRepo/defold-androidextension-ump), [Defold Usercentrics](https://github.com/HGPoint/def_usercentrics).

The SDK distribution and its third-party notices remain in `sdk/`. No source code was copied from the reference extensions.
