# MK Finance

**MK Finance — إدارة أموالك**

إدارة ذكية لأموالك، بصوتك وبسهولة.

An independent Arabic-first Android personal finance manager. The approved product name is MK Finance — إدارة أموالك. The product direction is a complete personal finance manager with a obsidian, champagne-gold and emerald visual identity and voice-first interaction. Only features present in the source and verified by appropriate tests should be considered implemented.

## Product direction

- **Product level:** Complete personal finance manager.
- **Visual identity:** Obsidian dark interface with champagne-gold actions and emerald insights.
- **Interaction:** Voice is primary; touch and text remain fully supported alternatives.
- **Language and locale:** Arabic-first, right-to-left (RTL).
- **Default currency:** Yemeni rial (YER); the current wallet screen supports YER, USD, SAR and AED with separate balances.
- **Platform:** Native Android; minimum Android 6.0 (API 23).
- **Offline-first:** Core records, calculations and reports work on-device. Speech uses the installed system Activity (`ar-YE`) by default, with optional on-device recognition where supported. No model is bundled or imported; offline speech availability must be checked on the phone.

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
- [Deep audit](docs/DEEP_PROJECT_AUDIT.md) and [remediation](docs/AUDIT_REMEDIATION.md) — findings, fixes, actual test results and remaining device gates.
- [Decision log](docs/DECISIONS.md) — approved decisions and future changes.

**Documentation rule:** Any approved change to product scope, UI, voice behavior, data handling or architecture must update the relevant document in the same development task. The repository documentation is the source of truth; chat discussions alone do not change the specification.

## Current foundation (implementation status must be verified)

The existing source has been described as containing:
- Kotlin, Jetpack Compose and Material 3.
- Arabic-first dark UI and right-to-left layout.
- Local Room storage for transactions, wallets, transfers, categories, budgets, debts, savings goals, recurring income, recurring expenses and bills/payment records (Room schema v12; migrations 1→12 are implemented, but the full upgrade chain still requires database-level verification).
- Wallet creation with opening balances and per-wallet currency balances; transaction entry can select a wallet.
- Internal transfers are stored separately from income/expense, currently limited to wallets with the same currency and sufficient source balance.
- Currency-isolated YER dashboard/report totals; balances are not combined across currencies.
- Manual transaction entry and initial voice-assisted entry.
- Initial Arabic/Persian numeral normalization and keyword-based category suggestions.
- Transaction categories, editing, deletion, search and filters.
- Custom income/expense/shared categories with archive and restore.
- Monthly budgets with category/currency isolation and in-app threshold warnings.
- Debt and advance tracking with partial repayment/collection.
- Savings goals with partial contributions and withdrawals. Contributions reserve wallet funds without being counted as expenses; withdrawals return funds without being counted as income.
- Salary and recurring income rules (weekly/monthly), with manually confirmed receipts, wallet routing, pause/resume and per-period duplicate protection.
- Bills with one-time, monthly or yearly due dates, in-app due/overdue status, WorkManager reminders before due dates, and user-confirmed payment records linked to expenses. Notifications require Android 13+ permission where applicable; no automatic bill payment is performed.

Recurring income is not posted automatically: the user confirms actual receipt for each due period. Monthly schedules retain the chosen day across short months; weekly schedules advance by seven days.

These items still require source review and successful CI/device verification before being treated as release-ready.

## Build and verification

GitHub Actions runs JVM unit tests, builds the app and instrumentation-test APK, and runs Android lint. Android emulator execution and system-image setup are removed by owner request; Room/Compose instrumentation execution is now a real-device verification step. It can also build a signed release APK when the owner supplies all signing credentials through GitHub Secrets. A successful build and its artifact must be confirmed from the relevant workflow run; configuration alone is not proof of success.

Speech opens the installed system recognition Activity with `ar-YE`; optional direct on-device recognition is available only when the phone reports support. No Vosk runtime, model ZIP import or automatic network fallback is present. Offline operation and dialect accuracy still require a physical-phone check. See [voice setup and limitations](docs/OFFLINE_VOICE.md).

## Development

Current redesign working branch: `arena/92bb740d-malekfinance-android` (based on `feature/voice-finance`).

Work incrementally: review the existing source, implement one roadmap phase at a time, update documentation with each change, run relevant tests, then verify the debug build.


## الميزات التي أضيفت في دفعة التطوير الأخيرة
- استعلامات صوتية محلية للرصيد والدخل والمصروف والملخص وآخر العمليات والديون والادخار والميزانيات والفواتير، مع فهم أولي للأرقام المنطوقة العربية واليمنية والتواريخ النسبية، ومسودة تحويل تتطلب تأكيداً صريحاً.
- تقارير يومية وأسبوعية وشهرية وسنوية، مع مقارنة نطاق مخصص وفصل المحافظ والعملات.
- تصدير PDF وCSV وExcel XLSX.
- نسخ احتياطي محلي مشفر بكلمة مرور واستعادة ذرية؛ النسخ التلقائي غير المشفر في Android معطل.
- اختبارات إضافية لدفتر الأستاذ وRoom والترحيلات والتقارير والنسخ الاحتياطي وواجهات Compose. هذه الميزات تحتاج نتائج CI واختبار جهاز قبل إعلان الإصدار مستقراً.

