# UI Reference Specification — DevOpsHub Design System
*(Extracted directly from visual source of truth: `media_1791824363517.png`)*

## 1. Visual Properties & Global Theme Tokens

### Color Palette (Sleek Dark Developer Board)
- **Root Canvas Background:** `#0F1017` (Deep Obsidian Black)
- **Sidebar Surface:** `#13141F` (Charcoal Slate)
- **Top Header Surface:** `#13141F` with border `#212232` (1px bottom)
- **Right Alerts Panel Surface:** `#13141F` with border `#212232` (1px left)
- **Primary Card Surface:** `#181926` (Navy Slate)
- **Secondary Card Surface / Inner Containers:** `#1E1F2E` / `#12131D`
- **Card Borders:** `#27283A` (1px solid)
- **Hover Surfaces:** `#242637` / `#222436`
- **Text Primary (Headings & Titles):** `#FFFFFF` (Crisp 100% White)
- **Text Secondary / Subtitles:** `#8A8D9E` (Cool Slate Gray)
- **Text Muted / Metadata:** `#616478` (Dark Muted Slate)
- **Border Separators:** `#212232` / `#282A3C`

### Accent Colors (From Reference Stage Pills & Traffic Lights)
- **Coral Red (Alerts / Failures / Pill 1):** `#F43F5E` (dot / 4px left pill bar)
- **Cyan Blue (In-Progress / Pill 2):** `#38BDF8`
- **Amber Orange (Warnings / Pill 3):** `#F59E0B`
- **Neon Purple (Development / Pill 4):** `#8B5CF6`
- **Emerald Green (Done / Succeeded / Pill 5):** `#10B981`
- **Mac Traffic Lights:** Red `#FF5F56`, Yellow `#FFBD2E`, Green `#27C93F`

### Geometry, Corner Radii & Shadows
- **Window Corner Radius:** 16px
- **Card Corner Radius:** 12px
- **Pill Corner Radius:** 20px (fully rounded capsules)
- **Button Corner Radius:** 6px – 8px
- **Search Field Corner Radius:** 20px
- **Drop Shadows:** `dropshadow(three-pass-box, rgba(0, 0, 0, 0.25), 8, 0, 0, 3)`

---

