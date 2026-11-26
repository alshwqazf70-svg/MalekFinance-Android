# MK Finance — Interactive design sandbox

A separate Arabic RTL web preview for reviewing the Android product's new visual direction. **Not the Android application, not an emulator, and not proof that an APK builds.**

```sh
npm ci
npm run dev    # 0.0.0.0:5174, Arena preview hosts supported
npm test
npm run build
```

Optional browser tests:

```sh
npx playwright install chromium
npm run test:e2e
```

`CHROMIUM_EXECUTABLE_PATH` can point to an existing compatible browser. Browser binaries and build artifacts are not committed.

- Working sandbox: navigation, responsive handset view, currency selection, privacy toggle, manual records and review drafts, search, wallets, same-currency transfers, computed reports and what-if saving scenarios.
- Visual examples only: budget limits, savings goal and bill cards; explicitly labeled in the UI.
- Android-only: installed system speech Activity (`ar-YE`) with optional on-device recognition when available, Room, notifications, encrypted backups and native exports. No speech model is bundled. No microphone or cloud speech fallback in this preview.
- Demonstration data only. Changes use unencrypted browser `localStorage`, separate from phone data; never enter real financial information. Reset/empty actions require confirmation.
- This is hand-authored JS/CSS, not compiled Compose. Approved visual changes need a separate Android implementation and device verification.

See [the Arabic guide](../docs/BROWSER_PREVIEW.md) for limitations, review workflow and verification details. Tajawal font files are served locally through `@fontsource/tajawal` (SIL Open Font License); retain its license notices when redistributing.

## Standalone download (no temporary sandbox URL)

```sh
npm run export:standalone
```

Creates the ignored `exports/MK-Finance-preview.html` with scripts, styles, fonts and favicon embedded. Open the downloaded file in a browser that permits local HTML JavaScript. Verified via `file://` in offline Chromium; some mobile file viewers disable scripts. Local-file storage support varies by browser. Regenerate after design changes: downloaded copies do not hot-reload.

## In-browser source and file access

Development defaults to **port 5174**. The visible “فتح الكود وملفات التنزيل” link opens `/preview-files/`: plain-text JS/CSS/core viewers plus normal same-origin HTTP attachments, not chat `sandbox:` file links. Run `npm run export:bundle` to prepare both downloads (Python 3 required for ZIP packaging). Source viewing works even without exports; missing downloads return an explanatory 404. These development middleware routes are not static-build routes. They still require a reachable sandbox preview gateway.

### دفتر الدين

من **مالي ← دفتر الدين**: شراء آجل باسم البائع، مع تاريخ/وقت، ثم تسجيل سداد وكشف مفصل قابل للنسخ والمشاركة والطباعة (بحسب المتصفح). الشراء لا يغير رصيد النقد؛ السداد يغيره. جرّب «ألف حق قات عبده مهدي» في المساعد لفتح مسودة تتطلب تأكيداً. لا بيانات حقيقية ولا إرسال تلقائي. التواريخ المختارة حسب توقيت جهازك والكشف بتوقيت عدن. كود الحسابات الإضافي في `src/credit.js` ومتاح للقراءة أيضاً في `/preview-files/credit.txt`.
