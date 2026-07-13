/** @type {import('next').NextConfig} */

const nextConfig = {

  // 添加一个空的 turbopack 配置来消除警告
  turbopack: {},

  // 确保路径别名正常工作
  webpack: (config) => {
    return config;
  },
}

// module.exports = nextConfig;
// 本地开发时允许来自特定 IP 的请求（例如你的开发服务器 IP）
module.exports = {
  allowedDevOrigins: ['192.168.3.9'],
}