import { test } from "node:test";
import assert from "node:assert/strict";
import {
  parseMoney,
  money,
  walletBalance,
  balanceFor,
  snapshot,
  savingScenario,
  createDemo,
  makeTransfer,
  textDraft,
  validateState,
  MAX_MINOR,
} from "../src/finance.js";

const now = new Date(2026, 10, 25, 15);
const empty = () => ({
  version: 1,
  wallets: [
    {
      id: "one",
      name: "نقدية",
      currency: "YER",
      type: "نقدية",
      opening: "100000",
    },
    { id: "two", name: "بنك", currency: "YER", type: "بنكية", opening: "0" },
    {
      id: "usd",
      name: "دولار",
      currency: "USD",
      type: "نقدية",
      opening: "50000",
    },
  ],
  transactions: [],
  transfers: [],
});
const row = (overrides = {}) => ({
  id: "row",
  amount: "1000",
  kind: "EXPENSE",
  currency: "YER",
  walletId: "one",
  note: "اختبار",
  category: "عام",
  at: now.toISOString(),
  ...overrides,
});

test("Arabic and Persian money is parsed exactly into minor units", () => {
  assert.equal(parseMoney("١٢٫٥٠"), "1250");
  assert.equal(parseMoney("۱۲.۰۵"), "1205");
  assert.equal(parseMoney("١٬٥٠٠"), "150000");
  assert.equal(parseMoney("0", true), "0");
});
test("invalid, zero, over-precision and overflowing money is rejected", () => {
  for (const input of [
    "-5",
    "0",
    "1.001",
    "abc",
    "1e4",
    "",
    "92233720368547758.08",
  ])
    assert.throws(() => parseMoney(input));
  assert.equal(parseMoney("92233720368547758.07"), MAX_MINOR.toString());
});
test("currency balances never combine currencies", () => {
  const state = empty();
  state.transactions.push(
    row(),
    row({ id: "usd-expense", currency: "USD", walletId: "usd", amount: "500" }),
  );
  assert.equal(balanceFor(state, "YER"), 99000n);
  assert.equal(balanceFor(state, "USD"), 49500n);
});
test("valid transfer conserves currency total and is not income or expense", () => {
  const state = empty();
  state.transfers.push(
    makeTransfer(state, { from: "one", to: "two", amount: "30000" }),
  );
  assert.equal(walletBalance(state, state.wallets[0]), 70000n);
  assert.equal(walletBalance(state, state.wallets[1]), 30000n);
  assert.equal(balanceFor(state, "YER"), 100000n);
  assert.equal(snapshot(state, "YER", now).income, 0n);
  assert.equal(snapshot(state, "YER", now).expense, 0n);
});
test("transfer rejects same wallet, different currencies and insufficient balance", () => {
  for (const transfer of [
    { from: "one", to: "one", amount: "1" },
    { from: "one", to: "usd", amount: "1" },
    { from: "one", to: "two", amount: "100001" },
    { from: "one", to: "two", amount: "0" },
  ])
    assert.throws(() => makeTransfer(empty(), transfer));
});
test("snapshot excludes future, previous month, wrong currency and invalid amounts", () => {
  const state = empty();
  state.transactions = [
    row(),
    row({ id: "income", kind: "INCOME", amount: "2000" }),
    row({ id: "future", at: new Date(2026, 10, 26).toISOString() }),
    row({ id: "old", at: new Date(2026, 9, 31).toISOString() }),
    row({ id: "wrong", currency: "USD" }),
    row({ id: "invalid", amount: "-1000" }),
  ];
  const s = snapshot(state, "YER", now);
  assert.equal(s.expense, 1000n);
  assert.equal(s.income, 2000n);
  assert.equal(s.daily.length, 7);
  assert.equal(s.daily[0].amount, 0n);
  assert.equal(s.daily[6].amount, 1000n);
});
test("30-day scenario uses elapsed month days without mutating transactions", () => {
  const state = empty();
  state.transactions = [row({ amount: "10000" })];
  const before = JSON.stringify(state);
  const s = snapshot(state, "YER", now);
  assert.equal(savingScenario(s.expense, s.elapsedDays, 20), 2400n);
  assert.equal(JSON.stringify(state), before);
  assert.throws(() => savingScenario(1n, 0, 20));
  assert.throws(() => savingScenario(1n, 1, 101));
});
test("aggregate sums remain exact beyond Number.MAX_SAFE_INTEGER", () => {
  const state = empty();
  state.transactions = [
    row({ kind: "INCOME", amount: MAX_MINOR.toString() }),
    row({ kind: "INCOME", amount: MAX_MINOR.toString() }),
  ];
  assert.equal(snapshot(state, "YER", now).income, MAX_MINOR * 2n);
});
test("demo fixture has a valid isolated state and positive balances", () => {
  const state = createDemo(now);
  assert.equal(validateState(state), true);
  assert.ok(balanceFor(state, "YER") > 0n);
  assert.equal(
    state.transactions.every((t) => t.id.startsWith("demo-")),
    true,
  );
});
test("stored malformed or mismatched records are not accepted", () => {
  for (const state of [
    null,
    {},
    { ...empty(), wallets: [null] },
    { ...empty(), transactions: [row({ currency: "USD" })] },
    {
      ...empty(),
      transactions: [row({ currency: undefined, walletId: undefined })],
    },
    { ...empty(), transactions: [row({ amount: "-1" })] },
    { ...empty(), transactions: [row({ at: "not-a-date" })] },
  ])
    assert.equal(validateState(state), false);
});
test("limited text parser produces a review draft, not a saved transaction", () => {
  const draft = textDraft("صرفت ١٥٠٠ ريال بنزين");
  assert.equal(draft.amount, "150000");
  assert.equal(draft.kind, "EXPENSE");
  assert.equal(draft.category, "بنزين");
  assert.equal(textDraft("استلمت ۷۵۰۰۰ راتب").kind, "INCOME");
});
test("limited parser rejects ambiguity, multiple amounts and spoken-number-only text", () => {
  for (const command of [
    "صرفت 1500 واستلمت 2000",
    "صرفت 100 و 200",
    "صرفت ألف ريال",
    "1500",
  ])
    assert.throws(() => textDraft(command));
});
test("privacy formatting never includes the amount", () => {
  assert.equal(money(123456n, "YER", true), "••••••");
  assert.ok(money(-50n).startsWith("−"));
});

test('negated and signed text must not silently become positive financial drafts', () => {
  for (const phrase of ['ما صرفت 100 ريال', 'لم استلمت 100 ريال', 'صرفت -100 ريال', 'صرفت −100 ريال', 'صرفت +100 ريال']) {
    assert.throws(() => textDraft(phrase));
  }
});
