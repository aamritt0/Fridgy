package com.example.friddgy.models;

import java.io.Serializable;

public class Ingredient implements Serializable {
    private String name;
    private String amount;
    private String emoji;

    public Ingredient(String name, String amount, String emoji) {
        this.name = name;
        this.amount = amount;
        this.emoji = emoji;
    }

    public String getName() {
        return name;
    }

    public String getAmount() {
        return amount;
    }

    public String getEmoji() {
        return emoji;
    }
}
