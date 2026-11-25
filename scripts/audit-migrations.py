#!/usr/bin/env python3
"""Smoke-test literal migration SQL with Python SQLite, NOT Room schema validation.
Only in-memory synthetic data is used. Room/instrumentation is still mandatory.
"""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parent.parent
main = root / 'app/src/main/java/com/malekfinance'
source = (main / 'MainActivity.kt').read_text()

def literal(expression):
    if expression.strip() == 'DEFAULT_WALLET_SQL':
        expression = source.split('private const val DEFAULT_WALLET_SQL =', 1)[1].split('\n\n', 1)[0]
    parts = re.findall(r'"(?:\\.|[^"\\])*"', expression)
    assert parts, 'Unrecognized SQL expression'
    rest = re.sub(r'"(?:\\.|[^"\\])*"', '', expression)
    assert re.fullmatch(r'[\s+]*', rest), f'Unsupported SQL expression: {rest}'
    return ''.join(json.loads(part) for part in parts)

def statements(block):
    # Migration execSQL calls currently contain literal strings/concatenations only.
    # Match balanced parentheses outside Kotlin string literals.
    for match in re.finditer(r'db\.execSQL\(', block):
        i = match.end()
        start = i
        depth = 1
        quoted = escaped = False
        while depth:
            c = block[i]
            if quoted:
                if escaped: escaped = False
                elif c == '\\': escaped = True
                elif c == '"': quoted = False
            elif c == '"': quoted = True
            elif c == '(': depth += 1
            elif c == ')': depth -= 1
            i += 1
        yield literal(block[start:i-1])

migrations = re.findall(r'val MIGRATION_(\d+)_(\d+) = object : Migration\([^\n]+\) \{(.*?)(?=\n        val MIGRATION_|\n        /\*\* Single migration)', source, re.S)
assert len(migrations) == 11
sql_by_version = {int(a): list(statements(block)) for a, b, block in migrations}

# Compare column names/affinities against the actual Room entity property declarations.
entities = {}
for path in main.glob('*.kt'):
    content = path.read_text()
    for match in re.finditer(r'@Entity\(.*?tableName\s*=\s*"([^\"]+)".*?\)\s*data class \w+\((.*?)\n\)', content, re.S):
        table, body = match.groups()
        entities[table] = {name: ('INTEGER' if kind in ('Long', 'Int', 'Boolean') else 'TEXT') for name, kind in re.findall(r'val (\w+): (Long|Int|Boolean|String)\??', body)}
assert len(entities) == 15, f'Expected 15 entities, got {list(entities)}'

for checkpoint in (1, 6, 10):
    db = sqlite3.connect(':memory:')
    db.execute('CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amountMinor INTEGER NOT NULL, kind TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)')
    db.execute("INSERT INTO transactions VALUES(1,125500,'EXPENSE','legacy row',1700000000000)")
    for version in range(1, 12):
        for sql in sql_by_version[version]: db.execute(sql)
        if version == 3:
            # Actual seeding inserts default/legacy categories. Use fixtures here;
            # dynamic seeding and Room identity hashes are not tested by this script.
            db.execute("INSERT OR IGNORE INTO categories(name,kind,colorHex,isActive,createdAt) VALUES('عام','BOTH','#A855F7',1,0)")
        if version + 1 == checkpoint:
            snapshot = db.serialize()
            db.close()
            db = sqlite3.connect(':memory:')
            db.deserialize(snapshot)
    for table, properties in entities.items():
        actual = {row[1]: row[2] for row in db.execute(f'PRAGMA table_info({table})')}
        assert actual == properties, f'{table}: columns/affinities differ: {actual} vs {properties}'
    row = db.execute('SELECT amountMinor,kind,note,walletId,currencyCode FROM transactions WHERE id=1').fetchone()
    assert row == (125500, 'EXPENSE', 'legacy row', 1, 'YER')
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    # Check unique occurrence enforcement, not just the index name.
    for table, owner in [('bill_payments', 'billId'), ('recurring_income_entries', 'ruleId'), ('recurring_expense_entries', 'ruleId')]:
        cols = [r[1] for r in db.execute(f'PRAGMA table_info({table})') if r[1] != 'id']
        values = ['day' if c == 'occurrenceKey' else 1 for c in cols]
        query = f"INSERT INTO {table}({','.join(cols)}) VALUES({','.join('?' for _ in cols)})"
        db.execute(query, values)
        try:
            other = [2 if c == 'transactionId' else v for c, v in zip(cols, values)]
            db.execute(query, other)
            raise AssertionError(f'{table} allowed a duplicate occurrence')
        except sqlite3.IntegrityError:
            pass
    db.close()
    print(f'PASS migration SQL through v12 (reopen checkpoint v{checkpoint}), 15 entity column/affinity comparisons and unique occurrence checks; NOT Room')
