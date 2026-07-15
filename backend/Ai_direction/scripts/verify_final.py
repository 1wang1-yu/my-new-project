"""Final verification: check categories and attraction list"""
import pyodbc
import json
import urllib.request

print("=" * 60)
print("1. SQL Server 分类检查")
print("=" * 60)
conn = pyodbc.connect(
    'DRIVER={ODBC Driver 18 for SQL Server};'
    'SERVER=localhost,1433;DATABASE=smart_guide_db;'
    'UID=sa;PWD=Root@123456;Encrypt=no;TrustServerCertificate=yes;'
)
cursor = conn.cursor()
cursor.execute("SELECT category, COUNT(*) as cnt FROM t_knowledge_doc GROUP BY category ORDER BY category")
for row in cursor.fetchall():
    print(f"  {row[0]}: {row[1]} docs")

# Show guide-category docs
cursor.execute("SELECT title FROM t_knowledge_doc WHERE category = 'guide'")
print("\nGuide category docs (will NOT appear in attraction selector):")
for row in cursor.fetchall():
    print(f"  - {row[0]}")
conn.close()

print("\n" + "=" * 60)
print("2. GET /attractions 端点验证")
print("=" * 60)
try:
    r = urllib.request.urlopen(
        "http://localhost:8081/api/v1/route/attractions"
    )
    data = json.loads(r.read())
    if data.get("code") == 0:
        attractions = data.get("data", [])
        print(f"  Attractions count: {len(attractions)}")
        for a in attractions:
            print(f"  - {a}")
        # Verify no guide docs leaked
        guide_doc_titles = ["小灵山千年历史渊源", "核心文化内涵", "灵山大佛全方位解析",
                           "灵山梵宫艺术殿堂详解", "九龙灌浴与祥符禅寺详解",
                           "五印坛城与曼飞龙塔", "历史文化爱好者深度游路线",
                           "自然风光与亲子游览路线", "实用游览贴士"]
        leaked = [t for t in guide_doc_titles if t in attractions]
        if leaked:
            print(f"\n  ❌ LEAKED guide docs in attractions: {leaked}")
        else:
            print(f"\n  ✅ No guide docs leaked into attractions list!")
    else:
        print(f"  API error: {data}")
except Exception as e:
    print(f"  API call failed: {e}")

print("\n" + "=" * 60)
print("3. ChromaDB metadata 验证")
print("=" * 60)
try:
    CHROMA_BASE = "http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database"
    r = urllib.request.urlopen(CHROMA_BASE + "/collections")
    cols = json.loads(r.read())
    for c in cols:
        if c["name"] == "travel_kb_v2":
            coll_id = c["id"]
            break
    # Query all
    r2 = urllib.request.urlopen(urllib.request.Request(
        CHROMA_BASE + f"/collections/{coll_id}/get",
        data=json.dumps({"ids": [], "include": ["metadatas"]}).encode(),
        method="POST",
        headers={"Content-Type": "application/json"}
    ))
    result = json.loads(r2.read())
    metas = result.get("metadatas", [])
    categories = {}
    for m in metas:
        cat = m.get("category", "unknown") if m else "unknown"
        categories[cat] = categories.get(cat, 0) + 1
    for cat, cnt in sorted(categories.items()):
        print(f"  {cat}: {cnt} docs")
except Exception as e:
    print(f"  Chroma check failed: {e}")
