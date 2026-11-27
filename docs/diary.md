# Development diary
- v0.1: Replaced shared-cookie prototype with AndroidX named profiles. Added Rust registry/JNI, native account management, explicit profile deletion, retained live workspaces, and fail-closed compatibility checks.
- App is unofficial; login flow and isolation require device verification. No credential interception or authentication workarounds.
- Launcher artwork is a simplified vector adaptation of the custom logo concept.

- v0.1.1: Floating-window continuity fix. Handle smallestScreenSize, screenLayout, keyboard, density and uiMode changes in place; preserve live WebViews on resize. Save bounded per-profile browser histories and sidebar state for unavoidable Activity recreation, update the last chat on SPA history changes and on pause. Device regression: open a chat, type an unsent draft, enter pop-up view, resize across portrait/landscape thresholds, maximize and switch accounts; verify no reload, draft remains on ordinary resizing, and navigation history recovers if Activity is recreated. Actual process death cannot guarantee DOM/draft restoration. Preview signing may differ from prior builds.
