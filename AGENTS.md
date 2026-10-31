# Agent Instructions

## Project target

- This project is a minimal launcher for the **HY300 Pro projector**. Do not target other projector models unless the user changes the scope.
- Verify projector-specific behavior using this repository, output from the HY300 Pro, or evidence supplied by the user. Do not infer support from another Android TV device.
- Do not implement hardware or firmware features that are unavailable on the HY300 Pro. Keep unsupported actions honest and visible to the user.
- On the verified HY300 Pro, OEM projector settings are available at `com.htc.htcsettings/com.htc.activity.HomeActivity`. Treat that as firmware-specific and show a clear unavailable message if it is missing on a target device.

## Architecture and UI

- The app is Kotlin with native Android Views. **Do not use Jetpack Compose** or add a UI framework.
- Keep responsibilities separated: `MainActivity` coordinates the launcher, `data/` handles app discovery and favorites persistence, `model/` holds app data, and `ui/` contains reusable views and overlays.
- Preserve D-pad and remote control navigation, visible focus states, and TV-friendly sizing when changing the UI.
- For nested rounded surfaces, derive the outer corner radius from the inner radius plus the inset between them.
- Reuse the existing Android resources and Lucide-derived vector icons. Preserve their notices in `THIRD_PARTY_NOTICES-LUCIDE.txt`.
- Avoid new dependencies unless the requested feature needs them.

## Work efficiently

- Inspect the relevant code, instructions, and working tree before editing. Search with `rg` and read only the files needed for the task.
- Preserve existing user changes. Make the smallest complete change that fits the current architecture.
- Resolve facts from the repository before asking the user. Make reasonable choices for reversible details; ask only when a missing decision materially changes the result.
- Carry out clear, authorized work through implementation and relevant verification. Do not stop at a plan unless asked for one.
- Do not run tests or builds unless the user asks for verification or the task requires them. Never claim hardware compatibility based only on a local build.

## Device, firmware, and secrets

- Only install the APK, change the active home app, or alter projector/system settings when the user explicitly requests that device action or it is clearly required by their request.
- Do not uninstall system apps, clear their data, or change Wi-Fi, picture, sound, or account settings.
- Treat signing keys, API keys, local configuration, and generated APKs as sensitive. Do not print or commit secrets.
- Report device verification separately from static inspection and local builds. Do not imply behavior was confirmed on the HY300 Pro unless it was.

## Build and verification

- The existing build is Gradle with JDK 17 and Android SDK Platform 35. The debug build command is:

  ```bash
  ./gradlew :app:assembleDebug
  ```

- The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
- For requested device verification, report separately whether the build succeeded, the APK was installed, and behavior was checked on the actual projector.
- Do not add tests or run them unless the user asks or the task requires verification.

## Commits

- Create commits only when the user explicitly asks for one.
- Use Conventional Commits 1.0.0, in English:

  ```text
  <type>[optional scope][!]: <short description>
  ```

- Prefer a concise imperative subject, such as `fix(home): keep focused app inside shelf`.

## Communication

- Reply in the user's language. Be direct and concise: lead with the result, then include relevant checks and limitations.
- Use plain language. State uncertainty and blockers clearly.
