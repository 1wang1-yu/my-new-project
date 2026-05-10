<template>
  <div class="avatar">
    <h2>数字人配置</h2>
    <a-card style="margin-bottom: 16px;">
      <a-form :model="avatarForm">
        <a-form-item label="数字人名称">
          <a-input v-model:value="avatarForm.name" placeholder="请输入数字人名称" />
        </a-form-item>
        <a-form-item label="头像图片">
          <a-upload
            name="file"
            :show-upload-list="false"
            @change="handleAvatarUpload"
          >
            <a-button>
              <upload-outlined /> 上传头像
            </a-button>
          </a-upload>
          <img v-if="avatarForm.avatarImage" :src="avatarForm.avatarImage" style="width: 100px; height: 100px; margin-top: 16px;" />
        </a-form-item>
        <a-form-item label="语音ID">
          <a-input v-model:value="avatarForm.voiceId" placeholder="请输入语音ID" />
        </a-form-item>
        <a-form-item label="语音速度">
          <a-slider v-model:value="avatarForm.voiceSpeed" :min="0.5" :max="2" :step="0.1" />
          <span style="margin-left: 16px;">{{ avatarForm.voiceSpeed }}</span>
        </a-form-item>
        <a-form-item label="风格描述">
          <a-textarea v-model:value="avatarForm.styleDesc" placeholder="请输入数字人风格描述" rows="4" />
        </a-form-item>
        <a-form-item label="是否默认">
          <a-switch v-model:checked="avatarForm.isDefault" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="saveAvatar">保存配置</a-button>
        </a-form-item>
      </a-form>
    </a-card>
    <a-card>
      <a-table :columns="columns" :data-source="avatarList" :pagination="pagination">
        <template #bodyCell="{ record, column }">
          <template v-if="column.key === 'avatarImage'">
            <img :src="record.avatarImage" style="width: 50px; height: 50px;" />
          </template>
          <template v-if="column.key === 'isDefault'">
            <a-tag v-if="record.isDefault" color="green">是</a-tag>
            <span v-else>否</span>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'red'">
              {{ record.status === 1 ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'actions'">
            <a-button size="small" @click="editAvatar(record)">编辑</a-button>
            <a-button size="small" danger @click="deleteAvatar(record.id)" style="margin-left: 8px;">
              {{ record.status === 1 ? '禁用' : '启用' }}
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { UploadOutlined } from '@ant-design/icons-vue'

const avatarForm = reactive({
  name: '',
  avatarImage: '',
  voiceId: '',
  voiceSpeed: 1.0,
  styleDesc: '',
  isDefault: false
})

const avatarList = ref([
  {
    id: 1,
    name: '导游小助手',
    avatarImage: 'https://via.placeholder.com/100',
    voiceId: 'voice1',
    voiceSpeed: 1.0,
    styleDesc: '热情友好，专业讲解',
    isDefault: true,
    status: 1
  },
  {
    id: 2,
    name: '历史讲解员',
    avatarImage: 'https://via.placeholder.com/100',
    voiceId: 'voice2',
    voiceSpeed: 0.9,
    styleDesc: '沉稳专业，知识渊博',
    isDefault: false,
    status: 1
  },
  {
    id: 3,
    name: '旅游顾问',
    avatarImage: 'https://via.placeholder.com/100',
    voiceId: 'voice3',
    voiceSpeed: 1.1,
    styleDesc: '活泼开朗，善于推荐',
    isDefault: false,
    status: 0
  }
])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 3
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '头像', dataIndex: 'avatarImage', key: 'avatarImage' },
  { title: '语音ID', dataIndex: 'voiceId', key: 'voiceId' },
  { title: '语音速度', dataIndex: 'voiceSpeed', key: 'voiceSpeed' },
  { title: '是否默认', dataIndex: 'isDefault', key: 'isDefault' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '操作', key: 'actions' }
]

const handleAvatarUpload = (info) => {
  if (info.file.status === 'done') {
    // 上传成功，设置头像URL
    avatarForm.avatarImage = URL.createObjectURL(info.file.originFileObj)
  }
}

const saveAvatar = () => {
  console.log('保存数字人配置:', avatarForm)
}

const editAvatar = (record) => {
  console.log('编辑数字人:', record)
  // 填充表单
  Object.assign(avatarForm, record)
}

const deleteAvatar = (id) => {
  console.log('禁用/启用数字人:', id)
}
</script>

<style scoped>
.avatar {
  padding: 0 16px;
}
</style>