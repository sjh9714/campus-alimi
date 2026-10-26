import type { NextConfig } from "next";

// 정적 export. 운영에서는 Caddy가 out/ 을 그대로 내보내고 /api 는 Spring으로 넘긴다.
// 로컬 개발은 NEXT_PUBLIC_API_BASE=http://localhost:8080 으로 API를 직접 부른다.
const nextConfig: NextConfig = {
  output: "export",
  trailingSlash: true,
  images: { unoptimized: true },
};

export default nextConfig;
