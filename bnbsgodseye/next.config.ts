import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  reactCompiler: true,
  output: 'standalone',

  // 添加详细的构建日志
  logging: {
    fetches: {
      fullUrl: true,
    },
  },

  // Next.js 15 需要明确配置允许的来源
  allowedDevOrigins: [
    '*',                     // 允许所有来源（最简单）
  ],

  // 兼容性配置
  transpilePackages: ['class-variance-authority', 'clsx', 'tailwind-merge'],
  
};

module.exports = {
  // 移除可能的路由重定向配置
  async redirects() {
    return [
      // 避免使用通配符重定向到 _not-found
    ];
  }, 
};

module.exports = nextConfig;
