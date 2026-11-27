# Claude Switcher

Unofficial Android app for switching between authorized Claude workspaces. Kotlin Android UI, Rust account registry via JNI, independent AndroidX WebKit profiles. Requires a WebView provider supporting MULTI_PROFILE. No account credentials are collected by the app. Google sign-in or other web authentication may reject embedded browsers; no bypass is provided.

## Build
Use GitHub Actions or JDK 17, Gradle 8.9, Android SDK 35, NDK 27.2.12479018, Rust and cargo-ndk. Run cargo test in rust-core, then cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release, then gradle :app:assembleDebug from root. Release signing requires private signing credentials. Preview builds are debug signed; updates may need a reinstall if signing keys change.

## Device verification
Sign in to two accounts using normal supported login methods. Switch repeatedly and verify identity, history, and login after force-stop. Verify removal, renaming, reordering, file picker, back navigation and unsupported WebView behavior. Background pages remain in memory but Android can reclaim them. Do not treat the app as production verified until these checks pass.
