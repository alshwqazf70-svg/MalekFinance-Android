import "@fontsource/tajawal/arabic-400.css";
import "@fontsource/tajawal/arabic-500.css";
import "@fontsource/tajawal/arabic-700.css";
import "@fontsource/tajawal/arabic-800.css";
import "./style.css";
import { icon } from "./icons.js";
import {
  CURRENCIES,
  parseMoney,
  money,
  snapshot,
  balanceFor,
  walletBalance,
  createDemo,
  validateState,
  savingScenario,
  makeTransfer,
  textDraft,
} from "./finance.js";

import { creditAccounts, creditAccount, addCreditPurchase, repayCreditAccount, creditStatement, creditDate, validateCreditState, isCreditCommand, creditDraft } from "./credit.js";

const STORAGE = "mk-finance-design-sandbox-v1";
const app = document.querySelector("#app");
const modal = document.querySelector("#modal");
let storageAvailable = true;
let protectedStoredData = false;
let data;
try {
  const raw = localStorage.getItem(STORAGE);
  let saved;
  try {
    saved = raw ? JSON.parse(raw) : null;
  } catch {
    saved = null;
  }
  const valid = validateState(saved) && validateCreditState(saved);
  data = valid ? saved : createDemo();
  // Never overwrite an unreadable ledger with demo data after the next edit.
  protectedStoredData = raw !== null && !valid;
  if (protectedStoredData) storageAvailable = false;
} catch {
  data = createDemo();
  storageAvailable = false;
}
let page = "home",
  currency = "YER",
  hidden = false,
  phone = false,
  reduction = 20,
  range = 30,
  tool = "wallets";
let search = "",
  kindFilter = "ALL",
  toastTimer;
let creditSelection = null;
const routes = [
  ["home", "الرئيسية", "home"],
  ["transactions", "العمليات", "receipt"],
  ["voice", "المساعد", "mic"],
  ["reports", "التقارير", "chart"],
  ["money", "مالي", "wallet"],
];
const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const amount = (v, code = currency) =>
  `<bdi>${esc(money(v, code, hidden))}</bdi>`;
const ratio = (a, b) =>
  b > 0n ? Math.min(100, Math.max(0, Number((a * 10000n) / b) / 100)) : 0;
function notify(text) {
  const toast = document.querySelector("#toast");
  toast.textContent = text;
  toast.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("show"), 3800);
}
function savedNotice(text) {
  notify(
    storageAvailable
      ? text
      : "تم التغيير في ذاكرة الصفحة فقط؛ لن يبقى بعد إعادة تحميلها لأن التخزين غير متاح أو محمي.",
  );
}
function save() {
  if (protectedStoredData) {
    storageAvailable = false;
    notify("البيانات السابقة غير مقروءة ومحميّة من الاستبدال؛ التغييرات في ذاكرة الصفحة فقط حتى تؤكد إعادة التجربة.");
    return;
  }
  if (!validateState(data) || !validateCreditState(data)) {
    storageAvailable = false;
    notify("لم تُكتب حالة غير متسقة أو تتجاوز حدود المعاينة؛ النسخة المحفوظة السابقة لم تتغير.");
    return;
  }
  try {
    localStorage.setItem(STORAGE, JSON.stringify(data));
    storageAvailable = true;
  } catch {
    storageAvailable = false;
    notify("التخزين غير متاح؛ تغييراتك ستبقى في ذاكرة هذه الصفحة فقط.");
  }
}
function navigate(next) {
  page = next;
  search = "";
  kindFilter = "ALL";
  render();
  window.scrollTo({ top: 0, behavior: "instant" });
}
const nav = () =>
  routes
    .map(
      ([id, name, symbol]) =>
        `<button class="nav-item ${page === id ? "active" : ""}" data-page="${id}" ${page === id ? 'aria-current="page"' : ""}>${icon(symbol)}<span>${name}</span>${id === "voice" ? '<small aria-hidden="true">محلي</small>' : ""}</button>`,
    )
    .join("");
const currencyPicker = () =>
  `<div class="currencies" role="group" aria-label="عملة العرض">${CURRENCIES.map((c) => `<button data-currency="${c}" aria-pressed="${currency === c}" class="${currency === c ? "selected" : ""}">${c === currency ? icon("check") : ""}${c}</button>`).join("")}</div>`;
const eyebrow = (text) => `<span class="eyebrow" dir="ltr">${text}</span>`;
const cardHeading = (en, title, right = "") =>
  `<div class="card-heading"><div>${eyebrow(en)}<h2>${title}</h2></div>${right}</div>`;

function chart(s) {
  if (hidden)
    return '<div class="private-state">المخطط مخفي لحماية خصوصيتك</div>';
  const max = s.daily.reduce((a, x) => (x.amount > a ? x.amount : a), 1n);
  return `<div class="bar-chart" aria-label="مصروف آخر سبعة أيام">${s.daily.map((x, i) => `<div class="bar-column" aria-label="${esc(x.day.toLocaleDateString("ar-YE"))}: ${esc(money(x.amount, currency))}"><div class="bar-track"><div class="bar ${i === 6 ? "current" : ""}" style="height:${Math.max(3, ratio(x.amount, max))}%"><span class="chart-tip">${esc(money(x.amount, currency))}</span></div></div><span>${i === 6 ? "اليوم" : x.day.toLocaleDateString("ar-YE", { weekday: "short" })}</span></div>`).join("")}</div>`;
}
function transactionRows(rows, deletable = false) {
  if (!rows.length)
    return `<div class="empty-state">${icon("receipt")}<h3>صفحة جديدة في قصتك المالية</h3><p>لا توجد عمليات مطابقة. أضف أول عملية تجريبية أو غيّر العملة.</p><button class="button secondary" data-action="add">إضافة عملية</button></div>`;
  return rows
    .map((row) => {
      const income = row.kind === "INCOME";
      return `<div class="transaction-row"><span class="transaction-icon ${income ? "income" : "expense"}">${icon(income ? "down" : row.category === "مطاعم" ? "coffee" : "up")}</span><div class="transaction-info"><strong>${hidden ? "عملية مخفية" : esc(row.note)}</strong><small>${hidden ? "••••" : `${income ? "دخل" : "مصروف"} · ${esc(row.category)}`} · ${new Date(row.at).toLocaleDateString("ar-YE", { day: "numeric", month: "short" })}</small></div><strong class="transaction-amount ${income ? "income" : ""}">${income ? "+" : "−"} ${amount(row.amount, row.currency)}</strong>${deletable && !row.creditPaymentId ? `<button class="icon-button delete" data-delete="${esc(row.id)}" aria-label="حذف العملية من المعاينة">${icon("trash")}</button>` : ""}</div>`;
    })
    .join("");
}

