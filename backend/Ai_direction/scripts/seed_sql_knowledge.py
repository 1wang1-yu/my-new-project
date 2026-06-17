"""将 docs/knowledge/*.txt 导入 SQL Server t_knowledge_doc 表"""
import os
import pymssql
from datetime import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KNOWLEDGE_DIR = os.path.join(ROOT, "docs", "knowledge")

CONN = {
    "server": "localhost",
    "port": 1433,
    "user": "sa",
    "password": "Root@123456",
    "database": "smart_guide_db",
}


def load_documents():
    docs = []
    for fname in sorted(os.listdir(KNOWLEDGE_DIR)):
        if not fname.endswith(".txt"):
            continue
        fpath = os.path.join(KNOWLEDGE_DIR, fname)
        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read().strip()
        title = fname.replace(".txt", "")
        for line in content.split("\n"):
            line = line.strip()
            if line.startswith("景点名称："):
                title = line.replace("景点名称：", "").strip()
                break
        # Determine category from filename prefix
        category = "scenic"
        if fname.startswith("NH-"):
            category = "nianhua_bay"
        docs.append((title, category, content))
        print(f"  Loaded: {title} ({len(content)} chars)")
    return docs


def main():
    documents = load_documents()
    print(f"\nTotal: {len(documents)} documents\n")

    conn = pymssql.connect(**CONN)
    cursor = conn.cursor()

    # Count existing
    cursor.execute("SELECT COUNT(*) FROM t_knowledge_doc")
    existing = cursor.fetchone()[0]
    print(f"Existing records in t_knowledge_doc: {existing}")

    # Delete old seed data (operator_id=0) to replace with full data
    cursor.execute("DELETE FROM t_knowledge_doc WHERE operator_id = 0")
    deleted = cursor.rowcount
    print(f"Deleted {deleted} old seed records")

    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    inserted = 0
    for title, category, content in documents:
        cursor.execute(
            "INSERT INTO t_knowledge_doc (title, category, content, chunk_count, index_status, operator_id, create_time, update_time) "
            "VALUES (%s, %s, %s, 0, 0, 0, %s, %s)",
            (title, category, content, now, now),
        )
        inserted += 1
        print(f"  Inserted: {title}")

    conn.commit()
    conn.close()
    print(f"\nDone! Inserted {inserted} documents into SQL.")


if __name__ == "__main__":
    main()
