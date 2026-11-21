# Shadow Alert — Testing & Verification Guide

## 1. Automated Unit Tests

Unit tests are located in `app/src/test/java/com/shadowalert/app/`:

- `SessionDurationValidatorTest.kt` — Validates preset durations (5, 10, 15, 20, 30m), custom duration bounds (1..480m), null/zero/negative rejections, and daily limit bounds (1..1440m).
- `ElapsedTimeTrackerTest.kt` — Verifies monotonic `elapsedRealtimeMs` delta accumulation, background/screen-off pausing (`pauseTickAnchor`), protection against negative deltas and sleep spikes, session recovery (`restoreSessionIfNeeded`), and session ID reset isolation.
- `WarningEvaluatorTest.kt` — Verifies single-emission 5-minute, 2-minute, and limit-reached threshold firing, hydration across service restarts (`syncSession` with persisted Room alerts), zero duplicate spam, and threshold reset after `Add More Time`.
- `AnalyticsCalculatorTest.kt` — Verifies daily/weekly aggregation, 7-day average and trend %, session completion rate %, break frequency %, most-used app share %, override history items, supportive insights, and clock formatting.

Run unit tests and lint in an Android build environment (JDK 17 + Android SDK 35):
```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

---

## 2. Physical Device Verification Plan (Android 8.0 – Android 15)

Test on physical Android devices (not only emulators), as OEM power managers and Usage Access logs differ across hardware.

1. **First Installation & Onboarding** — Verify Splash Screen (Navy/Cyan identity), 3 Onboarding slides, and persistent `onboardingCompleted` state.
2. **Permission Denial & Manual Fallback** — Deny Usage Access and confirm automatic tracking clearly reports paused state while Manual Session Timer remains functional without fabricating usage.
3. **Permission Granted** — Grant Usage Access, Notifications (API 33+), and optional Overlay permission; confirm `ON_RESUME` updates status pills immediately.
4. **Screen Lock and Unlock** — Start an active session, lock the screen (`ACTION_SCREEN_OFF`), wait 1+ minute, unlock, and confirm locked time was NOT counted and service suspended polling cleanly while locked.
5. **App Switching** — Switch between the target app and Launcher/other apps; verify `UsageEvents` (`ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`) pauses and resumes elapsed time accurately.
6. **Midnight / Daily Reset** — Advance date past the configured reset hour and confirm `currentUsageMinutes`, `dailyExtraMinutes`, `overrideUntilMs`, and notification flags reset cleanly.
7. **Device Restart & Session Recovery** — Reboot device mid-session and confirm `BootReceiver` + `UsageMonitorService` restore persisted `accumulatedActiveMs` and previously triggered alerts from Room without re-firing past warnings.
8. **Notification Delivery** — Verify Level 1 (5m), Level 2 (2m), and Level 3 (Limit Reached) notifications deliver once per threshold on their respective channels.
9. **Daily Limit Reached & Strong Intervention** — Reach a monitored app's daily limit; verify `Strong Overlay Shield` opens `PauseInterventionActivity` when Overlay is granted, and falls back to high-priority notification otherwise.
10. **Deliberate Override (Level 4)** — Confirm override requires deliberate checkbox confirmation, records optional reason in `AlertEventEntity`, and logs cleanly in Analytics Override History.
11. **Temporary Extension & Take a Break** — Verify `+5m`/`+10m`/`+15m` updates `effectiveDailyLimitMinutes` or session intention, and `Take a Break` starts the 5-minute mindful break countdown.
12. **Database Persistence & Migration** — Verify Room v1 -> v2 `MIGRATION_1_2` preserves existing limits, sessions, and reflections across updates and force-stops.
13. **Dark & Light Mode** — Verify Navy-Blue & Cyan contrast in both Light and Dark themes.
14. **Permission Revocation** — Revoke Usage Access mid-session and confirm graceful fallback without crashes.
15. **Battery Consumption** — Verify negligible battery impact during screen-off idle periods via Android Battery Usage settings / `adb shell dumpsys batterystats`.
