"""通过 Chroma v2 REST API 灌入知识库数据 —— 从 docs/knowledge/*.txt 读取
   支持增量添加：获取或创建 collection，然后批量添加文档。
"""
import json
import os
import urllib.request
import urllib.error

CHROMA_BASE = "http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database"
DASHSCOPE_KEY = "sk-ws-H.EMYXXED.Hqom.MEQCIBOsN2arr55ch1z5PWMRxkhfuWZG3T2wbvX4zqRybsBnAiBI_2jMX6tiXHzR0-38MjOmwCfjmten6U9BSYbMlQMb0A"
COLLECTION = "travel_kb_v2"

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

# 脚本所在目录的上上级是项目根目录
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KNOWLEDGE_DIR = os.path.join(ROOT, "docs", "knowledge")


def req(method, path, body=None):
    url = CHROMA_BASE + path
    data = json.dumps(body).encode() if body else None
    try:
        r = urllib.request.urlopen(
            urllib.request.Request(url, data=data, method=method,
                                   headers={"Content-Type": "application/json"}))
        return json.loads(r.read())
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}: {e.read().decode()}")
        return None


def get_embedding(text):
    body = {"model": "text-embedding-v1", "input": text}
    req_data = json.dumps(body).encode()
    r = urllib.request.urlopen(
        urllib.request.Request(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings",
            data=req_data,
            headers={
                "Authorization": f"Bearer {DASHSCOPE_KEY}",
                "Content-Type": "application/json",
            },
        )
    )
    return json.loads(r.read())["data"][0]["embedding"]


def get_or_create_collection():
    """获取已有 collection ID，不存在则创建"""
    all_coll = req("GET", "/collections")
    if all_coll:
        for c in all_coll:
            if c.get("name") == COLLECTION:
                print(f"Found existing collection: {COLLECTION} (id={c['id']})")
                return c["id"]

    # 不存在则创建
    print(f"Creating collection {COLLECTION}...")
    # 先尝试不带空间参数的创建
    res = req("POST", "/collections", {"name": COLLECTION, "metadata": {"hnsw:space": "cosine"}})
    if res and res.get("id"):
        print(f"Collection created: {COLLECTION} (id={res['id']})")
        return res["id"]

    # 如果已存在，再获取一次
    all_coll = req("GET", "/collections")
    if all_coll:
        for c in all_coll:
            if c.get("name") == COLLECTION:
                return c["id"]

    print(f"ERROR: failed to get or create collection {COLLECTION}")
    return None


def load_documents():
    """从 docs/knowledge 目录加载所有 txt 文件，返回 [(title, category, content), ...]"""
    docs = []
    if not os.path.isdir(KNOWLEDGE_DIR):
        print(f"ERROR: knowledge dir not found: {KNOWLEDGE_DIR}")
        return docs

    for fname in sorted(os.listdir(KNOWLEDGE_DIR)):
        if not fname.endswith(".txt"):
            continue
        fpath = os.path.join(KNOWLEDGE_DIR, fname)
        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read().strip()

        # 从第一行提取标题
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
            category = "guide"
        else:
            category = "scenic"

        docs.append((title, category, content))
        print(f"  Loaded: [{category}] {title} ({len(content)} chars)")

    return docs


def main():
    # 先删除旧 collection（如果存在），以干净重建
    all_coll = req("GET", "/collections")
    if all_coll:
        for c in all_coll:
            if c.get("name") == COLLECTION:
                print(f"Deleting old collection {COLLECTION}...")
                # Chroma v2: 按名称删除（DELETE /collections/{name}）
                try:
                    url = CHROMA_BASE + "/collections/" + COLLECTION
                    r = urllib.request.urlopen(urllib.request.Request(url, method="DELETE"))
                    print(f"  Deleted: {r.read().decode()}")
                except urllib.error.HTTPError as e:
                    print(f"  Delete error (may be ok): {e.code}")
                break

    # 获取或创建 collection
    coll_id = get_or_create_collection()
    if not coll_id:
        return

    documents = load_documents()
    if not documents:
        print("No documents found.")
        return
    print(f"\nTotal: {len(documents)} documents to process\n")

    # 生成 embeddings
    ids = []
    contents = []
    metadatas = []
    embeddings = []
    for i, (title, category, content) in enumerate(documents):
        print(f"Embedding {i+1}/{len(documents)}: {title}")
        ids.append(f"doc_{i:03d}")
        contents.append(content)
        metadatas.append({"title": title, "category": category, "source": "knowledge_txt"})
        embeddings.append(get_embedding(content))

    # 批量添加（分批发送，每批5条）
    batch_size = 5
    total_ok = 0
    print(f"\nAdding {len(documents)} documents to Chroma collection '{COLLECTION}' in batches of {batch_size}...")
    for start in range(0, len(documents), batch_size):
        end = min(start + batch_size, len(documents))
        batch = {
            "ids": ids[start:end],
            "documents": contents[start:end],
            "metadatas": metadatas[start:end],
            "embeddings": embeddings[start:end],
        }
        print(f"  Batch {start//batch_size + 1}: docs {start}-{end-1}...")
        res = req("POST", f"/collections/{coll_id}/add", batch)
        if res is not None:
            total_ok += (end - start)
            print(f"    -> OK ({total_ok}/{len(documents)})")
        else:
            print(f"    -> FAILED at batch starting at index {start}")
    if total_ok == len(documents):
        print(f"\nDone! All {len(documents)} documents seeded successfully.")
    else:
        print(f"\nPartially done. {total_ok}/{len(documents)} documents added.")


if __name__ == "__main__":
    main()
