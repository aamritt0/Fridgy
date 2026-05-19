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

    public String toJson() {
        try {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("id", id);
            obj.put("title", title);
            obj.put("description", description);
            obj.put("time", time);
            obj.put("difficulty", difficulty);
            obj.put("rating", rating);
            obj.put("proteins", proteins);
            obj.put("fats", fats);
            obj.put("carbs", carbs);
            obj.put("tips", tips);
            obj.put("imageUrl", imageUrl);

            org.json.JSONArray ingArr = new org.json.JSONArray();
            for (Ingredient ing : ingredients) {
                org.json.JSONObject ingObj = new org.json.JSONObject();
                ingObj.put("name", ing.getName());
                ingObj.put("amount", ing.getAmount());
                ingObj.put("emoji", ing.getEmoji());
                ingArr.put(ingObj);
            }
            obj.put("ingredients", ingArr);

            org.json.JSONArray stepArr = new org.json.JSONArray();
            for (String step : steps) {
                stepArr.put(step);
            }
            obj.put("steps", stepArr);

            return obj.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static Recipe fromJson(String jsonStr) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
            String id = obj.getString("id");
            String title = obj.getString("title");
            String description = obj.getString("description");
            String time = obj.getString("time");
            String difficulty = obj.getString("difficulty");
            double rating = obj.getDouble("rating");
            double proteins = obj.getDouble("proteins");
            double fats = obj.getDouble("fats");
            double carbs = obj.getDouble("carbs");
            String tips = obj.optString("tips", "");
            String imageUrl = obj.getString("imageUrl");

            List<Ingredient> ingredients = new java.util.ArrayList<>();
            org.json.JSONArray ingArr = obj.getJSONArray("ingredients");
            for (int i = 0; i < ingArr.length(); i++) {
                org.json.JSONObject ingObj = ingArr.getJSONObject(i);
                ingredients.add(new Ingredient(
                        ingObj.getString("name"),
                        ingObj.getString("amount"),
                        ingObj.getString("emoji")
                ));
            }

            List<String> steps = new java.util.ArrayList<>();
            org.json.JSONArray stepArr = obj.getJSONArray("steps");
            for (int i = 0; i < stepArr.length(); i++) {
                steps.add(stepArr.getString(i));
            }

            return new Recipe(id, title, description, time, difficulty, rating, ingredients, proteins, fats, carbs, steps, tips, imageUrl);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
