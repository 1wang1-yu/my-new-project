"""Verify Chinese character encoding in SQL Server"""
import pyodbc

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 18 for SQL Server};'
    'SERVER=localhost,1433;DATABASE=smart_guide_db;'
    'UID=sa;PWD=Root@123456;Encrypt=no;TrustServerCertificate=yes;'
)
cursor = conn.cursor()

# Check count
cursor.execute("SELECT COUNT(*) FROM t_knowledge_doc")
print(f"Total docs: {cursor.fetchone()[0]}")

# Verify UTF-8 title by hex comparison
cursor.execute("SELECT id, title FROM t_knowledge_doc ORDER BY id")
for row in cursor.fetchall():
    title = row[1]
    title_bytes = title.encode('utf-8') if isinstance(title, str) else b''
    # A Chinese character in UTF-8 is 3 bytes (0xE4-0xE9 range)
    has_chinese = any(b >= 0xE4 for b in title_bytes)
    marker = "OK" if has_chinese else "CORRUPTED"
    print(f"  ID={row[0]}: [{marker}] {title} ({len(title_bytes)} bytes)")

conn.close()
