'use client';

import { useState, useEffect, useRef } from 'react';
import { Send, User, Bot, MessageSquare } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { ChevronUp, Minimize2 } from 'lucide-react';
import { KeyboardEvent } from 'react';

// 定义 props 类型
interface ChildProps {
  onCallback: (dataSource: DataSource[]) => void;
}

type DataSource = {
  timeframe: string;
  symbol: string;
};

export default function CryptoExtractor({ onCallback }: ChildProps) {
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false); // 控制展开/折叠状态
  const inputRef = useRef<HTMLInputElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 发送消息
  const handleSend = async () => {
    if (!input.trim() || isLoading) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: input,
      timestamp: new Date(),
    };

    const currentInput = input;
    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const response = await fetch('/api', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: currentInput })
      });

      const data = await response.json();

      // 添加代币到一览
      if (data.success) {
        let dataSource: DataSource[] = [];
        data.symbols.map((crypto: string) => {
          const dataSource1 = { timeframe: '1h', symbol: crypto + '/USDT' };
          const dataSource2 = { timeframe: '4h', symbol: crypto + '/USDT' };
          dataSource = [...dataSource, dataSource1, dataSource2];
        });
        onCallback(dataSource);
      }

      const assistantMessage: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: data.message,
        timestamp: new Date(),
      };

      setMessages(prev => [...prev, assistantMessage]);
    } catch (error) {
      console.error('发送失败:', error);
    } finally {
      setIsLoading(false);
    }
  };

  type Message = {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    timestamp: Date;
  };

  const [messages, setMessages] = useState<Message[]>([
    {
      id: '1',
      role: 'assistant',
      content: 'Hello！You can enter "add cryptoname" to watch crypto！',
      timestamp: new Date(),
    }
  ]);
  
  // 处理键盘快捷键
  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };
  
  // 折叠/展开对话框
  const toggleDialog = () => {
    setIsExpanded(!isExpanded);
    // 自动滚动到底部
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };
  
  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 自动调整输入框高度
  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.style.height = 'auto';
      inputRef.current.style.height = `${Math.min(inputRef.current.scrollHeight, 100)}px`;
    }
  }, [input]);
  
  return (
    <>

      {/* 折叠状态按钮 */}
      {!isExpanded && (
        <button
          onClick={toggleDialog}
          className="fixed bottom-6 right-6 z-50 flex items-center space-x-3 px-4 py-3 bg-linear-to-r from-blue-500 to-purple-600 text-white rounded-xl shadow-lg hover:shadow-xl transition-all hover:scale-105 cursor-pointer group"
          style={{ width: '180px', height: '60px' }}
        >
          <div className="p-2 bg-white/20 rounded-lg">
            <MessageSquare className="w-5 h-5" />
          </div>
          <div className="text-left">
            <div className="font-medium">AI Agent</div>
            <div className="text-xs opacity-80">Click to open</div>
          </div>
          <ChevronUp className="w-4 h-4 opacity-60 group-hover:opacity-100 transition-opacity" />
        </button>
      )}

      {/* 展开状态对话框 - 屏幕的四分之一大小，右下角 */}
      {isExpanded && (
        <div className="fixed bottom-6 right-6 z-50 w-[calc(100vw-3rem)] max-w-lg h-[calc(100vh-3rem)] max-h-150 animate-scale-in">
          <div className="w-full h-full bg-white rounded-2xl shadow-2xl border border-gray-200 overflow-hidden flex flex-col">
            {/* 对话框标题栏 */}
            <div className="border-b border-gray-200 p-4 bg-linear-to-r from-blue-50 to-purple-50 flex items-center justify-between shrink-0">
              <div className="flex items-center space-x-3">
                <div className="p-2 bg-linear-to-r from-blue-500 to-purple-600 rounded-lg">
                  <MessageSquare className="w-5 h-5 text-white" />
                </div>
                <div>
                  <h1 className="text-lg font-bold text-gray-800">AI Agent</h1>
                  <div className="text-xs text-gray-500">At your service</div>
                </div>
              </div>

              {/* 缩小按钮 */}
              <button
                onClick={toggleDialog}
                className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
                title="Minimize dialog"
              >
                <Minimize2 className="w-4 h-4 text-gray-600" />
              </button>
            </div>

            {/* 消息区域 */}
            <div className="flex-1 overflow-y-auto p-4 bg-gray-50">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={`mb-3 ${message.role === 'user' ? 'text-right' : 'text-left'}`}
                >
                  <div className={`inline-flex max-w-[85%] ${message.role === 'user' ? 'flex-row-reverse' : ''}`}>
                    {/* 头像 */}
                    <div className={`shrink-0 ${message.role === 'user' ? 'ml-2' : 'mr-2'}`}>
                      <div className={`w-8 h-8 rounded-full flex items-center justify-center ${message.role === 'user'
                          ? 'bg-linear-to-r from-blue-500 to-blue-600'
                          : 'bg-linear-to-r from-purple-500 to-pink-600'
                        }`}>
                        {message.role === 'user' ? (
                          <User className="w-4 h-4 text-white" />
                        ) : (
                          <Bot className="w-4 h-4 text-white" />
                        )}
                      </div>
                    </div>

                    {/* 消息气泡 */}
                    <div className={`rounded-xl p-3 ${message.role === 'user'
                        ? 'bg-linear-to-r from-blue-500 to-blue-600 text-white rounded-br-none'
                        : 'bg-linear-to-r from-white to-gray-50 border border-gray-200 text-gray-800 rounded-bl-none'
                      }`}>
                      <div className="prose prose-sm max-w-none">
                        <ReactMarkdown>
                          {message.content}
                        </ReactMarkdown>
                      </div>
                      <div className={`text-xs mt-1 ${message.role === 'user' ? 'text-blue-200' : 'text-gray-500'
                        }`}>
                        {message.timestamp.toLocaleTimeString([], {
                          hour: '2-digit',
                          minute: '2-digit'
                        })}
                      </div>
                    </div>
                  </div>
                </div>
              ))}

              {/* 加载状态 */}
              {isLoading && (
                <div className="text-left mb-3">
                  <div className="inline-flex max-w-[85%]">
                    <div className="shrink-0 mr-2">
                      <div className="w-8 h-8 rounded-full flex items-center justify-center bg-linear-to-r from-purple-500 to-pink-600">
                        <Bot className="w-4 h-4 text-white" />
                      </div>
                    </div>
                    <div className="rounded-xl rounded-bl-none p-3 bg-linear-to-r from-white to-gray-50 border border-gray-200">
                      <div className="flex items-center space-x-1">
                        <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-pulse" />
                        <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-pulse delay-150" />
                        <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-pulse delay-300" />
                      </div>
                    </div>
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>

            {/* 输入区域 */}
            <div className="border-t border-gray-200 p-4 bg-white shrink-0">
              <div className="flex gap-2">
                <div className="flex-1 relative">
                  <textarea
                    // ref={inputRef}
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder="Entry message..."
                    className="w-full p-3 pr-10 border border-gray-300 rounded-xl focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200 resize-none text-sm"
                    rows={1}
                    disabled={isLoading}
                  />
                  <div className="absolute right-2 bottom-3 text-xs text-gray-400">
                    ↵
                  </div>
                </div>

                <button
                  onClick={handleSend}
                  disabled={isLoading || !input.trim()}
                  className="px-4 bg-linear-to-r from-blue-500 to-purple-600 text-white rounded-xl hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity flex items-center justify-center min-w-15"
                >
                  {isLoading ? (
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  ) : (
                    <Send className="w-4 h-4" />
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}