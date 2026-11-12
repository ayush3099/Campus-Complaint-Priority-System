package com.campuscare;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ComplaintRepository {
    private final List<Complaint> complaints = new ArrayList<>();
    private final Path dataFile;
    private int nextId = 1;

    public ComplaintRepository(Path dataFile) throws IOException {
        this.dataFile = dataFile;
        if (dataFile.getParent() != null) {
            Files.createDirectories(dataFile.getParent());
        }
        load();
        if (complaints.isEmpty()) {
            seedDemoData();
        }
    }

    public synchronized List<Complaint> findAll() {
        recalculateAllScores();
        return complaints.stream()
                .sorted(Comparator.comparingInt(Complaint::getPriorityScore).reversed()
                        .thenComparing(Complaint::getCreatedAt))
                .toList();
    }

    public synchronized Optional<Complaint> findById(int id) {
        return complaints.stream().filter(c -> c.getId() == id).findFirst();
    }

    public synchronized Complaint add(String studentName, String rollNumber, String title,
                                      String description, String category, String location,
                                      int urgency) throws IOException {
        int similar = countSimilar(category, location);
        int score = PriorityEngine.calculateScore(urgency, category, similar, 0, LocalDateTime.now());

        Complaint complaint = new Complaint(
                nextId++,
                studentName.trim(),
                rollNumber.trim(),
                title.trim(),
                description.trim(),
                category.trim(),
                location.trim(),
                urgency,
                score,
                "Submitted",
                Complaint.defaultDepartmentFor(category),
                "Awaiting administrative review.",
                0,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        complaints.add(complaint);
        recalculateAllScores();
        save();
        return complaint;
    }

    public synchronized Optional<Complaint> updateStatus(int id, String status, String adminRemark, String department) throws IOException {
        Optional<Complaint> item = findById(id);
        if (item.isPresent()) {
            Complaint c = item.get();
            c.setStatus(status);
            if (adminRemark != null && !adminRemark.isBlank()) {
                c.setAdminRemark(adminRemark.trim());
            }
            if (department != null && !department.isBlank()) {
                c.setDepartment(department.trim());
            }
            recalculateAllScores();
            save();
        }
        return item;
    }

    public synchronized Optional<Complaint> upvote(int id) throws IOException {
        Optional<Complaint> item = findById(id);
        if (item.isPresent()) {
            Complaint c = item.get();
            c.incrementUpvote();
            recalculateAllScores();
            save();
        }
        return item;
    }

    public synchronized boolean delete(int id) throws IOException {
        boolean removed = complaints.removeIf(c -> c.getId() == id);
        if (removed) {
            recalculateAllScores();
            save();
        }
        return removed;
    }

    public synchronized int countSimilar(String category, String location) {
        return (int) complaints.stream()
                .filter(c -> !"Resolved".equalsIgnoreCase(c.getStatus()) && !"Rejected".equalsIgnoreCase(c.getStatus()))
                .filter(c -> c.getCategory().equalsIgnoreCase(category))
                .filter(c -> c.getLocation().equalsIgnoreCase(location))
                .count();
    }

    private synchronized void recalculateAllScores() {
        for (Complaint c : complaints) {
            if ("Resolved".equalsIgnoreCase(c.getStatus()) || "Rejected".equalsIgnoreCase(c.getStatus())) {
                continue;
            }
            int similar = (int) complaints.stream()
                    .filter(other -> other.getId() != c.getId())
                    .filter(other -> !"Resolved".equalsIgnoreCase(other.getStatus()) && !"Rejected".equalsIgnoreCase(other.getStatus()))
                    .filter(other -> other.getCategory().equalsIgnoreCase(c.getCategory()))
                    .filter(other -> other.getLocation().equalsIgnoreCase(c.getLocation()))
                    .count();
            int newScore = PriorityEngine.calculateScore(c.getUrgency(), c.getCategory(), similar, c.getUpvotes(), c.getCreatedAt());
            c.setPriorityScore(newScore);
        }
    }

    public synchronized String exportCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Student Name,Roll Number,Title,Category,Location,Urgency,Priority Score,Priority Band,Status,Department,Upvotes,Reported At,Admin Remark\n");
        for (Complaint c : findAll()) {
            csv.append(String.format("\"#CC-%03d\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%d\",\"%d\",\"%s\",\"%s\",\"%s\",\"%d\",\"%s\",\"%s\"\n",
                    c.getId(),
                    escapeCsv(c.getStudentName()),
                    escapeCsv(c.getRollNumber()),
                    escapeCsv(c.getTitle()),
                    escapeCsv(c.getCategory()),
                    escapeCsv(c.getLocation()),
                    c.getUrgency(),
                    c.getPriorityScore(),
                    c.priorityLabel(),
                    c.getStatus(),
                    escapeCsv(c.getDepartment()),
                    c.getUpvotes(),
                    c.getCreatedAt().toString(),
                    escapeCsv(c.getAdminRemark())
            ));
        }
        return csv.toString();
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }

    private void load() throws IOException {
        if (!Files.exists(dataFile)) return;
        List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
        for (String line : lines) {
            String[] p = line.split("\\t", -1);
            if (p.length < 11) continue;
            try {
                int id = Integer.parseInt(p[0]);
                String studentName = unescape(p[1]);
                String rollNumber = unescape(p[2]);
                String title = unescape(p[3]);
                String description = unescape(p[4]);
                String category = unescape(p[5]);
                String location = unescape(p[6]);
                int urgency = Integer.parseInt(p[7]);
                int priorityScore = Integer.parseInt(p[8]);
                String status = unescape(p[9]);
                LocalDateTime createdAt = LocalDateTime.parse(p[10]);

                String department = p.length > 11 ? unescape(p[11]) : Complaint.defaultDepartmentFor(category);
                String adminRemark = p.length > 12 ? unescape(p[12]) : "No admin remarks yet.";
                int upvotes = p.length > 13 ? Integer.parseInt(p[13]) : 0;
                LocalDateTime updatedAt = p.length > 14 ? LocalDateTime.parse(p[14]) : createdAt;

                Complaint c = new Complaint(id, studentName, rollNumber, title, description,
                        category, location, urgency, priorityScore, status, department, adminRemark, upvotes, createdAt, updatedAt);
                complaints.add(c);
                nextId = Math.max(nextId, id + 1);
            } catch (Exception ignored) {
            }
        }
    }

    private void save() throws IOException {
        List<String> lines = complaints.stream().map(c -> String.join("\t",
                String.valueOf(c.getId()),
                escape(c.getStudentName()),
                escape(c.getRollNumber()),
                escape(c.getTitle()),
                escape(c.getDescription()),
                escape(c.getCategory()),
                escape(c.getLocation()),
                String.valueOf(c.getUrgency()),
                String.valueOf(c.getPriorityScore()),
                escape(c.getStatus()),
                c.getCreatedAt().toString(),
                escape(c.getDepartment()),
                escape(c.getAdminRemark()),
                String.valueOf(c.getUpvotes()),
                c.getUpdatedAt().toString()
        )).toList();
        Files.write(dataFile, lines, StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private String unescape(String value) {
        if (value == null) return "";
        return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    private void seedDemoData() throws IOException {
        Complaint c1 = add("Aryan Agnihotri", "2510003121", "Broken classroom projector",
                "Projector flickers and shuts down randomly during morning lectures.", "Equipment", "Room 301", 3);
        c1.incrementUpvote();
        c1.incrementUpvote();

        Complaint c2 = add("Riya Sharma", "2510003104", "Water cooler leaking and not cooling",
                "Water leaking onto hallway floor creating slipping hazard.", "Water", "Block B", 3);
        c2.incrementUpvote();

        Complaint c3 = add("Kabir Mehta", "2510003072", "Wi-Fi disconnects in AI Lab",
                "Connection drops intermittently during online test practicals.", "Wi-Fi", "Lab 204", 2);

        Complaint c4 = add("Mehak Kaur", "2510003155", "Overflowing dustbin near canteen",
                "Waste has accumulated since yesterday evening attracting flies.", "Cleanliness", "Main Canteen", 2);

        Complaint c5 = add("Aman Verma", "2510003019", "Sparking switchboard near water dispenser",
                "Exposed wiring and sparks observed when turning on the switch.", "Electricity", "Ground Floor Corridor", 4);
        c5.incrementUpvote();
        c5.incrementUpvote();
        c5.incrementUpvote();

        c3.setStatus("In Progress");
        c3.setAdminRemark("Network team is replacing the Cisco Access Point antenna today.");

        c4.setStatus("Resolved");
        c4.setAdminRemark("Canteen area cleaned and extra bin placed by housekeeping.");

        recalculateAllScores();
        save();
    }
}
