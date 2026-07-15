"""using pyodbc to import docs/knowledge/*.txt into SQL Server t_knowledge_doc table - with proper UTF-8 encoding"""
import os
import pyodbc
from datetime import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KNOWLEDGE_DIR = os.path.join(ROOT, "docs", "knowledge")

CONN_STR = (
    "DRIVER={ODBC Driver 18 for SQL Server};"
    "SERVER=localhost,1433;"
    "DATABASE=smart_guide_db;"
    "UID=sa;"
    "PWD=Root@123456;"
    "Encrypt=no;"
    "TrustServerCertificate=yes;"
)


# 文档名称 → 归类到 guide 的文件名列表（不在景点选择列表中展示）
GUIDE_FILES = {
    "LS-017_小灵山千年历史渊源.txt",
    "LS-018_核心文化内涵.txt",
    "LS-019_灵山大佛全方位解析.txt",
    "LS-020_灵山梵宫艺术殿堂详解.txt",
    "LS-021_九龙灌浴与祥符禅寺详解.txt",
    "LS-022_五印坛城与曼飞龙塔.txt",
    "LS-023_历史文化爱好者深度游路线.txt",
    "LS-024_自然风光与亲子游览路线.txt",
    "LS-025_实用游览贴士.txt",
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
        # Determine category
        if fname.startswith("NH-"):
            category = "nianhua_bay"
        elif fname in GUIDE_FILES:
            category = "guide"          # 攻略/文化/路线类文档，不出现在景点选择列表中
        else:
            category = "scenic"
        docs.append((title, category, content))
        print(f"  Loaded: [{category}] {title} ({len(content)} chars)")
    return docs


def main():
    documents = load_documents()
    print(f"\nTotal: {len(documents)} documents\n")

    conn = pyodbc.connect(CONN_STR)
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
            "VALUES (?, ?, ?, 0, 0, 0, ?, ?)",
            (title, category, content, now, now),
        )
        inserted += 1
        print(f"  Inserted: {title}")

    conn.commit()
    conn.close()
    print(f"\nDone! Inserted {inserted} documents into SQL.")


if __name__ == "__main__":
    main()