## 2. Layout Structure & 3-Column Shell Mapping

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ WINDOW FRAME (16px corner radius, #0F1017 canvas)                                      │
├──────────────────┬─────────────────────────────────────────────────┬───────────────────┤
│ LEFT SIDEBAR     │ TOP HEADER (Height: ~60px)                      │ RIGHT SIDEBAR     │
│ Width: ~220px    │ Subtitle: "Choose project:"                     │ Width: ~220px     │
│ • macOS Traffic  │ Title: "Choose a project to work"               │ Header:           │
│   Lights (🔴🟡🟢) │ Search: [ 🔍 Search...        ]                 │ • 🔔 New alerts:  │
│ • Hamburger menu │ Date: [ 📅 13.04.2021 ]                         │                   │
│ • Project Pill   │ Actions: [ + Create ] [ Import ] [ Remove ]     │ Alert Rows:       │
│ • Navigation:    ├─────────────────────────────────────────────────┤ • 👤 Nick Jonson  │
│   - Home         │ MAIN CONTENT CANVAS (Responsive)                │   2 min ago       │
│   - Dashboard    │                                                 │ • 👤 Mike P.      │
│   - Map (Screen1)│ SCREEN 1: Interactive Workflow Timeline & Map   │   14 min ago      │
│   - Board(Screen3│ SCREEN 2: Project Cards Grid (Screen 2)         │ • 👤 Omar Hulam   │
│   - Projects     │ SCREEN 3: 4-Column Status Kanban Board          │   1 hour ago      │
│   - Pipelines    │ DASHBOARD: Operational KPI & Live Overview      │ • 👤 Maria V.     │
│   - Logs         │ LOGS / ANALYTICS / SETTINGS: Text-optimized     │   2 hours ago     │
│   - Analytics    │                                                 │                   │
│   - Settings     │                                                 │                   │
│ • User Avatar    │                                                 │                   │
└──────────────────┴─────────────────────────────────────────────────┴───────────────────┘
```

---

## 3. Screen-by-Screen Component Specification

### Screen 1: Interactive Workflow Map (`SOS Computer redesign`)
- **Header:** Project title + Search input + Date pill (`12.04.2021`) + Time scale tabs (`Hours | Day | Week | Month`).
- **Flow Canvas:** Dark grid canvas with timeline columns.
- **Stage Pills:** Rounded capsule pills (`.timeline-pill`) with colored left accent bars (`#F43F5E`, `#38BDF8`, `#F59E0B`, `#8B5CF6`, `#10B981`), stage title, and circular avatar badges.
- **Curves:** Curved cubic bezier connector lines between stages.
- **Bottom Team Cards:**
  - Card 1: `Design team` / `Build Team` with red dot (`32 issues`), amber dot (`14 alerts`), green dot (`Done`), stats: `14 hours`, `5 persons`, `117 tasks`.
  - Card 2: `Team of programmers` / `QA Team` with red dot (`12`), amber dot (`8`), green dot (`Active`), stats: `72 hours`, `8 persons`, `219 tasks`.
  - Card 3: `Marketing team` / `Release Team` with red dot (`0`), amber dot (`2`), green dot (`Ready`), stats: `250 hours`, `12 persons`, `85 tasks`.
- **Composer Panel:** Collapsible stage & step properties editor below the map to configure commands, timeouts, failure policies, and save to MySQL.

### Screen 2: Projects Grid (`Choose a project to work`)
- **Header:** Subtitle `Choose project:`, bold Title `Choose a project to work`, Search field, Date badge `13.04.2021`, and buttons `+ Create`, `Import`, `Remove`.
- **Card Grid:** 3-column responsive grid of `.project-grid-card` cards:
  - Top Thumbnail Area (`.project-thumbnail-area`): stylized mini preview graphic banner.
  - Project Title: bold white font (`Promotion`, `Store`, `Gold store`, `HiLike`, `InStore`).
  - Relative Timestamp: `Edited 2 days ago`.
  - Right Footer: overlapping circular team member avatars (`#38BDF8`, `#8B5CF6`).
  - View toggle: Grid View (default) / Table View.

### Screen 3: Kanban Board (`Canban board`)
- **Header:** Subtitle `Edit your plate:`, Title `Canban board`, Search input, Date badge `12.04.2021`, team avatar cluster, `+ Create`, `Import`, `Remove`.
- **Columns:** 4 columns matching reference:
  1. `User story:` (Queued / Backlog)
  2. `To do:` (Scheduled)
  3. `In progress:` (Running)
  4. `Done:` (Success / Failed)
- **Cards:** `.kanban-card` with card title, avatars on top right, concise description, duration, and status badge.
- **Bottom Actions:** `+ Create` and `Remove` pill buttons per column.

### Screen 4: Operational Dashboard
- **Header:** Title `Dashboard`, subtitle `Operational truth at a glance`, search field, date badge, database latency pill.
- **KPI Summary:** 6 metric cards styled with dark theme cards (`Projects`, `Pipelines`, `Success Rate`, `Avg Build`, `Successful`, `Failed`).
- **Main Panels:**
  - Left: Recent Executions Feed with build numbers, durations, and action buttons.
  - Right: Active Projects Overview with repository links and statuses.

---

## 4. Typography & Text Optimization Scale
- **Window/Screen Title:** 18px – 20px, Bold (800), `#FFFFFF`
- **Section Headers:** 13px – 15px, Bold (700), `#FFFFFF`
- **Card Titles:** 13px – 14px, Semi-Bold (600), `#FFFFFF`
- **Body & Subtitles:** 11px – 12px, Regular (400), `#8A8D9E`
- **Metadata / Badges / Pills:** 9px – 10px, Bold (700), uppercase
- **Monospace Code / Terminal:** 11px, `Menlo`, `'JetBrains Mono'`, monospace, `#E2E8F0`

---

## 5. Responsive Behavior Across Window Dimensions
1. **Wide Desktop (> 1400px):**
   - Full 3-column shell: Left Sidebar (220px) + Center Canvas (fluid) + Right Alerts Sidebar (220px).
   - Project grid renders 3 to 4 columns.
   - Kanban renders 4 columns side-by-side without horizontal scrolling.
2. **Standard Desktop (1100px – 1400px):**
   - All 3 columns visible.
   - Project grid reflows to 2 or 3 columns.
   - Kanban board columns maintain minimum width (250px) with smooth horizontal scroll if needed.
3. **Compact Desktop (< 1100px):**
   - Left sidebar remains docked.
   - Right alerts sidebar collapses or adapts gracefully.
   - Main content wraps cleanly with zero overlapping text or clipped action buttons.
