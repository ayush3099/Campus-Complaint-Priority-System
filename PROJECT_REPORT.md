# ACADEMIC PROJECT REPORT
## Campus Complaint Priority System (CampusCare)

---

### 1. TITLE & INTRODUCTION
- **Project Title:** Campus Complaint Priority System (CampusCare)
- **Domain:** Web Technologies, Object-Oriented Software Engineering (Java 21), Algorithmic Decision Systems
- **Platform:** Pure Java LTS (JDK 21) & Modern Vanilla Web Stack (HTML5 / CSS3 / ES6 JavaScript)
- **Architecture:** Client-Server RESTful Single-Page Application (SPA)

---

### 2. PROBLEM STATEMENT & OBJECTIVE
In traditional academic institutions, campus facility complaints (such as electrical hazards, leaking water pipelines, Wi-Fi breakdowns in computer labs, or broken lecture hall projectors) are handled using a **First-Come-First-Serve (FCFS)** queue. 

**Drawbacks of Existing Systems:**
1. A trivial cosmetic report submitted at 9:00 AM takes precedence over an emergency safety hazard (e.g., exposed sparking wires) reported at 9:15 AM.
2. Multiple students facing the same issue (e.g., Wi-Fi outage during an online exam) submit individual duplicate tickets, overwhelming the administration.
3. Minor issues remain permanently unaddressed or "starved" in priority queues if higher urgency tickets keep arriving.

**Objective of CampusCare:**
To design and implement a dynamic, multi-factor priority algorithm in Java that evaluates real-world urgency, campus geographic clusters, collective student endorsements, and waiting days to assign strict Service Level Agreement (SLA) deadlines.

---

### 3. MATHEMATICAL PRIORITY ALGORITHM
The core algorithm is implemented in `PriorityEngine.java`:

$$\text{Priority Score} = \text{Urgency Weight} + \text{Cluster Outage Bonus} + \text{Student Endorsements} + \text{Anti-Starvation Aging} + \text{Category Weight}$$

#### Component Breakdown:
1. **Urgency Base Points ($U$):**
   - Low: $5\text{ pts}$ (Cosmetic, non-disruptive)
   - Medium: $12\text{ pts}$ (Noticeable inconvenience)
   - High: $22\text{ pts}$ (Lecture / lab disruption)
   - Emergency: $32\text{ pts}$ (Electrical spark, flooding, physical safety hazard)
2. **Cluster Multiplier ($C$):**
   - $+4\text{ pts}$ for each open complaint sharing the same campus location and category.
3. **Democratic Endorsements / "Me Too!" ($E$):**
   - $+3\text{ pts}$ per upvote from other students, preventing duplicate ticket creation while boosting impact.
4. **Anti-Starvation Aging Factor ($A$):**
   - $+2\text{ pts}$ per elapsed waiting day, guaranteeing that older low-priority complaints eventually escalate to the top.
5. **Category Utility Weight ($W$):**
   - Electricity / Security: $+3\text{ pts}$
   - Water / Equipment: $+2\text{ pts}$
   - Wi-Fi / Sanitation: $+1\text{ pt}$

#### Priority Bands & SLA Classification:
- 🔴 **Critical ($\ge 35\text{ pts}$):** Immediate response required (**SLA: < 4 Hours**)
- 🟠 **High ($25 - 34\text{ pts}$):** Urgent campus action (**SLA: < 24 Hours**)
- 🔵 **Medium ($15 - 24\text{ pts}$):** Scheduled maintenance (**SLA: < 3 Days**)
- 🟢 **Low ($< 15\text{ pts}$):** Routine review (**SLA: < 7 Days**)

---

### 4. SYSTEM MODULES & OOP ARCHITECTURE

```
                      +-----------------------------+
                      |   Browser Frontend (SPA)    |
                      |  HTML5 / Modern CSS / JS    |
                      +--------------+--------------+
                                     |  HTTP REST (JSON)
                                     v
                      +-----------------------------+
                      |    CampusCareServer.java    |
                      | (Built-in Java HttpServer)  |
                      +--------------+--------------+
                                     |
             +-----------------------+-----------------------+
             |                                               |
             v                                               v
+-------------------------+                     +-------------------------+
|   PriorityEngine.java   |                     | ComplaintRepository.java|
| (Algorithmic Calculator)|                     | (Thread-Safe Data Layer)|
+-------------------------+                     +------------+------------+
             |                                               |
             v                                               v
+-------------------------+                     +-------------------------+
|     Complaint.java      |                     |   complaints.tsv (DB)   |
|     (Domain Model)      |                     |    Atomic File Store    |
+-------------------------+                     +-------------------------+
```

1. **`Complaint.java`:** Domain model encapsulating complaint properties (`id`, `title`, `studentName`, `rollNumber`, `urgency`, `location`, `priorityScore`, `status`, `department`, `adminRemark`, `upvotes`).
2. **`PriorityEngine.java`:** Dedicated calculation engine implementing dynamic scoring and SLA mapping.
3. **`ComplaintRepository.java`:** Thread-safe DAO implementing synchronized read/write, duplicate cluster search, and CSV export.
4. **`CampusCareServer.java`:** Multi-threaded HTTP server (`Executors.newFixedThreadPool(10)`) handling routing, static asset delivery, and REST APIs.
5. **`JsonUtil.java`:** Custom JSON parsing and serialization utility with zero external dependencies.

---

### 5. TEAM WORK DIVISION (4 MEMBERS)

| Member | Assigned Module | Key Contributions |
|---|---|---|
| **Member 1** (Team Lead) | Priority Algorithm & Model | Implemented `PriorityEngine.java` and `Complaint.java`. Designed the mathematical scoring model, anti-starvation aging, and Max-Heap priority queue comparator logic. |
| **Member 2** | Backend & REST Server | Implemented `CampusCareServer.java` and `JsonUtil.java`. Configured the multi-threaded Java HTTP server, JSON parsers, CORS headers, and REST routing endpoints. |
| **Member 3** | Data Layer & Persistence | Implemented `ComplaintRepository.java` and `complaints.tsv`. Built synchronized file persistence, location cluster outage detection, and CSV export functionality. |
| **Member 4** | Frontend UI & History | Implemented `index.html`, `styles.css`, and `app.js`. Designed the glassmorphic dashboard, Student/Admin role switcher, live priority simulator, and 5-stage resolution audit history timeline. |

---

### 6. HOW TO RUN THE PROJECT

#### Method 1: 1-Click Execution (Windows)
Double-click **`run.bat`** in the project root directory.

#### Method 2: Command Line (PowerShell / Terminal)
```powershell
cd C:\Users\Ayona\OneDrive\Desktop\Campus-Complaint-Priority-System
javac -d out src/com/campuscare/*.java
java -cp out com.campuscare.CampusCareServer
```
Open **`http://localhost:8080`** in any web browser.

---

### 7. CONCLUSION
CampusCare demonstrates a practical, scalable, and dependency-free application of core Java concepts, multi-threading, algorithmic prioritization, and modern web interfaces to transform campus grievance resolution.
