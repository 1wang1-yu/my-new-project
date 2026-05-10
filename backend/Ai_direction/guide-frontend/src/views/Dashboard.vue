<template>
  <div class="dashboard">
    <h2>数据大屏</h2>
    <a-row :gutter="[16, 16]">
      <a-col :span="6">
        <a-card hoverable class="stat-card">
          <div class="stat-title">今日服务人次</div>
          <div class="stat-value">2,847</div>
          <div class="stat-desc"><a-tag color="green">↑ 12% 较昨日</a-tag></div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card hoverable class="stat-card">
          <div class="stat-title">平均满意度</div>
          <div class="stat-value">4.8/5</div>
          <div class="stat-desc"><a-tag color="green">↑ 0.2 较上周</a-tag></div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card hoverable class="stat-card">
          <div class="stat-title">问答准确率</div>
          <div class="stat-value">93.2%</div>
          <div class="stat-desc"><a-tag color="green">达标 >90%</a-tag></div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card hoverable class="stat-card">
          <div class="stat-title">平均响应时长</div>
          <div class="stat-value">2.3s</div>
          <div class="stat-desc"><a-tag color="green">优于目标 <5s</a-tag></div>
        </a-card>
      </a-col>
    </a-row>
    <a-row :gutter="[16, 16]" style="margin-top: 16px;">
      <a-col :span="12">
        <a-card title="本周满意度趋势">
          <div ref="satisfactionChart" style="height: 300px;"></div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="游客情感分布">
          <div ref="sentimentChart" style="height: 300px;"></div>
        </a-card>
      </a-col>
    </a-row>
    <a-row :gutter="[16, 16]" style="margin-top: 16px;">
      <a-col :span="12">
        <a-card title="今日热门问答 Top 5">
          <a-list>
            <a-list-item v-for="(item, index) in hotQuestions" :key="index">
              <a-list-item-meta>
                <template #title>
                  <div style="display: flex; justify-content: space-between;">
                    <span>{{ index + 1 }}. {{ item.question }}</span>
                    <span>{{ item.count }}次</span>
                  </div>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </a-list>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="知识库状态">
          <a-list>
            <a-list-item v-for="item in knowledgeStatus" :key="item.category">
              <a-list-item-meta>
                <template #title>
                  <div style="display: flex; justify-content: space-between;">
                    <span>{{ item.category }}</span>
                    <a-tag color="green">已索引 {{ item.count }}篇</a-tag>
                  </div>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </a-list>
          <div style="margin-top: 16px; padding: 12px; background: #fff3cd; border: 1px solid #ffeaa7; border-radius: 4px;">
            <p style="margin: 0;">上次更新: 2天前 <a href="#" style="margin-left: 8px; color: #856404;">建议检查新增景点资料</a></p>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'

const satisfactionChart = ref(null)
const sentimentChart = ref(null)

const hotQuestions = [
  { question: '断桥的历史典故是什么', count: 347 },
  { question: '附近哪里可以吃饭', count: 289 },
  { question: '推荐一条适合老人的路线', count: 201 },
  { question: '景区开放时间是几点', count: 178 },
  { question: '怎么到雷峰塔', count: 152 }
]

const knowledgeStatus = [
  { category: '景区历史文史资料', count: 234 },
  { category: '常见问答对', count: 89 },
  { category: '景点讲解词', count: 56 }
]

onMounted(() => {
  // 初始化满意度趋势图表
  if (satisfactionChart.value) {
    const chart = echarts.init(satisfactionChart.value)
    const option = {
      tooltip: {
        trigger: 'axis'
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
      },
      yAxis: {
        type: 'value',
        min: 4.0,
        max: 5.0
      },
      series: [
        {
          data: [4.5, 4.6, 4.5, 4.6, 4.7, 4.8, 4.8],
          type: 'bar',
          itemStyle: {
            color: function(params) {
              return params.dataIndex >= 5 ? '#52c41a' : '#1890ff'
            }
          }
        }
      ]
    }
    chart.setOption(option)
  }

  // 初始化情感分布图表
  if (sentimentChart.value) {
    const chart = echarts.init(sentimentChart.value)
    const option = {
      tooltip: {
        trigger: 'item'
      },
      legend: {
        orient: 'vertical',
        right: 10,
        top: 'center'
      },
      series: [
        {
          name: '情感分布',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 10,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            show: false,
            position: 'center'
          },
          emphasis: {
            label: {
              show: true,
              fontSize: '18',
              fontWeight: 'bold'
            }
          },
          labelLine: {
            show: false
          },
          data: [
            { value: 72, name: '正面情绪', itemStyle: { color: '#52c41a' } },
            { value: 21, name: '中性', itemStyle: { color: '#1890ff' } },
            { value: 7, name: '负面情绪', itemStyle: { color: '#ff4d4f' } }
          ]
        }
      ]
    }
    chart.setOption(option)
  }
})
</script>

<style scoped>
.dashboard {
  padding: 0 16px;
}

.stat-card {
  text-align: center;
}

.stat-title {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 8px;
}

.stat-desc {
  font-size: 12px;
}
</style>