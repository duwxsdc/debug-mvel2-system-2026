<template>
  <div class="debug-panel-container">
    <div 
      v-if="!isExpanded" 
      class="debug-trigger"
      @click="togglePanel"
    >
      <span class="debug-icon">🔧</span>
      <span class="debug-badge" v-if="snapshot.stepResults?.some(s => s.status === 'RUNNING')">●</span>
    </div>

    <div 
      v-if="isExpanded"
      class="debug-panel"
      :style="panelStyle"
      ref="panelRef"
    >
      <div class="panel-header" @mousedown="startDrag">
        <div class="header-title">
          <span class="status-dot" :class="connectionStatus"></span>
          <span>调试面板</span>
          <span class="session-id">{{ sessionId.substring(0, 8) }}</span>
        </div>
        <div class="header-actions">
          <span class="connection-status" :class="connectionStatus">
            {{ connectionStatus === 'connected' ? '已连接' : connectionStatus === 'reconnecting' ? '重连中' : '未连接' }}
          </span>
          <button class="btn-icon" @click="toggleMinimize" :title="isMinimized ? '展开' : '收起'">
            {{ isMinimized ? '▼' : '▲' }}
          </button>
          <button class="btn-icon" @click="togglePanel" title="关闭">×</button>
        </div>
      </div>

      <div v-if="!isMinimized" class="panel-body">
        <div class="tabs">
          <button 
            v-for="tab in tabs" 
            :key="tab.key"
            :class="['tab', { active: activeTab === tab.key }]"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <div class="tab-content">
          <div v-if="activeTab === 'steps'" class="steps-tab">
            <div class="step-editor">
              <div class="step-input-row">
                <input 
                  v-model="newStep.key" 
                  placeholder="Key" 
                  class="input-key"
                  @keyup.enter="addStep"
                />
                <input 
                  v-model="newStep.expression" 
                  placeholder="Expression (e.g. a * 2)" 
                  class="input-expr"
                  @keyup.enter="addStep"
                />
                <input 
                  v-model="newStep.desc" 
                  placeholder="Description" 
                  class="input-desc"
                  @keyup.enter="addStep"
                />
                <button class="btn-add" @click="addStep" title="Add Step">+</button>
              </div>
            </div>

            <div class="steps-list">
              <div 
                v-for="(step, index) in steps" 
                :key="index"
                :class="['step-item', { 
                  active: index === snapshot.currentStepIndex,
                  success: getStepStatus(index) === 'SUCCESS',
                  error: getStepStatus(index) === 'ERROR',
                  running: getStepStatus(index) === 'RUNNING'
                }]"
              >
                <div class="step-header">
                  <span class="step-index">{{ index + 1 }}</span>
                  <span class="step-key">{{ step.key }}</span>
                  <span class="step-status" :class="getStepStatus(index)?.toLowerCase()">
                    {{ getStatusText(getStepStatus(index)) }}
                  </span>
                  <button class="btn-remove" @click="removeStep(index)" title="Remove">×</button>
                </div>
                <div class="step-expression">{{ step.expression }}</div>
                <div v-if="step.desc" class="step-desc">{{ step.desc }}</div>
                <div v-if="getStepResult(index) !== undefined" class="step-result">
                  <span class="result-label">Result:</span>
                  <span class="result-value">{{ formatValue(getStepResult(index)) }}</span>
                  <span class="result-time">{{ getStepTime(index) }}ms</span>
                </div>
                <div v-if="getStepError(index)" class="step-error">
                  {{ getStepError(index) }}
                </div>
              </div>
              <div v-if="steps.length === 0" class="empty-state">
                No steps defined. Add steps above to begin debugging.
              </div>
            </div>

            <div class="action-buttons">
              <button class="btn btn-primary" @click="executeAll" :disabled="isRunning || !isConnected">
                ▶ Execute All
              </button>
              <button class="btn btn-secondary" @click="executeStep" :disabled="isRunning || !isConnected">
                ⏭ Step
              </button>
              <button class="btn btn-warning" @click="handlePause" :disabled="!isRunning">
                ⏸ Pause
              </button>
              <button class="btn btn-secondary" @click="handleReset" :disabled="!isConnected">
                ↺ Reset
              </button>
              <button class="btn btn-danger" @click="handleTerminate" :disabled="!isRunning">
                ⏹ Terminate
              </button>
            </div>
          </div>

          <div v-if="activeTab === 'params'" class="params-tab">
            <div class="param-input">
              <input v-model="newParam.key" placeholder="Key" class="input-key" />
              <input v-model="newParam.value" placeholder="Value (JSON supported)" class="input-value" />
              <button class="btn btn-add" @click="updateParam" :disabled="!isConnected">Update</button>
            </div>
            <div class="params-list">
              <div v-for="(value, key) in snapshot.context" :key="key" class="param-item">
                <span class="param-key">{{ key }}</span>
                <span class="param-value">{{ formatValue(value) }}</span>
                <span class="param-type">{{ getType(value) }}</span>
                <button class="btn-remove-param" @click="removeParam(key)" title="Remove">×</button>
              </div>
              <div v-if="!snapshot.context || Object.keys(snapshot.context).length === 0" class="empty-state">
                No parameters yet. Add parameters or execute steps.
              </div>
            </div>
          </div>

          <div v-if="activeTab === 'logs'" class="logs-tab">
            <div class="logs-header">
              <span>Execution Logs</span>
              <button class="btn-clear" @click="clearLogs" :disabled="!isConnected">Clear</button>
            </div>
            <div class="logs-list" ref="logsRef">
              <div 
                v-for="(log, index) in snapshot.logs" 
                :key="index"
                :class="['log-item', log.level?.toLowerCase()]"
              >
                <span class="log-time">{{ formatTime(log.timestamp) }}</span>
                <span class="log-level">{{ log.level }}</span>
                <span class="log-message">{{ log.message }}</span>
              </div>
              <div v-if="!snapshot.logs || snapshot.logs.length === 0" class="empty-state">
                No logs yet.
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="isMinimized" class="panel-minimized">
        <span class="minimized-status" :class="statusClass">
          {{ snapshot.status }} | Step {{ snapshot.currentStepIndex }}/{{ snapshot.totalSteps || steps.length }}
        </span>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { createDebugWebSocket } from '../utils/websocket'

