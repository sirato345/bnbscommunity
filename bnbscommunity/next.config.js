/** @type {import('next').NextConfig} */
const nextConfig = {
  // 确保路径别名正常工作
  webpack: (config) => {
    return config;
  },
}

module.exports = nextConfig