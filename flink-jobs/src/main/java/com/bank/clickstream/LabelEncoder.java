package com.bank.clickstream;

import java.io.Serializable;

public class LabelEncoder implements Serializable {
    // Your exact attack type classes in order
    private static final String[] CLASSES = {
            "Bots", // 0
            "Brute Force", // 1
            "DDoS", // 2
            "DoS", // 3
            "Normal Traffic", // 4
            "Port Scanning", // 5
            "Web Attacks" // 6
    };

    public int labelToIndex(String label) {
        for (int i = 0; i < CLASSES.length; i++) {
            if (CLASSES[i].equals(label)) {
                return i;
            }
        }
        return -1; // Unknown label
    }

    public String indexToLabel(int index) {
        if (index >= 0 && index < CLASSES.length) {
            return CLASSES[index];
        }
        return "Unknown";
    }

    public String[] getAllClasses() {
        return CLASSES.clone();
    }
}