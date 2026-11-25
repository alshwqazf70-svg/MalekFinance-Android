# جرد نطاق المراجعة — 2026-11-25

هذا جرد ملفات المصدر والإعداد والاختبارات والتوثيق ضمن نطاق الفحص، وليس شهادة نجاح لكل ملف أو ادعاء اختبار كل مسار. المراجعة اليدوية ركزت على المسارات عالية الخطورة، وأكملتها فحوص ساكنة واختبارات بحسب نوع الملف. استُبعدت التبعيات ومخرجات البناء والتصدير وGit. راجع التقرير الرئيسي للنتائج والحدود.

| الملف | نوع التحقق |
|---|---|
| `.github/workflows/android.yml` | تحليل YAML ومراجعة الإعداد؛ لم يُشغّل Actions |
| `.github/workflows/web-preview.yml` | تحليل YAML ومراجعة الإعداد؛ لم يُشغّل Actions |
| `.gitignore` | مراجعة إعدادات ساكنة |
| `README.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `app/build.gradle.kts` | مراجعة إعدادات ساكنة |
| `app/src/androidTest/java/com/malekfinance/FinanceBackupInstrumentedTest.kt` | قراءة/تحليل ساكن؛ لم يُنفّذ على Android |
| `app/src/androidTest/java/com/malekfinance/FinanceDbMigrationInstrumentedTest.kt` | قراءة/تحليل ساكن؛ لم يُنفّذ على Android |
| `app/src/androidTest/java/com/malekfinance/FinanceNavigationUiTest.kt` | قراءة/تحليل ساكن؛ لم يُنفّذ على Android |
| `app/src/androidTest/java/com/malekfinance/FinancePdfExportInstrumentedTest.kt` | قراءة/تحليل ساكن؛ لم يُنفّذ على Android |
| `app/src/androidTest/java/com/malekfinance/FinanceRoomIntegrityTest.kt` | قراءة/تحليل ساكن؛ لم يُنفّذ على Android |
| `app/src/main/AndroidManifest.xml` | تحليل XML؛ ليست معاينة Android |
| `app/src/main/java/com/malekfinance/FinanceBackupManager.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceBill.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceBillOperations.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceBudget.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceCategory.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceDebt.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceDebtOperations.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceInsights.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceLedger.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinancePersonStatement.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceRecurringExpense.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceRecurringIncome.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceReportAnalytics.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceReportExporter.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceSavings.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceSavingsOperations.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/FinanceTransferOperations.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/MainActivity.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/notifications/BillReminderWorker.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/ui/BillManagementSection.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/ui/FinanceApp.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/ui/FinanceTheme.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/ui/LocalVoicePanel.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/ui/PersonCreditSection.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/ui/PremiumDashboard.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/ui/RecurringExpenseSection.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/ui/RecurringIncomeSection.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/ui/SavingsManagementSection.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/voice/LocalVoiceEngine.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/voice/OfflineModelArchive.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/voice/VoiceQueryEngine.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/java/com/malekfinance/voice/VoiceTransactionParser.kt` | مراجعة ساكنة؛ المنطق النقي ضمن مشغّل JVM حيث ينطبق، واجهات Android غير مبنية |
| `app/src/main/res/drawable/ic_launcher_art.xml` | تحليل XML؛ ليست معاينة Android |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | تحليل XML؛ ليست معاينة Android |
| `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml` | تحليل XML؛ ليست معاينة Android |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | تحليل XML؛ ليست معاينة Android |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | تحليل XML؛ ليست معاينة Android |
| `app/src/main/res/values/ic_launcher_background.xml` | تحليل XML؛ ليست معاينة Android |
| `app/src/main/res/values/strings.xml` | تحليل XML؛ ليست معاينة Android |
| `app/src/test/java/com/malekfinance/FinanceBillPolicyTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceBudgetPolicyTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceCategoryPolicyTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceDebtPolicyTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceInsightsTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceLedgerTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinancePersonStatementTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceRecurringExpensePolicyTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceRecurringIncomePolicyTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceReportAnalyticsTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceReportExporterTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/FinanceSavingsPolicyTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/voice/OfflineModelArchiveTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/voice/VoiceQueryEngineTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `app/src/test/java/com/malekfinance/voice/VoiceTransactionParserTest.kt` | مشغّل JVM المعزول؛ ليس JUnit/Gradle |
| `build.gradle.kts` | مراجعة إعدادات ساكنة |
| `docs/ARCHITECTURE.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `docs/BROWSER_PREVIEW.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `docs/DECISIONS.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `docs/DESIGN_SYSTEM.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `docs/OFFLINE_VOICE.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `docs/PERSON_CREDIT.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `docs/PRODUCT_SPEC.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `docs/ROADMAP.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `docs/VOICE_SYSTEM.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `gradle.properties` | مراجعة إعدادات ساكنة |
| `scripts/audit-core.py` | أداة تدقيق مُنفّذة؛ قيودها موضحة بالتقرير |
| `scripts/audit-migrations.py` | أداة تدقيق مُنفّذة؛ قيودها موضحة بالتقرير |
| `settings.gradle.kts` | مراجعة إعدادات ساكنة |
| `web-preview/.gitignore` | مراجعة إعدادات ساكنة |
| `web-preview/README.md` | مراجعة اتساق التوثيق والنطاق، لا دليل تشغيل |
| `web-preview/e2e/preview.spec.js` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/index.html` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/package-lock.json` | npm audit / اختبارات وبناء الويب |
| `web-preview/package.json` | npm audit / اختبارات وبناء الويب |
| `web-preview/playwright.config.js` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/public/favicon.svg` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/scripts/export-standalone.mjs` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/scripts/package-preview.py` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/src/credit.js` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/src/finance.js` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/src/icons.js` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/src/main.js` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/src/style.css` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/tests/credit.test.js` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/tests/finance.test.js` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
| `web-preview/vite.config.js` | مراجعة المصدر + اختبارات/بناء الويب حيث ينطبق |
