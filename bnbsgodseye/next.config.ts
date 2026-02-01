import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  reactCompiler: true,
};

module.exports = {
  // 移除可能的路由重定向配置
  async redirects() {
    return [
      // 避免使用通配符重定向到 _not-found
    ];
  }
};

export default nextConfig;
