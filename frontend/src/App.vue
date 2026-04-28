<template>
  <div id="app">
    <div class="main-content">
      <h1>多步骤调试系统</h1>
      <p>这是一个基于 MVEL2 安全沙箱的多步骤调试系统演示页面。</p>
      
      <div class="demo-section">
        <h3>使用说明</h3>
        <ol>
          <li>点击右下角悬浮按钮打开调试面板</li>
          <li>添加步骤定义（key + expression + description）</li>
          <li>点击"执行"开始运行</li>
          <li>实时查看参数计算结果和日志</li>
        </ol>
      </div>

      <div class="demo-section">
        <h3>示例表达式</h3>
        <table class="demo-table">
          <thead>
            <tr>
              <th>Key</th>
              <th>Expression</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>a</td>
              <td>10</td>
              <td>基础数值</td>
            </tr>
            <tr>
              <td>b</td>
              <td>a * 2</td>
              <td>引用上一步参数</td>
            </tr>
            <tr>
              <td>c</td>
              <td>a + b + 5</td>
              <td>多参数引用</td>
            </tr>
            <tr>
              <td>result</td>
              <td>c > 20 ? 'large' : 'small'</td>
              <td>条件判断</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <DebugPanel ref="debugPanel" />
  </div>
</template>

<script>
import DebugPanel from './components/DebugPanel.vue'

export default {
  name: 'App',
  components: {
    DebugPanel
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: 100vh;
}

#app {
  min-height: 100vh;
  padding: 40px;
}

.main-content {
  max-width: 800px;
  margin: 0 auto;
  background: white;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

h1 {
  color: #333;
  margin-bottom: 20px;
  font-size: 28px;
}

h3 {
  color: #555;
  margin-bottom: 15px;
  font-size: 18px;
}

p {
  color: #666;
  line-height: 1.6;
  margin-bottom: 20px;
}

.demo-section {
  margin-top: 30px;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 8px;
}

.demo-section ol {
  margin-left: 20px;
  color: #555;
  line-height: 2;
}

.demo-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.demo-table th,
.demo-table td {
  padding: 10px;
  text-align: left;
  border-bottom: 1px solid #e0e0e0;
}

.demo-table th {
  background: #e9ecef;
  font-weight: 600;
  color: #333;
}

.demo-table td {
  color: #555;
  font-family: 'Consolas', 'Monaco', monospace;
}
</style>