export default {
  name: 'DebugPanel',
  setup() {
    const sessionId = ref('session-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9))
    const isExpanded = ref(false)
    const isMinimized = ref(false)
    const activeTab = ref('steps')
    const ws = ref(null)
    const panelRef = ref(null)
    const logsRef = ref(null)
    const connectionStatus = ref('disconnected')

    const steps = ref([
      { key: 'a', expression: '10', desc: 'Base value' },
      { key: 'b', expression: 'a * 2', desc: 'Reference parameter' },
      { key: 'c', expression: 'a + b + 5', desc: 'Multi-parameter reference' },
      { key: 'result', expression: "c > 20 ? 'large' : 'small'", desc: 'Conditional' }
    ])

    const newStep = reactive({ key: '', expression: '', desc: '' })
    const newParam = reactive({ key: '', value: '' })

    const snapshot = reactive({
      sessionId: '',
      status: 'IDLE',
      currentStepIndex: 0,
      totalSteps: 0,
      stepResults: [],
      context: {},
      logs: [],
      lastUpdateTime: null
    })

    const dragState = reactive({
      isDragging: false,
      startX: 0,
      startY: 0,
      offsetX: 0,
      offsetY: 0
    })

    const tabs = [
      { key: 'steps', label: 'Steps' },
      { key: 'params', label: 'Params' },
      { key: 'logs', label: 'Logs' }
    ]

    const isRunning = computed(() => snapshot.status === 'RUNNING')
    const isConnected = computed(() => connectionStatus.value === 'connected')
    
    const statusClass = computed(() => {
      switch (snapshot.status) {
        case 'RUNNING': return 'running'
        case 'COMPLETED': return 'success'
        case 'ERROR': return 'error'
        case 'PAUSED': return 'paused'
        default: return 'idle'
      }
    })

    const panelStyle = computed(() => ({
      left: dragState.offsetX ? `${dragState.offsetX}px` : 'auto',
      top: dragState.offsetY ? `${dragState.offsetY}px` : 'auto',
      right: dragState.offsetX ? 'auto' : '20px',
      bottom: dragState.offsetY ? 'auto' : '20px'
    }))

    const togglePanel = () => {
      isExpanded.value = !isExpanded.value
      if (isExpanded.value && !ws.value) {
        connectWebSocket()
      }
    }

    const toggleMinimize = () => {
      isMinimized.value = !isMinimized.value
    }

    const connectWebSocket = () => {
      connectionStatus.value = 'disconnected'
      ws.value = createDebugWebSocket(sessionId.value)
      
      ws.value.on('onSnapshot', (data) => {
        Object.assign(snapshot, data)
        scrollToBottom()
      })

      ws.value.on('onConnected', (data) => {
        connectionStatus.value = 'connected'
        console.log('WebSocket connected:', data)
      })

      ws.value.on('onOpen', () => {
        connectionStatus.value = 'connected'
      })

      ws.value.on('onClose', () => {
        connectionStatus.value = 'disconnected'
      })

      ws.value.on('onReconnect', (attempt, max) => {
        connectionStatus.value = 'reconnecting'
        console.log(`Reconnecting: ${attempt}/${max}`)
      })

      ws.value.on('onError', (error) => {
        connectionStatus.value = 'disconnected'
        console.error('WebSocket error:', error)
      })

      ws.value.connect()
    }

    const addStep = () => {
      if (newStep.key && newStep.expression) {
        steps.value.push({
          key: newStep.key.trim(),
          expression: newStep.expression.trim(),
          desc: newStep.desc.trim()
        })
        newStep.key = ''
        newStep.expression = ''
        newStep.desc = ''
      }
    }

    const removeStep = (index) => {
      steps.value.splice(index, 1)
    }

    const getStepStatus = (index) => {
      return snapshot.stepResults?.[index]?.status
    }

    const getStepResult = (index) => {
      return snapshot.stepResults?.[index]?.result
    }

    const getStepError = (index) => {
      return snapshot.stepResults?.[index]?.errorMessage
    }

    const getStepTime = (index) => {
      return snapshot.stepResults?.[index]?.executionTimeMs || 0
    }

    const executeAll = () => {
      if (ws.value) {
        ws.value.start(sessionId.value, steps.value)
      }
    }

    const executeStep = () => {
      if (ws.value) {
        ws.value.step(sessionId.value, snapshot.currentStepIndex)
      }
    }

    const handlePause = () => {
      if (ws.value) {
        ws.value.pause(sessionId.value)
      }
    }

    const handleReset = () => {
      if (ws.value) {
        ws.value.reset(sessionId.value)
      }
    }

    const handleTerminate = () => {
      if (ws.value) {
        ws.value.terminate(sessionId.value)
      }
    }

    const updateParam = () => {
      if (ws.value && newParam.key) {
        let value = newParam.value
        try {
          value = JSON.parse(newParam.value)
        } catch (e) {
          // keep as string
        }
        ws.value.updateParam(sessionId.value, newParam.key, value)
        newParam.key = ''
        newParam.value = ''
      }
    }

    const removeParam = (key) => {
      if (ws.value) {
        ws.value.updateParam(sessionId.value, key, null)
      }
    }

    const clearLogs = () => {
      if (ws.value) {
        ws.value.clearLogs(sessionId.value)
      }
    }

    const startDrag = (e) => {
      if (e.target.closest('.header-actions')) return
      
      dragState.isDragging = true
      dragState.startX = e.clientX
      dragState.startY = e.clientY

      const rect = panelRef.value.getBoundingClientRect()
      dragState.offsetX = rect.left
      dragState.offsetY = rect.top

      document.addEventListener('mousemove', onDrag)
      document.addEventListener('mouseup', stopDrag)
    }

    const onDrag = (e) => {
      if (!dragState.isDragging) return

      const deltaX = e.clientX - dragState.startX
      const deltaY = e.clientY - dragState.startY

      dragState.offsetX = Math.max(0, Math.min(window.innerWidth - 400, dragState.offsetX + deltaX))
      dragState.offsetY = Math.max(0, Math.min(window.innerHeight - 200, dragState.offsetY + deltaY))

      dragState.startX = e.clientX
      dragState.startY = e.clientY
    }

    const stopDrag = () => {
      dragState.isDragging = false
      document.removeEventListener('mousemove', onDrag)
      document.removeEventListener('mouseup', stopDrag)
    }

    const getStatusText = (status) => {
      switch (status) {
        case 'PENDING': return 'Pending'
        case 'RUNNING': return 'Running'
        case 'SUCCESS': return 'Success'
        case 'ERROR': return 'Error'
        case 'SKIPPED': return 'Skipped'
        default: return status || 'Pending'
      }
    }

    const formatValue = (value) => {
      if (value === null) return 'null'
      if (value === undefined) return 'undefined'
      if (typeof value === 'object') return JSON.stringify(value)
      return String(value)
    }

    const getType = (value) => {
      if (value === null) return 'null'
      if (Array.isArray(value)) return 'array'
      return typeof value
    }

    const formatTime = (timestamp) => {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      return date.toLocaleTimeString()
    }

    const scrollToBottom = () => {
      nextTick(() => {
        if (logsRef.value) {
          logsRef.value.scrollTop = logsRef.value.scrollHeight
        }
      })
    }

    watch(() => snapshot.logs, () => {
      scrollToBottom()
    }, { deep: true })

    onUnmounted(() => {
      if (ws.value) {
        ws.value.close()
      }
    })

    return {
      sessionId,
      isExpanded,
      isMinimized,
      activeTab,
      steps,
      newStep,
      newParam,
      snapshot,
      dragState,
      tabs,
      isRunning,
      isConnected,
      statusClass,
      connectionStatus,
      panelStyle,
      panelRef,
      logsRef,
      togglePanel,
      toggleMinimize,
      addStep,
      removeStep,
      getStepStatus,
      getStepResult,
      getStepError,
      getStepTime,
      executeAll,
      executeStep,
      handlePause,
      handleReset,
      handleTerminate,
      updateParam,
      removeParam,
      clearLogs,
      startDrag,
      getStatusText,
      formatValue,
      getType,
      formatTime
    }
  }
}
</script>

<style scoped>
.debug-panel-container {
  position: fixed;
  z-index: 9999;
}

.debug-trigger {
  position: fixed;
  right: 20px;
  bottom: 20px;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
}

.debug-trigger:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 25px rgba(102, 126, 234, 0.5);
}

