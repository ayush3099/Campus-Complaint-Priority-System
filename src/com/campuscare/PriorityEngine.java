package com.campuscare;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Dedicated Priority Calculation and Escalation Engine.
 * Implements a weighted algorithmic scoring model to rank campus issues by real-world impact.
 *
 * Formula:
 * Score = BaseUrgencyWeight + (ClusterReports * 4) + (StudentUpvotes * 3) + (WaitingDays * 2) + CategoryFactor
 */
public class PriorityEngine {

    public static int calculateScore(int urgency, String category, int similarCount, int upvotes, LocalDateTime createdAt) {
        // 1. Urgency Base Points
        int urgencyPoints = switch (urgency) {
            case 4 -> 32; // Emergency (Safety, power hazard, flood)
            case 3 -> 22; // High (Affects entire class/lab)
            case 2 -> 12; // Medium (Noticeable inconvenience)
            default -> 5; // Low (Cosmetic, minor)
        };

        // 2. Category Weight (Critical campus utilities get slight priority boost)
        int categoryFactor = switch (category != null ? category.toLowerCase() : "") {
            case "electricity", "security" -> 3;
            case "water", "equipment" -> 2;
            case "wi-fi", "cleanliness" -> 1;
            default -> 0;
        };

        // 3. Cluster Impact (Multiple students complaining about the same venue/issue)
        int clusterPoints = Math.min(similarCount * 4, 16); // capped at 16 to avoid runaway scores

        // 4. Student Endorsements ("Me Too" upvotes)
        int upvotePoints = Math.min(upvotes * 3, 15);

        // 5. Aging / Anti-Starvation Factor (Adds 2 points per waiting day)
        long waitingDays = Math.max(0, Duration.between(createdAt != null ? createdAt : LocalDateTime.now(), LocalDateTime.now()).toDays());
        int agingPoints = (int) Math.min(waitingDays * 2, 20);

        return urgencyPoints + categoryFactor + clusterPoints + upvotePoints + agingPoints;
    }

    public static String getPriorityBand(int score) {
        if (score >= 35) return "Critical";
        if (score >= 25) return "High";
        if (score >= 15) return "Medium";
        return "Low";
    }

    public static String getExpectedSla(String priorityBand) {
        return switch (priorityBand) {
            case "Critical" -> "Within 4 Hours";
            case "High" -> "Within 24 Hours";
            case "Medium" -> "Within 3 Days";
            default -> "Within 7 Days";
        };
    }
}
