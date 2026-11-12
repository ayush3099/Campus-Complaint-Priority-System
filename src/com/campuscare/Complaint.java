package com.campuscare;

import java.time.Duration;
import java.time.LocalDateTime;

public class Complaint {
    private final int id;
    private final String studentName;
    private final String rollNumber;
    private final String title;
    private final String description;
    private final String category;
    private final String location;
    private final int urgency;
    private int priorityScore;
    private String status;
    private String department;
    private String adminRemark;
    private int upvotes;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Complaint(int id, String studentName, String rollNumber, String title,
                     String description, String category, String location,
                     int urgency, int priorityScore, String status,
                     String department, String adminRemark, int upvotes,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.studentName = studentName;
        this.rollNumber = rollNumber;
        this.title = title;
        this.description = description;
        this.category = category;
        this.location = location;
        this.urgency = urgency;
        this.priorityScore = priorityScore;
        this.status = status;
        this.department = department != null && !department.isBlank() ? department : defaultDepartmentFor(category);
        this.adminRemark = adminRemark != null ? adminRemark : "";
        this.upvotes = Math.max(0, upvotes);
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static String defaultDepartmentFor(String category) {
        if (category == null) return "Campus Maintenance";
        return switch (category.toLowerCase()) {
            case "electricity" -> "Electrical Department";
            case "water" -> "Estate & Plumbing Cell";
            case "wi-fi" -> "IT & Network Infrastructure";
            case "equipment" -> "Academic Labs & AV Support";
            case "cleanliness" -> "Housekeeping & Sanitation";
            case "hostel" -> "Hostel Administration";
            case "canteen" -> "Food & Hospitality Committee";
            case "security" -> "Campus Safety & Vigilance";
            case "transport" -> "Transport Operations";
            default -> "Estate Office";
        };
    }

    public int getId() { return id; }
    public String getStudentName() { return studentName; }
    public String getRollNumber() { return rollNumber; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getLocation() { return location; }
    public int getUrgency() { return urgency; }
    public int getPriorityScore() { return priorityScore; }
    public String getStatus() { return status; }
    public String getDepartment() { return department; }
    public String getAdminRemark() { return adminRemark; }
    public int getUpvotes() { return upvotes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setPriorityScore(int priorityScore) { this.priorityScore = priorityScore; }
    public void setStatus(String status) { 
        this.status = status; 
        this.updatedAt = LocalDateTime.now();
    }
    public void setDepartment(String department) { this.department = department; }
    public void setAdminRemark(String adminRemark) { 
        this.adminRemark = adminRemark; 
        this.updatedAt = LocalDateTime.now();
    }
    public void incrementUpvote() {
        this.upvotes++;
        this.updatedAt = LocalDateTime.now();
    }

    public String priorityLabel() {
        return PriorityEngine.getPriorityBand(this.priorityScore);
    }

    public String expectedSla() {
        return PriorityEngine.getExpectedSla(priorityLabel());
    }

    public long getWaitingDays() {
        return Math.max(0, Duration.between(createdAt, LocalDateTime.now()).toDays());
    }

    public String formattedCode() {
        return String.format("#CC-%03d", id);
    }
}
