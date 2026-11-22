# Malek Finance

**مالك فاينانس — أموالك تحت سيطرتك، بصوتك فقط.**

An independent Arabic-first Android personal finance manager. This repository is developed incrementally; only features present in the source and verified by CI should be considered implemented.

## Current foundation

- Native Android app using Kotlin, Jetpack Compose and Material 3.
- Arabic-first dark interface with right-to-left layout.
- Local Room database for income and expense transactions.
- Balance, total income and total expenses calculated from saved transactions.
- Manual transaction entry with explicit confirmation.
- Voice-assisted entry using the device's speech recognition activity.
- Arabic and Persian numeral normalization, basic amount extraction and keyword-based category suggestions.
- User review before saving; voice parsing is an assistant, not an automatic bank transaction.
- Transaction categories and deletion.
- Yemeni rial (YER) display; amounts are stored in hundredths of a rial.
- Minimum Android version: Android 6.0 (API 23).

## Build and verification

GitHub Actions runs JVM unit tests and assembles a debug APK on pushes to main and feature branches. The APK is uploaded as the MalekFinance-debug-apk workflow artifact when the build succeeds.

Speech recognition depends on the recognition app/service available on the device and may require an internet connection. Offline speech recognition is not guaranteed across devices.

## Next milestones

1. Verify the first CI build and test results.
2. Improve voice understanding for Yemeni Arabic and mixed natural-language amounts.
3. Add monthly budgets, recurring income, debts/loans and savings goals.
4. Add reports, search, export and encrypted backup.
5. Expand automated tests and validate on Android 6 through current Android versions.
