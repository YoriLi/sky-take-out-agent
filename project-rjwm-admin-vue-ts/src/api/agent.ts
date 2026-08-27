import { getToken } from '@/utils/cookies'

export interface AgentStreamHandlers {
  onText: (content: string) => void
  onToolCall: (name: string, args: { [key: string]: any }) => void
  onToolResult: (name: string, result: string) => void
  onEnd: () => void
  onError: (message: string) => void
}

function dispatchEvent(raw: string, handlers: AgentStreamHandlers): void {
  let parsed: any
  try {
    parsed = JSON.parse(raw)
  } catch (e) {
    return
  }
  if (!parsed || !parsed.type) {
    return
  }
  const type = String(parsed.type)
  if (type === 'text') {
    if (parsed.content) {
      handlers.onText(String(parsed.content))
    }
    return
  }
  if (type === 'tool_call') {
    handlers.onToolCall(parsed.name ? String(parsed.name) : '', parsed.arguments ? parsed.arguments : {})
    return
  }
  if (type === 'tool_result') {
    handlers.onToolResult(parsed.name ? String(parsed.name) : '', parsed.result ? String(parsed.result) : '')
    return
  }
  if (type === 'end') {
    handlers.onEnd()
    return
  }
  if (type === 'error') {
    handlers.onError(parsed.message ? String(parsed.message) : '助手暂时不可用，请稍后重试')
  }
}

function consumeSseBlock(block: string, handlers: AgentStreamHandlers): void {
  if (!block) {
    return
  }
  const lines = block.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line.indexOf('data:') === 0) {
      const jsonStr = line.substring(5).replace(/^\s+/, '')
      if (jsonStr) {
        dispatchEvent(jsonStr, handlers)
      }
    }
  }
}

function consumeBuffer(buffer: string, handlers: AgentStreamHandlers, flush: boolean): string {
  const parts = buffer.split('\n\n')
  let rest = ''
  if (!flush) {
    const last = parts.pop()
    rest = last === undefined ? '' : last
  }
  for (let i = 0; i < parts.length; i++) {
    consumeSseBlock(parts[i], handlers)
  }
  return rest
}

export function startAgentStream(message: string, handlers: AgentStreamHandlers): Promise<void> {
  const token = getToken()
  const headers: { [key: string]: string } = {
    'Content-Type': 'application/json',
    'Accept': 'text/event-stream',
    token: token ? String(token) : ''
  }

  return fetch('/api/agent/stream', {
    method: 'POST',
    headers: headers,
    body: JSON.stringify({ message: message })
  }).then((response) => {
    if (response.status === 401) {
      handlers.onError('登录已过期，请重新登录')
      return
    }
    if (!response.ok) {
      handlers.onError('助手请求失败（HTTP ' + response.status + '）')
      return
    }
    if (!response.body) {
      handlers.onError('当前浏览器不支持流式响应')
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let finished = false

    const wrapped: AgentStreamHandlers = {
      onText: handlers.onText,
      onToolCall: handlers.onToolCall,
      onToolResult: handlers.onToolResult,
      onEnd: () => {
        if (!finished) {
          finished = true
          handlers.onEnd()
        }
      },
      onError: (msg: string) => {
        if (!finished) {
          finished = true
          handlers.onError(msg)
        }
      }
    }

    const pump = (): Promise<void> => {
      return reader.read().then((chunk) => {
        if (chunk.done) {
          buffer += decoder.decode()
          consumeBuffer(buffer, wrapped, true)
          wrapped.onEnd()
          return
        }
        buffer += decoder.decode(chunk.value, { stream: true })
        buffer = consumeBuffer(buffer, wrapped, false)
        return pump()
      })
    }

    return pump().catch(() => {
      wrapped.onError('读取助手响应失败')
    })
  }).catch(() => {
    handlers.onError('无法连接助手服务，请确认后端已启动')
  })
}
