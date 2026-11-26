#!/usr/bin/env python3
"""Read-only remediation checks; no user database, Android emulator or generated Git files.

KOTLINC=/path/to/kotlinc JAVA_HOME=/path/to/jdk python3 scripts/deep-audit.py
Use --skip-core after running audit-core.py separately. Static wiring checks are NOT
Compose execution; SQLite checks are NOT Room execution. Robolectric tests run in CI.
Original pre-fix observations remain in docs/audit/deep-probe-results.txt.
"""
from pathlib import Path
import re
import runpy
import sqlite3
import sys

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / 'app/src/main/java/com/malekfinance'
if '--skip-core' not in sys.argv:
    runpy.run_path(str(ROOT / 'scripts/audit-core.py'))

ui = (MAIN / 'ui/FinanceApp.kt').read_text()
cards = (MAIN / 'ui/VoiceFinancialDraftCards.kt').read_text()
schedule = (MAIN / 'FinanceScheduleOperations.kt').read_text()
# Small source-contract checks complement (not replace) physical-device UI tests.
for flag, card, callback, tag in [
    ('hasBillDraft', 'VoiceBillDraftCard', 'confirmVoiceBillPayment', 'confirm-voice-bill'),
    ('hasSavingsDraft', 'VoiceSavingsDraftCard', 'confirmVoiceSavings', 'confirm-voice-savings'),
]:
    assert re.search(r'if \(' + flag + r'\) \{\s*item \{\s*' + card, ui)
    assert 'onConfirm = ::' + callback in ui
    assert 'testTag("' + tag + '")' in cards
    print('PASS review UI wiring:', card)
reset = ui[ui.index('    fun clearDraftSelection()'):ui.index('    fun processRecognizedText(')]
for flag in ['hasDraft', 'hasTransferDraft', 'hasDebtDraft', 'hasSavingsDraft', 'hasBillDraft']:
    assert flag + ' = false' in reset
assert 'clearDebtPaymentDraft()' in reset and 'billDraftId = null' in reset
command_reset = ui[ui.index('        if (parsed.command in setOf('):ui.index('        transcript = spoken')]
for command in ['TRANSACTION', 'CANCEL_DRAFT', 'TRANSFER_DRAFT', 'DEBT_CREATE_DRAFT', 'DEBT_PAYMENT_DRAFT',
                'BILL_PAYMENT_DRAFT', 'SAVINGS_CONTRIBUTION_DRAFT', 'SAVINGS_WITHDRAWAL_DRAFT']:
    assert 'VoiceCommandType.' + command in command_reset
assert 'QUERY' not in command_reset and 'CORRECT_AMOUNT' not in command_reset
assert ')) clearDraftSelection()' in command_reset
print('PASS shared draft reset covers all mutation/cancellation commands but not queries or correction')
assert 'parsed.debtPaymentDirection.orEmpty()' in ui
assert 'FinanceVoiceDraftPolicy.transferParties(spoken, wallets)' in ui
assert 'FinanceMoney.format(balance, wallet.currencyCode)' in ui
assert 'rememberFinanceClock()' in (MAIN / 'ui/PremiumDashboard.kt').read_text()
print('PASS no guessed repayment default; transfer/money helpers and lifecycle-aware clock are wired')

migration = runpy.run_path(str(ROOT / 'scripts/audit-migrations.py'))
db = sqlite3.connect(':memory:')
db.row_factory = sqlite3.Row
db.execute('CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amountMinor INTEGER NOT NULL, kind TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)')
for version in range(1, 12):
    for sql in migration['sql_by_version'][version]:
        db.execute(sql)
for filename, table, method in [
    ('FinanceBill.kt', 'bills', 'toggleBill'),
    ('FinanceRecurringIncome.kt', 'recurring_income_rules', 'toggleIncome'),
    ('FinanceRecurringExpense.kt', 'recurring_expense_rules', 'toggleExpense'),
]:
    source = (MAIN / filename).read_text()
    sql = re.search(r'@Query\("(UPDATE ' + table + r' SET isActive = :active WHERE id = :id)"\)', source)[1]
    assert f'suspend fun {method}(db: FinanceDb, id: Long): Boolean = db.withTransaction' in schedule
    # Seed through the actual v12 schema, then simulate a schedule advancement
    # after an old status was read. Run the actual production column-only SQL.
    columns = {row[1]: row[2] for row in db.execute('PRAGMA table_info(' + table + ')') if row[1] != 'id'}
    values = {column: ('audit' if kind == 'TEXT' else 1) for column, kind in columns.items()}
    values.update(nextDueAt=1000, isActive=1)
    db.execute('INSERT INTO ' + table + '(' + ','.join(values) + ') VALUES(' + ','.join('?' for _ in values) + ')', list(values.values()))
    stale_active = db.execute('SELECT isActive FROM ' + table + ' WHERE id=1').fetchone()[0]
    db.execute('UPDATE ' + table + ' SET nextDueAt=2000 WHERE id=1')
    db.execute(sql, {'id': 1, 'active': not stale_active})
    actual = db.execute('SELECT nextDueAt,isActive FROM ' + table + ' WHERE id=1').fetchone()
    assert tuple(actual) == (2000, 0), (table, tuple(actual))
    print('PASS production status-only SQL preserves advanced due date:', table)
db.close()
print('PASS deep remediation contracts; device UI and actual concurrent Room validation remain separate')
