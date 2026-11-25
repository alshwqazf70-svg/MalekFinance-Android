// A browser-only design sandbox, not the Android ledger or voice parser.
export const CURRENCIES = ["YER", "USD", "SAR", "AED"];
export const MAX_MINOR = 9223372036854775807n;
export const incomingKinds = ["INCOME", "DEBT_RECEIVED", "SAVINGS_WITHDRAWAL"];
const outgoingKinds = ["EXPENSE", "DEBT_ISSUED", "SAVINGS_CONTRIBUTION"];
export const normalizeDigits = (value) =>
  String(value)
    .replace(/[٠-٩]/g, (d) => "٠١٢٣٤٥٦٧٨٩".indexOf(d))
    .replace(/[۰-۹]/g, (d) => "۰۱۲۳۴۵۶۷۸۹".indexOf(d));

export function parseMoney(raw, allowZero = false) {
  const value = normalizeDigits(raw)
    .trim()
    .replace(/٬/g, "")
    .replace(/[٫,]/g, ".");
  if (!/^\d+(\.\d{1,2})?$/.test(value))
    throw new Error("اكتب مبلغاً صحيحاً بمنزلتين عشريتين كحد أقصى.");
  const [whole, fraction = ""] = value.split(".");
  const minor = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
  if (minor > MAX_MINOR || minor < (allowZero ? 0n : 1n))
    throw new Error("المبلغ خارج النطاق المسموح أو لا يزيد على صفر.");
  return minor.toString();
}

export function money(minor, currency = "YER", hidden = false) {
  if (hidden) return "••••••";
  const value = BigInt(minor);
  const abs = value < 0n ? -value : value;
  const fraction = abs % 100n;
  const decimal = fraction
    ? "٫" +
      fraction
        .toString()
        .padStart(2, "0")
        .replace(/\d/g, (d) => "٠١٢٣٤٥٦٧٨٩"[d])
    : "";
  return (
    (value < 0n ? "−" : "") +
    new Intl.NumberFormat("ar-YE").format(abs / 100n) +
    decimal +
    " " +
    ({ YER: "ر.ي", USD: "$", SAR: "ر.س", AED: "د.إ" }[currency] || currency)
  );
}

export function walletBalance(state, wallet) {
  let balance = BigInt(wallet.opening);
  for (const row of state.transactions) {
    if (
      row.walletId !== wallet.id ||
      row.currency !== wallet.currency ||
      BigInt(row.amount) <= 0n
    )
      continue;
    if (incomingKinds.includes(row.kind)) balance += BigInt(row.amount);
    if (outgoingKinds.includes(row.kind)) balance -= BigInt(row.amount);
  }
  for (const row of state.transfers) {
    if (
      row.currency !== wallet.currency ||
      BigInt(row.amount) <= 0n ||
      row.from === row.to
    )
      continue;
    if (row.from === wallet.id) balance -= BigInt(row.amount);
    if (row.to === wallet.id) balance += BigInt(row.amount);
  }
  return balance;
}

export function balanceFor(state, currency) {
  return state.wallets
    .filter((w) => w.currency === currency)
    .reduce((sum, wallet) => sum + walletBalance(state, wallet), 0n);
}
const sumKind = (rows, kind) =>
  rows
    .filter((r) => r.kind === kind)
    .reduce((sum, r) => sum + BigInt(r.amount), 0n);
