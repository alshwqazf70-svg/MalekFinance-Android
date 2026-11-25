import { test } from 'node:test';
import assert from 'node:assert/strict';
import { walletBalance } from '../src/finance.js';
import { addCreditPurchase, repayCreditAccount, creditAccount, creditStatement, validateCreditState, creditDraft, creditDate } from '../src/credit.js';
const now = new Date('2026-11-25T12:00:00Z');
const base = () => ({ version: 1, wallets: [{ id: 'cash', name: 'نقد', type: 'نقدية', currency: 'YER', opening: '1000000' }, { id: 'usd', name: 'دولار', type: 'نقدية', currency: 'USD', opening: '1000000' }], transactions: [], transfers: [] });
const add = (state, overrides = {}) => addCreditPurchase(state, { person: 'عبده مهدي', item: 'قات', amount: '100000', walletId: 'cash', at: '2026-11-24T10:20:30Z', ...overrides }, now);

test('credit purchase preserves full name, exact timestamp and unchanged cash', () => {
  const state = base(), next = add(state);
  assert.equal(next.creditPurchases[0].person, 'عبده مهدي');
  assert.equal(next.creditPurchases[0].at, '2026-11-24T10:20:30.000Z');
  assert.equal(walletBalance(next, next.wallets[0]), 1000000n);
  assert.equal(state.creditPurchases, undefined);
});
test('daily purchases and repayment remain in a chronological person statement', () => {
  let state = add(add(base()), { amount: '200000', at: '2026-11-25T10:00:00Z' });
  state = repayCreditAccount(state, { person: 'عبده مهدي', currency: 'YER', amount: '150000', walletId: 'cash' }, now);
  const a = creditAccount(state, 'عبده مهدي', 'YER');
  assert.equal(a.total, 300000n); assert.equal(a.totalPaid, 150000n); assert.equal(a.outstanding, 150000n);
  assert.equal(a.events.length, 3); assert.equal(a.events[0].type, 'purchase'); assert.equal(a.events[2].type, 'payment');
  assert.equal(walletBalance(state, state.wallets[0]), 850000n);
  assert.deepEqual(state.creditPayments[0].allocations.map(a => a.amount), ['100000', '50000']);
  const text = creditStatement(state, 'عبده مهدي', 'YER', now);
  assert.ok(text.includes('عبده مهدي')); assert.ok(text.includes('1500.00 YER')); assert.ok(text.includes('Asia/Aden'));
  assert.equal(validateCreditState(state), true);
});
test('accounts never combine currencies or similar people', () => {
  let state = add(add(add(base()), { person: 'عبده مهدي صالح' }), { walletId: 'usd' });
  assert.equal(creditAccount(state, 'عبده مهدي', 'YER').total, 100000n);
  assert.equal(creditAccount(state, 'عبده مهدي', 'USD').total, 100000n);
});
test('repayment rejects insufficient funds, overpayment and currency mismatch without mutation', () => {
  const state = add(base()); const before = JSON.stringify(state);
  for (const input of [{ person: 'عبده مهدي', currency: 'YER', amount: '100001', walletId: 'cash' }, { person: 'عبده مهدي', currency: 'YER', amount: '100000', walletId: 'usd' }]) assert.throws(() => repayCreditAccount(state, input, now));
  const poor = { ...state, wallets: state.wallets.map(w => ({ ...w, opening: '0' })) };
  assert.throws(() => repayCreditAccount(poor, { person: 'عبده مهدي', currency: 'YER', amount: '1', walletId: 'cash' }, now));
  assert.equal(JSON.stringify(state), before);
});
test('future purchase time and malformed records are rejected', () => {
  assert.throws(() => add(base(), { at: '2026-11-26T00:00:00Z' }));
  assert.equal(validateCreditState({ ...base(), creditPurchases: [null] }), false);
});
test('linked settlement cannot disappear from stored transactions unnoticed', () => {
  const state = repayCreditAccount(add(base()), { person: 'عبده مهدي', currency: 'YER', amount: '100000', walletId: 'cash' }, now);
  assert.equal(validateCreditState(state), true);
  assert.equal(validateCreditState({ ...state, transactions: [] }), false);
  assert.equal(creditAccount(state, 'عبده مهدي', 'YER').events.length, 2);
});
test('web text draft understands the exact example but does not save automatically', () => {
  const result = creditDraft('ألف حق قات عبده مهدي');
  assert.deepEqual(result, { person: 'عبده مهدي', item: 'قات', amount: '100000' });
  assert.equal(creditDraft('أخذت قات بألفين ريال بالدين من عبده مهدي').amount, '200000');
  assert.throws(() => creditDraft('ما أخذت قات بالدين من عبده مهدي بألف'));
  assert.throws(() => creditDraft('أخذت قات بألف وخمسمية بالدين من عبده مهدي'));
});
test('Aden time conversion is explicit rather than depending on local timezone', () => {
  assert.ok(creditDate('2026-11-25T10:20:30Z').includes('١٣:٢٠:٣٠'));
});

test('credit parser never turns a signed negative amount into a positive debt', () => {
  assert.throws(() => creditDraft('أخذت قات بالدين من عبده مهدي -100 ريال'));
  assert.throws(() => creditDraft('أخذت قات بالدين من عبده مهدي −100 ريال'));
});
