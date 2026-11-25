// Demonstration-only credit ledger. No microphone, bank connection or Android data.
import { walletBalance, MAX_MINOR } from './finance.js';
export const personKey = name => String(name).trim().replace(/\s+/g, ' ');
const positive = value => typeof value === 'string' && /^\d{1,19}$/.test(value) && BigInt(value) > 0n && BigInt(value) <= MAX_MINOR;
const validDate = value => typeof value === 'string' && Number.isFinite(Date.parse(value)) && Date.parse(value) >= 0;
const purchases = state => state.creditPurchases || [];
const payments = state => state.creditPayments || [];
export function creditAccounts(state) {
  return [...new Map(purchases(state).map(p => [JSON.stringify([p.person, p.currency]), { person: p.person, currency: p.currency }])).values()];
}
export function creditAccount(state, person, currency) {
  const rows = purchases(state).filter(p => p.person === personKey(person) && p.currency === currency);
  const paid = payments(state).filter(p => p.person === personKey(person) && p.currency === currency);
  const total = rows.reduce((sum, p) => sum + BigInt(p.amount), 0n);
  const totalPaid = paid.reduce((sum, p) => sum + BigInt(p.amount), 0n);
  const events = [...rows.map(p => ({ ...p, type: 'purchase' })), ...paid.map(p => ({ ...p, type: 'payment' }))]
    .sort((a, b) => Date.parse(a.at) - Date.parse(b.at) || (a.type === b.type ? a.id.localeCompare(b.id) : a.type === 'purchase' ? -1 : 1));
  return { rows, payments: paid, events, total, totalPaid, outstanding: total - totalPaid };
}
export function addCreditPurchase(state, { person, item, amount, walletId, at }, now = new Date()) {
  const wallet = state.wallets.find(w => w.id === walletId);
  const clean = personKey(person);
  if (!wallet || !clean || clean.length > 120 || !String(item).trim() || String(item).length > 160 || !positive(amount) || !validDate(at) || Date.parse(at) > +now) throw new Error('راجع الاسم والمشتريات والمبلغ والمحفظة والتاريخ؛ لا يُقبل وقت مستقبلي.');
  return { ...state, creditPurchases: [...purchases(state), { id: crypto.randomUUID(), person: clean, item: String(item).trim(), amount, walletId, currency: wallet.currency, at: new Date(at).toISOString() }], creditPayments: payments(state) };
}
export function repayCreditAccount(state, { person, currency, amount, walletId }, now = new Date()) {
  const account = creditAccount(state, person, currency);
  const wallet = state.wallets.find(w => w.id === walletId);
  if (!wallet || wallet.currency !== currency || !positive(amount)) throw new Error('اختر محفظة بالعملة نفسها ومبلغاً صحيحاً.');
  if (BigInt(amount) > account.outstanding) throw new Error('مبلغ السداد أكبر من المتبقي.');
  if (walletBalance(state, wallet) < BigInt(amount)) throw new Error('رصيد محفظة السداد غير كافٍ.');
  if (account.events.some(e => Date.parse(e.at) > +now)) throw new Error('تاريخ السداد يسبق بعض سجلات الحساب.');
  let left = BigInt(amount);
  const allocations = [];
  for (const purchase of [...account.rows].sort((a,b) => Date.parse(a.at) - Date.parse(b.at) || a.id.localeCompare(b.id))) {
    const alreadyPaid = payments(state).flatMap(p => p.allocations).filter(a => a.purchaseId === purchase.id).reduce((sum,a) => sum + BigInt(a.amount), 0n);
    const remaining = BigInt(purchase.amount) - alreadyPaid;
    const allocated = remaining < left ? remaining : left;
    if (allocated > 0n) { allocations.push({ purchaseId: purchase.id, amount: allocated.toString() }); left -= allocated; }
    if (!left) break;
  }
  if (left) throw new Error('تعذر توزيع السداد على سجلات الحساب.');
  const id = crypto.randomUUID(), transactionId = crypto.randomUUID(), at = now.toISOString();
  return { ...state, creditPayments: [...payments(state), { id, person: personKey(person), currency, amount, walletId, at, allocations, transactionId }], transactions: [{ id: transactionId, amount, kind: 'EXPENSE', note: 'سداد حساب: ' + personKey(person), category: 'سداد دين', walletId, currency, at, creditPaymentId: id }, ...state.transactions] };
}
export function creditDate(at) {
  return new Intl.DateTimeFormat('ar-YE', { timeZone: 'Asia/Aden', weekday: 'long', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).format(new Date(at));
}
const amountText = (value, currency) => `${BigInt(value) / 100n}.${(BigInt(value) % 100n).toString().padStart(2,'0')} ${currency}`;
export function creditStatement(state, person, currency, now = new Date()) {
  const account = creditAccount(state, person, currency);
  let balance = 0n;
  return [
    'MK Finance — كشف مشتريات بالدين ودفعات سداد (معاينة تجريبية)',
    `الشخص: ${personKey(person)}`, `العملة: ${currency} — التوقيت: Asia/Aden (عدن)`, `أُعدّ: ${creditDate(now)}`,
    '────────────────────',
    ...account.events.flatMap((e, i) => {
      balance += (e.type === 'purchase' ? 1n : -1n) * BigInt(e.amount);
      return [`${i + 1}. ${creditDate(e.at)}`, `${e.type === 'purchase' ? 'شراء بالدين: ' + e.item.replace(/\s+/g, ' ') : 'سداد حساب'} — ${amountText(e.amount, currency)}`, `المرجع: ${e.id}`, `المتبقي بعد الحركة: ${amountText(balance, currency)}`, '────────────────────'];
    }),
    `إجمالي المشتريات: ${amountText(account.total, currency)}`, `إجمالي السداد: ${amountText(account.totalPaid, currency)}`, `المتبقي عليّ: ${amountText(account.outstanding, currency)}`,
    'بيانات معاينة منفصلة عن Android. هذا الكشف ليس إثبات تحويل مصرفي ولا إقراراً من الطرف الآخر.'
  ].join('\n');
}
export function validateCreditState(state) {
  const rows = purchases(state), paid = payments(state);
  if (!Array.isArray(rows) || !Array.isArray(paid) || rows.length > 10000 || paid.length > 10000) return false;
  if (!rows.every(p => p && typeof p.id === 'string' && typeof p.person === 'string' && p.person === personKey(p.person) && p.person.length > 0 && p.person.length <= 120 && typeof p.item === 'string' && p.item.trim().length > 0 && p.item.length <= 160 && positive(p.amount) && validDate(p.at) && state.wallets.some(w => w.id === p.walletId && w.currency === p.currency))) return false;
  if (new Set(rows.map(p => p.id)).size !== rows.length || new Set(paid.map(p => p?.id)).size !== paid.length) return false;
  const allocationTotals = new Map();
  for (const p of paid) {
    if (!p || typeof p.id !== 'string' || !positive(p.amount) || !validDate(p.at) || !Array.isArray(p.allocations) || !p.allocations.length || !state.wallets.some(w => w.id === p.walletId && w.currency === p.currency)) return false;
    const transaction = state.transactions.find(t => t.id === p.transactionId);
    if (!transaction || transaction.creditPaymentId !== p.id || transaction.kind !== 'EXPENSE' || transaction.walletId !== p.walletId || transaction.currency !== p.currency || transaction.amount !== p.amount || transaction.at !== p.at) return false;
    let total = 0n;
    for (const a of p.allocations) {
      if (!a || !positive(a.amount)) return false;
      const purchase = rows.find(r => r.id === a.purchaseId);
      if (!purchase || purchase.person !== p.person || purchase.currency !== p.currency || Date.parse(p.at) < Date.parse(purchase.at)) return false;
      total += BigInt(a.amount);
      allocationTotals.set(purchase.id, (allocationTotals.get(purchase.id) || 0n) + BigInt(a.amount));
      if (allocationTotals.get(purchase.id) > BigInt(purchase.amount)) return false;
    }
    if (total !== BigInt(p.amount)) return false;
  }
  return state.transactions.every(t => !t.creditPaymentId || paid.some(p => p.id === t.creditPaymentId && p.transactionId === t.id));
}

export function isCreditCommand(raw) {
  return /قات/.test(raw) && (/بالدين|على الحساب|عالحساب|آجل|اجل|تدينت/.test(raw) || /حق\s+قات\s+.+/.test(raw) && !/صرفت|دفعت|استلفت|اقترضت|سلفت/.test(raw));
}
export function creditDraft(raw) {
  if (!isCreditCommand(raw) || /(?:^|\s)(?:لا|لم|ما)\s/.test(raw) || /استلفت|اقترضت|قرض|سلفت/.test(raw)) throw new Error('اكتب شراءً واحداً بالدين وحدد اسم البائع، ولا تخلطه بقرض نقدي.');
  const normalized = raw.replace(/[٠-٩]/g, d => '٠١٢٣٤٥٦٧٨٩'.indexOf(d)).replace(/[۰-۹]/g, d => '۰۱۲۳۴۵۶۷۸۹'.indexOf(d));
  if (/[+\-−]\s*\d/.test(normalized)) throw new Error('اكتب مبلغاً موجباً واضحاً دون إشارة.');
  const numbers = normalized.match(/\d+(?:[.٫]\d{1,2})?/g) || [];
  const spoken = [...normalized.matchAll(/(?:^|\s)ب?(ألفين|الفين|ألف|الف)(?=\s|$)/g)];
  if (numbers.length + spoken.length !== 1 || /(?:الف|ألف|الفين|ألفين)\s+و/.test(raw) || /خمسم|مئ|مائ|مية|ميه|عشرين|خمسين|مليون/.test(raw)) throw new Error('محلل الويب محدود؛ اكتب مبلغاً واحداً بالأرقام، أو ألف/ألفين فقط.');
  const amount = numbers.length ? String(BigInt(numbers[0].replace('٫', '.').split('.')[0]) * 100n + BigInt((numbers[0].replace('٫', '.').split('.')[1] || '').padEnd(2,'0'))) : /ألفين|الفين/.test(spoken[0][1]) ? '200000' : '100000';
  let tail = normalized.match(/(?:من|عند)\s+(.+)$/)?.[1] || normalized.match(/حق\s+قات\s+(.+)$/)?.[1] || '';
  tail = tail.split(/\s+(?:ب?(?:ألفين|الفين|ألف|الف)|\d|ريال|بالدين|حق|اليوم|أمس|امس|قات)/)[0];
  const person = personKey(tail);
  if (!person || !positive(amount)) throw new Error('حدد المبلغ واسم البائع كاملاً في نموذج المراجعة.');
  return { person, item: 'قات', amount };
}
