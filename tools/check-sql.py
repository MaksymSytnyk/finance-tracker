#!/usr/bin/env python3
"""Validate the checked-in SQL and v1 migration with SQLite, independently of code generation."""
from pathlib import Path
import sqlite3, tempfile, re, unittest, sys
source = Path('data/src/commonMain/sqldelight/com/maksym/financetracker/db/Finance.sq').read_text()
ddl = source.split('selectMeta:')[0]
statements = re.split(r'(?m)^([A-Za-z][A-Za-z0-9]*):\n', 'selectMeta:'+source.split('selectMeta:',1)[1])
queries = dict(zip(statements[1::2], statements[2::2]))
baseline = Path('data/src/commonMain/sqldelight/databases/1.db')
if '--create-baseline' in sys.argv:
    baseline.parent.mkdir(parents=True,exist_ok=True)
    if baseline.exists(): raise SystemExit('Baseline already exists; do not overwrite released schemas')
    db = sqlite3.connect(baseline)
    db.executescript(ddl.replace("    notes TEXT NOT NULL DEFAULT '',\n",''))
    db.execute('PRAGMA user_version=1');db.close()

class SchemaTest(unittest.TestCase):
    def setUp(self):
        self.db=sqlite3.connect(':memory:');self.db.execute('PRAGMA foreign_keys=ON');self.db.executescript(ddl)
        self.db.execute(queries['insertAccount'], ('a','Bank','USD','BANK',10000,'2024-01-01',0,0,0))
        self.db.execute(queries['insertAccount'], ('b','Cash','USD','CASH',0,'2024-01-01',0,0,0))
        self.db.execute(queries['insertCategory'], ('food','Food','EXPENSE','x',0,0));self.db.commit()
    def expense(self, id='t', amount=100): return (id,'EXPENSE','a','USD',amount,'2024-02-29','food',None,None,None,'Lunch','[]',0,0,None,None,'')
    def test_all_queries_parse(self):
        for name, query in queries.items():
            with self.subTest(query=name): self.db.execute('EXPLAIN '+query, (None,)*query.count('?')).fetchall()
    def test_foreign_keys_prevent_orphans(self):
        tx=list(self.expense());tx[2]='missing'
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute(queries['insertTransaction'],tx)
    def test_zero_and_negative_rejected(self):
        for amount in [0,-1]:
            with self.assertRaises(sqlite3.IntegrityError): self.db.execute(queries['insertTransaction'],self.expense(amount=amount))
    def test_transfer_is_single_atomic_record(self):
        transfer=('t','TRANSFER','a','USD',200,'2024-02-29',None,'b','USD',200,'Transfer','[]',0,0,None,None,'')
        self.db.execute(queries['insertTransaction'],transfer)
        self.assertEqual(self.db.execute('SELECT count(*) FROM financial_transaction').fetchone()[0],1)
        self.db.rollback()
        self.assertEqual(self.db.execute('SELECT count(*) FROM financial_transaction').fetchone()[0],0)
    def test_recurring_identity_survives_transaction_delete(self):
        self.db.execute(queries['insertRule'],('r','{}','MONTHLY',1,'2024-01-31',0,'UTC',None,1))
        self.db.execute(queries['insertTransaction'],self.expense())
        self.db.execute(queries['insertOccurrence'],('r','2024-02-29','t'))
        self.db.execute(queries['updateOccurrence'],(None,'r','2024-02-29'))
        self.db.execute(queries['deleteTransaction'],('t',))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute(queries['insertOccurrence'],('r','2024-02-29',None))
    def test_budget_unique_per_currency_month(self):
        self.db.execute(queries['insertBudget'],('b','food','2024-02-01','USD',1000))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute(queries['insertBudget'],('c','food','2024-02-01','USD',2000))
        self.db.execute(queries['insertBudget'],('d','food','2024-02-01','EUR',2000))
    def test_failure_rolls_back_every_statement(self):
        try:
            with self.db:
                self.db.execute(queries['insertTransaction'],self.expense())
                self.db.execute(queries['insertTransaction'],self.expense(id='bad',amount=-1))
        except sqlite3.IntegrityError: pass
        self.assertEqual(self.db.execute('SELECT count(*) FROM financial_transaction').fetchone()[0],0)
    def test_v1_migration_preserves_amount_and_adds_notes(self):
        old=sqlite3.connect(':memory:')
        with sqlite3.connect(baseline) as fixture: fixture.backup(old)
        old.execute(queries['insertAccount'],('a','Bank','USD','BANK',9223372036854775807,'2024-01-01',0,0,0))
        old.executescript(Path('data/src/commonMain/sqldelight/migrations/1.sqm').read_text())
        self.assertEqual(old.execute('SELECT opening_minor FROM account').fetchone()[0],9223372036854775807)
        for table in ['meta','account','category','financial_transaction','budget','recurring_rule','occurrence','rate']:
            self.assertEqual(old.execute(f'PRAGMA table_info({table})').fetchall(),self.db.execute(f'PRAGMA table_info({table})').fetchall())
        old.close()
    def tearDown(self): self.db.close()
if __name__ == '__main__': unittest.main(argv=[sys.argv[0]])
