# 006. 배포

**상황.** 실제 사용자가 접속하려면 서버가 켜져 있어야 한다. 새 비용은 0원이다. 웹푸시는 HTTPS가 필요하다.

**선택지.** (1) Oracle Cloud Always Free VM 1대 + Caddy(HTTPS 자동) + DuckDNS 무료 서브도메인, 프론트는 Vercel 무료. (2) 집 맥 + Cloudflare Tunnel. (3) Fly.io·Koyeb 무료 티어.

**결정.** (1). VM 한 대에 Docker Compose로 app, postgres, caddy를 올린다. 프론트는 Vercel에 정적 배포하고 API만 VM으로 보낸다. 도메인은 DuckDNS로 시작하고, 유료 도메인은 사용자가 생긴 뒤 따로 정한다.

**이유.** 영구 무료이고 상주 프로세스(수집기, 발송기)를 돌릴 수 있다. (2)는 맥이 꺼지면 서비스가 꺼진다. (3)은 유휴 시 꺼지거나 메모리가 부족하다.

**면접에서 한 문장.** "비용 0원 조건에서 상주 프로세스가 필요해 Oracle 무료 VM에 Compose로 올리고 Caddy로 HTTPS를 자동화했습니다."
