# InMobi CMP for Defold

Use **InMobi CMP on Android** to collect and read user privacy choices from your Defold application. The extension includes InMobi CMP **2.4.3** and exposes GDPR / TCF 2.3, Additional Consent V2 and US Regulations / GPP through a Lua API.

InMobi offers the free **Essentials** tier; see the [InMobi plan overview](https://support.inmobi.com/choice/inmobi-cmp-premium/inmobi-cmp-premium) for features and optional Premium services. You need an InMobi CMP account and a registered application to configure consent forms.

Android is the supported platform. Other platforms provide stubs so your project can still run with `is_supported()` checks. The bundled SDK requires Android API 21 or later; your Defold version may require a higher minimum.

## Installation

Add this URL to **Dependencies** in your project's `game.project`, then select **Project > Fetch Libraries** in the Defold editor:

```text
https://github.com/indiesoftby/defold-inmobi-cmp/archive/refs/heads/main.zip
```

For reproducible builds, replace the branch archive with an archive URL for a specific commit. The library exports only `inmobi_cmp/`; the demo and SDK source archive are not included in your application. You can also copy that directory into your project.

The InMobi SDK is bundled with the extension. Maven resolves its support libraries; do not add a second `com.inmobi:inmobicmp` dependency.

## Configuration

Register your Android application in the [InMobi CMP portal](https://support.inmobi.com/choice/getting-started-cmp/protect-your-properties/protect-an-app). Configure its privacy policy URL, applicable regions and regulations, vendors, and consent UI. Choose the storage encodings required by the SDKs that will consume the consent signals. Layout, language and theme are managed in the portal.

Set your registered Android package and account p-code in `game.project`:

```ini
[android]
package = com.example.myapp

[inmobi_cmp]
p_code = YOUR_P_CODE
```

The package must match your registered application. Find the p-code in your InMobi account profile. It must be configured at build time and passed to Lua `initialize` with the same value. An empty p-code leaves CMP inactive.

## Quick start

Add this code to a script in your application. The native module is the global `inmobi_cmp`; no `require` is needed. `inmobi_cmp/api.lua` provides editor type definitions only.

```lua
--- Handle CMP events.
local function on_cmp_event(self, event, data)
    if event == "error" then
        print(data.code, data.message)
    elseif event == "gdpr_consent" or event == "us_consent" then
        local consent = inmobi_cmp.get_consent()
        -- Pass the applicable signals to your ad integration here.
        -- No universal boolean represents consent to all partners/purposes.
    end
end

--- Attach the CMP listener when the script starts.
function init(self)
    if inmobi_cmp.is_supported() then
        local ok, err = inmobi_cmp.initialize({
            p_code = sys.get_config_string("inmobi_cmp.p_code", ""),
        }, on_cmp_event)
        if not ok then print(err) end
    end
end

--- Release the listener when the script is destroyed.
function final(self)
    inmobi_cmp.set_listener(nil)
end
```

InMobi decides whether to display a form using your portal configuration, regional applicability and saved choices. Attach the listener on every launch. Use the manual form methods when the user opens privacy settings, rather than forcing a form at startup.

## Lua API

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

An internal, non-exported ContentProvider registers an Android lifecycle callback. With a configured p-code, it starts the SDK synchronously during the first Activity's `onActivityCreated`, **before `onActivityStarted`**, without replacing your application's Application class. This timing matters: InMobi 2.4.3 tracks foreground state through `onActivityStarted`; starting it only from Lua misses the first start and can delay its automatic form until a later foreground transition. SDK automatic display follows the portal's configuration and may precede Lua startup. Events and state are retained in the Java bridge for Lua to consume.

Lua `initialize` attaches the listener to that early-started instance and checks the p-code. The same p-code is idempotent; another p-code is rejected without replacing the existing listener. An optional leading `p-` is removed. The package is taken from the installed application, never overridden only for the consent request. A runtime-only p-code without build-time configuration returns `startup_configuration_required`; it never silently starts too late. Empty build-time configuration keeps the SDK dormant.

Commands returning `true` indicate acceptance, **not consent, completed loading or guaranteed UI display**. Later failures arrive as `error` events. Forms requested before configuration is loaded return `not_ready`. Other bridge errors include `invalid_p_code`, `startup_configuration_required`, `already_initialized`, `busy`, `activity_unavailable`, `bridge_unavailable`, `java_exception` and `unsupported_platform`. SDK errors retain their uppercase enum codes and human-readable messages. A failed network/configuration load is not converted into consent; the SDK controls recovery/cache behavior. A process restart retries SDK initialization.

On unsupported platforms `is_supported()` returns `false`, `get_status()` returns `{ supported = false, initialized = false, loaded = false }`, and all other operations return `nil, "unsupported_platform"`.

### Events and data

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

## Using consent with other SDKs

Wait for the native flow to resolve, then read the applicable consent signals before initializing SDKs or requesting ads that depend on them. `loaded` alone is insufficient; `flow_ready` reports flow completion, not consent to all processing. Unknown applicability or missing consent data must not be interpreted as permission.

The extension exposes consent data and leaves SDK-specific decisions to your application. Apply the required vendor, purpose and opt-out signals for each integration. Re-evaluate them after consent or region changes. There is no universal `can_request_ads` flag.

### Privacy settings

Provide controls to reopen the applicable consent forms after CMP has loaded. For example, a GDPR settings button can call:

```lua
local state = inmobi_cmp.get_status()
if state and state.loaded and state.cmp and state.cmp.gdpr_applies == true then
    local ok, err = inmobi_cmp.show_gdpr()
    if not ok then print(err) end
end
```

Use `show_us_regulations()` for applicable US settings. If both regulations apply, make both settings accessible. Handle return values and subsequent events; these calls do not override regional applicability. Age screening and application-specific privacy decisions belong to your application.

### Yandex Ads / Boost

For this integration, enable **TCF storage encoding** in the InMobi portal (TCF or TCF/GPP, not GPP-only) and configure the actual mediation vendors. Enable Additional Consent when required by those vendors.

InMobi writes TCF and Additional Consent signals to Android SharedPreferences; [Yandex Ads reads these signals automatically](https://ads.yandex.com/helpcenter/ru/dev/android/tcf-2-0). Apply any additional privacy APIs required by the selected mediation adapters, including US opt-out signals, before initializing advertising or loading ads. Do not replace granular choices with a blanket `set_user_consent(true)`.

Yandex Ads and its mediation adapters must be installed and configured separately. When privacy settings change, suspend new ad requests and refresh cached ads according to your advertising integration.

## Demo, build and testing

Open this repository's `game.project` in Defold to run the demo. It provides controls to attach the listener, open GDPR and US settings, and inspect status and consent data. Configure a registered package and p-code to test on Android. Desktop builds demonstrate the unsupported-platform API.

For command-line demo builds, put your settings in an ignored `local.project` file using the configuration above, then run:

```text
python tools/build.py android --variant debug --settings local.project
```

The helper stages settings under `.cache` before invoking Bob. For editor builds, configure `game.project` locally instead.

Repository layout:

- `inmobi_cmp/`: native extension, bundled Android library/resources and Lua type definitions.
- `main/`: runnable Defold demo.
- `sdk/`: original SDK archive, Android AAR, changelog, license notices and checksums.
- `tools/`: SDK preparation, build and test scripts.
- `tests/`: automated tests and device test instructions.

The SDK files are already prepared. Run `python tools/prepare_sdk.py` only when regenerating the library/resource layout from the archived SDK. Build and test commands:

```text
python tools/test_bootstrap.py
python tools/build.py android --variant debug
python tools/build.py android --variant release
python tools/build.py windows --variant debug
python tools/test_desktop.py
python tools/build.py android --variant release --r8
```

The build helper pins Defold **1.13.1** and builds Android ARM64 and ARMv7. Ordinary release builds use D8. The separate `--r8` check uses pinned Defold **1.14.0 alpha** (`9ca5465caa34c4872c3dcad260fbed3ea35f5c6a`) with the built-in engine keep rules; stable 1.13.1 does not support that setting. Build reports are written to `.cache/` and bundles to `bundles/`.

See [testing](tests/README.md) for automated and device scenarios, dated results and remaining coverage.

## References

- [InMobi Android integration](https://support.inmobi.com/choice/implementing-cmp-via-code/mobile-app/android-app-implementation-sdk)
- [Defold native extensions](https://defold.com/manuals/extensions/)

The SDK distribution and its third-party notices are preserved in `sdk/`.
