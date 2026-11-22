# Malek Finance

**مالك فاينانس — أموالك تحت سيطرتك، بصوتك فقط.**

An independent Arabic-first Android personal finance manager. The approved product direction is a complete personal finance manager with a purple dark visual identity and voice-first interaction. Only features present in the source and verified by appropriate tests should be considered implemented.

## Product direction

- **Product level:** Complete personal finance manager.
- **Visual identity:** Dark interface with Neon Purple accents.
- **Interaction:** Voice is primary; touch and text remain fully supported alternatives.
- **Language and locale:** Arabic-first, right-to-left (RTL).
- **Default currency:** Yemeni rial (YER), with a multi-currency-ready design.
- **Platform:** Native Android; minimum Android 6.0 (API 23).
- **Offline-first:** Core financial records, calculations and local reports work on-device. Speech recognition availability/offline behavior depends on the device's recognition service.

## Product capabilities in the approved plan

- Home dashboard, income, expenses, categories, wallets and internal transfers.
- Voice assistant for recording transactions and asking financial questions, with confirmation before saving.
- Budgets, debts and loans, partial repayments, savings goals.
- Recurring income, salaries, bills and reminders.
- Financial reports, comparisons, export and encrypted backup.

These are product goals, not a claim that every capability is already implemented. See the roadmap for actual status.

## Documentation — source of truth

The `docs/` directory is the canonical reference for product scope and engineering decisions.

- [Product specification](docs/PRODUCT_SPEC.md) — approved features, screens, behavior and acceptance criteria.
- [Design system](docs/DESIGN_SYSTEM.md) — colors, typography, components, RTL and visual rules.
- [Voice system](docs/VOICE_SYSTEM.md) — intents, Arabic/Yemeni language handling, review and confirmation.
- [Architecture](docs/ARCHITECTURE.md) — app layers, data model, money precision, privacy and tests.
- [Roadmap](docs/ROADMAP.md) — phases and verified implementation status.
- [Decision log](docs/DECISIONS.md) — approved decisions and future changes.

**Documentation rule:** Any approved change to product scope, UI, voice behavior, data handling or architecture must update the relevant document in the same development task. The repository documentation is the source of truth; chat discussions alone do not change the specification.

## Current foundation (implementation status must be verified)

The existing source has been described as containing:
- Kotlin, Jetpack Compose and Material 3.
- Arabic-first dark UI and right-to-left layout.
- Local Room storage for income and expense transactions.
- Balance, income and expense summaries.
- Manual transaction entry and initial voice-assisted entry.
- Initial Arabic/Persian numeral normalization and keyword-based category suggestions.
- Transaction categories and deletion.

These items still require source review and successful CI/device verification before being treated as release-ready.

## Build and verification

GitHub Actions is configured to run JVM unit tests and assemble a debug APK. A successful build and its artifact must be confirmed from the relevant workflow run; configuration alone is not proof of success.

Speech recognition depends on the recognition app/service available on the device and may require an internet connection. Offline speech recognition is not guaranteed across devices.

## Development

Active development branch: `feature/voice-finance`.

Work incrementally: review the existing source, implement one roadmap phase at a time, update documentation with each change, run relevant tests, then verify the debug build.