function homePage() {
  const s = snapshot(data, currency);
  const wallets = data.wallets.filter((w) => w.currency === currency);
  const top = s.categories[0];
  const recent = data.transactions
    .filter((t) => t.currency === currency && new Date(t.at) <= new Date())
    .sort((a, b) => new Date(b.at) - new Date(a.at))
    .slice(0, 4);
  return `<section class="page-intro"><div><p class="subtle">${new Date().toLocaleDateString("ar-YE", { weekday: "long", day: "numeric", month: "long" })}</p><h1>مساحتك المالية<span class="gold">.</span></h1><p>وضوح اليوم، وطموح أبعد.</p></div><div class="intro-actions"><button class="icon-button privacy-toggle" data-action="privacy" aria-label="${hidden ? "إظهار المبالغ" : "إخفاء المبالغ"}" aria-pressed="${hidden}">${icon(hidden ? "eyeOff" : "eye")}</button><button class="button primary desktop-only" data-action="add">${icon("plus")}إضافة عملية</button></div></section>
  <div class="section-top"><span class="section-caption">نظرة شاملة <span> / OVERVIEW</span></span>${currencyPicker()}</div>
  <div class="dashboard-grid">
    <section class="card balance-card"><div class="orbit one"></div><div class="orbit two"></div><div class="balance-content"><div class="balance-heading"><span class="status-label"><i></i> النقد المتاح في محافظك</span>${icon("wallet")}</div><div class="balance-value">${amount(balanceFor(data, currency))}</div><p class="balance-meta">${wallets.length} محافظ · ${currency} فقط · بعد حجز الادخار</p><div class="balance-divider"></div><div class="balance-bottom"><span>حركة الشهر الحالي</span><span class="small-mono">MK / PRIVATE FINANCE</span></div><div class="balance-metrics"><div><span>${icon("down")} الدخل</span><strong>${amount(s.income)}</strong></div><div><span class="expense">${icon("up")} المصروف</span><strong>${amount(s.expense)}</strong></div></div></div></section>
    <section class="card voice-card"><span class="tag">${icon("shield")} خصوصيتك أولاً</span><div class="voice-orb">${icon("mic")}</div><h2>قلها… واترك<br>التفاصيل لنا.</h2><p>مساحة لصوتك. ووضوح لأموالك.</p><button class="button primary" data-page="voice">اكتشف مساعدك المالي ${icon("arrow")}</button><small>تجربة النص هنا · الصوت على Android</small></section>
    <div class="quick-actions"><button data-action="add">${icon("plus")}<span>تسجيل عملية</span>${icon("left")}</button><button data-page="reports">${icon("chart")}<span>تحليل أموالك</span>${icon("left")}</button><button data-page="money">${icon("wallet")}<span>إدارة المحافظ</span>${icon("left")}</button></div>
    <section class="card pulse-card">${cardHeading("MONEY PULSE", "نبض الإنفاق", '<span class="muted badge">آخر ٧ أيام</span>')}${chart(s)}<div class="chart-footer"><span><i class="legend-dot"></i> المصروف اليومي · ${currency}</span><button class="text-button" data-page="reports">التقرير الكامل ${icon("left")}</button></div></section>
    <section class="card insight-card">${cardHeading("ON-DEVICE INSIGHTS", "بصيرة مالية", `<span class="insight-symbol">${icon("sparkle")}</span>`)}${hidden ? '<div class="private-state">التفاصيل المالية مخفية</div>' : `<p class="insight-main">${top ? `أكثر إنفاقك هذا الشهر<br><strong>${esc(top[0])}</strong>` : "كل بداية تصنع فرقاً."}</p><div class="insight-value">${amount(top?.[1] || 0n)}</div><p class="subtle">${top ? `يمثّل ${new Intl.NumberFormat("ar").format(Math.round(ratio(top[1], s.expense)))}٪ من مصروف الشهر المسجّل.` : "سجّل عملياتك لتظهر رؤيتك المالية."}</p><div class="insight-note">${icon("shield")} حسابات محلية للسجلات، لا تخمين.</div>`}</section>
    <section class="card future-card">${cardHeading("WHAT IF", "مختبر المستقبل", icon("target"))}<p>ماذا لو خفّضت إنفاقك <strong id="reduction-label">${reduction}٪</strong>؟</p><input id="reduction" type="range" min="0" max="50" step="5" value="${reduction}" aria-label="نسبة خفض الإنفاق الافتراضية"/><div class="range-labels"><span>٠٪</span><span>٥٠٪</span></div><div class="scenario-result"><strong id="scenario-result">${amount(savingScenario(s.expense, s.elapsedDays, reduction))}</strong><span>وفر افتراضي خلال ٣٠ يوماً</span></div><p class="fine-print">بناءً على متوسط أيام الشهر المنقضية. ليس وعداً أو توقعاً مضموناً، ولا ينقل أي أموال.</p></section>
    <section class="card recent-card">${cardHeading("YOUR MONEY, IN MOTION", "أحدث الحركات", '<button class="text-button" data-page="transactions">السجل الكامل ' + icon("left") + "</button>")}<div class="transaction-list">${transactionRows(recent)}</div></section>
  </div>`;
}
function transactionsPage() {
  return `<section class="page-intro"><div>${eyebrow("THE DETAILS MATTER")}<h1>كل حركة، في مكانها<span class="gold">.</span></h1><p>مساحة مرتبة لتفاصيل يومك المالي.</p></div><button class="button primary" data-action="add">${icon("plus")} إضافة عملية</button></section><section class="card"><div class="filters"><label class="search-box">${icon("search")}<input id="search" placeholder="ابحث بالوصف أو التصنيف" aria-label="البحث في العمليات" value="${esc(search)}"/></label>${currencyPicker()}<div class="segmented">${[
    ["ALL", "الكل"],
    ["INCOME", "الدخل"],
    ["EXPENSE", "المصروف"],
  ]
    .map(
      ([v, label]) =>
        `<button data-kind="${v}" class="${v === kindFilter ? "selected" : ""}" aria-pressed="${v === kindFilter}">${label}</button>`,
    )
    .join("")}</div></div><div id="transaction-results"></div></section>`;
}
function updateTransactionResults() {
  const node = document.querySelector("#transaction-results");
  if (!node) return;
  const rows = data.transactions
    .filter(
      (r) =>
        r.currency === currency &&
        (kindFilter === "ALL" || r.kind === kindFilter) &&
        (r.note.includes(search) || r.category.includes(search)),
    )
    .sort((a, b) => new Date(b.at) - new Date(a.at));
  node.innerHTML =
    `<p class="results-count">${rows.length} عمليات تجريبية · ${currency}</p>` +
    transactionRows(rows, true);
}
function reportsPage() {
  const s = snapshot(data, currency, new Date(), range);
  return `<section class="page-intro"><div>${eyebrow("A CLEARER PICTURE")}<h1>الأرقام تحكي قصتك<span class="gold">.</span></h1><p>افهم التفاصيل. واتخذ خطوتك القادمة بوضوح.</p></div>${icon("chart", "page-mark")}</section><div class="section-top">${currencyPicker()}<div class="segmented">${[7, 30, 90].map((d) => `<button data-range="${d}" class="${range === d ? "selected" : ""}" aria-pressed="${range === d}">${d} أيام</button>`).join("")}</div></div><div class="report-stats"><section class="card"><span class="subtle">الدخل خلال الفترة</span><strong class="income">${amount(s.income)}</strong></section><section class="card"><span class="subtle">المصروف خلال الفترة</span><strong>${amount(s.expense)}</strong></section><section class="card"><span class="subtle">صافي العمليات، وليس الرصيد</span><strong class="gold">${amount(s.income - s.expense)}</strong></section></div><div class="two-column"><section class="card">${cardHeading("SPENDING BREAKDOWN", "أين تذهب أموالك؟")}${hidden ? '<div class="private-state">التفاصيل مخفية</div>' : s.categories.length ? s.categories.map(([name, total], i) => `<div class="category-item"><div><span>${esc(name)}</span><strong>${amount(total)}</strong></div><div class="progress-track"><span style="width:${ratio(total, s.expense)}%;background:var(${i % 2 ? "--accent" : "--gold"})"></span></div></div>`).join("") : '<p class="empty-copy">لا توجد مصروفات في هذه الفترة.</p>'}</section><section class="card">${cardHeading("WEEKLY RHYTHM", "إيقاع الأسبوع")}<p class="subtle">آخر ٧ أيام، مستقلة عن فترة الملخص أعلاه.</p>${chart(s)}<p class="fine-print">التقارير في هذه المعاينة تستخدم بيانات متصفحك التجريبية فقط.</p></section></div>`;
}
function voicePage() {
  return `<section class="page-intro"><div>${eyebrow("PRIVATE BY DESIGN")}<h1>مساعد يفهم تفاصيلك<span class="gold">.</span></h1><p>من عبارة بسيطة إلى مسودة واضحة، والقرار لك.</p></div></section><div class="two-column voice-layout"><section class="card assistant-card"><span class="tag">${icon("shield")} تجربة واجهة المساعد</span><div class="large-orb">${icon("mic")}<span></span></div><h2>صوتك يبقى على جهازك.</h2><p>في تطبيق Android ندمج Vosk للتعرف المحلي بعد تثبيت نموذج عربي. <strong>المحرك الصوتي لا يعمل في معاينة المتصفح هذه.</strong></p><button class="button secondary" data-action="voice-info">${icon("info")} كيف سيعمل الصوت على الهاتف؟</button><div class="assistant-footnotes"><span>${icon("check")} مراجعة قبل الحفظ</span><span>${icon("check")} بديل كتابي دائماً</span></div></section><section class="card text-assistant">${cardHeading("TRY THE FLOW", "جرّب الفكرة بالكتابة")}<p class="subtle">محلل ويب مبسط لتجربة مسار المراجعة، وليس محرك Android نفسه. استخدم مبلغاً واحداً بأرقام مكتوبة.</p><form id="text-command-form"><label for="command">ماذا تريد أن تسجل؟</label><textarea id="command" name="command" rows="4" maxlength="160" placeholder="مثال: صرفت ١٥٠٠ ريال بنزين" required></textarea><div class="example-chips"><button type="button" data-example="صرفت ١٥٠٠ ريال بنزين">صرفت ١٥٠٠ بنزين</button><button type="button" data-example="استلمت ٧٥٠٠٠ ريال راتب">استلمت ٧٥٠٠٠ راتب</button><button type="button" data-example="كم رصيدي؟">كم رصيدي؟</button></div><p id="command-error" class="form-error" role="alert"></p><button class="button primary" type="submit">${icon("sparkle")} فهم العبارة ومراجعتها</button></form><div id="query-answer" role="status"></div><div class="secure-note">${icon("shield")} هذه التجربة لا تطلب الميكروفون، ولا ترسل نصك إلى خدمة ذكاء اصطناعي.</div></section></div>`;
}
function creditPage() {
  if (hidden) return `<section class="card empty-state">${icon("lock")}<h2>تفاصيل دفتر الدين مخفية</h2><p>أوقف وضع الخصوصية لعرض أسماء الأشخاص والكشوف أو إضافة مشتريات وسداد.</p></section>`;
  const accounts = creditAccounts(data);
  const selected = creditSelection && accounts.find(a => a.person === creditSelection.person && a.currency === creditSelection.currency) || accounts[0];
  const a = selected ? creditAccount(data, selected.person, selected.currency) : null;
  return `<section class="card"><div class="card-heading"><div>${eyebrow("PERSONAL CREDIT LEDGER")}<h2>دفتر المشتريات بالدين</h2></div><button class="button primary" data-action="credit-add">${icon("plus")} شراء بالدين</button></div><p class="subtle">كل مرة تأخذ فيها مشتريات بالدين تُسجَّل باسم البائع ومبلغها وتاريخها ووقتها. لا تتغير المحفظة حتى السداد. بيانات تجريبية منفصلة عن الهاتف.</p><div class="example-chips credit-account-chips">${accounts.map((account,i) => `<button data-credit-account="${i}" aria-pressed="${selected?.person === account.person && selected?.currency === account.currency}">${esc(account.person)} · ${account.currency}</button>`).join("")}</div>${a ? `<div class="report-stats"><div class="card"><span>إجمالي المشتريات</span><strong>${amount(a.total, selected.currency)}</strong></div><div class="card"><span>إجمالي السداد</span><strong class="income">${amount(a.totalPaid, selected.currency)}</strong></div><div class="card"><span>المتبقي عليّ</span><strong class="gold">${amount(a.outstanding, selected.currency)}</strong></div></div><div class="credit-actions"><button class="button secondary" data-action="credit-statement">${icon("receipt")} معاينة ومشاركة كشف الحساب</button><button class="button primary" data-action="credit-pay" ${a.outstanding <= 0n ? 'disabled' : ''}>تسجيل سداد الحساب</button></div><p class="fine-print">اليوم والتاريخ والوقت بتوقيت عدن Asia/Aden. السداد يوزع على أقدم المشتريات أولاً؛ لا تُخلط العملات أو الأسماء المتشابهة.</p><div class="credit-history">${a.events.map(e => `<div class="transaction-row"><span class="transaction-icon">${icon(e.type === 'purchase' ? 'receipt' : 'check')}</span><div class="transaction-info"><strong>${e.type === 'purchase' ? 'شراء بالدين: ' + esc(e.item) : 'سداد الحساب'}</strong><small>${esc(creditDate(e.at))}</small></div><strong class="${e.type === 'payment' ? 'income' : ''}">${amount(e.amount, selected.currency)}</strong></div>`).join("")}</div>` : `<div class="empty-state">${icon("receipt")}<h3>حساب واضح بينك وبين البائع</h3><p>جرّب إضافة شراء من عبده مهدي، ثم أضف شراءً آخر في يوم مختلف، واستخرج الكشف. لا توجد ديون تجريبية مفترضة باسم أي شخص.</p><button class="button secondary" data-action="credit-add">تسجيل أول شراء بالدين</button></div>`}</section>`;
}