export function snapshot(state, currency, now = new Date(), rangeDays = null) {
  const month = new Date(now.getFullYear(), now.getMonth(), 1);
  const start = rangeDays
    ? new Date(now.getFullYear(), now.getMonth(), now.getDate() - rangeDays + 1)
    : month;
  const rows = state.transactions.filter(
    (r) =>
      r.currency === currency &&
      BigInt(r.amount) > 0n &&
      new Date(r.at) >= start &&
      new Date(r.at) <= now,
  );
  const categories = new Map();
  for (const row of rows.filter((r) => r.kind === "EXPENSE"))
    categories.set(
      row.category,
      (categories.get(row.category) || 0n) + BigInt(row.amount),
    );
  const daily = Array.from({ length: 7 }, (_, i) => {
    const day = new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate() - 6 + i,
    );
    const next = new Date(day.getFullYear(), day.getMonth(), day.getDate() + 1);
    return {
      day,
      amount: sumKind(
        state.transactions.filter(
          (r) =>
            r.currency === currency &&
            BigInt(r.amount) > 0n &&
            new Date(r.at) >= day &&
            new Date(r.at) < next &&
            new Date(r.at) <= now,
        ),
        "EXPENSE",
      ),
    };
  });
  return {
    income: sumKind(rows, "INCOME"),
    expense: sumKind(rows, "EXPENSE"),
    categories: [...categories].sort((a, b) =>
      a[1] === b[1] ? a[0].localeCompare(b[0], "ar") : a[1] > b[1] ? -1 : 1,
    ),
    daily,
    elapsedDays: now.getDate(),
    rows,
  };
}

export function savingScenario(expense, days, percent) {
  if (!Number.isInteger(percent) || percent < 0 || percent > 100 || days < 1)
    throw new Error("Invalid scenario");
  return (BigInt(expense) * BigInt(30 * percent)) / BigInt(days * 100);
}

export function makeTransfer(state, { from, to, amount, note = "" }) {
  const source = state.wallets.find((w) => w.id === from);
  const target = state.wallets.find((w) => w.id === to);
  if (!source || !target || from === to)
    throw new Error("اختر محفظتين مختلفتين.");
  if (source.currency !== target.currency)
    throw new Error("التحويل متاح بين محافظ العملة نفسها فقط.");
  if (BigInt(amount) <= 0n || BigInt(amount) > MAX_MINOR)
    throw new Error("المبلغ غير صالح.");
  if (walletBalance(state, source) < BigInt(amount))
    throw new Error("رصيد المحفظة المصدر غير كافٍ.");
  return {
    id: crypto.randomUUID(),
    from,
    to,
    amount,
    note,
    currency: source.currency,
    at: new Date().toISOString(),
  };
}

export function textDraft(raw) {
  const text = normalizeDigits(raw);
  if (/(?:^|\s)(?:ما|لم|لا)\s/.test(text) || /[+\-−]\s*\d/.test(text) || /بالدين|استلفت|اقترضت/.test(text))
    throw new Error("العبارة منفية أو ملتبسة؛ استخدم نموذج المراجعة اليدوي وحدد نوع العملية والمبلغ.");
  const income = /استلمت|قبضت|دخل|راتب/.test(text);
  const expense = /صرفت|دفعت|اشتريت|شريت/.test(text);
  if (income === expense)
    throw new Error("للتجربة اكتب عملية واحدة مثل: صرفت ١٥٠٠ ريال بنزين.");
  const matches = text.match(/\d+(?:[.٫,]\d+)?/g) || [];
  if (matches.length !== 1)
    throw new Error(
      "استخدم مبلغاً واحداً بأرقام مكتوبة. محلل الويب مبسط ولا يدعم الأعداد المنطوقة.",
    );
  const amount = parseMoney(matches[0]);
  const category = /بنزين/.test(text)
    ? "بنزين"
    : /مطعم|غداء|قهوة/.test(text)
      ? "مطاعم"
      : /بقالة|سوبر/.test(text)
        ? "بقالة"
        : /راتب/.test(text)
          ? "راتب"
          : "عام";
  return {
    amount,
    kind: income ? "INCOME" : "EXPENSE",
    category,
    note: raw.trim().slice(0, 160),
  };
}

