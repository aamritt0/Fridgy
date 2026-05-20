package com.example.friddgy;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.friddgy.models.Ingredient;
import com.example.friddgy.models.Recipe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;

public class HomeActivity extends AppCompatActivity {

    // Use the API key from BuildConfig if available, otherwise fallback
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY_HERE")
            ? "YOUR_GEMINI_API_KEY_HERE"
            : BuildConfig.GEMINI_API_KEY;

    private List<String> currentIngredients = new ArrayList<>();
    private List<Recipe> currentRecipes = new ArrayList<>();
    private List<Recipe> generatedRecipes = new ArrayList<>();
    private Map<String, List<Recipe>> mealDbCache = new HashMap<>();

    private LinearLayout containerRecents, containerFavorites;
    private View sectionRecents, sectionFavorites;
    private String activeTab = "Breakfast";

    private LinearLayout linearIngredients;
    private View scrollIngredients;
    private Button btnGenerate;
    private ProgressBar progressBar;
    private TextView tvRecipeCount;
    private LinearLayout containerRecipes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        linearIngredients = findViewById(R.id.linear_ingredients);
        scrollIngredients = findViewById(R.id.scroll_ingredients);
        btnGenerate = findViewById(R.id.btn_generate);
        progressBar = findViewById(R.id.progress_bar);
        tvRecipeCount = findViewById(R.id.tv_recipe_count);
        containerRecipes = findViewById(R.id.container_recipes);

        ImageView ivProfile = findViewById(R.id.iv_profile);
        if (ivProfile != null) {
            ThemeManager.applyTouchScaleAnimation(ivProfile, () -> showCustomizationDialog());
        }

        containerRecents = findViewById(R.id.container_recents);
        containerFavorites = findViewById(R.id.container_favorites);
        sectionRecents = findViewById(R.id.section_recents);
        sectionFavorites = findViewById(R.id.section_favorites);

        View catBreakfast = findViewById(R.id.cat_breakfast);
        View catLunch = findViewById(R.id.cat_lunch);
        View catDinner = findViewById(R.id.cat_dinner);
        View catDessert = findViewById(R.id.cat_dessert);
        View catSalads = findViewById(R.id.cat_salads);

        if (catBreakfast != null) catBreakfast.setOnClickListener(v -> switchTab("Breakfast"));
        if (catLunch != null) catLunch.setOnClickListener(v -> switchTab("Lunch"));
        if (catDinner != null) catDinner.setOnClickListener(v -> switchTab("Dinner"));
        if (catDessert != null) catDessert.setOnClickListener(v -> switchTab("Dessert"));
        if (catSalads != null) catSalads.setOnClickListener(v -> switchTab("Salads"));

        EditText etSearch = findViewById(R.id.et_search);
        TextView btnAddIngredient = findViewById(R.id.btn_add_ingredient);

        if (btnAddIngredient != null) {
            ThemeManager.applyTouchScaleAnimation(btnAddIngredient, () -> {
                String ingredient = etSearch.getText().toString().trim();
                if (!ingredient.isEmpty() && !currentIngredients.contains(ingredient.toLowerCase())) {
                    currentIngredients.add(ingredient.toLowerCase());
                    etSearch.setText("");
                    updateIngredientsUI();
                }
            });
        }

        if (btnGenerate != null) {
            ThemeManager.applyTouchScaleAnimation(btnGenerate, () -> generateRecipesFromGemini());
        }