function moneyPage() {
  const tools = [
    ["credit", "دفتر الدين", "receipt"],
    ["wallets", "المحافظ", "wallet"],
    ["transfers", "التحويلات", "transfer"],
    ["budgets", "الميزانيات", "chart"],
    ["savings", "الادخار", "target"],
    ["bills", "الفواتير", "bill"],
  ];
  let body = "";
  if (tool === "credit") body = creditPage();
  if (tool === "wallets")
    body = `<div class="wallet-grid">${data.wallets.map((w) => `<section class="card wallet-card"><div class="wallet-top"><span class="wallet-symbol">${icon("wallet")}</span><span class="badge">${esc(w.type)} · ${w.currency}</span></div><h2>${esc(w.name)}</h2><div class="wallet-value">${amount(walletBalance(data, w), w.currency)}</div><div class="wallet-footer"><span>رصيد افتتاحي ${amount(w.opening, w.currency)}</span>${eyebrow("MK / WALLET")}</div></section>`).join("")}<button class="card add-wallet-card" data-action="wallet">${icon("plus")}<strong>مساحة لمحفظة جديدة</strong><span>أنشئ محفظة تجريبية بعملتها المستقلة</span></button></div>`;
  if (tool === "transfers")
    body = `<section class="card transfer-intro">${cardHeading("BETWEEN YOUR WALLETS", "انقل أموالك، لا تصنيفاتها", icon("transfer"))}<p>جرّب تحويلاً داخلياً بين محفظتين بالعملة نفسها. يتغير الرصيدان دون تضخيم الدخل أو المصروفات.</p><button class="button primary" data-action="transfer">${icon("transfer")} إنشاء تحويل تجريبي</button><div class="transfer-history">${data.transfers.length ? data.transfers.map((t) => `<div class="transaction-row"><span class="transaction-icon">${icon("transfer")}</span><div class="transaction-info"><strong>${esc(data.wallets.find((w) => w.id === t.from)?.name)} ← ${esc(data.wallets.find((w) => w.id === t.to)?.name)}</strong><small>تحويل داخلي · ${new Date(t.at).toLocaleDateString("ar-YE")}</small></div><strong>${amount(t.amount, t.currency)}</strong></div>`).join("") : '<p class="subtle">لا توجد تحويلات تجريبية بعد.</p>'}</div></section>`;
  if (tool === "budgets") {
    const s = snapshot(data, currency);
    body = `<section class="card">${cardHeading("SPEND WITH INTENTION", "كل تصنيف، وله مساحة", '<span class="badge">عرض تصميم فقط</span>')}${currencyPicker()}<p class="subtle">حدود توضيحية ثابتة؛ الإنفاق محسوب من العمليات التجريبية. إدارة الميزانيات الكاملة على Android.</p>${
      (currency === "YER"
        ? [
            ["مطاعم", 3000000n],
            ["بقالة", 6000000n],
            ["فواتير", 5000000n],
          ]
        : []
      )
        .map(([name, limit]) => {
          const spent = s.categories.find((c) => c[0] === name)?.[1] || 0n;
          return `<div class="category-item"><div><strong>${name}</strong><span>${amount(spent)} / ${amount(limit)}</span></div><div class="progress-track"><span style="width:${hidden ? 0 : ratio(spent, limit)}%"></span></div><small class="subtle">${hidden ? "••••" : Math.round(ratio(spent, limit)) + "٪ من الحد التوضيحي"}</small></div>`;
        })
        .join("") ||
      '<p class="empty-copy">مثال الميزانيات متاح بعملة YER فقط.</p>'
    }</section>`;
  }
  if (tool === "savings")
    body = `<section class="card savings-preview">${cardHeading("MAKE ROOM FOR MORE", "طموحك يستحق خطة", '<span class="badge">عرض تصميم فقط</span>')}<span class="goal-icon">${icon("target")}</span><h2>صندوق الأمان</h2><p class="subtle">مثال بصري مستقل، غير مرتبط بأرصدة المعاينة.</p><div class="goal-amount">${amount(15000000n, "YER")} <span>من ${amount(50000000n, "YER")}</span></div><div class="progress-track"><span style="width:${hidden ? 0 : 30}%"></span></div><p class="fine-print">المساهمات والسحوبات الحقيقية وحجز رصيد المحفظة وظائف Android، وليست مفعّلة في هذا المثال.</p></section>`;
  if (tool === "bills")
    body = `<section class="card">${cardHeading("A LITTLE AHEAD", "لا تدع التفاصيل تفاجئك", '<span class="badge">عرض تصميم فقط</span>')}<p class="subtle">أمثلة بصرية؛ لا دفع ولا إشعارات فعلية من المتصفح.</p>${[
      ["فاتورة الإنترنت", 1200000n, "دورة شهرية"],
      ["كهرباء المنزل", 1800000n, "دورة شهرية"],
    ]
      .map(
        ([name, total, subtitle]) =>
          `<div class="transaction-row"><span class="transaction-icon">${icon("bill")}</span><div class="transaction-info"><strong>${name}</strong><small>${subtitle} · مثال توضيحي</small></div><strong>${amount(total, "YER")}</strong></div>`,
      )
      .join("")}</section>`;
  return `<section class="page-intro"><div>${eyebrow("ORGANIZE YOUR AMBITIONS")}<h1>مساحة لكل طموح<span class="gold">.</span></h1><p>محافظك، خططك، وتفاصيلك. بتنظيم يشبهك.</p></div><button class="button primary" data-action="wallet">${icon("plus")} محفظة جديدة</button></section><div class="tool-tabs" role="group" aria-label="أدوات مالي">${tools.map(([id, title, symbol]) => `<button data-tool="${id}" aria-pressed="${tool === id}" class="${tool === id ? "selected" : ""}">${icon(symbol)} ${title}</button>`).join("")}</div>${body}`;
}
function render() {
  const title = routes.find((r) => r[0] === page)?.[1];
  app.innerHTML = `<div class="app-shell ${phone ? "phone-mode" : ""}"><aside class="sidebar"><a class="brand" href="#home" data-page="home"><span class="brand-mark"><b>M</b><b>K</b></span><span><strong dir="ltr">MK Finance</strong><small>مساحة خاصة لطموحك</small></span></a><span class="sidebar-label">مساحتك الشخصية</span><nav aria-label="التنقل الرئيسي">${nav()}</nav><div class="sidebar-bottom"><div class="privacy-note">${icon("shield")}<strong>أموالك. خصوصيتك.</strong><p>هذه مساحة تجريبية منفصلة تماماً عن بيانات هاتفك.</p></div><button class="sidebar-help" data-action="features">${icon("info")} ما الذي يمكنني تجربته؟</button><div class="sidebar-signature"><span class="avatar">MK</span><div><strong>مساحة المعاينة</strong><small>DESIGN SANDBOX / V.01</small></div><span class="online-dot"></span></div></div></aside><div class="workspace"><header class="topbar"><div class="breadcrumb"><span class="desktop-only">مساحتك الخاصة</span><span class="desktop-only">/</span><strong>${title}</strong><span class="preview-pill">معاينة التصميم</span></div><div class="toolbar"><button class="icon-button" data-action="phone" aria-label="${phone ? "عرض سطح المكتب" : "عرض بحجم الهاتف"}" title="${phone ? "عرض سطح المكتب" : "عرض بحجم الهاتف"}">${icon(phone ? "desktop" : "phone")}</button><button class="icon-button" data-action="features" aria-label="حدود المعاينة والمميزات">${icon("info")}</button><span class="avatar small desktop-only">MK</span></div></header><div class="preview-notice">${icon("info")}<p><strong>معاينة تفاعلية، وليست نسخة Android.</strong> بيانات تجريبية ${storageAvailable ? "محفوظة في متصفحك فقط" : "في ذاكرة الصفحة فقط"}. لا تدخل بيانات حقيقية. الصوت المحلي يُختبر على الهاتف.${protectedStoredData ? " <strong>وجدنا بيانات سابقة غير مقروءة؛ لم نحذفها ولن نستبدلها دون تأكيد إعادة التجربة.</strong>" : ""}</p><button data-action="reset" title="إعادة البيانات التجريبية">${icon("reset")}<span>إعادة التجربة</span></button></div><main id="main-content" tabindex="-1">${location.protocol === "http:" || location.protocol === "https:" ? '<a class="preview-files-link" href="/preview-files/">فتح الكود وملفات التنزيل</a>' : ""}${{ home: homePage, transactions: transactionsPage, reports: reportsPage, voice: voicePage, money: moneyPage }[page]()}</main><footer class="page-footer"><span dir="ltr">MK FINANCE — OBSIDIAN ATELIER</span><span>صُمّم للوضوح. بُني لطموحك.</span></footer><nav class="bottom-nav" aria-label="التنقل على الهاتف">${nav()}</nav></div></div>`;
  if (page === "transactions") updateTransactionResults();
  document.title = `${title} · MK Finance — معاينة التصميم`;
}
function openDialog(title, content, onSubmit) {
  modal.classList.remove("statement-modal");
  modal.innerHTML = `<div class="dialog-header"><div>${eyebrow("DESIGN SANDBOX")}<h2 id="dialog-title">${title}</h2></div><button class="icon-button" data-close aria-label="إغلاق">${icon("close")}</button></div>${content}`;
  modal.showModal();
  const form = modal.querySelector("form");
  if (form && onSubmit)
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      try {
        onSubmit(new FormData(form));
      } catch (error) {
        modal.querySelector(".form-error").textContent = error.message;
      }
    });
}
const formEnd = (label) =>
  `<p class="form-error" role="alert"></p><p class="fine-print">تجربة مستقلة في المتصفح؛ لا تؤثر على تطبيق Android أو أموالك.</p><div class="form-actions"><button class="button primary" type="submit">${icon("check")} ${label}</button><button class="button secondary" type="button" data-close>إلغاء</button></div>`;