- يدعم مساعد الصوت الآن تصفية سؤال المصروف حسب التصنيف عند ذكره صراحةً.
- إعداد توقيع Release جاهز لاستقبال Keystore عبر GitHub Secrets؛ لم يُضمّن مفتاح توقيع في المستودع.

## إعادة التصميم والمحرك المحلي — 2026-11-25

تنفيذ أولي جديد، وليس إصداراً معتمداً بعد:
- هوية حبرية/ذهبية/زمردية تشمل الألوان والمكونات والتنقل والأيقونة؛ إعادة بناء الرئيسية وبطاقة الصوت.
- رصيد المحافظ الفعلي لكل عملة، ملخص الشهر، إخفاء تفاصيل الرئيسية، مخطط إنفاق سبعة أيام، وبصيرة مالية حتمية على الجهاز.
- مختبر «ماذا لو» لخفض الإنفاق افتراضياً بناء على متوسط الشهر؛ لا يكتب عمليات مالية.
- تقسيم «مالي» إلى أدوات مختارة بدلاً من تكديس جميع النماذج في صفحة واحدة.
- **تحديث لاحق:** أُلغي Vosk والاستيراد؛ يستخدم الصوت الآن واجهة الهاتف مع ar-YE والتعرف المحلي الاختياري عند توفره؛ راجع تحديث إصلاح Note9 أدناه.
- لم يُضمّن نموذج صوتي في APK؛ لا تدّعي هذه الدفعة تدريب نموذج عربي من الصفر أو اعتماد دقته.
- أضيفت اختبارات للحسابات الجديدة والتنقل. يلزم التحقق من البناء وخدمة الصوت على الهاتف الحقيقي بعد هذا التغيير. راجع [نظام التصميم](docs/DESIGN_SYSTEM.md) و[الصوت المحلي](docs/OFFLINE_VOICE.md).

## معاينة التصميم في المتصفح

توجد معاينة تفاعلية مستقلة في `web-preview/` لمراجعة التصميم دون تنزيل APK لكل تعديل:

```sh
cd web-preview
npm ci
npm run dev
```

افتح نافذة Live Preview. يمكنك تجربة التنقل، عرض الهاتف، العمليات والمحافظ والتحويلات التجريبية والتقارير. البيانات معزولة في المتصفح وليست بيانات هاتفك. **هذه ليست نسخة Android أو محاكياً، والصوت الأصلي غير مفعّل هنا.** الميزانيات والادخار والفواتير أمثلة بصرية موسومة فقط. راجع [دليل معاينة المتصفح](docs/BROWSER_PREVIEW.md) للتشغيل وحدود التجربة وسير اعتماد التصميم.


### إصلاح توافق الصوت مع Note 9 / Android 10

المساعد يفتح واجهة `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` الموجودة على الهاتف افتراضياً، باللغة **ar-YE** ويستقبل `EXTRA_RESULTS` كما في المسار القديم الناجح. Android6–11 لا يُلزم بالربط المباشر بخدمة SpeechRecognizer. على Android12+ يمكن اختيار on-device المباشر فقط عند توفره فعلياً؛ لا fallback شبكي تلقائي عند الفشل.

النص المعترف به يملأ الحقول عبر `VoiceTransactionParser` الحالي ويعرض مسودة للتعديل؛ لا حفظ مالي دون تأكيد. انتقال التطبيق للخلفية أثناء نشاط التعرف الخارجي لا يلغي نتيجته، وطلب الإذن ينتظر استئناف الواجهة قبل البدء.

لا Vosk/JNA أو Moonshine أو نموذج عربي/Google Services داخل APK ولا تنزيلات من التطبيق. إعداد نشاط التوافق يطابق مصدر البناء الناجح `dc9416a`، دون PREFER_OFFLINE افتراضياً؛ يمكن اختياره كتفضيل مستقل لا يضمن عدم اتصال المزود. يلزم اختبار Note9 بوضع الطيران وWi-Fi مغلق. راجع [المقارنة والإصلاح والاختبارات](docs/OFFLINE_VOICE.md).

حسب طلب الإصلاح الأخير، الهدف APK أقل من **30 MB**، ويسجل CI حجم ملف APK الحقيقي في `APK-SIZE.txt`. لا تشغيل لمحاكيات Android؛ تبقى اختبارات JVM (ومنها Robolectric لاختبار واجهات Android دون جهاز افتراضي) وlint والبناء وبناء اختبارات الهاتف.
