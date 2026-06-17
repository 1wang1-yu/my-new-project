"""
ChromaDB 知识库初始化脚本
用法: pip install chromadb && python seed_chroma.py
"""
import chromadb
from chromadb.utils import embedding_functions

# DashScope embedding (阿里云百炼)
DASHSCOPE_API_KEY = "YOUR_DASHSCOPE_API_KEY"

DOCUMENTS = [
    ("灵山胜境概况", "灵山胜境位于江苏省无锡市，是国家5A级旅游景区，以佛教文化为主题..."),
    ("灵山大佛", "灵山大佛高达88米，是迄今为止我国最高的巨型佛像之一..."),
    ("梵宫", "梵宫是灵山胜境的核心建筑之一，外观融合了佛教石窟艺术与传统宫殿建筑风格..."),
    ("九龙灌浴", "九龙灌浴是灵山胜境的大型动态音乐喷泉表演..."),
    ("五印坛城", "五印坛城是藏传佛教风格的建筑群，仿照西藏布达拉宫而建..."),
    ("游览路线推荐", "推荐游览路线：景区入口→九龙灌浴→灵山大佛→梵宫→五印坛城..."),
    ("美食推荐", "灵山胜境及周边美食：梵宫素斋、太湖三白..."),
    ("交通指南", "前往灵山胜境交通方式：自驾、公交88路、地铁+专线大巴..."),
    ("门票与开放时间", "灵山胜境开放时间：夏季7:30-17:30，冬季8:00-17:00..."),
    ("拍照打卡点", "灵山胜境最佳拍照打卡位置：大佛正面广场、梵宫东侧长廊..."),
]

ef = embedding_functions.OpenAIEmbeddingFunction(
    api_key=DASHSCOPE_API_KEY,
    api_base="https://dashscope.aliyuncs.com/compatible-mode/v1",
    model_name="text-embedding-v1"
)

client = chromadb.PersistentClient(path="./chroma_data")
collection = client.get_or_create_collection(
    name="travel_kb",
    embedding_function=ef
)

ids = [f"doc_{i}" for i in range(len(DOCUMENTS))]
titles = [d[0] for d in DOCUMENTS]
contents = [d[1] for d in DOCUMENTS]

collection.upsert(
    ids=ids,
    documents=contents,
    metadatas=[{"title": t} for t in titles]
)

print(f"ChromaDB 初始化完成，共 {len(DOCUMENTS)} 条文档")
