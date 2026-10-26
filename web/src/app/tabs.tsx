"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";

export function Tabs() {
  const path = usePathname();
  const [admin, setAdmin] = useState(false);
  useEffect(() => {
    api.me().then((me) => setAdmin(!!me?.admin)).catch(() => setAdmin(false));
  }, [path]);
  const tab = (href: string, label: string) => (
    <Link href={href} className={path === href ? "active" : ""}>
      {label}
    </Link>
  );
  return (
    <nav className="tabs">
      {tab("/", "일정")}
      {tab("/benefits/", "혜택")}
      {tab("/settings/", "설정")}
      {admin && tab("/admin/", "검수")}
    </nav>
  );
}
