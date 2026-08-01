'use client';

export default function AiAnalysePage() {
  return (
    <div 
      className="w-full bg-white" 
      style={{ 
        paddingTop: 64, 
        height: '100vh',
        overflow: 'hidden'  // 父容器不滚动
      }}
    >
      <iframe
        src="https://godseye.bnbscommunity.com?homepage=true"
        className="w-full border-0"
        style={{ 
          height: 'calc(100vh - 64px)',
        }}
        title="AI Analyse"
        sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
        scrolling="yes"  // 强制 iframe 始终显示滚动条
      />
    </div>
  );
}