.debug-icon {
  font-size: 24px;
}

.debug-badge {
  position: absolute;
  top: 0;
  right: 0;
  width: 12px;
  height: 12px;
  background: #ff4d4f;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.debug-panel {
  position: fixed;
  width: 500px;
  max-height: 650px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  cursor: move;
  user-select: none;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
}

.status-dot.connected { background: #52c41a; }
.status-dot.reconnecting { background: #faad14; animation: blink 1s infinite; }
.status-dot.disconnected { background: #ff4d4f; }

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.session-id {
  font-size: 11px;
  opacity: 0.8;
  font-family: monospace;
  background: rgba(255,255,255,0.2);
  padding: 2px 6px;
  border-radius: 4px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.connection-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255,255,255,0.2);
}

.connection-status.connected { background: rgba(82, 196, 26, 0.3); }
.connection-status.reconnecting { background: rgba(250, 173, 20, 0.3); }
.connection-status.disconnected { background: rgba(255, 77, 79, 0.3); }

.btn-icon {
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-icon:hover {
  background: rgba(255, 255, 255, 0.3);
}

.panel-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tabs {
  display: flex;
  border-bottom: 1px solid #e8e8e8;
}

.tab {
  flex: 1;
  padding: 10px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 14px;
  color: #666;
  transition: all 0.2s;
}

.tab:hover {
  background: #f5f5f5;
}

.tab.active {
  color: #667eea;
  font-weight: 600;
  border-bottom: 2px solid #667eea;
}

.tab-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.step-editor {
  margin-bottom: 16px;
}

.step-input-row {
  display: flex;
  gap: 8px;
}

.input-key {
  width: 80px;
  padding: 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
}

.input-expr {
  flex: 1;
  padding: 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  font-family: monospace;
}

.input-desc {
  width: 100px;
  padding: 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
}

.btn-add {
  padding: 8px 12px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
}

.btn-add:hover {
  background: #5a6fd6;
}

.steps-list {
  max-height: 220px;
  overflow-y: auto;
  margin-bottom: 16px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
}

.step-item {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.2s;
  position: relative;
}

.step-item:last-child {
  border-bottom: none;
}

.step-item.active {
  border-left: 3px solid #667eea;
  background: #f0f5ff;
}

.step-item.success {
  border-left: 3px solid #52c41a;
}

.step-item.error {
  border-left: 3px solid #ff4d4f;
  background: #fff2f0;
}

.step-item.running {
  border-left: 3px solid #667eea;
  background: #e6f7ff;
  animation: highlight 1s infinite;
}

@keyframes highlight {
  0%, 100% { background: #e6f7ff; }
  50% { background: #d6f4ff; }
}

.step-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.step-index {
  width: 22px;
  height: 22px;
  background: #667eea;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.step-key {
  font-weight: 600;
  color: #333;
  font-family: monospace;
}

.step-status {
  margin-left: auto;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}

.step-status.pending { background: #f0f0f0; color: #999; }
.step-status.running { background: #e6f7ff; color: #1890ff; }
.step-status.success { background: #f6ffed; color: #52c41a; }
.step-status.error { background: #fff2f0; color: #ff4d4f; }

.btn-remove {
  width: 20px;
  height: 20px;
  border: none;
  background: #ff4d4f;
  color: white;
  border-radius: 50%;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.step-item:hover .btn-remove {
  opacity: 1;
}

.step-expression {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  color: #666;
  background: #f5f5f5;
  padding: 6px 10px;
  border-radius: 4px;
  word-break: break-all;
}

.step-desc {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.step-result {
  margin-top: 10px;
  padding: 8px 10px;
  background: #f6ffed;
  border-radius: 4px;
  font-size: 13px;
  border-left: 3px solid #52c41a;
}

.result-label {
  color: #999;
  margin-right: 8px;
}

.result-value {
  font-family: monospace;
  font-weight: 600;
  color: #52c41a;
}

.result-time {
  float: right;
  color: #999;
  font-size: 11px;
}

.step-error {
  margin-top: 10px;
  padding: 8px 10px;
  background: #fff2f0;
  border-radius: 4px;
  color: #ff4d4f;
  font-size: 12px;
  word-break: break-all;
  border-left: 3px solid #ff4d4f;
}

.action-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 4px;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: #667eea;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #5a6fd6;
}

.btn-secondary {
  background: #f0f0f0;
  color: #333;
}

.btn-secondary:hover:not(:disabled) {
  background: #e0e0e0;
}

.btn-warning {
  background: #faad14;
  color: white;
}

.btn-warning:hover:not(:disabled) {
  background: #d99a11;
}

.btn-danger {
  background: #ff4d4f;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: #f5222d;
}

.params-tab {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.param-input {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.input-value {
  flex: 1;
  padding: 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
}

.params-list {
  flex: 1;
  overflow-y: auto;
  max-height: 280px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
}

.param-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.param-item:last-child {
  border-bottom: none;
}

.param-key {
  font-weight: 600;
  color: #667eea;
  min-width: 80px;
  font-family: monospace;
}

.param-value {
  flex: 1;
  font-family: monospace;
  color: #333;
  word-break: break-all;
}

.param-type {
  font-size: 11px;
  color: #999;
  padding: 2px 6px;
  background: #f5f5f5;
  border-radius: 4px;
}

.btn-remove-param {
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: #ff4d4f;
  cursor: pointer;
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s;
}

.param-item:hover .btn-remove-param {
  opacity: 1;
}

.logs-tab {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
}

.btn-clear {
  padding: 4px 12px;
  background: #f0f0f0;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.btn-clear:hover {
  background: #e0e0e0;
}

.logs-list {
  flex: 1;
  overflow-y: auto;
  max-height: 300px;
  background: #1e1e1e;
  border-radius: 6px;
  padding: 8px;
  font-family: 'Consolas', monospace;
}

.log-item {
  display: flex;
  gap: 10px;
  padding: 4px 8px;
  font-size: 12px;
  color: #d4d4d4;
}

.log-item.info { color: #9cdcfe; }
.log-item.warn { color: #dcdcaa; background: rgba(250, 173, 20, 0.1); }
.log-item.error { color: #f48771; background: rgba(255, 77, 79, 0.1); }
.log-item.debug { color: #6a9955; }

.log-time {
  color: #6a9955;
  min-width: 70px;
}

.log-level {
  min-width: 40px;
  font-weight: 600;
}

.log-message {
  flex: 1;
  word-break: break-all;
}

.empty-state {
  text-align: center;
  color: #999;
  padding: 40px 20px;
  font-size: 13px;
}

.panel-minimized {
  padding: 8px 16px;
  background: #fafafa;
}

.minimized-status {
  font-size: 13px;
}

.minimized-status.running { color: #52c41a; }
.minimized-status.success { color: #52c41a; }
.minimized-status.error { color: #ff4d4f; }
.minimized-status.paused { color: #faad14; }
.minimized-status.idle { color: #999; }
</style>
