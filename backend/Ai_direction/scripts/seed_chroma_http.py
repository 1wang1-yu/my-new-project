"""通过 Chroma v2 REST API 灌入知识库数据 —— 从 docs/knowledge/*.txt 读取"""
import json
import os
import urllib.request
import urllib.error

CHROMA_BASE = "http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database"
DASHSCOPE_KEY = "YOUR_DASHSCOPE_API_KEY"
COLLECTION = "travel_kb"

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


def load_documents():
    """从 docs/knowledge 目录加载所有 txt 文件，返回 [(title, content), ...]"""
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

        docs.append((title, content))
        print(f"  Loaded: {title} ({len(content)} chars)")

    return docs


def main():
    documents = load_documents()
    if not documents:
        print("No documents found.")
        return
    print(f"\nTotal: {len(documents)} documents\n")

    # 删除旧 collection（如果存在）
    all_coll = req("GET", "/collections")
    if all_coll:
        for c in all_coll:
            if c.get("name") == COLLECTION:
                print(f"Deleting old collection {COLLECTION} (id={c['id']})...")
                req("DELETE", f"/collections/{c['id']}")
                break

    # 创建新 collection
    print(f"Creating collection {COLLECTION}...")
    res = req("POST", "/collections", {"name": COLLECTION})
    if not res:
        print("ERROR: failed to create collection")
        return
    coll_id = res["id"]
    print(f"Collection id: {coll_id}")

    # 生成 embeddings
    ids = []
    contents = []
    metadatas = []
    embeddings = []
    for i, (title, content) in enumerate(documents):
        print(f"Embedding {i+1}/{len(documents)}: {title}")
        ids.append(f"doc_{i:03d}")
        contents.append(content)
        metadatas.append({"title": title, "source": "knowledge_txt"})
        embeddings.append(get_embedding(content))

    # 批量添加
    print(f"\nAdding {len(documents)} documents to Chroma...")
    res = req("POST", f"/collections/{coll_id}/add", {
        "ids": ids,
        "documents": contents,
        "metadatas": metadatas,
        "embeddings": embeddings,
    })
    if res:
        print(f"Done! {len(documents)} documents seeded successfully.")
    else:
        print("Failed to add documents.")


if __name__ == "__main__":
    main()