        loadDefaultRecipes();
        applyCustomizations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        populateHorizontalSection(containerRecents, sectionRecents, loadRecents());
        populateHorizontalSection(containerFavorites, sectionFavorites, loadFavorites());
    }

    private void updateIngredientsUI() {
        linearIngredients.removeAllViews();
        if (currentIngredients.isEmpty()) {
            scrollIngredients.setVisibility(View.GONE);
            btnGenerate.setVisibility(View.GONE);
            return;
        }

        scrollIngredients.setVisibility(View.VISIBLE);
        btnGenerate.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (String ingredient : currentIngredients) {
            View pillView = inflater.inflate(R.layout.item_ingredient_pill, linearIngredients, false);
            TextView tvName = pillView.findViewById(R.id.tv_name);
            tvName.setText(ingredient);

            pillView.findViewById(R.id.btn_remove).setOnClickListener(v -> {
                currentIngredients.remove(ingredient);
                updateIngredientsUI();
            });

            linearIngredients.addView(pillView);
        }
    }

    private void generateRecipesFromGemini() {
        if (currentIngredients.isEmpty()) return;

        progressBar.setVisibility(View.VISIBLE);
        btnGenerate.setEnabled(false);

        new Thread(() -> {
            try {
                if (GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY_HERE")) {
                    Thread.sleep(1500); // Simulate network
                    Recipe r1 = createMockRecipe("Gemini generated 1");
                    Recipe r2 = createMockRecipe("Gemini generated 2");
                    currentRecipes.clear();
                    currentRecipes.add(r1);
                    currentRecipes.add(r2);
                } else {
                    // Build JSON request manually with generationConfig and responseSchema
                    JSONObject requestBody = new JSONObject();
                    JSONArray contents = new JSONArray();
                    JSONObject content = new JSONObject();
                    JSONArray parts = new JSONArray();
                    JSONObject part = new JSONObject();

                    String prompt = "I have these ingredients: " + String.join(", ", currentIngredients) + ". " +
                            "Suggest 3 creative and delicious recipes that primarily use these ingredients. " +
                            "You can assume basic pantry staples like oil, salt, pepper, and water are available. " +
                            "For each recipe, provide: " +
                            "- id: a unique short string\n" +
                            "- title: name of the dish\n" +
                            "- description: a short, appetizing summary\n" +
                            "- time: total cooking time (e.g., \"15 min\")\n" +
                            "- difficulty: \"Easy\", \"Medium\", or \"Hard\"\n" +
                            "- rating: A number between 4.0 and 5.0 representing the general user rating\n" +
                            "- ingredients: an array of objects representing required ingredients. For each, give a \"name\", an \"amount\", and an \"emoji\" that best represents it\n" +
                            "- nutrition: an estimated nutritional breakdown per serving with number values for \"proteins\", \"fats\", and \"carbs\" (in grams)\n" +
                            "- steps: numbered step-by-step instructions\n" +
                            "- tips: an optional professional chef tip\n" +
                            "- imageKeyword: a short, 1 or 2 word keyword to search for an image of this dish";

                    part.put("text", prompt);
                    parts.put(part);
                    content.put("parts", parts);
                    contents.put(content);
                    requestBody.put("contents", contents);

                    // Structured outputs configuration
                    JSONObject generationConfig = new JSONObject();
                    generationConfig.put("responseMimeType", "application/json");

                    JSONObject responseSchema = new JSONObject();
                    responseSchema.put("type", "OBJECT");

                    JSONObject properties = new JSONObject();
                    JSONObject recipesObj = new JSONObject();
                    recipesObj.put("type", "ARRAY");

                    JSONObject items = new JSONObject();
                    items.put("type", "OBJECT");

                    JSONObject itemProperties = new JSONObject();
                    itemProperties.put("id", new JSONObject().put("type", "STRING"));
                    itemProperties.put("title", new JSONObject().put("type", "STRING"));
                    itemProperties.put("description", new JSONObject().put("type", "STRING"));
                    itemProperties.put("time", new JSONObject().put("type", "STRING"));
                    itemProperties.put("difficulty", new JSONObject().put("type", "STRING"));
                    itemProperties.put("rating", new JSONObject().put("type", "NUMBER"));

                    JSONObject ingredientItem = new JSONObject();
                    ingredientItem.put("type", "OBJECT");
                    JSONObject ingredientProperties = new JSONObject();
                    ingredientProperties.put("name", new JSONObject().put("type", "STRING"));
                    ingredientProperties.put("amount", new JSONObject().put("type", "STRING"));
                    ingredientProperties.put("emoji", new JSONObject().put("type", "STRING"));
                    ingredientItem.put("properties", ingredientProperties);
                    ingredientItem.put("required", new JSONArray(Arrays.asList("name", "amount", "emoji")));

                    JSONObject ingredientsArray = new JSONObject();
                    ingredientsArray.put("type", "ARRAY");
                    ingredientsArray.put("items", ingredientItem);
                    itemProperties.put("ingredients", ingredientsArray);

                    JSONObject nutritionObj = new JSONObject();
                    nutritionObj.put("type", "OBJECT");
                    JSONObject nutritionProperties = new JSONObject();
                    nutritionProperties.put("proteins", new JSONObject().put("type", "NUMBER"));
                    nutritionProperties.put("fats", new JSONObject().put("type", "NUMBER"));
                    nutritionProperties.put("carbs", new JSONObject().put("type", "NUMBER"));
                    nutritionObj.put("properties", nutritionProperties);
                    nutritionObj.put("required", new JSONArray(Arrays.asList("proteins", "fats", "carbs")));
                    itemProperties.put("nutrition", nutritionObj);

                    JSONObject stepsArray = new JSONObject();
                    stepsArray.put("type", "ARRAY");
                    stepsArray.put("items", new JSONObject().put("type", "STRING"));
                    itemProperties.put("steps", stepsArray);

                    itemProperties.put("tips", new JSONObject().put("type", "STRING"));
                    itemProperties.put("imageKeyword", new JSONObject().put("type", "STRING"));

                    items.put("properties", itemProperties);
                    items.put("required", new JSONArray(Arrays.asList("id", "title", "description", "time", "difficulty", "rating", "ingredients", "nutrition", "steps", "imageKeyword")));

                    recipesObj.put("items", items);
                    properties.put("recipes", recipesObj);

                    responseSchema.put("properties", properties);
                    responseSchema.put("required", new JSONArray(Arrays.asList("recipes")));

                    generationConfig.put("responseSchema", responseSchema);
                    requestBody.put("generationConfig", generationConfig);

                    String responseJsonStr = null;
                    String[] models = {"gemini-3-flash-preview", "gemini-2.5-flash", "gemini-1.5-flash"};
                    for (String model : models) {
                        try {
                            Log.d("GeminiCall", "Trying to generate recipe using model: " + model);
                            responseJsonStr = makeGeminiCall(model, requestBody.toString());
                            break; // Success!
                        } catch (Exception e) {
                            Log.w("GeminiCall", model + " failed: " + e.getMessage() + ". Retrying with next model...");
                            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                        }
                    }

                    if (responseJsonStr == null) {
                        throw new Exception("All models failed to generate recipes. Please try again later.");
                    }

                    JSONObject responseJson = new JSONObject(responseJsonStr);
                    JSONArray candidates = responseJson.getJSONArray("candidates");
                    if (candidates.length() > 0) {
                        JSONObject candidate = candidates.getJSONObject(0);
                        JSONObject contentObj = candidate.getJSONObject("content");
                        JSONArray partsVal = contentObj.getJSONArray("parts");
                        if (partsVal.length() > 0) {
                            String recipeJsonText = partsVal.getJSONObject(0).getString("text");

                            JSONObject parsed = new JSONObject(recipeJsonText);
                            JSONArray recipesJson = parsed.getJSONArray("recipes");
                            currentRecipes.clear();
                            for (int i = 0; i < recipesJson.length(); i++) {
                                JSONObject rObj = recipesJson.getJSONObject(i);
                                String id = rObj.getString("id");
                                String title = rObj.getString("title");
                                String desc = rObj.getString("description");
                                String time = rObj.getString("time");
                                String diff = rObj.getString("difficulty");
                                double rating = rObj.getDouble("rating");

                                JSONArray ingArray = rObj.getJSONArray("ingredients");
                                List<Ingredient> ingredientsList = new ArrayList<>();
                                for (int j = 0; j < ingArray.length(); j++) {
                                    JSONObject ingObj = ingArray.getJSONObject(j);
                                    ingredientsList.add(new Ingredient(
                                            ingObj.getString("name"),
                                            ingObj.getString("amount"),
                                            ingObj.getString("emoji")
                                    ));
                                }

                                JSONObject nutrObj = rObj.getJSONObject("nutrition");
                                double proteins = nutrObj.getDouble("proteins");
                                double fats = nutrObj.getDouble("fats");
                                double carbs = nutrObj.getDouble("carbs");

                                JSONArray stepsArrayJson = rObj.getJSONArray("steps");
                                List<String> stepsList = new ArrayList<>();
                                for (int j = 0; j < stepsArrayJson.length(); j++) {
                                    stepsList.add(stepsArrayJson.getString(j));
                                }

                                String tips = rObj.optString("tips", "");
                                String imageKeyword = rObj.getString("imageKeyword");

                                // Fetch image from MealDB
                                String imageUrl = fetchMealDbImage(imageKeyword, title);

                                currentRecipes.add(new Recipe(
                                        id, title, desc, time, diff, rating,
                                        ingredientsList, proteins, fats, carbs,
                                        stepsList, tips, imageUrl
                                ));
                            }
                        }
                    }
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnGenerate.setEnabled(true);
                    generatedRecipes.clear();
                    generatedRecipes.addAll(currentRecipes);
                    updateRecipesUI();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnGenerate.setEnabled(true);
                    Toast.makeText(HomeActivity.this, "Failed to generate: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void loadDefaultRecipes() {
        switchTab("Breakfast");
    }

    private Recipe createMockRecipe(String title) {
        return new Recipe(
                String.valueOf(System.currentTimeMillis()),
                title,
                "A delicious meal.",
                "15 min",
                "Medium",
                4.8,
                Arrays.asList(new Ingredient("Pork", "100g", "🥩"), new Ingredient("Noodle", "200g", "🍜")),
                3.45, 10.69, 22.72,
                Arrays.asList("Boil water.", "Add noodles.", "Enjoy."),
                "Add some extra soy sauce for flavor.",
                getFallbackImage(title)
        );
    }

    private void updateRecipesUI() {
        String label = activeTab.toLowerCase();
        tvRecipeCount.setText(String.format("%02d %s recipes", currentRecipes.size(), label));
        containerRecipes.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout currentRow = null;

        for (int i = 0; i < currentRecipes.size(); i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                currentRow.setClipChildren(false);
                currentRow.setClipToPadding(false);
                containerRecipes.addView(currentRow);
            }

            Recipe recipe = currentRecipes.get(i);
            View cardView = inflater.inflate(R.layout.item_recipe_card, currentRow, false);
            
            // Staggered layout simulation
            if (i % 2 != 0) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) cardView.getLayoutParams();
                params.topMargin = 120; // Stagger effect
                cardView.setLayoutParams(params);
            }

            ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);
            int accentColor = Color.parseColor(theme.accentColor);

            TextView tvTitle = cardView.findViewById(R.id.item_title);
            ImageView ivImage = cardView.findViewById(R.id.item_image);

            tvTitle.setText(recipe.getTitle());

            // Set the 5-star rating programmatically
            ImageView star1 = cardView.findViewById(R.id.star1);
            ImageView star2 = cardView.findViewById(R.id.star2);
            ImageView star3 = cardView.findViewById(R.id.star3);
            ImageView star4 = cardView.findViewById(R.id.star4);
            ImageView star5 = cardView.findViewById(R.id.star5);

            ImageView[] stars = {star1, star2, star3, star4, star5};
            double rating = recipe.getRating();
            int roundedRating = (int) Math.round(rating);

            for (int j = 0; j < 5; j++) {
                if (stars[j] != null) {
                    if (j < roundedRating) {
                        stars[j].setImageResource(android.R.drawable.star_on);
                        stars[j].setImageTintList(ColorStateList.valueOf(accentColor));
                    } else {
                        stars[j].setImageResource(android.R.drawable.star_off);
                        stars[j].setImageTintList(ColorStateList.valueOf(Color.parseColor("#4b4c53"))); // dark grey unselected
                    }
                }
            }

            if (ivImage != null) {
                ivImage.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                ivImage.setClipToOutline(true);
            }

            // Load image
            loadImage(recipe.getImageUrl(), ivImage);

            ThemeManager.applyTouchScaleAnimation(cardView, () -> {
                Intent intent = new Intent(HomeActivity.this, DetailActivity.class);
                intent.putExtra("RECIPE", recipe);
                startActivity(intent);
            });

            currentRow.addView(cardView);
        }
    }

    private void switchTab(String categoryName) {
        activeTab = categoryName;
        if (categoryName.equals("Breakfast")) {
            loadMealDbRecipes("Breakfast", "Breakfast");
        } else if (categoryName.equals("Lunch")) {
            loadMealDbRecipes("Chicken", "Lunch");
        } else if (categoryName.equals("Dinner")) {
            loadMealDbRecipes("Beef", "Dinner");
        } else if (categoryName.equals("Dessert")) {
            loadMealDbRecipes("Dessert", "Dessert");
        } else if (categoryName.equals("Salads")) {
            loadMealDbRecipes("Vegetarian", "Salads");
        }
    }

    private List<Recipe> loadFavorites() {
        List<Recipe> list = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences("favorites", Context.MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String value = entry.getValue().toString();
            if (value.startsWith("{")) {
                Recipe recipe = Recipe.fromJson(value);
                if (recipe != null) {
                    list.add(recipe);
                }
            } else {
                list.add(createMockRecipe(value));
            }
        }
        return list;
    }

    private List<Recipe> loadRecents() {
        List<Recipe> list = new ArrayList<>();
        SharedPreferences recentsPrefs = getSharedPreferences("recents", Context.MODE_PRIVATE);
        String recentsStr = recentsPrefs.getString("list", "[]");
        try {
            JSONArray arr = new JSONArray(recentsStr);
            for (int i = 0; i < arr.length(); i++) {
                Recipe r = Recipe.fromJson(arr.getString(i));
                if (r != null) {
                    list.add(r);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void populateHorizontalSection(LinearLayout container, View sectionLayout, List<Recipe> recipes) {
        container.removeAllViews();
        if (recipes == null || recipes.isEmpty()) {
            sectionLayout.setVisibility(View.GONE);
            return;
        }
        sectionLayout.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Recipe recipe : recipes) {
            View cardView = inflater.inflate(R.layout.item_recipe_card, container, false);
            
            // Set custom layout params for horizontal scroll items
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 150, getResources().getDisplayMetrics()),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(
                (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()),
                (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics()),
                (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()),
                (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics())
            );
            cardView.setLayoutParams(params);

            TextView tvTitle = cardView.findViewById(R.id.item_title);
            ImageView ivImage = cardView.findViewById(R.id.item_image);
            LinearLayout layoutRating = cardView.findViewById(R.id.layout_rating_stars);

            tvTitle.setText(recipe.getTitle());

            // Rating Stars
            if (layoutRating != null) {
                int rating = (int) Math.round(recipe.getRating());
                ImageView[] stars = {
                    cardView.findViewById(R.id.star1),
                    cardView.findViewById(R.id.star2),
                    cardView.findViewById(R.id.star3),
                    cardView.findViewById(R.id.star4),
                    cardView.findViewById(R.id.star5)
                };
                for (int j = 0; j < 5; j++) {
                    if (stars[j] != null) {
                        if (j < rating) {
                            stars[j].setImageTintList(ColorStateList.valueOf(Color.parseColor("#ffcc00"))); // yellow gold
                        } else {
                            stars[j].setImageTintList(ColorStateList.valueOf(Color.parseColor("#4b4c53"))); // dark grey
                        }
                    }
                }
            }

            if (ivImage != null) {
                ivImage.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                ivImage.setClipToOutline(true);
                loadImage(recipe.getImageUrl(), ivImage);
            }

            ThemeManager.applyTouchScaleAnimation(cardView, () -> {
                Intent intent = new Intent(HomeActivity.this, DetailActivity.class);
                intent.putExtra("RECIPE", recipe);
                startActivity(intent);
            });

            container.addView(cardView);
        }
    }

    private void loadMealDbRecipes(String categoryName, String tabName) {
        if (mealDbCache.containsKey(tabName)) {
            currentRecipes.clear();
            currentRecipes.addAll(mealDbCache.get(tabName));
            updateRecipesUI();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                URL url = new URL("https://www.themealdb.com/api/json/v1/1/filter.php?c=" + categoryName);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    List<Recipe> fetchedRecipes = new ArrayList<>();
                    if (res.has("meals") && !res.isNull("meals")) {
                        JSONArray meals = res.getJSONArray("meals");
                        
                        List<JSONObject> mealList = new ArrayList<>();
                        for (int i = 0; i < meals.length(); i++) {
                            mealList.add(meals.getJSONObject(i));
                        }
                        Collections.shuffle(mealList);
                        int count = Math.min(mealList.size(), 12);

                        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);
                        java.util.concurrent.Future<Recipe>[] futures = new java.util.concurrent.Future[count];

                        for (int i = 0; i < count; i++) {
                            final JSONObject m = mealList.get(i);
                            futures[i] = executor.submit(() -> {
                                try {
                                    String idMeal = m.getString("idMeal");
                                    URL detailsUrl = new URL("https://www.themealdb.com/api/json/v1/1/lookup.php?i=" + idMeal);
                                    HttpURLConnection detailsConn = (HttpURLConnection) detailsUrl.openConnection();
                                    detailsConn.setRequestMethod("GET");
                                    if (detailsConn.getResponseCode() == 200) {
                                        BufferedReader dBr = new BufferedReader(new InputStreamReader(detailsConn.getInputStream(), "UTF-8"));
                                        StringBuilder dSb = new StringBuilder();
                                        String dLine;
                                        while ((dLine = dBr.readLine()) != null) {
                                            dSb.append(dLine);
                                        }
                                        dBr.close();

                                        JSONObject detailsRes = new JSONObject(dSb.toString());
                                        if (detailsRes.has("meals") && !detailsRes.isNull("meals")) {
                                            JSONObject details = detailsRes.getJSONArray("meals").getJSONObject(0);

                                            List<Ingredient> ingredients = new ArrayList<>();
                                            for (int k = 1; k <= 20; k++) {
                                                if (details.has("strIngredient" + k) && !details.isNull("strIngredient" + k)) {
                                                    String name = details.getString("strIngredient" + k);
                                                    String amount = details.has("strMeasure" + k) && !details.isNull("strMeasure" + k)
                                                            ? details.getString("strMeasure" + k)
                                                            : "to taste";
                                                     if (!name.trim().isEmpty()) {
                                                         ingredients.add(new Ingredient(name, amount, getIngredientEmoji(name)));
                                                     }
                                                }
                                            }

                                            String instructionsText = details.optString("strInstructions", "");
                                            List<String> steps = new ArrayList<>();
                                            for (String step : instructionsText.split("\r\n")) {
                                                if (!step.trim().isEmpty()) {
                                                    steps.add(step.trim());
                                                }
                                            }
                                            if (steps.isEmpty() && !instructionsText.isEmpty()) {
                                                steps.add(instructionsText);
                                            }

                                            double proteins = Math.floor(Math.random() * 30) + 10;
                                            double fats = Math.floor(Math.random() * 20) + 5;
                                            double carbs = Math.floor(Math.random() * 50) + 15;
                                            double rating = Math.round((Math.random() * (5.0 - 4.2) + 4.2) * 10.0) / 10.0;
                                            String[] times = {"15 min", "25 min", "45 min", "1 hr"};
                                            String time = times[(int) (Math.random() * 4)];
                                            String[] diffs = {"Easy", "Medium", "Hard"};
                                            String diff = diffs[(int) (Math.random() * 3)];

                                            return new Recipe(
                                                    idMeal,
                                                    details.getString("strMeal"),
                                                    "A delicious " + details.optString("strArea", "") + " " + details.optString("strCategory", "") + " dish.",
                                                    time,
                                                    diff,
                                                    rating,
                                                    ingredients,
                                                    proteins, fats, carbs,
                                                    steps,
                                                    "Serve hot and enjoy!",
                                                    details.getString("strMealThumb")
                                            );
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                return null;
                            });
                        }

                        for (int i = 0; i < count; i++) {
                            try {
                                Recipe r = futures[i].get();
                                if (r != null) {
                                    fetchedRecipes.add(r);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        executor.shutdown();
                    }
                    mealDbCache.put(tabName, fetchedRecipes);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        if (activeTab.equals(tabName)) {
                            currentRecipes.clear();
                            currentRecipes.addAll(fetchedRecipes);
                            updateRecipesUI();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(HomeActivity.this, "Failed to load category: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void loadImage(String urlStr, ImageView imageView) {
        if (urlStr == null || urlStr.isEmpty()) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            return;
        }
        runOnUiThread(() -> imageView.setImageResource(android.R.drawable.ic_menu_gallery)); // Set default placeholder
        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                runOnUiThread(() -> {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                    } else {
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> imageView.setImageResource(android.R.drawable.ic_menu_gallery));
            }
        }).start();
    }

    private static String getIngredientEmoji(String name) {
        if (name == null) return "🥘";
        String lower = name.toLowerCase().trim();
        if (lower.contains("chicken")) return "🍗";
        if (lower.contains("beef") || lower.contains("meat") || lower.contains("steak")) return "🥩";
        if (lower.contains("pork") || lower.contains("bacon") || lower.contains("ham")) return "🥓";
        if (lower.contains("fish") || lower.contains("salmon") || lower.contains("tuna") || lower.contains("cod")) return "🐟";
        if (lower.contains("shrimp") || lower.contains("prawn") || lower.contains("seafood")) return "🍤";
        if (lower.contains("egg")) return "🥚";
        if (lower.contains("milk") || lower.contains("cream") || lower.contains("yogurt")) return "🥛";
        if (lower.contains("cheese")) return "🧀";
        if (lower.contains("butter")) return "🧈";
        if (lower.contains("tomato")) return "🍅";
        if (lower.contains("potato")) return "🥔";
        if (lower.contains("onion")) return "🧅";
        if (lower.contains("garlic")) return "🧄";
        if (lower.contains("carrot")) return "🥕";
        if (lower.contains("lettuce") || lower.contains("cabbage") || lower.contains("spinach") || lower.contains("salad")) return "🥬";
        if (lower.contains("chili") || lower.contains("pepper") || lower.contains("paprika")) return "🌶️";
        if (lower.contains("lemon") || lower.contains("lime")) return "🍋";
        if (lower.contains("orange")) return "🍊";
        if (lower.contains("apple")) return "🍎";
        if (lower.contains("banana")) return "🍌";
        if (lower.contains("strawberry") || lower.contains("berry") || lower.contains("blueberry")) return "🍓";
        if (lower.contains("grape")) return "🍇";
        if (lower.contains("bread") || lower.contains("flour") || lower.contains("dough")) return "🍞";
        if (lower.contains("rice")) return "🍚";
        if (lower.contains("noodle") || lower.contains("pasta") || lower.contains("spaghetti")) return "🍝";
        if (lower.contains("soup") || lower.contains("broth")) return "🥣";
        if (lower.contains("salt") || lower.contains("sugar") || lower.contains("powder") || lower.contains("spice") || lower.contains("cinnamon")) return "🧂";
        if (lower.contains("sauce") || lower.contains("vinegar") || lower.contains("mustard") || lower.contains("ketchup")) return "🥫";
        if (lower.contains("oil")) return "🏺";
        if (lower.contains("chocolate") || lower.contains("cocoa")) return "🍫";
        if (lower.contains("honey")) return "🍯";
        if (lower.contains("nut") || lower.contains("peanut") || lower.contains("almond") || lower.contains("walnut")) return "🥜";
        if (lower.contains("mushroom")) return "🍄";
        if (lower.contains("bean") || lower.contains("pea") || lower.contains("lentil")) return "🫘";
        if (lower.contains("avocado")) return "🥑";
        if (lower.contains("corn")) return "🌽";
        if (lower.contains("herb") || lower.contains("parsley") || lower.contains("cilantro") || lower.contains("basil") || lower.contains("oregano") || lower.contains("thyme")) return "🌿";
        if (lower.contains("ginger")) return "🫚";
        if (lower.contains("wine") || lower.contains("beer") || lower.contains("alcohol")) return "🍷";

        String[] fallbackEmojis = {"🥘", "🍳", "🥣", "🥗", "🍯", "🥫", "🧂", "🍽️"};
        int index = Math.abs(lower.hashCode()) % fallbackEmojis.length;
        return fallbackEmojis[index];
    }

    private static final String[] FOOD_IMAGE_IDS = {
        "1546069901-ba9599a7e63c", "1504674900247-0877df9cc836", "1476224203421-9ac3993511d1",
        "1482049016688-2d3e1b311543", "1473093226795-af9932fe5856", "1493770348161-369560ae357d",
        "1540189549336-e6e99c3679fe", "1567621132-12143467bf4d", "1467003909585-2f8a72700288",
        "1565299624946-b28f40a0ae38", "1555939594-58d7cb561ad1", "1512621776951-a57141f2eefd",
        "1504754524776-8f4f37790ca0", "1498837167922-ddd27525d352"
    };

    private String getFallbackImage(String title) {
        int hash = 0;
        for (int i = 0; i < title.length(); i++) {
            hash = title.charAt(i) + ((hash << 5) - hash);
        }
        return "https://images.unsplash.com/photo-" + FOOD_IMAGE_IDS[Math.abs(hash) % FOOD_IMAGE_IDS.length] + "?auto=format&fit=crop&w=800&q=80";
    }

    private String fetchMealDbImage(String imageKeyword, String title) {
        try {
            URL url = new URL("https://www.themealdb.com/api/json/v1/1/search.php?s=" + URLEncoder.encode(imageKeyword, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                
                JSONObject res = new JSONObject(sb.toString());
                if (res.has("meals") && !res.isNull("meals")) {
                    JSONArray meals = res.getJSONArray("meals");
                    if (meals.length() > 0) {
                        return meals.getJSONObject(0).getString("strMealThumb");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return getFallbackImage(title);
    }

    private static class GeminiHttpException extends Exception {
        private final int statusCode;
        private final String errorBody;

        public GeminiHttpException(int statusCode, String errorBody) {
            super("HTTP error code: " + statusCode);
            this.statusCode = statusCode;
            this.errorBody = errorBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorBody() {
            return errorBody;
        }
    }

    private String makeGeminiCall(String modelName, String requestBodyStr) throws Exception {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + GEMINI_API_KEY);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        OutputStream os = conn.getOutputStream();
        os.write(requestBodyStr.getBytes("UTF-8"));
        os.close();

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            return sb.toString();
        } else {
            InputStream errorStream = conn.getErrorStream();
            String errorMsg = "";
            if (errorStream != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(errorStream, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                errorMsg = sb.toString();
                Log.e("GeminiError", "Response Code: " + responseCode + ", Error: " + errorMsg);
            }
            throw new GeminiHttpException(responseCode, errorMsg);
        }
    }

    private void applyCustomizations() {
        ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);
        int accentColor = Color.parseColor(theme.accentColor);
        int secondaryColor = Color.parseColor(theme.secondaryColor);

        // Update profile picture
        ImageView ivProfile = findViewById(R.id.iv_profile);
        if (ivProfile != null) {
            loadImage(ThemeManager.getAvatarUrl(this), ivProfile);
        }

        // Update User Greeting Name
        TextView tvHelloUser = findViewById(R.id.tv_hello_user);
        if (tvHelloUser != null) {
            tvHelloUser.setText("Hello, " + ThemeManager.getUserName(this) + " \uD83D\uDC4B");
        }

        // Update Add Ingredient button text color
        TextView btnAddIngredient = findViewById(R.id.btn_add_ingredient);
        if (btnAddIngredient != null) {
            btnAddIngredient.setTextColor(accentColor);
        }

        // Update Generate Button background tint
        if (btnGenerate != null) {
            btnGenerate.setBackgroundTintList(ColorStateList.valueOf(accentColor));
        }

        // Update progress bar
        if (progressBar != null) {
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(accentColor));
        }

        // Update recipes display
        updateRecipesUI();
    }

    private void showCustomizationDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_customization, null);
        builder.setView(dialogView);

        final android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        EditText etName = dialogView.findViewById(R.id.dialog_et_name);
        etName.setText(ThemeManager.getUserName(this));

        LinearLayout layoutThemes = dialogView.findViewById(R.id.dialog_layout_themes);
        LinearLayout layoutAvatars = dialogView.findViewById(R.id.dialog_layout_avatars);

        // Pre-fetch choices
        final String[] selectedTheme = {ThemeManager.getCurrentTheme(this).name};
        final String[] selectedAvatar = {ThemeManager.getAvatarUrl(this)};

        // Populate Theme choices
        layoutThemes.removeAllViews();
        for (ThemeManager.ThemePreset preset : ThemeManager.PRESETS) {
            View swatchView = new View(this);
            int size = (int) (40 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(16, 8, 16, 8);
            swatchView.setLayoutParams(lp);

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(Color.parseColor(preset.accentColor));
            
            if (preset.name.equals(selectedTheme[0])) {
                gd.setStroke(6, Color.WHITE);
            } else {
                gd.setStroke(2, Color.parseColor("#444444"));
            }
            swatchView.setBackground(gd);

            swatchView.setOnClickListener(v -> {
                selectedTheme[0] = preset.name;
                for (int i = 0; i < layoutThemes.getChildCount(); i++) {
                    View child = layoutThemes.getChildAt(i);
                    ThemeManager.ThemePreset p = ThemeManager.PRESETS.get(i);
                    android.graphics.drawable.GradientDrawable childGd = (android.graphics.drawable.GradientDrawable) child.getBackground();
                    if (childGd != null) {
                        if (p.name.equals(selectedTheme[0])) {
                            childGd.setStroke(6, Color.WHITE);
                        } else {
                            childGd.setStroke(2, Color.parseColor("#444444"));
                        }
                    }
                }
            });

            layoutThemes.addView(swatchView);
        }

        // Populate Avatar choices
        layoutAvatars.removeAllViews();
        for (String url : ThemeManager.AVATARS) {
            ImageView avatarView = new ImageView(this);
            int size = (int) (64 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(12, 8, 12, 8);
            avatarView.setLayoutParams(lp);
            
            avatarView.setBackgroundResource(R.drawable.circle_shape_border);
            avatarView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            avatarView.setClipToOutline(true);

            if (url.equals(selectedAvatar[0])) {
                avatarView.setAlpha(1.0f);
                avatarView.setPadding(6, 6, 6, 6);
            } else {
                avatarView.setAlpha(0.6f);
                avatarView.setPadding(0, 0, 0, 0);
            }

            loadImage(url, avatarView);

            avatarView.setOnClickListener(v -> {
                selectedAvatar[0] = url;
                for (int i = 0; i < layoutAvatars.getChildCount(); i++) {
                    ImageView child = (ImageView) layoutAvatars.getChildAt(i);
                    String u = ThemeManager.AVATARS.get(i);
                    if (u.equals(selectedAvatar[0])) {
                        child.setAlpha(1.0f);
                        child.setPadding(6, 6, 6, 6);
                    } else {
                        child.setAlpha(0.6f);
                        child.setPadding(0, 0, 0, 0);
                    }
                }
            });

            layoutAvatars.addView(avatarView);
        }

        dialogView.findViewById(R.id.dialog_btn_cancel).setOnClickListener(v -> dialog.dismiss());
        
        Button btnApply = dialogView.findViewById(R.id.dialog_btn_apply);
        ThemeManager.ThemePreset activeTheme = ThemeManager.getCurrentTheme(this);
        btnApply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(activeTheme.accentColor)));

        btnApply.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                ThemeManager.setUserName(HomeActivity.this, name);
            }
            ThemeManager.setCurrentTheme(HomeActivity.this, selectedTheme[0]);
            ThemeManager.setAvatarUrl(HomeActivity.this, selectedAvatar[0]);

            applyCustomizations();
            dialog.dismiss();
            
            Toast.makeText(HomeActivity.this, "Customizations applied!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }
}
