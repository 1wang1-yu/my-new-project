"""Final verification: test that the new content is searchable via semantic query"""
import json
import urllib.request

CHROMA_BASE = "http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database"
DASHSCOPE_KEY = "sk-ws-H.EMYXXED.Hqom.MEQCIBOsN2arr55ch1z5PWMRxkhfuWZG3T2wbvX4zqRybsBnAiBI_2jMX6tiXHzR0-38MjOmwCfjmten6U9BSYbMlQMb0A"
COLLECTION_ID = "7d869031-ef67-4f7d-b93f-5a5096943b43"


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


def query_chroma(query_text, n=3):
    emb = get_embedding(query_text)
    r = urllib.request.urlopen(urllib.request.Request(
        CHROMA_BASE + f"/collections/{COLLECTION_ID}/query",
        data=json.dumps({"query_embeddings": [emb], "n_results": n}).encode(),
        method="POST",
        headers={"Content-Type": "application/json"},
    ))
    result = json.loads(r.read())

    results = []
    for doc, meta in zip(result.get("documents", [[]])[0], result.get("metadatas", [[]])[0]):
        title = meta.get("title", "(no title)")
        source = meta.get("source", "unknown")
        results.append((title, source, doc[:100]))
    return results


print("=" * 60)
print("TEST 1: 历史渊源相关查询")
print("=" * 60)
results = query_chroma("玄奘法师和小灵山有什么历史渊源？")
for i, (title, source, preview) in enumerate(results):
    print(f"\n  Result {i+1}: [{title}] (source={source})")
    print(f"  Preview: {preview}...")

print("\n" + "=" * 60)
print("TEST 2: 游览路线推荐")
print("=" * 60)
results = query_chroma("带孩子去灵山胜境玩，有什么推荐的游览路线？")
for i, (title, source, preview) in enumerate(results):
    print(f"\n  Result {i+1}: [{title}] (source={source})")
    print(f"  Preview: {preview}...")

print("\n" + "=" * 60)
print("TEST 3: 门票价格")
print("=" * 60)
results = query_chroma("灵山胜境门票多少钱？有优惠吗？")
for i, (title, source, preview) in enumerate(results):
    print(f"\n  Result {i+1}: [{title}] (source={source})")
    print(f"  Preview: {preview}...")

print("\n" + "=" * 60)
print("TEST 4: 灵山梵宫艺术特色")
print("=" * 60)
results = query_chroma("灵山梵宫的穹顶天象图和华藏世界琉璃有什么特色？")
for i, (title, source, preview) in enumerate(results):
    print(f"\n  Result {i+1}: [{title}] (source={source})")
    print(f"  Preview: {preview}...")
