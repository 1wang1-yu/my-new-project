<template>
  <div class="data">
    <h2>数据分析</h2>
    <a-card style="margin-bottom: 16px;">
      <a-form :model="dateForm" layout="inline">
        <a-form-item label="开始日期">
          <a-date-picker v-model:value="dateForm.startDate" />
        </a-form-item>
        <a-form-item label="结束日期">
          <a-date-picker v-model:value="dateForm.endDate" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="fetchReport">查询报告</a-button>
        </a-form-item>
      </a-form>
    </a-card>
    <a-row :gutter="[16, 16]">
      <a-col :span="8">
        <a-card hoverable class="stat-card">
          <div class="stat-title">正面情绪占比</div>
          <div class="stat-value">{{ sentimentReport.positiveRate }}%</div>
          <div class="stat-desc"><a-tag color="green">良好</a-tag></div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card hoverable class="stat-card">
          <div class="stat-title">负面情绪占比</div>
          <div class="stat-value">{{ 100 - sentimentReport.positiveRate }}%</div>
          <div class="stat-desc"><a-tag color="blue">正常</a-tag></div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card hoverable class="stat-card">
          <div class="stat-title">平均满意度</div>
          <div class="stat-value">{{ sentimentReport.avgSatisfaction }}/5</div>
          <div class="stat-desc"><a-tag color="green">优秀</a-tag></div>
        </a-card>
      </a-col>
    </a-row>
    <a-row :gutter="[16, 16]" style="margin-top: 16px;">
      <a-col :span="12">
        <a-card title="情感趋势">
          <div ref="trendChart" style="height: 300px;"></div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="负面关键词">
          <a-list>
            <a-list-item v-for="(item, index) in sentimentReport.negativeKeywords" :key="index">
              <a-list-item-meta>
                <template #title>
                  <div style="display: flex; justify-content: space-between;">
                    <span>{{ item.keyword }}</span>
                    <span>{{ item.count }}次</span>
                  </div>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </a-list>
        </a-card>
      </a-col>
    </a-row>
    <a-row style="margin-top: 16px;">
      <a-col :span="24">
        <a-card title="运营建议">
          <a-list>
            <a-list-item v-for="(item, index) in sentimentReport.suggestions" :key="index">
              <a-list-item-meta>
                <template #title>
                  <span>{{ index + 1 }}. {{ item }}</span>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </a-list>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import * as echarts from 'echarts'

const dateForm = reactive({
  startDate: null,
  endDate: null
})

const trendChart = ref(null)

const sentimentReport = reactive({
  positiveRate: 72,
  avgSatisfaction: 4.8,
  negativeKeywords: [
    { keyword: '排队时间长', count: 128 },
    { keyword: '人太多', count: 96 },
    { keyword: '价格贵', count: 72 },
    { keyword: '标识不清', count: 48 },
    { keyword: '服务态度差', count: 36 }
  ],
  suggestions: [
    '优化景区排队流程，考虑增加快速通道',
    '加强景区容量管理，建议实行分时段预约',
    '优化景区内商品价格，提供更多性价比选择',
    '增加景区标识，提高导航系统准确性',
    '加强员工培训，提高服务质量'
  ],
  trendData: [
    { date: '4月10日', positive: 70, negative: 30 },
    { date: '4月11日', positive: 72, negative: 28 },
    { date: '4月12日', positive: 68, negative: 32 },
    { date: '4月13日', positive: 75, negative: 25 },
    { date: '4月14日', positive: 73, negative: 27 },
    { date: '4月15日', positive: 72, negative: 28 },
    { date: '4月16日', positive: 74, negative: 26 }
  ]
})

const fetchReport = () => {
  console.log('查询报告:', dateForm)
  // 这里可以添加实际的API调用
}

onMounted(() => {
  // 初始化情感趋势图表
  if (trendChart.value) {
    const chart = echarts.init(trendChart.value)
    const option = {
      tooltip: {
        trigger: 'axis'
      },
      legend: {
        data: ['正面情绪', '负面情绪']
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: sentimentReport.trendData.map(item => item.date)
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100
      },
      series: [
        {
          name: '正面情绪',
          type: 'line',
          stack: 'Total',
          areaStyle: {
            color: 'rgba(82, 196, 26, 0.3)'
          },
          lineStyle: {
            color: '#52c41a'
          },
          data: sentimentReport.trendData.map(item => item.positive)
        },
        {
          name: '负面情绪',
          type: 'line',
          stack: 'Total',
          areaStyle: {
            color: 'rgba(255, 77, 79, 0.3)'
          },
          lineStyle: {
            color: '#ff4d4f'
          },
          data: sentimentReport.trendData.map(item => item.negative)
        }
      ]
    }
    chart.setOption(option)
  }
})
</script>

<style scoped>
.data {
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