function transactionDialog(draft = {}) {
  if (!data.wallets.length) {
    notify("أنشئ محفظة تجريبية أولاً.");
    walletDialog();
    return;
  }
  const draftAmount = draft.amount
    ? `${BigInt(draft.amount) / 100n}.${(BigInt(draft.amount) % 100n).toString().padStart(2, "0")}`
    : "";
  openDialog(
    draft.amount ? "راجع مسودتك قبل الحفظ" : "تفصيلة جديدة في قصتك",
    `<form id="transaction-form"><label>نوع العملية<select name="kind"><option value="EXPENSE" ${draft.kind === "INCOME" ? "" : "selected"}>مصروف</option><option value="INCOME" ${draft.kind === "INCOME" ? "selected" : ""}>دخل</option></select></label><label>المحفظة والعملة<select name="walletId">${data.wallets.map((w) => `<option value="${esc(w.id)}" ${w.currency === currency && w === data.wallets.find((x) => x.currency === currency) ? "selected" : ""}>${esc(w.name)} (${w.currency})</option>`).join("")}</select></label><label>المبلغ بعملة المحفظة المختارة<input name="amount" value="${esc(draftAmount)}" inputmode="decimal" maxlength="24" placeholder="مثال: ١٥٠٠" required /></label><label>الوصف<input name="note" value="${esc(draft.note || "")}" maxlength="160" placeholder="أين ذهبت هذه التفاصيل؟" required /></label><label>التصنيف<select name="category">${["عام", "بقالة", "مطاعم", "بنزين", "فواتير", "مواصلات", "راتب", "عمل حر"].map((c) => `<option ${draft.category === c ? "selected" : ""}>${c}</option>`).join("")}</select></label>${formEnd("تأكيد وحفظ العملية التجريبية")}</form>`,
    (form) => {
      const wallet = data.wallets.find((w) => w.id === form.get("walletId"));
      const kind = form.get("kind");
      const note = form.get("note").trim();
      if (!wallet || !["INCOME", "EXPENSE"].includes(kind) || !note)
        throw new Error("أكمل المحفظة والنوع والوصف.");
      data.transactions.unshift({
        id: crypto.randomUUID(),
        amount: parseMoney(form.get("amount")),
        kind,
        note: note.slice(0, 160),
        category: form.get("category"),
        walletId: wallet.id,
        currency: wallet.currency,
        at: new Date().toISOString(),
      });
      save();
      modal.close();
      render();
      savedNotice("حُفظت العملية التجريبية في هذا المتصفح فقط.");
    },
  );
}
function selectedCreditAccount() {
  const accounts = creditAccounts(data);
  return creditSelection && accounts.find(a => a.person === creditSelection.person && a.currency === creditSelection.currency) || accounts[0];
}
function creditPurchaseDialog(draft = {}) {
  if (!data.wallets.length) { notify("أنشئ محفظة مرجعية للعملة أولاً."); walletDialog(); return; }
  const value = draft.amount ? `${BigInt(draft.amount) / 100n}.${(BigInt(draft.amount) % 100n).toString().padStart(2,'0')}` : '';
  openDialog("مراجعة شراء بالدين", `<form><label>اسم البائع كاملاً<input name="person" maxlength="120" value="${esc(draft.person || '')}" placeholder="عبده مهدي" required /></label><label>المشتريات<input name="item" maxlength="160" value="${esc(draft.item || 'قات')}" required /></label><label>المبلغ<input name="amount" inputmode="decimal" value="${esc(value)}" maxlength="24" required /></label><label>محفظة مرجعية للعملة فقط<select name="walletId">${data.wallets.map(w => `<option value="${esc(w.id)}">${esc(w.name)} (${w.currency})</option>`).join('')}</select></label><label>تاريخ ووقت الشراء (اختياري)<input type="datetime-local" name="at" step="1" /></label><p class="fine-print">اترك التاريخ فارغاً ليُسجل وقت الحفظ. عند تحديده أدخله حسب توقيت جهازك؛ الكشف يُعرض بتوقيت عدن. لا يُقبل تاريخ مستقبلي.</p><label class="credit-ack"><input name="ack" type="checkbox" required /> أؤكد أنه شراء بالدين وليس دفعاً نقدياً أو استلام قرض؛ لا تتغير المحفظة الآن.</label>${formEnd('تأكيد تسجيل الشراء بالدين')}</form>`, form => {
    if (!form.get('ack')) throw new Error('أكد أن العملية شراء بالدين أولاً.');
    const now = new Date();
    const next = addCreditPurchase(data, { person: form.get('person'), item: form.get('item'), amount: parseMoney(form.get('amount')), walletId: form.get('walletId'), at: form.get('at') ? new Date(form.get('at')).toISOString() : now.toISOString() }, now);
    data = next; creditSelection = { person: next.creditPurchases.at(-1).person, currency: next.creditPurchases.at(-1).currency };
    save(); modal.close(); page = 'money'; tool = 'credit'; render(); savedNotice('سُجل الشراء التجريبي مع التاريخ والوقت، دون تغيير النقد.');
  });
}
function creditRepaymentDialog() {
  const selected = selectedCreditAccount(); if (!selected) return;
  const account = creditAccount(data, selected.person, selected.currency);
  openDialog('مراجعة سداد حساب ' + esc(selected.person), `<form><p>المتبقي في المعاينة: ${amount(account.outstanding, selected.currency)}. يوزع السداد على أقدم المشتريات أولاً.</p><label>مبلغ السداد<input name="amount" inputmode="decimal" required /></label><label>الدفع من المحفظة<select name="walletId">${data.wallets.filter(w => w.currency === selected.currency).map(w => `<option value="${esc(w.id)}">${esc(w.name)} (${w.currency})</option>`).join('')}</select></label><p class="fine-print">سيُسجل مصروف سداد وينقص النقد عند التأكيد. هذا ليس تحويلاً مالياً حقيقياً.</p>${formEnd('تأكيد تسجيل سداد الحساب')}</form>`, form => {
    data = repayCreditAccount(data, { ...selected, amount: parseMoney(form.get('amount')), walletId: form.get('walletId') });
    save(); modal.close(); render(); savedNotice('سُجل السداد مع تاريخه ووقته؛ يمكنك مشاركة الكشف المحدث.');
  });
}
function creditStatementDialog() {
  const selected = selectedCreditAccount(); if (!selected) return;
  const text = creditStatement(data, selected.person, selected.currency);
  openDialog('راجع الكشف قبل إرساله', `<div class="statement-content"><p class="fine-print no-print">يتضمن اسم الشخص وكل المشتريات والدفعات بهذه العملة، بما فيها المسددة. راجعه قبل اختيار التطبيق والمستلم.</p><pre class="statement-paper">${esc(text)}</pre><div class="credit-actions no-print"><button class="button primary" id="share-credit">مشاركة النص</button><button class="button secondary" id="copy-credit">نسخ الكشف</button><button class="button secondary" id="print-credit">طباعة / حفظ PDF حسب المتصفح</button></div><p id="statement-status" class="fine-print no-print" role="status"></p></div>`);
  modal.classList.add('statement-modal');
  modal.querySelector('#share-credit').onclick = async () => {
    try { if (!navigator.share) throw new Error('unsupported'); await navigator.share({ title: 'كشف حساب ' + selected.person, text }); }
    catch (error) { if (error.name !== 'AbortError') modal.querySelector('#statement-status').textContent = 'المشاركة غير متاحة في هذا المتصفح؛ انسخ النص أو حدده يدوياً وأرسله بالتطبيق الذي تختاره.'; }
  };
  modal.querySelector('#copy-credit').onclick = async () => {
    try { await navigator.clipboard.writeText(text); modal.querySelector('#statement-status').textContent = 'نُسخ الكشف. اختر المستلم بنفسك قبل الإرسال.'; }
    catch { modal.querySelector('#statement-status').textContent = 'النسخ محظور هنا؛ اضغط مطولاً على نص الكشف لتحديده ونسخه يدوياً.'; }
  };
  modal.querySelector('#print-credit').onclick = () => window.print();
}

