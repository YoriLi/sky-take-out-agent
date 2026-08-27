<template>
  <div class="dashboard-container agent-page">
    <div class="container agent-panel">
      <div class="agent-header">
        <div class="agent-title">AI 助手</div>
        <div class="agent-sub">用自然语言查询营业数据、处理订单、开关店</div>
      </div>

      <div ref="messageList" class="agent-messages">
        <div v-if="messages.length === 0" class="agent-empty">
          <p>你好，我是苍穹外卖老板助手，可以直接问我：</p>
          <div class="agent-hints">
            <el-button
              v-for="hint in hints"
              :key="hint"
              size="small"
              class="hint-btn"
              @click="useHint(hint)"
            >{{ hint }}</el-button>
          </div>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="msg-row"
          :class="msg.role"
        >
          <div class="bubble">
            <div v-if="msg.tools && msg.tools.length" class="tool-list">
              <div
                v-for="(tool, tIndex) in msg.tools"
                :key="tIndex"
                class="tool-card"
                :class="{ done: !tool.loading }"
              >
                <div class="tool-head">
                  <i :class="tool.loading ? 'el-icon-loading' : 'el-icon-circle-check'" />
                  <span>{{ tool.loading ? '正在调用' : '已完成' }} {{ tool.name }}</span>
                </div>
                <pre v-if="tool.argumentsText" class="tool-args">{{ tool.argumentsText }}</pre>
                <pre v-if="tool.result" class="tool-result">{{ tool.result }}</pre>
              </div>
            </div>
            <div v-if="msg.streaming && !msg.content" class="typing">
              <span /><span /><span />
            </div>
            <div v-if="msg.content" class="msg-text">{{ msg.content }}</div>
          </div>
        </div>
      </div>

      <div class="agent-input">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="2"
          placeholder="输入问题，例如：今天营业额怎么样"
          :disabled="sending"
          @keydown.native="onKeydown"
        />
        <el-button
          type="primary"
          class="send-btn"
          :disabled="sending || !draft.trim()"
          :loading="sending"
          @click="send"
        >发送</el-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { startAgentStream } from '@/api/agent'

interface ToolCard {
  name: string
  argumentsText: string
  result: string
  loading: boolean
}

interface ChatMessage {
  role: string
  content: string
  tools: ToolCard[]
  streaming: boolean
}

@Component({
  name: 'Agent'
})
export default class Agent extends Vue {
  private draft = ''
  private sending = false
  private messages: ChatMessage[] = []
  private hints: string[] = [
    '今天营业额怎么样',
    '现在营业吗',
    '帮我打烊',
    '查一下待接单的订单',
    '接单'
  ]

  private useHint(text: string) {
    this.draft = text
    this.send()
  }

  private onKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      this.send()
    }
  }

  private currentAssistant(): ChatMessage | null {
    if (!this.messages.length) {
      return null
    }
    const last = this.messages[this.messages.length - 1]
    if (last.role === 'assistant') {
      return last
    }
    return null
  }

  private send() {
    const text = this.draft ? this.draft.trim() : ''
    if (!text || this.sending) {
      return
    }
    this.draft = ''
    this.sending = true
    this.messages.push({
      role: 'user',
      content: text,
      tools: [],
      streaming: false
    })
    this.messages.push({
      role: 'assistant',
      content: '',
      tools: [],
      streaming: true
    })
    this.scrollToBottom()

    const self = this
    startAgentStream(text, {
      onText: (content: string) => {
        const msg = self.currentAssistant()
        if (msg) {
          msg.content = msg.content + content
        }
        self.scrollToBottom()
      },
      onToolCall: (name: string, args: { [key: string]: any }) => {
        const msg = self.currentAssistant()
        if (!msg) {
          return
        }
        let argumentsText = ''
        try {
          argumentsText = JSON.stringify(args, null, 2)
        } catch (e) {
          argumentsText = ''
        }
        msg.tools.push({
          name: name,
          argumentsText: argumentsText,
          result: '',
          loading: true
        })
        self.scrollToBottom()
      },
      onToolResult: (name: string, result: string) => {
        const msg = self.currentAssistant()
        if (!msg) {
          return
        }
        for (let i = msg.tools.length - 1; i >= 0; i--) {
          if (msg.tools[i].name === name && msg.tools[i].loading) {
            msg.tools[i].loading = false
            msg.tools[i].result = result
            break
          }
        }
        self.scrollToBottom()
      },
      onEnd: () => {
        const msg = self.currentAssistant()
        if (msg) {
          msg.streaming = false
        }
        self.sending = false
        self.scrollToBottom()
      },
      onError: (message: string) => {
        const msg = self.currentAssistant()
        if (msg) {
          msg.streaming = false
          if (!msg.content) {
            msg.content = message
          }
        }
        self.sending = false
        self.$message.error(message)
      }
    })
  }

  private scrollToBottom() {
    this.$nextTick(() => {
      const el = this.$refs.messageList as HTMLElement
      if (el) {
        el.scrollTop = el.scrollHeight
      }
    })
  }
}
</script>

<style lang="scss" scoped>
.agent-page {
  height: calc(100vh - 64px);
  box-sizing: border-box;
}

.agent-panel {
  background: #fff;
  border-radius: 8px;
  height: calc(100vh - 104px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.agent-header {
  height: 64px;
  padding: 0 22px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.agent-title {
  font-size: 16px;
  font-weight: 700;
  color: #333;
}

.agent-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #818693;
  font-weight: 400;
}

.agent-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: #fafafb;
}

.agent-empty {
  color: #818693;
  font-size: 14px;
  p {
    margin: 0 0 12px;
  }
}

.agent-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hint-btn {
  border-color: #ffc200;
  color: #333;
  background: #fff8e1;
}

.msg-row {
  display: flex;
  margin-bottom: 16px;
  &.user {
    justify-content: flex-end;
    .bubble {
      background: #ffc200;
      color: #333;
      border-radius: 8px 8px 0 8px;
    }
  }
  &.assistant {
    justify-content: flex-start;
    .bubble {
      background: #fff;
      border: 1px solid #ebeef5;
      border-radius: 8px 8px 8px 0;
    }
  }
}

.bubble {
  max-width: 72%;
  padding: 12px 14px;
  box-shadow: 0 1px 2px rgba(32, 35, 42, 0.04);
}

.msg-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.6;
  color: inherit;
}

.tool-list {
  margin-bottom: 8px;
}

.tool-card {
  background: #f3f4f7;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #20232a;
  &.done {
    background: #f0f9eb;
  }
}

.tool-head {
  display: flex;
  align-items: center;
  font-weight: 600;
  i {
    margin-right: 6px;
    color: #ffc200;
  }
  .el-icon-circle-check {
    color: #1dc779;
  }
}

.tool-args,
.tool-result {
  margin: 6px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 12px;
  color: #818693;
}

.typing {
  display: flex;
  align-items: center;
  height: 18px;
  span {
    width: 6px;
    height: 6px;
    margin-right: 4px;
    border-radius: 50%;
    background: #bac0cd;
    animation: blink 1.2s infinite ease-in-out;
  }
  span:nth-child(2) {
    animation-delay: 0.2s;
  }
  span:nth-child(3) {
    animation-delay: 0.4s;
  }
}

@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
  }
  40% {
    opacity: 1;
  }
}

.agent-input {
  padding: 16px 20px 18px;
  border-top: 1px solid #ebeef5;
  display: flex;
  align-items: flex-end;
  background: #fff;
}

.send-btn {
  margin-left: 12px;
  height: 54px;
  padding: 0 22px;
}
</style>
