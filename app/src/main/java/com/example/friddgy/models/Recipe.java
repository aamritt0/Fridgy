package com.example.friddgy.models;

import java.io.Serializable;
import java.util.List;

public class Recipe implements Serializable {
    private String id;
    private String title;
    private String description;
    private String time;
    private String difficulty;
    private double rating;
    private List<Ingredient> ingredients;
    private double proteins;
    private double fats;
    private double carbs;
    private List<String> steps;
    private String tips;
    private String imageUrl;

    public Recipe(String id, String title, String description, String time, String difficulty, double rating,
                  List<Ingredient> ingredients, double proteins, double fats, double carbs,
                  List<String> steps, String tips, String imageUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.time = time;
        this.difficulty = difficulty;
        this.rating = rating;
        this.ingredients = ingredients;
        this.proteins = proteins;
        this.fats = fats;
        this.carbs = carbs;
        this.steps = steps;
        this.tips = tips;
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTime() { return time; }
    public String getDifficulty() { return difficulty; }
    public double getRating() { return rating; }
    public List<Ingredient> getIngredients() { return ingredients; }
    public double getProteins() { return proteins; }
    public double getFats() { return fats; }
    public double getCarbs() { return carbs; }
    public List<String> getSteps() { return steps; }
    public String getTips() { return tips; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
