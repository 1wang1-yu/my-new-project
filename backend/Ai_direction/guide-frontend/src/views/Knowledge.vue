<template>
  <div class="knowledge">
    <h2>知识库管理</h2>
    <a-card style="margin-bottom: 16px;">
      <a-upload
        name="file"
        :action="'/api/admin/knowledge/upload'"
        :headers="{ 'Content-Type': 'multipart/form-data' }"
        :data="uploadData"
        :show-upload-list="false"
        @change="handleUpload"
      >
        <a-button type="primary">
          <upload-outlined /> 上传知识库文档
        </a-button>
      </a-upload>
      <a-form :model="uploadForm" style="margin-top: 16px;">
        <a-form-item label="标题">
          <a-input v-model:value="uploadForm.title" placeholder="请输入文档标题" />
        </a-form-item>
        <a-form-item label="分类">
          <a-select v-model:value="uploadForm.category" placeholder="请选择分类">
            <a-select-option value="景区历史">景区历史</a-select-option>
            <a-select-option value="景点讲解">景点讲解</a-select-option>
            <a-select-option value="常见问题">常见问题</a-select-option>
            <a-select-option value="其他">其他</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-card>
    <a-card>
      <a-table :columns="columns" :data-source="knowledgeList" :pagination="pagination">
        <template #headerCell="{ column }">
          <template v-if="column.key === 'actions'">
            操作
          </template>
        </template>
        <template #bodyCell="{ record, column }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : record.status === 2 ? 'red' : 'blue'">
              {{ record.status === 1 ? '已索引' : record.status === 2 ? '索引失败' : '待索引' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'actions'">
            <a-button size="small" @click="viewDocument(record)">查看</a-button>
            <a-button size="small" @click="editDocument(record)" style="margin-left: 8px;">编辑</a-button>
            <a-button size="small" danger @click="deleteDocument(record.id)" style="margin-left: 8px;">删除</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { UploadOutlined } from '@ant-design/icons-vue'

const uploadForm = reactive({
  title: '',
  category: ''
})

const uploadData = reactive({
  title: '',
  category: ''
})

const knowledgeList = ref([
  {
    id: 1,
    title: '西湖景区历史',
    category: '景区历史',
    chunkCount: 12,
    status: 1,
    createTime: '2026-04-10 10:00:00'
  },
  {
    id: 2,
    title: '断桥残雪景点讲解',
    category: '景点讲解',
    chunkCount: 8,
    status: 1,
    createTime: '2026-04-09 14:30:00'
  },
  {
    id: 3,
    title: '常见问题解答',
    category: '常见问题',
    chunkCount: 20,
    status: 1,
    createTime: '2026-04-08 09:15:00'
  },
  {
    id: 4,
    title: '雷峰塔游览指南',
    category: '景点讲解',
    chunkCount: 15,
    status: 2,
    createTime: '2026-04-07 16:45:00'
  }
])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 4
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '标题', dataIndex: 'title', key: 'title' },
  { title: '分类', dataIndex: 'category', key: 'category' },
  { title: '分块数', dataIndex: 'chunkCount', key: 'chunkCount' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'actions' }
]

const handleUpload = (info) => {
  if (info.file.status === 'done') {
    if (info.file.response.code === 0) {
      // 上传成功，刷新列表
      console.log('上传成功:', info.file.response)
    } else {
      console.error('上传失败:', info.file.response.message)
    }
  } else if (info.file.status === 'error') {
    console.error('上传失败')
  }
}

const viewDocument = (record) => {
  console.log('查看文档:', record)
}

const editDocument = (record) => {
  console.log('编辑文档:', record)
}

const deleteDocument = (id) => {
  console.log('删除文档:', id)
}
</script>

<style scoped>
.knowledge {
  padding: 0 16px;
}
</style>