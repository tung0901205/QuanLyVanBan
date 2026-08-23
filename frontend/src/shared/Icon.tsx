import type { SVGProps } from "react";

export type IconName =
  | "archive" | "audit" | "bell" | "building" | "check" | "chevron-left"
  | "chevron-right" | "clock" | "dashboard" | "file" | "folder" | "home"
  | "inbox" | "lock" | "network" | "people" | "person" | "report" | "search"
  | "download" | "refresh" | "send" | "settings" | "shield" | "template" | "upload" | "workflow" | "x";

const paths: Record<IconName, React.ReactNode> = {
  home: <><path d="m3 11 9-8 9 8"/><path d="M5 10v10h14V10M9 20v-6h6v6"/></>,
  dashboard: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
  inbox: <><path d="M4 4h16v14H4z"/><path d="M4 13h4l2 3h4l2-3h4"/><path d="M12 3v8m-3-3 3 3 3-3"/></>,
  send: <><path d="m21 3-7.5 18-4-8-8-4L21 3Z"/><path d="m9.5 13 5-5"/></>,
  check: <><circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16 9"/></>,
  workflow: <><circle cx="6" cy="6" r="2.5"/><circle cx="18" cy="18" r="2.5"/><path d="M8.5 6H15a3 3 0 0 1 3 3v6.5M15.5 18H9a3 3 0 0 1-3-3V8.5"/></>,
  network: <><rect x="9" y="3" width="6" height="5" rx="1"/><rect x="3" y="16" width="6" height="5" rx="1"/><rect x="15" y="16" width="6" height="5" rx="1"/><path d="M12 8v4m-6 4v-4h12v4"/></>,
  template: <><path d="M5 3h10l4 4v14H5z"/><path d="M15 3v5h4M8 12h8M8 16h6"/></>,
  report: <><path d="M4 20V10m5 10V4m6 16v-7m5 7V7"/></>,
  folder: <><path d="M3 6h7l2 2h9v11H3z"/></>,
  building: <><path d="M4 21V5l8-3 8 3v16M8 8h1m6 0h1M8 12h1m6 0h1M8 16h1m6 0h1M10 21v-4h4v4"/></>,
  people: <><circle cx="9" cy="8" r="3"/><path d="M3.5 20v-2a5.5 5.5 0 0 1 11 0v2"/><circle cx="17" cy="9" r="2.5"/><path d="M16 14a5 5 0 0 1 5 5v1"/></>,
  upload: <><path d="M12 16V4m-4 4 4-4 4 4"/><path d="M4 15v5h16v-5"/></>,
  download: <><path d="M12 4v12m-4-4 4 4 4-4"/><path d="M4 15v5h16v-5"/></>,
  refresh: <><path d="M20 7v5h-5"/><path d="M4 17v-5h5"/><path d="M6.1 8.5A7 7 0 0 1 18.7 7L20 12M4 12l1.3 5A7 7 0 0 0 18 15.5"/></>,
  search: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
  person: <><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></>,
  bell: <><path d="M6 9a6 6 0 0 1 12 0c0 7 3 7 3 7H3s3 0 3-7"/><path d="M10 20h4"/></>,
  settings: <><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.8 1.8 0 0 0 .36 2l.05.05-2.8 2.8-.06-.05a1.8 1.8 0 0 0-2-.36 1.8 1.8 0 0 0-1.1 1.65V21h-4v-.08A1.8 1.8 0 0 0 8.7 19.3a1.8 1.8 0 0 0-2 .36l-.05.05-2.8-2.8.05-.06a1.8 1.8 0 0 0 .36-2A1.8 1.8 0 0 0 2.6 13H2v-4h.6a1.8 1.8 0 0 0 1.65-1.1 1.8 1.8 0 0 0-.36-2l-.05-.05 2.8-2.8.06.05a1.8 1.8 0 0 0 2 .36A1.8 1.8 0 0 0 9.8 2H14v.08a1.8 1.8 0 0 0 1.1 1.65 1.8 1.8 0 0 0 2-.36l.05-.05 2.8 2.8-.05.06a1.8 1.8 0 0 0-.36 2A1.8 1.8 0 0 0 21.4 9H22v4h-.6a1.8 1.8 0 0 0-2 2Z"/></>,
  shield: <><path d="M12 3 4.5 6v5c0 5 3.2 8.5 7.5 10 4.3-1.5 7.5-5 7.5-10V6z"/><path d="m8.5 12 2.2 2.2 4.8-5"/></>,
  lock: <><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3"/></>,
  clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
  audit: <><path d="M6 3h12v18H6z"/><path d="M9 8h6M9 12h6M9 16h4"/></>,
  file: <><path d="M5 3h10l4 4v14H5z"/><path d="M15 3v5h4"/></>,
  archive: <><path d="M3 5h18v4H3zM5 9h14v11H5zM9 13h6"/></>,
  "chevron-left": <path d="m15 18-6-6 6-6"/>,
  "chevron-right": <path d="m9 18 6-6-6-6"/>,
  x: <path d="M6 6l12 12M18 6 6 18"/>,
};

type IconProps = SVGProps<SVGSVGElement> & { name: IconName; size?: number };

export function Icon({ name, size = 20, ...props }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...props}>
      {paths[name]}
    </svg>
  );
}
