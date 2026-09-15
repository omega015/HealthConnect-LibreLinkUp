# Changelog

All notable changes to this maintained fork of **HealthConnect-LibreLinkUp** are documented here.

The project was originally created by Sam Steele. Version 1.6 is the first release of this fork maintained by **omega015** and is based on the upstream 1.5 codebase.

## [1.7] - 2026-09-15

### Wear OS complication compatibility

- Expanded Wear OS complication support for Samsung watch faces and other faces that accept different complication data types.
- Added four discoverable complication providers so compatible slots can choose the representation they support:
  - **LibreLinkUp - Glucose** for text-capable slots.
  - **LibreLinkUp - Glucose + trend** for text-capable slots with a Unicode trend arrow appended to the glucose value.
  - **LibreLinkUp - Glucose (big)** for image/icon-capable slots.
  - **LibreLinkUp - Glucose + trend (big)** for image/icon-capable slots with the glucose value and trend arrow rendered together.
- Added support for `SHORT_TEXT`, `RANGED_VALUE` and `LONG_TEXT` complication types for the text providers.
- Added `ICON` and `SMALL_IMAGE` providers for watch-face slots that reject text complications.
- Added adaptive bitmap rendering for glucose values in image complications.
- Added live trend-arrow rendering for the glucose + trend variants.
- Prevented duplicate trend arrows on watch faces that render both complication text and icon fields by using a neutral icon for the text-based glucose + trend provider.
- New glucose readings and display-unit changes now request refreshes for all four complication providers.
- Tested on physical Samsung Wear OS watch faces with text-only, image-only and mixed complication slots, including automatic refresh on new LibreLinkUp readings.

### Build and release

- Updated phone and wearable apps to `versionCode 7` / `versionName 1.7`.

## [1.6] - 2026-09-14

### Highlights

- Added configurable glucose synchronization with **Standard** and **Fast** modes.
- Added optional low and high glucose alerts on Wear OS.
- Added selectable **mmol/L** and **mg/dL** display units for the watch Tile, complication and alert notifications.
- Added reliable phone-to-watch alert-settings synchronization with acknowledgement and retry handling.
- Improved LibreLinkUp regional login handling and general synchronization reliability.

### Glucose synchronization

- Retained Standard mode using Android WorkManager at a 15-minute interval.
- Added Fast mode using an Android foreground service for more dependable synchronization.
- Added selectable Fast intervals of 1, 2, 3, 5, 10, 15 and 30 minutes.
- Fast mode continues to operate if the phone notification permission is denied; Android may still show the service under Active apps depending on OS behaviour.
- Refactored the common LibreLinkUp -> Health Connect -> Wear OS synchronization path so Standard and Fast modes use the same glucose-processing logic.
- Added duplicate suppression based on LibreLinkUp `FactoryTimestamp` so the same reading is not repeatedly inserted into Health Connect or resent to Wear OS.
- Health Connect insertion is now confirmed before a reading is marked as synchronized, allowing failed inserts to be retried.
- Wear transfer state is tracked independently from Health Connect state so a Wear transfer failure does not discard a successful Health Connect sync.

### LibreLinkUp account and regional handling

- Added automatic handling of LibreView regional redirects returned during login, including regional endpoints such as `api-eu2.libreview.io`.
- Improved login validation and error handling.
- Added explicit logout support which clears the cached session and stops glucose synchronization.
- The selected/resolved LibreView endpoint is retained for subsequent use.

### Wear OS glucose alerts

- Added optional **Low** and **High** glucose alerts generated locally on the watch when new glucose data arrives.
- Added configurable low and high thresholds.
- Added configurable re-arm margins (hysteresis) to prevent repeated alerts while glucose remains around a threshold.
- Added optional repeating alerts while glucose remains outside the configured range.
- Repeat intervals can be set to 1, 2, 5, 10, 15, 30 or 60 minutes.
- Added optional persistent vibration until the alert is acknowledged or dismissed.
- Added an **Acknowledge** action to glucose alert notifications.
- Swiping/dismissing an alert also stops persistent vibration.
- Low and high alert state is maintained independently and only re-arms after glucose returns through the configured hysteresis margin.
- Added a watch launcher screen for requesting/checking notification permission required by watch alerts.
- Alerts default to disabled and depend on LibreLinkUp cloud availability, phone synchronization and Wear connectivity. They are not a replacement for Abbott/Libre medical alarms.

### Alert settings and phone-to-watch confirmation

- Added a **Watch glucose alerts** section to the phone app.
- Alert settings are stored on the phone and mirrored to the paired Wear OS app through the Wear Data Layer.
- Added request IDs and watch acknowledgements so the phone can confirm that settings were received and persisted by the watch.
- User-initiated settings sends wait for watch confirmation, retry once after approximately 15 seconds if required, and report if confirmation is still not received.
- Phone settings are retained if the watch is unavailable rather than being rolled back.
- Outstanding user-initiated requests are preserved across quiet/app-start synchronization so delayed acknowledgements remain attributable to the correct save operation.
- Settings queued while the watch is temporarily disconnected can be delivered when Wear connectivity returns.

### Units and validation

- Added watch display-unit selection between mmol/L and mg/dL.
- Unit selection applies consistently to the Tile, complication and glucose alert notifications.
- Alert evaluation remains canonical in mg/dL internally regardless of the selected display unit.
- Added validation ranges:
  - Low threshold: 60-100 mg/dL / 3.3-5.6 mmol/L.
  - High threshold: 120-400 mg/dL / 6.7-22.2 mmol/L.
  - Re-arm margin: 0-9 mg/dL / 0-0.5 mmol/L.
- Added validation to prevent low/high re-arm ranges from overlapping.
- Added boundary-safe unit conversion so changing display units does not make valid endpoint values invalid due to rounding.

### Wear OS Tile and complication

- Updated the Tile and complication to follow the display units selected on the phone rather than relying only on the unit supplied by LibreLinkUp.
- Added canonical mg/dL transfer alongside the source glucose value so watch display and alert logic use consistent data.
- Unit-only settings changes request Tile and complication refreshes without requiring them to be removed and re-added.

### Phone user interface

- Reorganised the phone app into **LibreLinkUp account**, **Glucose sync** and **Watch glucose alerts** sections.
- Added Standard/Fast synchronization selection and Fast interval selection.
- Added alert configuration controls for thresholds, units, re-arm margins, repeat behaviour and persistent vibration.
- Added clearer validation messages for permitted threshold and hysteresis ranges.
- Added logged-in state and logout controls.

### Build and release

- Updated phone and wearable apps to `versionCode 6` / `versionName 1.6`.
- Added GitHub Actions workflows for debug/test APK builds and manually triggered release builds.
- Added release APK signing using repository secrets and a dedicated fork release key.
- Release builds are zip-aligned, signed, signature-verified and uploaded as separate phone and wearable artifacts.
- The signing key used by this fork is different from the original developer's key. An original-developer build or a debug build must therefore be uninstalled before installing this fork's first signed release. Future releases from this fork can update normally when signed with the same key.

### Release validation

The 1.6 release candidate has been tested on a physical Android phone and Wear OS watch, including clean installation of the signed APKs, Health Connect permission flow, LibreLinkUp login, 1-minute Fast synchronization with the phone notification hidden, phone-to-watch settings acknowledgement, live Tile glucose and live complication glucose.

## [1.5] - Upstream baseline

Version 1.6 of this fork was developed from Sam Steele's upstream 1.5 release. For the original project's history, see the upstream repository at <https://github.com/c99koder/HealthConnect-LibreLinkUp>.