function walletDialog() {
  openDialog(
    "مساحة جديدة لأموالك",
    `<form><label>اسم المحفظة<input name="name" maxlength="50" placeholder="مثال: محفظة السفر" required /></label><div class="form-grid"><label>العملة<select name="currency">${CURRENCIES.map((c) => `<option ${currency === c ? "selected" : ""}>${c}</option>`).join("")}</select></label><label>النوع<select name="type"><option>نقدية</option><option>بنكية</option><option>إلكترونية</option></select></label></div><label>الرصيد الافتتاحي<input name="opening" inputmode="decimal" maxlength="24" value="0" required /></label>${formEnd("إنشاء محفظة تجريبية")}</form>`,
    (form) => {
      const name = form.get("name").trim();
      if (!name) throw new Error("اكتب اسم المحفظة.");
      data.wallets.push({
        id: crypto.randomUUID(),
        name: name.slice(0, 50),
        currency: form.get("currency"),
        type: form.get("type"),
        opening: parseMoney(form.get("opening"), true),
      });
      save();
      modal.close();
      page = "money";
      tool = "wallets";
      render();
      savedNotice("أُنشئت المحفظة التجريبية.");
    },
  );
}
function transferDialog() {
  if (data.wallets.length < 2) {
    notify("أنشئ محفظتين على الأقل أولاً.");
    return;
  }
  const options = data.wallets
    .map(
      (w) =>
        `<option value="${esc(w.id)}">${esc(w.name)} (${w.currency})</option>`,
    )
    .join("");
  openDialog(
    "تحويل بين مساحاتك",
    `<form><label>من المحفظة<select name="from">${options}</select></label><label>إلى المحفظة<select name="to">${data.wallets.map((w, i) => `<option value="${esc(w.id)}" ${i === 1 ? "selected" : ""}>${esc(w.name)} (${w.currency})</option>`).join("")}</select></label><label>المبلغ بعملة المحفظتين<input name="amount" inputmode="decimal" maxlength="24" required /></label><p class="secure-note">${icon("info")} العملة نفسها فقط، مع فحص الرصيد. التحويل ليس دخلاً أو مصروفاً.</p>${formEnd("تأكيد التحويل التجريبي")}</form>`,
    (form) => {
      const transfer = makeTransfer(data, {
        from: form.get("from"),
        to: form.get("to"),
        amount: parseMoney(form.get("amount")),
      });
      data.transfers.unshift(transfer);
      save();
      modal.close();
      render();
      savedNotice("تم التحويل بين أرصدة المعاينة فقط.");
    },
  );
}
function featureDialog() {
  openDialog(
    "ما الذي يمكنك تجربته هنا؟",
    `<div class="feature-guide"><span class="tag">معاينة ويب منفصلة عن تطبيق Android</span><h3>تفاعلي في المتصفح</h3><ul><li>الشاشات الخمس، العملات، عرض الهاتف وإخفاء المبالغ.</li><li>إضافة وحذف عمليات تجريبية والبحث والتصفية.</li><li>إنشاء محافظ وتحويلات داخلية تجريبية مع فحص الرصيد.</li><li>دفتر مشتريات بالدين بالأيام والتواريخ والأوقات، وسداد وكشف قابل للنسخ والمشاركة.</li><li>تقارير من بيانات المعاينة ومختبر التوفير الافتراضي.</li><li>إدخال نصي مبسط مع مسودة قابلة للمراجعة.</li></ul><h3>عرض بصري فقط هنا</h3><p>بطاقات الميزانيات والادخار والفواتير. إدارتها الكاملة وتسجيل مساهماتها ومدفوعاتها ليست مفعلة في الويب.</p><h3>يحتاج تطبيق Android</h3><p>التعرف المحلي Vosk، Room، استيراد النموذج، الإشعارات، النسخ الاحتياطي المشفر وتصدير ملفات التطبيق. لا تُشغّل هذه المعاينة كود Kotlin، ولا تثبت نجاح بناء APK.</p><h3>كيف نراجع التصميم؟</h3><p>تنقّل، جرّب عرض الهاتف، ثم أرسل في المحادثة اسم الشاشة وما تريد تغييره. التغييرات هنا لا تنتقل تلقائياً إلى واجهات Android؛ نعتمدها ثم نطبقها على المصدر الأصلي.</p><p class="fine-print">التخزين محلي في هذا المتصفح فقط وغير مشفر؛ لا تستخدم بياناتك الحقيقية. يمكنك إعادة التجربة أو إفراغها.</p><button class="button secondary" data-action="empty">إفراغ بيانات المعاينة</button></div>`,
  );
}
modal.addEventListener("click", (event) => {
  if (event.target === modal || event.target.closest("[data-close]"))
    modal.close();
});
document.addEventListener("click", (event) => {
  const button = event.target.closest("button, a[data-page]");
  if (!button) return;
  if (button.dataset.creditAccount !== undefined) {
    creditSelection = creditAccounts(data)[Number(button.dataset.creditAccount)]; render(); return;
  }
  if (button.dataset.page) {
    event.preventDefault();
    navigate(button.dataset.page);
    return;
  }
  if (button.dataset.currency) {
    currency = button.dataset.currency;
    render();
    return;
  }
  if (button.dataset.kind) {
    kindFilter = button.dataset.kind;
    render();
    return;
  }
  if (button.dataset.range) {
    range = Number(button.dataset.range);
    render();
    return;
  }
  if (button.dataset.tool) {
    tool = button.dataset.tool;
    render();
    return;
  }
  if (button.dataset.example) {
    const field = document.querySelector("#command");
    field.value = button.dataset.example;
    field.focus();
    return;
  }
  if (button.dataset.delete) {
    const id = button.dataset.delete;
    if (data.transactions.find(t => t.id === id)?.creditPaymentId) { notify("هذه حركة سداد مرتبطة بكشف شخص؛ لا تُحذف من قائمة العمليات."); return; }
    openDialog(
      "حذف هذه العملية التجريبية؟",
      `<form><p>سيُحدّث الحذف أرصدة وتقارير المعاينة فقط. لا يوجد اتصال ببيانات هاتفك.</p>${formEnd("نعم، احذف من المعاينة")}</form>`,
      () => {
        data.transactions = data.transactions.filter((t) => t.id !== id);
        save();
        modal.close();
        render();
        savedNotice("حُذفت العملية من المعاينة.");
      },
    );
    return;
  }
  switch (button.dataset.action) {
    case "credit-add": creditPurchaseDialog(); break;
    case "credit-pay": creditRepaymentDialog(); break;
    case "credit-statement": creditStatementDialog(); break;
    case "add":
      transactionDialog();
      break;
    case "wallet":
      walletDialog();
      break;
    case "transfer":
      transferDialog();
      break;
    case "privacy":
      hidden = !hidden;
      render();
      break;
    case "phone":
      phone = !phone;
      render();
      break;
    case "features":
      featureDialog();
      break;
    case "voice-info":
      openDialog(
        "الصوت المحلي يُختبر على Android",
        `<div class="feature-guide"><p>يعمل التكامل الأصلي بواسطة Vosk داخل التطبيق بعد تثبيت نموذج عربي متوافق مرة واحدة. المعاينة الحالية لا تضم نموذجاً صوتياً ولا تشغّل المحرك الأصلي.</p><p>لن نطلب الميكروفون ولن نستخدم خدمة تعرف سحابية بديلة هنا. جرّب مسار المراجعة بالنص، ثم نختبر الدقة ووضع الطيران على هاتف فعلي.</p><button class="button primary" data-close>فهمت، لنجرّب الكتابة</button></div>`,
      );
      break;
    case "reset":
    case "empty": {
      const empty = button.dataset.action === "empty";
      if (modal.open) modal.close();
      openDialog(
        empty ? "بدء مساحة فارغة؟" : "إعادة بيانات العرض؟",
        `<form><p>سيُحذف ما أضفته في معاينة هذا المتصفح فقط، ${empty ? "لتبدأ من الصفر." : "وتعود الأمثلة التوضيحية."}</p>${formEnd(empty ? "نعم، ابدأ من الصفر" : "نعم، أعد الأمثلة")}</form>`,
        () => {
          protectedStoredData = false;
          creditSelection = null;
          data = empty
            ? { version: 1, wallets: [], transactions: [], transfers: [] }
            : createDemo();
          save();
          modal.close();
          render();
          notify(
            empty ? "أصبحت مساحة المعاينة فارغة." : "عادت البيانات التجريبية.",
          );
        },
      );
      break;
    }
  }
});
document.addEventListener("input", (event) => {
  if (event.target.id === "search") {
    search = event.target.value;
    updateTransactionResults();
  }
  if (event.target.id === "reduction") {
    reduction = Number(event.target.value);
    const s = snapshot(data, currency);
    document.querySelector("#reduction-label").textContent = reduction + "٪";
    document.querySelector("#scenario-result").innerHTML = amount(
      savingScenario(s.expense, s.elapsedDays, reduction),
    );
  }
});
document.addEventListener("submit", (event) => {
  if (event.target.id !== "text-command-form") return;
  event.preventDefault();
  const command = new FormData(event.target).get("command").trim();
  const error = document.querySelector("#command-error");
  error.textContent = "";
  const answer = document.querySelector("#query-answer");
  answer.innerHTML = "";
  try {
    if (/رصيدي|الرصيد/.test(command)) {
      answer.innerHTML = `<div class="query-answer">رصيد المعاينة بعملة ${currency}: <strong>${amount(balanceFor(data, currency))}</strong></div>`;
    } else if (isCreditCommand(command)) creditPurchaseDialog(creditDraft(command));
    else transactionDialog(textDraft(command));
  } catch (e) {
    error.textContent = e.message;
  }
});
render();
