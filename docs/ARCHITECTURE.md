# Architecture

The app uses Android framework APIs and native Java views. It has no runtime library dependencies or backend.

| Component | Responsibility |
| --- | --- |
| `MainActivity` | App selection, session start, countdown, and additions during an active session |
| `Store` | Private preferences, deadlines, boot count, and persisted selection rules |
| `SessionClock` | Elapsed-time countdown within a boot; wall-clock fallback after restart |
| `SelectionRules` | Union of existing and requested apps while active; replacement after expiry |
| `BlockService` | Window events, foreground package checks, blocking overlay, and Home action |
| `FocusHomeActivity` | Plain launcher that omits selected apps during a session |
| `FocusPolicy` | Grayscale, deadline alarms, and experimental device-owner policies |
| `FocusDeadlineReceiver` | Policy synchronization after the alarm, boot, or package update |
| `FocusAdmin` | Receiver for the experimental device-owner integration |

## Session State

Starting a session saves both wall-clock and elapsed-realtime deadlines and the current boot count. A session cannot be started over an existing active session. Active selection updates take a union with the saved list, so an attempted removal cannot shrink it. Additions leave both deadlines unchanged.

The Accessibility service reacts to window changes and also checks the active root package every 300 milliseconds. A selected foreground package triggers an Accessibility overlay and a throttled Home action. Policy synchronization runs approximately once per second while the service is connected.

Extensions accept only positive additional time while a session is active. The store serializes session starts, selection updates, and extensions. It extends both deadlines without changing selected apps, refreshes the saved boot count after a reboot, and invalidates the cached alarm and managed policy deadline. Deadline arithmetic rejects overflow. A backward clock change cannot make an extension reduce the previously stored wall-clock deadline.

## Restoration

Grayscale stores the previous display settings before applying changes. The deadline alarm and subsequent service checks restore those values when the session expires. Boot and package-update broadcasts clear cached alarm scheduling state so the alarm can be scheduled again.

## Experimental Management

When Android reports this package as device owner, policy synchronization can hide selected packages and prevent uninstalling Focus Lock. On Android 11 and later it also requests disabled user controls for its own package. Intended hidden targets are saved before applying changes so synchronization can retry and restore them later.

This path requires exact-alarm access before a managed session starts. After expiry it attempts to restore hidden apps, remove its uninstall restriction, and restore the previous disabled-control list. Device ownership remains until removed through the app outside an active session.

These policy calls are compiled but have not been tested on a device provisioned with this app as owner. Failure recovery and interactions with other device policies remain unverified.
