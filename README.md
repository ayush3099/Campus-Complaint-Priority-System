# CampusCare — Campus Complaint Priority System

A full-stack college project developed in pure **Java 21** with a modern, high-performance web dashboard. It solves the critical limitation of traditional First-Come-First-Serve (FCFS) grievance portals by dynamically calculating real-world impact and priority scores.

---

## 🚀 Quick Start (How to Run)

### Method 1: Double-Click (Easiest for Windows)
Simply double-click `run.bat` in the project root folder. It will compile all Java files, launch the server, and automatically open `http://localhost:8080` in your default browser.

### Method 2: PowerShell
```powershell
cd c:\Users\Ayona\OneDrive\Desktop\Campus-Complaint-Priority-System
.\run.ps1
```

### Method 3: Command Line (Manual)
```bash
# 1. Compile Java backend
javac -d out src/com/campuscare/*.java

# 2. Start HTTP Server
java -cp out com.campuscare.CampusCareServer
```
Open **`http://localhost:8080`** in your browser.

---

## 💡 Core Concept & Problem Statement

In colleges and universities, students frequently submit complaints regarding electricity hazards, Wi-Fi breakdowns, lab equipment failure, or cleanliness. 

- **The Problem with FCFS (First-Come-First-Serve):** A routine request (e.g. asking for a poster board) submitted at 9:00 AM gets handled before an emergency electrical spark or projector outage in an ongoing exam submitted at 9:15 AM.
- **The CampusCare Solution:** CampusCare dynamically scores every incoming ticket using a multi-factor priority algorithm. Emergency safety issues, cluster outages (multiple students complaining about the same location), and heavily endorsed issues bubble straight to the top of the queue.

---

## 📐 Mathematical Priority Scoring Algorithm

The priority score is calculated in `PriorityEngine.java` using:

$$\text{Priority Score} = \text{Urgency Weight} + \text{Cluster Bonus} + \text{Student Endorsements} + \text{Aging Factor} + \text{Category Weight}$$

### Factor Breakdown:
| Component | Points / Formula | Purpose |
|---|---|---|
| **Urgency Weight** | Low = 5, Medium = 12, High = 22, Emergency = 32 | Baseline severity set by student |
| **Cluster Multiplier** | $+4\text{ pts}$ per similar open complaint in same area | Detects widespread campus disruptions |
| **Community Upvotes** | $+3\text{ pts}$ per student "Me Too!" endorsement | Democratic corroboration by students |
| **Aging / Anti-Starvation** | $+2\text{ pts}$ per day waiting | Prevents low-urgency issues from being starved |
| **Category Base Weight** | Electricity/Security: $+3$, Water/AV: $+2$, IT: $+1$ | Safety & utilities prioritization |

### Priority Bands & SLA:
- 🔴 **Critical ($\ge 35$ pts):** Immediate emergency response (**Target SLA: < 4 Hours**)
- 🟠 **High ($25 - 34$ pts):** High disruption (**Target SLA: < 24 Hours**)
- 🔵 **Medium ($15 - 24$ pts):** Routine classroom repair (**Target SLA: < 3 Days**)
- 🟢 **Low ($< 15$ pts):** Cosmetic maintenance (**Target SLA: < 7 Days**)

---

## 🏛️ System Architecture

```text
Campus-Complaint-Priority-System/
├── src/com/campuscare/
│   ├── CampusCareServer.java       # Pure Java HTTP server, REST endpoints, CORS & static handler
│   ├── Complaint.java              # OOP Model class (Encapsulation, SLA logic, getters/setters)
│   ├── ComplaintRepository.java    # Thread-safe persistent storage with TSV serialization
│   ├── PriorityEngine.java         # Algorithmic scoring and SLA classification engine
│   └── JsonUtil.java               # Dependency-free JSON parser and query decoder
├── public/
│   ├── index.html                  # Single-page modern web portal
│   ├── styles.css                  # Responsive SaaS design with light theme & dark accents
│   └── app.js                      # Dynamic UI logic, real-time simulator, AJAX API calls
├── data/
│   └── complaints.tsv              # Local flat-file database (UTF-8 tab-separated)
├── run.bat                         # Windows 1-click batch runner
├── run.ps1                         # PowerShell runner
└── README.md                       # Complete documentation & viva prep
```

---

## 🔌 REST API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/complaints` | Returns filtered and priority-sorted complaints list |
| `POST` | `/api/complaints` | Registers a new complaint and calculates priority score |
| `POST` | `/api/complaints/{id}/upvote` | Endorses an issue ("Me Too!"), dynamically bumping score |
| `PATCH` | `/api/complaints/{id}/status` | Admin updates status, resolution remark, or department |
| `DELETE` | `/api/complaints/{id}` | Admin removes an invalid or duplicate ticket |
| `GET` | `/api/stats` | Dashboard statistics (Total, Open, Urgent, Resolved, Mix) |
| `GET` | `/api/export` | Downloads full complaints register as `.csv` spreadsheet |

---

## 🎓 Questions & Answers for Teacher / Viva ("Ma'am")

**Q1: Which backend framework did you use in Java?**
> *Answer:* We deliberately used **Java's built-in `com.sun.net.httpserver.HttpServer`** rather than heavy external frameworks like Spring Boot. This makes the project lightweight, portable, fast to compile, and has zero external dependencies while still exposing standard RESTful JSON APIs.

**Q2: What Data Structures are utilized?**
> *Answer:* 
> 1. **Priority Queue / Max-Heap logic** implemented via Java `List` with custom `Comparator` sorting primarily by `priorityScore` descending and secondarily by `createdAt` ascending.
> 2. **HashMaps / Sets** for fast status verification and JSON key-value extraction.
> 3. **Thread-Safe collections** to manage concurrent student submissions.

**Q3: How does your system prevent starvation of low-priority complaints?**
> *Answer:* Traditional priority systems can cause low-priority issues to wait forever (starvation). Our `PriorityEngine` incorporates an **Aging Factor ($+2\text{ pts}$ per waiting day)**. As days pass, older low-priority complaints gradually elevate in score and move up the queue.

**Q4: How are duplicate complaints handled?**
> *Answer:* Instead of filing duplicate complaints for the same issue (like a broken water cooler), students can click **"Me Too!" (Endorse)**. This increases the complaint's upvote count, which grants $+3$ priority points without cluttering the administrative database.
