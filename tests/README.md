# Validation

## Automated

`python tools/test_bridge.py` compiles the production Java bridge against the real local InMobi SDK model classes. Minimal Android scheduling/preferences stand-ins allow host-JVM tests of event serialization, null versus false, vendor maps, US numeric encodings, queue concurrency, standard storage filtering, validation and shutdown. These tests do not simulate the CMP's network or real UI lifecycle.

`python tools/test_bootstrap.py` also verifies the production ContentProvider and bridge against a simulated Android lifecycle and a substituted SDK startup boundary. It checks that empty configuration is dormant, SDK registration happens synchronously before `onActivityStarted`, p-code normalization is consistent, the same SDK instance is reused from Lua, a different p-code is rejected, and the bootstrap callback unregisters after one use. Real InMobi model classes are still used; live network/UI behavior is not simulated.

After `python tools/build.py windows`, run `python tools/test_desktop.py`. It launches the bundled executable with `--config=inmobi_cmp.run_tests=1`. The demo invokes `tests.desktop` against the compiled C++ module, then exits. Success prints `CMP_DESKTOP_TESTS_PASSED` and exits with code 0. The runner checks both the marker and exit status, and terminates a stalled test after 30 seconds.

Run Android debug and release builds using `tools/build.py`. Run the separate `android --variant release --r8` check for Defold's R8 rules plus extension keep rules; this requires the pinned alpha engine because stable 1.13.1 does not support the new R8 setting. Inspect the APK for both ARM ABIs, CMP activity/resources, Java bridge and SDK classes.

## Device checks with a configured InMobi property

- Match p-code and package ID. Confirm automatic initial display according to portal rules; loading alone must not authorize ads.
- Accept all, reject all and select individual vendors/purposes. Inspect callbacks and standard storage; compare choice changes after reopening.
- Restart the process: persisted choices remain available; the SDK decides whether another prompt is required.
- Test GDPR-applicable and non-applicable locations and configured US jurisdictions. `*_applies` unknown must remain distinct from false. US values preserve the SDK's numeric encoding.
- Open each settings screen again; check return to game, Back, background/foreground and repeated button taps.
- Test offline first launch, offline relaunch, invalid p-code/property and interrupted loading. No error may manufacture consent.
- Remove or replace a Lua listener from inside a callback; unload the GUI scene and verify late events cannot call destroyed scripts.
- Repeat important UI and consent checks on the release APK with R8 enabled.
- Future Boost integration: verify consent for each configured adapter before advertising requests. That integration is not part of this standalone project.

Uninstall/clear app data only on a dedicated test installation when a fresh-user scenario is needed. No consent-reset API is exposed in production.

## Testing from Belarus

The demo's GDPR button calls `ChoiceCmp.forceDisplayUI`; its US button calls `ChoiceCmp.showUSRegulationScreen`. These are manual opening APIs, not geography overrides. Inspection of the supplied 2.4.3 SDK confirms that both check the applicable-regulation list before opening their activity. No public debug-geography setter was found in this SDK.

For a dedicated test property, include Belarus in the consent audience or select Worldwide to make GDPR applicable there, and activate the GDPR legal basis/purposes. The [official property guide](https://support.inmobi.com/choice/getting-started-cmp/protect-your-properties/protect-an-app) documents GDPR for selected countries outside the EEA/US. A valid account p-code and an exactly matching registered Android package ID are required before either form can be tested.

For US tests, use a test device/network with US egress (for example, a VPN to the intended state) and enable that audience and the corresponding US regulation in the test property. Verify the SDK's reported region/applicability; a manual button alone does not establish a US test environment. Geography changes may require a process restart and configuration refresh. Do not change a production property's audience simply to test the demo, or interpret a hidden/not-applicable form as consent.