export function createDemo(now = new Date()) {
  const date = (offset) =>
    new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate() - offset,
      9,
    ).toISOString();
  const entries = [
    [0, "EXPENSE", 3500, "قهوة ووقت هادئ", "مطاعم", "cash"],
    [1, "EXPENSE", 8500, "مشتريات المنزل", "بقالة", "cash"],
    [2, "EXPENSE", 6000, "تعبئة وقود", "بنزين", "cash"],
    [3, "INCOME", 75000, "دفعة مشروع تصميم", "عمل حر", "bank"],
    [3, "EXPENSE", 12000, "فاتورة الإنترنت", "فواتير", "bank"],
    [4, "EXPENSE", 4500, "غداء مع الأصدقاء", "مطاعم", "cash"],
    [5, "EXPENSE", 7000, "احتياجات يومية", "بقالة", "cash"],
    [6, "EXPENSE", 2500, "مواصلات", "مواصلات", "cash"],
    [8, "INCOME", 280000, "الراتب الشهري", "راتب", "bank"],
    [10, "EXPENSE", 18000, "كهرباء المنزل", "فواتير", "bank"],
  ];
  return {
    version: 1,
    wallets: [
      {
        id: "cash",
        name: "محفظتي اليومية",
        currency: "YER",
        type: "نقدية",
        opening: "9500000",
      },
      {
        id: "bank",
        name: "الحساب الرئيسي",
        currency: "YER",
        type: "بنكية",
        opening: "11000000",
      },
      {
        id: "usd",
        name: "محفظة الدولار",
        currency: "USD",
        type: "نقدية",
        opening: "125000",
      },
    ],
    transactions: entries.map(
      ([offset, kind, amount, note, category, walletId], i) => ({
        id: `demo-${i}`,
        kind,
        amount: String(amount * 100),
        note,
        category,
        walletId,
        currency: "YER",
        at: date(offset),
      }),
    ),
    transfers: [],
  };
}

export function validateState(state) {
  if (
    !state ||
    state.version !== 1 ||
    !["wallets", "transactions", "transfers"].every(
      (k) => Array.isArray(state[k]) && state[k].length < 10000,
    )
  )
    return false;
  if (
    !["wallets", "transactions", "transfers"].every((k) =>
      state[k].every(
        (row) => row !== null && typeof row === "object" && !Array.isArray(row),
      ),
    )
  )
    return false;
  const amountValid = (v, zero = false) =>
    typeof v === "string" &&
    /^\d{1,19}$/.test(v) &&
    BigInt(v) <= MAX_MINOR &&
    BigInt(v) >= (zero ? 0n : 1n);
  const textValid = (v, max = 160) =>
    typeof v === "string" && v.length > 0 && v.length <= max;
  const idValid = (id) => textValid(id, 100);
  if (new Set(state.wallets.map((w) => w.id)).size !== state.wallets.length)
    return false;
  if (
    !state.wallets.every(
      (w) =>
        idValid(w.id) &&
        textValid(w.name, 50) &&
        textValid(w.type, 30) &&
        CURRENCIES.includes(w.currency) &&
        amountValid(w.opening, true),
    )
  )
    return false;
  const wallet = (id) => state.wallets.find((w) => w.id === id);
  const dateValid = (at) =>
    typeof at === "string" && Number.isFinite(Date.parse(at));
  if (
    new Set(state.transactions.map((r) => r.id)).size !==
      state.transactions.length ||
    new Set(state.transfers.map((r) => r.id)).size !== state.transfers.length
  )
    return false;
  if (
    !state.transactions.every(
      (r) =>
        idValid(r.id) &&
        ["INCOME", "EXPENSE"].includes(r.kind) &&
        amountValid(r.amount) &&
        textValid(r.note) &&
        textValid(r.category, 40) &&
        dateValid(r.at) &&
        CURRENCIES.includes(r.currency) &&
        wallet(r.walletId)?.currency === r.currency,
    )
  )
    return false;
  return state.transfers.every(
    (r) =>
      idValid(r.id) &&
      amountValid(r.amount) &&
      r.from !== r.to &&
      dateValid(r.at) &&
      wallet(r.from)?.currency === r.currency &&
      wallet(r.to)?.currency === r.currency &&
      CURRENCIES.includes(r.currency),
  );
}
