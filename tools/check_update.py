"""Update gate, run by fg.ps1 ship: every database step must upgrade on real SQLite and keep every saved row.

The owner's phone keeps its library across updates. Each step from the first exported schema to the current one is
replayed from the statements in Migrations.kt, then compared with the schema Room exported for that version. The
comparison follows what Room checks when it opens a database: columns, types, null rules, keys, indices.
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

DATA = Path(__file__).resolve().parent.parent / "data"
SCHEMAS = DATA / "schemas" / "com.frameglass.data.db.FrameDb"
SOURCE = DATA / "src" / "main" / "java" / "com" / "frameglass" / "data"


def load(version):
    return json.loads((SCHEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]


def fill(entity, text):
    return text.replace("${TABLE_NAME}", entity["tableName"])


def statements(source, start, end):
    """The strings of STATEMENTS_start_end, with adjacent literals joined. None when the list is missing."""
    head = f"STATEMENTS_{start}_{end}: List<String> = listOf("
    if head not in source:
        return None
    body = source.split(head)[1].split("\n    )\n")[0]
    found, current = [], ""
    for match in re.finditer(r'"((?:[^"\\]|\\.)*)"(\s*\+)?', body):
        current += match.group(1)
        if not match.group(2):
            found.append(current)
            current = ""
    return found


def create(db, schema):
    for entity in schema["entities"]:
        db.execute(fill(entity, entity["createSql"]))
        for index in entity.get("indices", []):
            db.execute(fill(entity, index["createSql"]))


def seed(db, schema):
    """One saved row in every plain table, as the phone would hold."""
    for entity in schema["entities"]:
        if "ftsVersion" in entity:
            continue
        table = entity["tableName"]
        single_key = len(entity["primaryKey"]["columnNames"]) == 1
        values = {}
        for _, name, kind, notnull, default, pk in db.execute(f"PRAGMA table_info(`{table}`)"):
            kind = kind.upper()
            if (pk and single_key and kind == "INTEGER") or default is not None or not (notnull or pk):
                continue
            values[name] = 1 if "INT" in kind else 1.0 if "REAL" in kind else "x"
        if values:
            names = ",".join(f"`{name}`" for name in values)
            marks = ",".join("?" for _ in values)
            db.execute(f"INSERT INTO `{table}` ({names}) VALUES ({marks})", list(values.values()))
        else:
            db.execute(f"INSERT INTO `{table}` DEFAULT VALUES")


def rows(db, schema):
    """Row count of every plain table, or None for a table that no longer exists."""
    counts = {}
    for entity in schema["entities"]:
        if "ftsVersion" in entity:
            continue
        table = entity["tableName"]
        try:
            counts[table] = db.execute(f"SELECT count(*) FROM `{table}`").fetchone()[0]
        except sqlite3.OperationalError:
            counts[table] = None
    return counts


def compare(db, schema):
    problems = []
    present = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    for entity in schema["entities"]:
        table = entity["tableName"]
        if table not in present:
            problems.append(f"missing table {table}")
            continue
        if "ftsVersion" in entity:
            continue
        info = {row[1]: row for row in db.execute(f"PRAGMA table_info(`{table}`)")}
        want = {field["columnName"]: field for field in entity["fields"]}
        if set(info) != set(want):
            problems.append(f"{table}: columns differ {sorted(set(info) ^ set(want))}")
            continue
        for name, field in want.items():
            row = info[name]
            if row[2].upper() != field["affinity"].upper():
                problems.append(f"{table}.{name}: type {row[2]}, expected {field['affinity']}")
            if bool(row[3]) != bool(field.get("notNull")):
                problems.append(f"{table}.{name}: null rule differs")
            if "defaultValue" in field and str(row[4]) != str(field["defaultValue"]):
                problems.append(f"{table}.{name}: default {row[4]}, expected {field['defaultValue']}")
        key = [row[1] for row in sorted((r for r in info.values() if r[5]), key=lambda r: r[5])]
        if key != entity["primaryKey"]["columnNames"]:
            problems.append(f"{table}: primary key differs")
        have = {row[1]: row[2] for row in db.execute(f"PRAGMA index_list(`{table}`)") if row[3] == "c"}
        wanted = {index["name"]: index for index in entity.get("indices", [])}
        if set(have) != set(wanted):
            problems.append(f"{table}: indices differ {sorted(set(have) ^ set(wanted))}")
        for name, index in wanted.items():
            if name in have:
                columns = [row[2] for row in db.execute(f"PRAGMA index_info(`{name}`)")]
                if columns != index["columnNames"] or bool(have[name]) != bool(index["unique"]):
                    problems.append(f"{name}: definition differs")
        keys = {(row[2], row[3], row[4]) for row in db.execute(f"PRAGMA foreign_key_list(`{table}`)")}
        expected = {
            (fk["table"], column, target)
            for fk in entity.get("foreignKeys", [])
            for column, target in zip(fk["columns"], fk["referencedColumns"])
        }
        if keys != expected:
            problems.append(f"{table}: foreign keys differ")
    return problems


def main():
    versions = sorted(int(path.stem) for path in SCHEMAS.glob("*.json"))
    open_code = (SOURCE / "db" / "FrameDb.kt").read_text(encoding="utf-8")
    migrations = (SOURCE / "db" / "Migrations.kt").read_text(encoding="utf-8")
    problems = []

    declared = int(re.search(r"version = (\d+)", open_code).group(1))
    if declared != versions[-1]:
        problems.append(f"FrameDb says version {declared}, newest exported schema is {versions[-1]}")
    for path in SOURCE.rglob("*.kt"):
        if "fallbackToDestructive" in path.read_text(encoding="utf-8"):
            problems.append(f"{path.name} allows a destructive fallback")

    db = sqlite3.connect(":memory:")
    first = load(versions[0])
    create(db, first)
    seed(db, first)

    schema = first
    for start, end in zip(versions, versions[1:]):
        if f"MIGRATION_{start}_{end}" not in open_code:
            problems.append(f"MIGRATION_{start}_{end} is not registered in FrameDb.open")
        steps = statements(migrations, start, end)
        if steps is None:
            problems.append(f"STATEMENTS_{start}_{end} is missing from Migrations.kt")
            break
        before = rows(db, schema)
        for step in steps:
            db.execute(step)
        after = rows(db, schema)
        for table, count in before.items():
            if after[table] is None or after[table] < count:
                problems.append(f"step {start} to {end} lost rows in {table}: {count} before, {after[table]} after")
        schema = load(end)
        problems += [f"after step {start} to {end}: {problem}" for problem in compare(db, schema)]

    if problems:
        print("\n".join(problems))
        print("update check FAILED: this build could harm the library on the phone")
        return 1
    kept = sum(count or 0 for count in rows(db, first).values())
    print(f"update check passed: {len(versions) - 1} step(s), {len(schema['entities'])} tables, {kept} saved rows kept")
    return 0


if __name__ == "__main__":
    sys.exit(main())
