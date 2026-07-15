"""Fix SQL Server column types to support Chinese characters - change varchar to nvarchar"""
import pyodbc

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 18 for SQL Server};'
    'SERVER=localhost,1433;DATABASE=smart_guide_db;'
    'UID=sa;PWD=Root@123456;Encrypt=no;TrustServerCertificate=yes;'
)
cursor = conn.cursor()

# Alter columns from varchar to nvarchar
alter_statements = [
    "ALTER TABLE t_knowledge_doc ALTER COLUMN title NVARCHAR(255)",
    "ALTER TABLE t_knowledge_doc ALTER COLUMN category NVARCHAR(100)",
    "ALTER TABLE t_knowledge_doc ALTER COLUMN file_url NVARCHAR(500)",
]

for stmt in alter_statements:
    try:
        cursor.execute(stmt)
        print(f"OK: {stmt}")
    except Exception as e:
        print(f"ERROR: {stmt}")
        print(f"  {e}")

conn.commit()

# Verify
cursor.execute("""
    SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_SET_NAME
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 't_knowledge_doc'
    ORDER BY ORDINAL_POSITION
""")
print("\nUpdated column types:")
for row in cursor.fetchall():
    print(f"  {row[0]}: {row[1]} (charset={row[2]})")

conn.close()
