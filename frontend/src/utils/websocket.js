/**
 * WebSocket 调试客户端
 * 
 * 功能：
 * 1. 建立长连接
 * 2. 自动重连（3次重试）
 * 3. 心跳响应
 * 4. 消息收发
 * 5. 事件回调
 */
export class DebugWebSocket {
  constructor(url, options = {}) {
    this.url = url
    this.options = {
      reconnectInterval: 3000,
      maxReconnectAttempts: 3,
      heartbeatInterval: 30000,
      ...options
    }
    this.ws = null
    this.reconnectAttempts = 0
    this.isConnected = false
    this.messageQueue = []
    this.heartbeatTimer = null
    this.reconnectTimer = null
    this.handlers = {
      onOpen: null,
      onClose: null,
      onError: null,
      onMessage: null,
      onSnapshot: null,
      onConnected: null,
      onReconnect: null
    }
  }

  connect() {
    try {
      if (this.ws) {
        this.ws.close()
      }
      
      this.ws = new WebSocket(this.url)
      
      this.ws.onopen = (event) => {
        this.isConnected = true
        this.reconnectAttempts = 0
        this.flushMessageQueue()
        this.startHeartbeat()
        this.handlers.onOpen?.(event)
      }

      this.ws.onclose = (event) => {
        this.isConnected = false
        this.stopHeartbeat()
        this.handlers.onClose?.(event)
        
        if (!event.wasClean && this.reconnectAttempts < this.options.maxReconnectAttempts) {
          this.scheduleReconnect()
        }
      }

      this.ws.onerror = (error) => {
        this.handlers.onError?.(error)
      }

      this.ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)
          this.handleMessage(message)
        } catch (e) {
          if (event.data === 'PONG' || event.data.includes('"type":"PONG"')) {
            return
          }
          console.error('Failed to parse message:', e)
        }
      }
    } catch (error) {
      console.error('WebSocket connection failed:', error)
      this.scheduleReconnect()
    }
  }

  handleMessage(message) {
    const { type, data } = message
    
    if (type === 'PING') {
      this.sendRaw('PONG')
      return
    }

    this.handlers.onMessage?.(message)

    switch (type) {
      case 'CONNECTED':
        this.handlers.onConnected?.(data)
        break
      case 'SNAPSHOT':
        this.handlers.onSnapshot?.(data)
        break
      case 'ERROR':
        console.error('Server error:', message.message)
        break
      default:
        console.log('Unknown message type:', type, data)
    }
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.isConnected && this.ws?.readyState === WebSocket.OPEN) {
        this.sendRaw('PING')
      }
    }, this.options.heartbeatInterval)
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  scheduleReconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
    }
    
    this.reconnectAttempts++
    console.log(`Scheduling reconnect attempt ${this.reconnectAttempts}/${this.options.maxReconnectAttempts}...`)
    
    this.handlers.onReconnect?.(this.reconnectAttempts, this.options.maxReconnectAttempts)
    
    this.reconnectTimer = setTimeout(() => {
      console.log(`Reconnecting... (${this.reconnectAttempts}/${this.options.maxReconnectAttempts})`)
      this.connect()
    }, this.options.reconnectInterval)
  }

  sendRaw(message) {
    if (this.isConnected && this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(message)
    }
  }

  send(command) {
    const message = JSON.stringify(command)
    if (this.isConnected && this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(message)
    } else {
      this.messageQueue.push(message)
    }
  }

  flushMessageQueue() {
    while (this.messageQueue.length > 0 && this.isConnected) {
      const message = this.messageQueue.shift()
      this.ws.send(message)
    }
  }

  on(event, handler) {
    if (this.handlers.hasOwnProperty(event)) {
      this.handlers[event] = handler
    }
  }

  off(event) {
    if (this.handlers.hasOwnProperty(event)) {
      this.handlers[event] = null
    }
  }

  close() {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      this.ws.close(1000, 'Client closed')
      this.ws = null
      this.isConnected = false
    }
  }

  sendCommand(sessionId, command, data = {}) {
    this.send({
      sessionId,
      command,
      ...data
    })
  }

  start(sessionId, steps) {
    this.sendCommand(sessionId, 'START', { steps })
  }

  step(sessionId, stepIndex) {
    this.sendCommand(sessionId, 'STEP', { stepIndex })
  }

  pause(sessionId) {
    this.sendCommand(sessionId, 'PAUSE')
  }

  resume(sessionId) {
    this.sendCommand(sessionId, 'RESUME')
  }

  reset(sessionId) {
    this.sendCommand(sessionId, 'RESET')
  }

  terminate(sessionId) {
    this.sendCommand(sessionId, 'TERMINATE')
  }

  updateParam(sessionId, key, value) {
    this.sendCommand(sessionId, 'UPDATE_PARAM', { key, value })
  }

  clearLogs(sessionId) {
    this.sendCommand(sessionId, 'CLEAR_LOGS')
  }
}

export function createDebugWebSocket(sessionId) {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/api/ws/debug?sessionId=${sessionId}`
  return new DebugWebSocket(wsUrl)
}
