"""Debug SQL Server encoding issues"""
import pyodbc

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 18 for SQL Server};'
    'SERVER=localhost,1433;DATABASE=smart_guide_db;'
    'UID=sa;PWD=Root@123456;Encrypt=no;TrustServerCertificate=yes;'
)
cursor = conn.cursor()

# 1. Check the column types
cursor.execute("""
    SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_SET_NAME
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 't_knowledge_doc'
""")
print("Column types:")
for row in cursor.fetchall():
    print(f"  {row[0]}: {row[1]} (charset={row[2]})")

# 2. Try inserting and reading back test data
print("\n--- Test insert/read ---")
test_title = "灵山胜境测试"
cursor.execute(
    "INSERT INTO t_knowledge_doc (title, category, content, chunk_count, index_status, operator_id, create_time, update_time) "
    "VALUES (?, ?, ?, 0, 0, 999, '2024-01-01', '2024-01-01')",
    (test_title, "test", "test content for 灵山胜境")
)
conn.commit()

cursor.execute("SELECT title, content FROM t_knowledge_doc WHERE operator_id = 999")
row = cursor.fetchone()
if row:
    print(f"Title read back: [{row[0]}]")
    print(f"Title hex: {row[0].encode('utf-8').hex() if isinstance(row[0], str) else 'NOT STR'}")
    print(f"Content read back: [{row[1]}]")

# Cleanup
cursor.execute("DELETE FROM t_knowledge_doc WHERE operator_id = 999")
conn.commit()
conn.close()
