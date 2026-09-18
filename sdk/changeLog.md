# Changelog
## [2.4.3] - 2026-05-27
### Added
- Added support for special purposes, features, special features, opt out, and SDKs information in the storage disclosures.

## [2.4.2] - 2026-04-07
### Added
- Added support for AC String V2

## [2.4.1] - 2026-02-12
### Added
- Added support for TCF 2.3

## [2.4.0] - 2026-01-29
### Added
- Support for dynamic font and size customization via portal
- Single click opt-out for US Regulations
- Ability to configure custom text for the second GDPR screen
- Premium feature availability based on subscription status

## [2.3.3] - 2025-08-20
### Fixed
- Saving Gpp string when publisher restriction is present
- Fixed the value of `IABGPP_2_TCString` saved in shared preferences
- Fixed crash while saving the gdpr consent again

## [2.3.1] - 2025-07-30

### Added
- MSPA support for 10 more states in US (DE,FL,IA,MT,NE,NH,NJ,OR,TN,TX).
- Updated IAB encoder dependency
``` 
implementation 'com.iabgpp:iabgpp-encoder:3.2.3'
```
- Added support for the Android OS version 15 (API 35).

### Fixed
- Fixed the publisher vendor related issues.

## [2.2.3] - 2025-06-10

### Added
- Added Portuguese-Brazil and Portuguese-Portugal languages support.

### Fixed
- Fixed the gdprApplies for US regulations


## [2.2.2] - 2025-04-24

### Fixed
- Fixed the USP string for Non-California States


## [2.2.1] - 2025-03-04

### Added
- Added support for Colour customisation from Themes Section in portal.

### Fixed
- Fixed a minor issue on Advanced text customisation.


## [2.2.0] - 2025-02-17

### Added
- Added “Consent or Pay” feature for GDPR regulation. 
- Added GDPR support for countries like India, Vietnam, Hong kong, Thailand, Malaysia, Singapore and Philippines. 
- Added support for Hindi, Thai, Malay, and Vietnamese languages.

### Fixed
- Fixed few issues regarding Visit and Session events in Analytics.


## [2.1.1] - 2025-01-27

### Added

- Support for GDPR in USA using the API `ChoiceCmp.forceDisplayUI()`.
- GDPR support for all the countries listed on the portal.
- Support for Japanese, Mandarin, Korean, and Bahasa language. 


## [2.1.0] - 2024-10-24

### Added
- 13 months expiry for GDPR consent


## [2.0.2] - 2024-09-10

### Added
- Removed auto pop-up for CCPA flow.
- Added versioning for sdk. Use `getSDKVersion()` to get current sdk version.
- Removed the `onCmpUIShown(info: PingReturn)` callback and added a callback
  `onCMPUIStatusChanged(status: DisplayInfo)` to inform when the CMP UI status is changed.
- Added support for Ukranian and Turkish languages.
- Added `usRegulationApplies` flag in the PingReturn model to inform whether the US regulations is applicable at any point of time.

### Fixed
- Fixed the issue where Accept all button in MSPA change preferences screen is taking theme color
- Fixed the issue where `onUserMovedToOtherState` callback in MSPA is triggering when the user is moving to another state and regulation is changing.


## [2.0.1] - 2024-07-10

### Added
- Deprecated `getTCData()`. Use `getGDPRData()` instead.

### Fixed
- Saving user consent choices after app is killed.


## [2.0.0] - 2024-07-03

### Added

- Support for MSPA
 ```
ChoiceCmp.showUSRegulationScreen(this)
```
- Callback for getting MSPA consent after user has given consent or consent is saved.
 ```
override fun onReceiveUSRegulationsConsent(usRegulationData: USRegulationData) {}
 ```
- Callback if user has moved from one state to other due to which applicable regulation is changed.
 ```
override fun onUserMovedToOtherState() {}
 ```
- Added IAB encoder dependency
``` 
implementation 'com.iabgpp:iabgpp-encoder:3.1.1'
```
-Changed the way of customizing UI. Changed the model names and introduced the builder to create the object.

```
ChoiceStyle.Builder().setThemeMode(ThemeMode.AUTO). setLightModeColors(ChoiceColor()). setDarkModeColors(ChoiceColor()).build() 
```


### Deprecated

- Deprecated CCPA, using MSPA is recommended.


## [1.2.2] - 2024-06-13

### Added

- Included Iceland, Liechtenstein, Norway in the list of EEA countries.


### Fixed

- Fixed the crash related to late initialisation of few variables.
- Fixed the Proguard issues related to Gson conversion.


## [1.2.1] - 2024-05-02

### Added

- Added support for Android 14 devices for apps targeting API 34.
- Included Switzerland in the list of EEA countries.


### Fixed

- Fixed the issue related to the vendor numbers and details on each purpose.
- Fixed the behaviour on back press from CCPA screen.
- Fixed the crash on activity destroying while opening CCPA screen.
- Fixed the issue in customisation of “More Options” button.


## [1.2.0] - 2024-03-22

### Added

- Support for Google Basic Consent which can be integrated along with GDPR/CCPA
- Google Basic Consent can also be displayed independently
 ```
ChoiceCmp.showGBCScreen(this)
```
- Callback for getting GBC consent while calling startChoice method
 ```
override fun onGoogleBasicConsentChange(consents: GoogleBasicConsents) {}
 ```
- Added gson dependency
``` 
implementation 'com.google.code.gson:gson:2.8.8' 
```
- CCPA will only be shown in US region
- GDPR will not be shown in US region


### Fixed

- Corrected inaccuracies in CCPA values under certain conditions
- Corrected colors applied in various fields while passing values to ChoiceStyleSheet
- Fixed showing number of purposes if language other than English is selected
- Fixed retention days in vendor description
- GDPR doesn't show up if it is disabled from portal
- CCPA doesn't show up if it is disabled from portal

### Deprecated

- Deprecated the following fields in ChoiceStyleSheet while passing in `startChoice` method for customisation:  
  tabForegroundColor
  infoButtonForegroundColor
  infoScreenBackgroundColor
  infoScreenForegroundColor
  globalTextColor
  listTextColor


## [1.1.0] - 2024-01-25

### Added

- Support for configuring dark and light mode styles.
 ```
ChoiceCmp.startChoice( 
  app = application, 
  packageId = packageId,  
  pCode = <YOUR PCODE>,  
  callback= choiceCmpCallback,  
  resources = ChoiceStyleResources(themeMode = ThemeMode.AUTO, 
                                   styleId = R.raw.choice_style_sheet,
                                   styleIdNight = R.raw.choice_style_sheet_dark)
)
```

## [1.0.0] - 2023-12-21

### Added
- Package name changed from “com.inmobi.choice” to “com.inmobi.cmp”
- Changed consent screen from dialog box to bottom sheet

### Fixed
- User can now access purpose detail in legitimate section
- End user will not see non-IAB vendors deleted by publisher
- Remove objection button is clickable when one or more purpose is deselected
