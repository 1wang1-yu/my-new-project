<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>图书管理</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { background: #f0f2f5; font-family: "Microsoft YaHei", sans-serif; padding: 30px 40px; }
        .topbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
        .topbar h1 { font-size: 24px; color: #333; }
        .btn { padding: 8px 20px; border: none; border-radius: 6px; font-size: 14px; cursor: pointer; }
        .btn-primary { background: #409eff; color: #fff; }
        .btn-primary:hover { background: #337ecc; }
        .btn-success { background: #67c23a; color: #fff; }
        .btn-success:hover { background: #529b2e; }
        .btn-danger { background: #f56c6c; color: #fff; }
        .btn-danger:hover { background: #e05050; }
        .btn-sm { padding: 4px 12px; font-size: 12px; }
        table { width: 100%; background: #fff; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 16px rgba(0,0,0,0.08); border-collapse: collapse; }
        th, td { padding: 14px 16px; text-align: left; border-bottom: 1px solid #eee; }
        th { background: #fafafa; font-weight: 600; color: #555; font-size: 14px; }
        td { font-size: 14px; color: #333; }
        td input { width: 100%; padding: 6px 8px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 13px; }
        td input:focus { outline: none; border-color: #409eff; }
        .add-row td { background: #f9fbfd; }
        .add-row input { border: 1px dashed #409eff; }
        .msg { text-align: center; padding: 10px; font-size: 14px; display: none; }
        .msg.success { color: #67c23a; }
        .msg.error { color: #f56c6c; }
        .actions { display: flex; gap: 8px; }
    </style>
</head>
<body>
    <div class="topbar">
        <h1>图书信息管理</h1>
        <span>欢迎，<b>${sessionScope.loginUser}</b></span>
    </div>

    <div id="msgBox" class="msg"></div>

    <table>
        <thead>
            <tr>
                <th style="width:60px">ID</th>
                <th>书名</th>
                <th>作者</th>
                <th>出版社</th>
                <th style="width:100px">价格</th>
                <th style="width:140px">操作</th>
            </tr>
        </thead>
        <tbody id="bookTableBody">
            <c:forEach items="${bookList}" var="book">
            <tr data-id="${book.id}">
                <td class="cell-id">${book.id}</td>
                <td class="cell-name">${book.bookName}</td>
                <td class="cell-author">${book.author}</td>
                <td class="cell-publish">${book.publish}</td>
                <td class="cell-price">${book.price}</td>
                <td>
                    <div class="actions">
                        <button class="btn btn-primary btn-sm" onclick="startEdit(this)">编辑</button>
                        <button class="btn btn-danger btn-sm" onclick="deleteBook(this)">删除</button>
                    </div>
                </td>
            </tr>
            </c:forEach>
            <!-- 新增行 -->
            <tr class="add-row">
                <td>#</td>
                <td><input id="addName" placeholder="书名" /></td>
                <td><input id="addAuthor" placeholder="作者" /></td>
                <td><input id="addPublish" placeholder="出版社" /></td>
                <td><input id="addPrice" placeholder="价格" type="number" step="0.01" /></td>
                <td><button class="btn btn-success btn-sm" onclick="addBook()">添加</button></td>
            </tr>
        </tbody>
    </table>

<script>
var ctx = '${pageContext.request.contextPath}';

function showMsg(text, isOk) {
    var box = document.getElementById('msgBox');
    box.textContent = text;
    box.className = 'msg ' + (isOk ? 'success' : 'error');
    box.style.display = 'block';
    setTimeout(function () { box.style.display = 'none'; }, 3000);
}

/* ========== 编辑功能 ========== */
function startEdit(btn) {
    var tr = btn.closest('tr');
    var cells = tr.querySelectorAll('td.cell-name, td.cell-author, td.cell-publish, td.cell-price');
    var original = [];
    for (var i = 0; i < cells.length; i++) {
        original.push(cells[i].textContent);
        var field = ['bookName', 'author', 'publish', 'price'][i];
        var val = cells[i].textContent;
        var inputHtml = '<input id="edit_' + field + '" value="' + escapeHtml(val) + '" />';
        if (field === 'price') {
            inputHtml = '<input id="edit_' + field + '" value="' + val + '" type="number" step="0.01" />';
        }
        cells[i].innerHTML = inputHtml;
    }
    var actionTd = tr.querySelector('td:last-child .actions');
    actionTd.innerHTML = '<button class="btn btn-success btn-sm" onclick="saveEdit(this)">保存</button>' +
                         '<button class="btn btn-sm" style="background:#ccc;color:#333" onclick="cancelEdit(this)">取消</button>';
}

function escapeHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/'/g, '&#39;');
}

function saveEdit(btn) {
    var tr = btn.closest('tr');
    var id = tr.getAttribute('data-id');
    var bookName = document.getElementById('edit_bookName').value.trim();
    var author   = document.getElementById('edit_author').value.trim();
    var publish  = document.getElementById('edit_publish').value.trim();
    var price    = parseFloat(document.getElementById('edit_price').value);

    if (!bookName || !author) {
        showMsg('书名和作者不能为空', false);
        return;
    }

    fetch(ctx + '/books/' + id, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ bookName: bookName, author: author, publish: publish, price: price })
    }).then(function (r) { return r.json(); })
      .then(function (res) {
          if (res.success) {
              tr.querySelector('.cell-name').textContent = bookName;
              tr.querySelector('.cell-author').textContent = author;
              tr.querySelector('.cell-publish').textContent = publish;
              tr.querySelector('.cell-price').textContent = price.toFixed(2);
              var actionTd = tr.querySelector('td:last-child .actions');
              actionTd.innerHTML = '<button class="btn btn-primary btn-sm" onclick="startEdit(this)">编辑</button>' +
                                   '<button class="btn btn-danger btn-sm" onclick="deleteBook(this)">删除</button>';
              showMsg('修改成功', true);
          } else {
              showMsg(res.message || '修改失败', false);
          }
      }).catch(function () { showMsg('请求失败', false); });
}

function cancelEdit(btn) {
    var tr = btn.closest('tr');
    var cells = tr.querySelectorAll('td.cell-name, td.cell-author, td.cell-publish, td.cell-price');
    var fieldInputs = ['edit_bookName', 'edit_author', 'edit_publish', 'edit_price'];
    for (var i = 0; i < cells.length; i++) {
        var inp = document.getElementById(fieldInputs[i]);
        cells[i].textContent = inp ? inp.value : cells[i].textContent;
    }
    var actionTd = tr.querySelector('td:last-child .actions');
    actionTd.innerHTML = '<button class="btn btn-primary btn-sm" onclick="startEdit(this)">编辑</button>' +
                         '<button class="btn btn-danger btn-sm" onclick="deleteBook(this)">删除</button>';
}

/* ========== 删除功能 ========== */
function deleteBook(btn) {
    if (!confirm('确定删除该书吗？')) return;
    var tr = btn.closest('tr');
    var id = tr.getAttribute('data-id');

    fetch(ctx + '/books/' + id, { method: 'DELETE' })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.success) {
                tr.remove();
                showMsg('删除成功', true);
            } else {
                showMsg(res.message || '删除失败', false);
            }
        }).catch(function () { showMsg('请求失败', false); });
}

/* ========== 新增功能 ========== */
function addBook() {
    var bookName = document.getElementById('addName').value.trim();
    var author   = document.getElementById('addAuthor').value.trim();
    var publish  = document.getElementById('addPublish').value.trim();
    var price    = parseFloat(document.getElementById('addPrice').value);

    if (!bookName || !author) {
        showMsg('书名和作者不能为空', false);
        return;
    }

    fetch(ctx + '/books', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ bookName: bookName, author: author, publish: publish, price: price })
    }).then(function (r) { return r.json(); })
      .then(function (res) {
          if (res.success) {
              location.reload();
          } else {
              showMsg(res.message || '添加失败', false);
          }
      }).catch(function () { showMsg('请求失败', false); });
}
</script>
</body>
</html>
