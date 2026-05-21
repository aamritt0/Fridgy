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

    private View layoutLoadingState;
    private View layoutHomeContent;
    private View layoutHeaderBar;
    private View layoutSearchBar;
    private View layoutCategories;
    private View layoutGeneratedHeader;
    private TextView tvGeneratedTitle;
    private View btnBackGenerated;
    private android.os.Handler loadingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable loadingRunnable;
    // Guard: prevents updateRecipesUI from flickering content while loading screen is active
    private boolean isLoadingActive = false;

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

        layoutLoadingState = findViewById(R.id.layout_loading_state);
        layoutHomeContent = findViewById(R.id.layout_home_content);
        layoutHeaderBar = findViewById(R.id.layout_header_bar);
        layoutSearchBar = findViewById(R.id.layout_search_bar);
        layoutCategories = findViewById(R.id.layout_categories);
        layoutGeneratedHeader = findViewById(R.id.layout_generated_header);
        tvGeneratedTitle = findViewById(R.id.tv_generated_title);
        btnBackGenerated = findViewById(R.id.btn_back_generated);

        if (btnBackGenerated != null) {
            ThemeManager.applyTouchScaleAnimation(btnBackGenerated, () -> showGeneratedRecipesView(false));
        }

        // Initialize cache from disk (v6 key – invalidates older mock/untranslated caches)
        mealDbCache.put("Breakfast", loadCacheFromDisk("Breakfast"));
        mealDbCache.put("Lunch", loadCacheFromDisk("Lunch"));
        mealDbCache.put("Dinner", loadCacheFromDisk("Dinner"));
        mealDbCache.put("Dessert", loadCacheFromDisk("Dessert"));
        mealDbCache.put("Salads", loadCacheFromDisk("Salads"));

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
        if (currentIngredients.isEmpty() || (layoutGeneratedHeader != null && layoutGeneratedHeader.getVisibility() == View.VISIBLE)) {
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

        progressBar.setVisibility(View.GONE);
        btnGenerate.setEnabled(false);
        showLoadingState(false);

        new Thread(() -> {
            try {
                if (GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY_HERE")) {
                    Thread.sleep(1500); // Simulate network
                    Recipe r1 = createMockRecipe("Ricetta Generata 1");
                    Recipe r2 = createMockRecipe("Ricetta Generata 2");
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
                            "IMPORTANT: Provide all the recipe text details (title, description, steps, tips, ingredient names, and ingredient amounts if units are words) in Italian. " +
                            "Translate the difficulty to 'Facile', 'Medio', or 'Difficile'. " +
                            "However, the 'imageKeyword' field MUST be in English (1 or 2 words only) to help search for an image on Unsplash/MealDB. " +
                            "For each recipe, provide:\n" +
                            "- id: a unique short string\n" +
                            "- title: name of the dish in Italian\n" +
                            "- description: a short, appetizing summary in Italian\n" +
                            "- time: total cooking time (e.g., \"15 min\")\n" +
                            "- difficulty: 'Facile', 'Medio', or 'Difficile'\n" +
                            "- rating: A number between 4.0 and 5.0 representing the general user rating\n" +
                            "- ingredients: an array of objects representing required ingredients. For each, give a \"name\" in Italian, an \"amount\" in Italian (e.g. \"100g\" or \"1 cucchiaio\"), and an \"emoji\" that best represents it\n" +
                            "- nutrition: an estimated nutritional breakdown per serving with number values for \"proteins\", \"fats\", and \"carbs\" (in grams)\n" +
                            "- steps: numbered step-by-step instructions in Italian\n" +
                            "- tips: an optional professional chef tip in Italian\n" +
                            "- imageKeyword: a short, 1 or 2 word keyword IN ENGLISH to search for an image of this dish";

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
                    String[] models = {"gemini-2.5-flash", "gemini-1.5-flash"};
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
                    generatedRecipes.clear();
                    generatedRecipes.addAll(currentRecipes);
                    showGeneratedRecipesView(true);
                    dismissLoadingState();
                    btnGenerate.setEnabled(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    dismissLoadingState();
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
                "Un pasto delizioso.",
                "15 min",
                "Medio",
                4.8,
                Arrays.asList(new Ingredient("Maiale", "100g", "🥩"), new Ingredient("Noodle", "200g", "🍜")),
                3.45, 10.69, 22.72,
                Arrays.asList("Far bollire l'acqua.", "Aggiungere la pasta.", "Gusta il piatto."),
                "Aggiungi un po' di salsa di soia extra per insaporire.",
                getFallbackImage(title)
        );
    }

    private void updateRecipesUI() {
        String label = activeTab;
        if (activeTab.equalsIgnoreCase("Breakfast")) label = "Colazione";
        else if (activeTab.equalsIgnoreCase("Lunch")) label = "Pranzo";
        else if (activeTab.equalsIgnoreCase("Dinner")) label = "Cena";
        else if (activeTab.equalsIgnoreCase("Dessert")) label = "Dessert";
        else if (activeTab.equalsIgnoreCase("Salads")) label = "Insalate";
        
        tvRecipeCount.setText(String.format("%02d ricette per %s", currentRecipes.size(), label.toLowerCase()));
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

    private void saveCacheToDisk(String tabName, List<Recipe> recipes) {
        try {
            SharedPreferences cachePrefs = getSharedPreferences("mealdb_recipes_cache_v6", Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray();
            for (Recipe recipe : recipes) {
                arr.put(recipe.toJson());
            }
            cachePrefs.edit().putString(tabName, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Recipe> loadCacheFromDisk(String tabName) {
        List<Recipe> list = new ArrayList<>();
        SharedPreferences cachePrefs = getSharedPreferences("mealdb_recipes_cache_v6", Context.MODE_PRIVATE);
        String cacheStr = cachePrefs.getString(tabName, null);
        if (cacheStr != null) {
            try {
                JSONArray arr = new JSONArray(cacheStr);
                for (int i = 0; i < arr.length(); i++) {
                    Recipe r = Recipe.fromJson(arr.getString(i));
                    if (r != null) {
                        list.add(r);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    private List<Recipe> fetchCategoryFromMealDb(String categoryName) throws Exception {
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
                int count = Math.min(mealList.size(), 6);

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
                return fetchedRecipes;
            }
        }
        return new ArrayList<>();
    }

    private void preFetchOtherTabs() {
        String[] tabs = {"Lunch", "Dinner", "Dessert", "Salads"};
        String[] categories = {"Chicken", "Beef", "Dessert", "Vegetarian"};
        
        new Thread(() -> {
            for (int i = 0; i < tabs.length; i++) {
                if (i > 0) {
                    try {
                        Thread.sleep(7000); // safety delay to avoid Gemini rate limits during background pre-fetch
                    } catch (InterruptedException ignored) {}
                }
                String tab = tabs[i];
                String cat = categories[i];
                if (!mealDbCache.containsKey(tab) || mealDbCache.get(tab) == null || mealDbCache.get(tab).isEmpty()) {
                    try {
                        List<Recipe> fetched = fetchCategoryFromMealDb(cat);
                        List<Recipe> translated = translateRecipesToItalian(fetched);
                        if (translated != null && !translated.isEmpty()) {
                            mealDbCache.put(tab, translated);
                            saveCacheToDisk(tab, translated);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void loadMealDbRecipes(String categoryName, String tabName) {
        // If we have a valid in-memory cache for this tab, use it immediately (no loading needed)
        List<Recipe> cached = mealDbCache.get(tabName);
        if (cached != null && !cached.isEmpty()) {
            currentRecipes.clear();
            currentRecipes.addAll(cached);
            updateRecipesUI();
            return;
        }

        // No cache available – show the loading animation and fetch+translate
        progressBar.setVisibility(View.GONE);
        showLoadingState(true);
        new Thread(() -> {
            try {
                List<Recipe> fetchedRecipes = fetchCategoryFromMealDb(categoryName);
                List<Recipe> translatedRecipes = translateRecipesToItalian(fetchedRecipes);
                if (translatedRecipes == null || translatedRecipes.isEmpty()) {
                    translatedRecipes = fetchedRecipes; // fallback to untranslated if everything failed
                }
                mealDbCache.put(tabName, translatedRecipes);
                saveCacheToDisk(tabName, translatedRecipes);
                final List<Recipe> finalTranslated = translatedRecipes;
                runOnUiThread(() -> {
                    dismissLoadingState();
                    if (activeTab.equals(tabName)) {
                        currentRecipes.clear();
                        currentRecipes.addAll(finalTranslated);
                        updateRecipesUI();
                    }
                    preFetchOtherTabs();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    dismissLoadingState();
                    Toast.makeText(HomeActivity.this, "Impossibile caricare la categoria: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                Bitmap bitmap = null;
                if (urlStr.startsWith("content://") || urlStr.startsWith("file://")) {
                    android.net.Uri uri = android.net.Uri.parse(urlStr);
                    InputStream input = getContentResolver().openInputStream(uri);
                    bitmap = BitmapFactory.decodeStream(input);
                    if (input != null) input.close();
                } else if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                    URL url = new URL(urlStr);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    bitmap = BitmapFactory.decodeStream(input);
                    if (input != null) input.close();
                } else {
                    java.io.File file = new java.io.File(urlStr);
                    if (file.exists()) {
                        bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    }
                }
                
                final Bitmap finalBitmap = bitmap;
                runOnUiThread(() -> {
                    if (finalBitmap != null) {
                        imageView.setImageBitmap(finalBitmap);
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
        int maxRetries = 3;
        long retryDelayMs = 3000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + GEMINI_API_KEY);
                conn = (HttpURLConnection) url.openConnection();
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
                } else if (responseCode == 429) {
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
                    }
                    Log.w("GeminiCall", "Rate limit (429) hit on model " + modelName + ", attempt " + attempt + " of " + maxRetries + ". Error: " + errorMsg);
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException ignored) {}
                        retryDelayMs *= 2;
                        continue;
                    }
                    throw new GeminiHttpException(responseCode, errorMsg);
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
            } catch (Exception e) {
                if (e instanceof GeminiHttpException && ((GeminiHttpException) e).getStatusCode() == 429) {
                    throw e;
                }
                Log.e("GeminiCall", "Exception in makeGeminiCall (attempt " + attempt + "): " + e.getMessage());
                if (attempt == maxRetries) {
                    throw e;
                }
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ignored) {}
                retryDelayMs *= 2;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        throw new Exception("Failed to make Gemini call after " + maxRetries + " attempts");
    }

    private void applyCustomizations() {
        ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);
        int accentColor = Color.parseColor(theme.accentColor);
        int secondaryColor = Color.parseColor(theme.secondaryColor);

        // Update profile picture
        ImageView ivProfile = findViewById(R.id.iv_profile);
        if (ivProfile != null) {
            ivProfile.setPadding(6, 6, 6, 6);
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.OVAL);
            border.setColor(accentColor);
            ivProfile.setBackground(border);
            ivProfile.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            ivProfile.setClipToOutline(true);
            loadImage(ThemeManager.getAvatarUrl(this), ivProfile);
        }

        // Update User Greeting Name
        TextView tvHelloUser = findViewById(R.id.tv_hello_user);
        if (tvHelloUser != null) {
            tvHelloUser.setText("Ciao, " + ThemeManager.getUserName(this) + " \uD83D\uDC4B");
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

        // Update recipes display only when NOT in loading state
        if (!isLoadingActive) {
            updateRecipesUI();
        }
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
        List<String> avatarOptions = new ArrayList<>(ThemeManager.AVATARS);
        java.io.File customAvatarFile = new java.io.File(getFilesDir(), "profile_picture.jpg");
        if (customAvatarFile.exists()) {
            String customPath = customAvatarFile.getAbsolutePath();
            avatarOptions.add(0, customPath);
        }

        layoutAvatars.removeAllViews();
        for (String url : avatarOptions) {
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
                    String u = avatarOptions.get(i);
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

    private void showLoadingState(boolean isInitialization) {
        // Must be called on the main thread – do NOT wrap in runOnUiThread (that would defer
        // execution to the next looper cycle, letting subsequent main-thread code like
        // applyCustomizations() run first and flicker the content layout back into view).
        if (layoutLoadingState == null || layoutHomeContent == null) return;

        isLoadingActive = true;

        // Cancel any running animations or runnable
        if (loadingRunnable != null) {
            loadingHandler.removeCallbacks(loadingRunnable);
            loadingRunnable = null;
        }

        layoutHomeContent.setVisibility(View.GONE);
        layoutLoadingState.setVisibility(View.VISIBLE);

        if (layoutHeaderBar != null) layoutHeaderBar.setVisibility(View.GONE);
        if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.GONE);

        // Hide ingredients scroll and generate button to keep UI completely clean during loading
        if (scrollIngredients != null) scrollIngredients.setVisibility(View.GONE);
        if (btnGenerate != null) btnGenerate.setVisibility(View.GONE);

            TextView tvEmoji = layoutLoadingState.findViewById(R.id.tv_loading_emoji);
            TextView tvTitle = layoutLoadingState.findViewById(R.id.tv_loading_title);
            TextView tvSubtitle = layoutLoadingState.findViewById(R.id.tv_loading_subtitle);
            TextView tvTip = layoutLoadingState.findViewById(R.id.tv_loading_tip);
            View emojiContainer = layoutLoadingState.findViewById(R.id.layout_emoji_container);
            View dot1 = layoutLoadingState.findViewById(R.id.dot1);
            View dot2 = layoutLoadingState.findViewById(R.id.dot2);
            View dot3 = layoutLoadingState.findViewById(R.id.dot3);

            // Set initial texts based on state
            if (isInitialization) {
                if (tvTitle != null) tvTitle.setText("Inizializzazione...");
                if (tvSubtitle != null) tvSubtitle.setText("Preparazione dei tuoi ingredienti freschi");
            } else {
                if (tvTitle != null) tvTitle.setText("Creando qualche idea...");
                if (tvSubtitle != null) tvSubtitle.setText("Consultando i nostri chef stellati");
            }

            // 1. Rotation animation for the emoji container
            android.view.animation.RotateAnimation rotate = new android.view.animation.RotateAnimation(
                0, 360,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
            );
            rotate.setDuration(3000);
            rotate.setRepeatCount(android.view.animation.Animation.INFINITE);
            rotate.setInterpolator(new android.view.animation.LinearInterpolator());
            if (emojiContainer != null) {
                emojiContainer.startAnimation(rotate);
            }

            // 2. Pulse / Scale animation for the emoji text itself
            android.view.animation.ScaleAnimation pulse = new android.view.animation.ScaleAnimation(
                0.8f, 1.2f, 0.8f, 1.2f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
            );
            pulse.setDuration(1200);
            pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
            pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
            if (tvEmoji != null) {
                tvEmoji.startAnimation(pulse);
            }

            // List of Chef Tips
            String[] chefTips = {
                "Leggi l'intera ricetta prima di iniziare a cucinare.",
                "Non affollare la padella! Fa evaporare il cibo invece di rosolarlo.",
                "Fai riposare la carne prima di tagliarla per mantenerla succosa.",
                "L'acido (succo di limone o aceto) può ravvivare un piatto scialbo.",
                "Condisci con sale in ogni fase della cottura, non solo alla fine.",
                "Prepara tutti gli ingredienti (mise en place) prima di accendere i fornelli.",
                "Un coltello affilato è più sicuro di uno smussato.",
                "Assaggia il cibo mentre cucini per regolare i condimenti.",
                "Usa un tovagliolo di carta umido sotto il tagliere per evitare che scivoli."
            };

            // List of titles
            String[] loadingTitles = isInitialization ? new String[]{
                "Inizializzazione...",
                "Caricamento ingredienti freschi...",
                "Allestimento della dispensa...",
                "Preparazione del ricettario...",
                "Quasi pronto..."
            } : new String[]{
                "Creando qualche idea...",
                "Consultando i nostri chef stellati...",
                "Impiattando il tuo menu personalizzato...",
                "Sobbollendo gli ingredienti segreti...",
                "Miscelando il mix perfetto..."
            };

            // List of emojis
            String[] foodEmojis = {"🍳", "🥣", "🥘", "🥗", "🍲", "🥞", "🧁", "🍝", "🍕"};

            final int[] tipIndex = {0};
            final int[] titleIndex = {0};
            final int[] emojiIndex = {0};
            final int[] activeDot = {0};

            loadingRunnable = new Runnable() {
                @Override
                public void run() {
                    if (layoutLoadingState.getVisibility() != View.VISIBLE) return;

                    // Update Chef Tip with simple fade animation
                    tipIndex[0] = (tipIndex[0] + 1) % chefTips.length;
                    if (tvTip != null) {
                        tvTip.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                            tvTip.setText(chefTips[tipIndex[0]]);
                            tvTip.animate().alpha(1f).setDuration(250).start();
                        }).start();
                    }

                    // Update Title with simple fade animation
                    titleIndex[0] = (titleIndex[0] + 1) % loadingTitles.length;
                    if (tvTitle != null) {
                        tvTitle.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                            tvTitle.setText(loadingTitles[titleIndex[0]]);
                            tvTitle.animate().alpha(1f).setDuration(250).start();
                        }).start();
                    }

                    // Update Emoji occasionally
                    emojiIndex[0] = (emojiIndex[0] + 1) % foodEmojis.length;
                    if (tvEmoji != null) {
                        tvEmoji.setText(foodEmojis[emojiIndex[0]]);
                    }

                    // Update dots indicators
                    activeDot[0] = (activeDot[0] + 1) % 3;
                    int activeColor = Color.parseColor(ThemeManager.getCurrentTheme(HomeActivity.this).accentColor);
                    int inactiveColor = getResources().getColor(R.color.text_secondary, getTheme());
                    if (dot1 != null) dot1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeDot[0] == 0 ? activeColor : inactiveColor));
                    if (dot2 != null) dot2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeDot[0] == 1 ? activeColor : inactiveColor));
                    if (dot3 != null) dot3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeDot[0] == 2 ? activeColor : inactiveColor));

                    loadingHandler.postDelayed(this, 2500);
                }
            };

        loadingHandler.postDelayed(loadingRunnable, 2500);
    }

    private void dismissLoadingState() {
        // dismissLoadingState is always called via runOnUiThread from background threads,
        // so it is already on the main thread when it runs.
        isLoadingActive = false;
        if (loadingRunnable != null) {
            loadingHandler.removeCallbacks(loadingRunnable);
            loadingRunnable = null;
        }
        if (layoutLoadingState != null) {
            layoutLoadingState.setVisibility(View.GONE);
            View emojiContainer = layoutLoadingState.findViewById(R.id.layout_emoji_container);
            TextView tvEmoji = layoutLoadingState.findViewById(R.id.tv_loading_emoji);
            if (emojiContainer != null) emojiContainer.clearAnimation();
            if (tvEmoji != null) tvEmoji.clearAnimation();
        }
        if (layoutHomeContent != null) {
            layoutHomeContent.setVisibility(View.VISIBLE);
        }
        if (layoutHeaderBar != null && layoutGeneratedHeader != null && layoutGeneratedHeader.getVisibility() != View.VISIBLE) {
            layoutHeaderBar.setVisibility(View.VISIBLE);
        }
        if (layoutSearchBar != null && layoutGeneratedHeader != null && layoutGeneratedHeader.getVisibility() != View.VISIBLE) {
            layoutSearchBar.setVisibility(View.VISIBLE);
        }
        // Restore ingredients UI visibility dynamically
        updateIngredientsUI();
    }

    private void showGeneratedRecipesView(boolean show) {
        runOnUiThread(() -> {
            if (show) {
                // Show only back button and generated recipe count, hide standard greeting, search bar, categories, recents, favorites, tv_recipe_count
                if (layoutHeaderBar != null) layoutHeaderBar.setVisibility(View.GONE);
                if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.GONE);
                if (layoutCategories != null) layoutCategories.setVisibility(View.GONE);
                if (sectionRecents != null) sectionRecents.setVisibility(View.GONE);
                if (sectionFavorites != null) sectionFavorites.setVisibility(View.GONE);
                if (tvRecipeCount != null) tvRecipeCount.setVisibility(View.GONE);
                if (layoutGeneratedHeader != null) layoutGeneratedHeader.setVisibility(View.VISIBLE);
                
                if (tvGeneratedTitle != null) {
                    tvGeneratedTitle.setText(String.format("%d ricette generate", generatedRecipes.size()));
                }
                
                currentRecipes.clear();
                currentRecipes.addAll(generatedRecipes);
                updateRecipesUI();
            } else {
                // Restore standard views
                if (layoutHeaderBar != null) layoutHeaderBar.setVisibility(View.VISIBLE);
                if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.VISIBLE);
                if (layoutCategories != null) layoutCategories.setVisibility(View.VISIBLE);
                if (layoutGeneratedHeader != null) layoutGeneratedHeader.setVisibility(View.GONE);
                if (tvRecipeCount != null) tvRecipeCount.setVisibility(View.VISIBLE);
                
                // Refresh recent and favorites and normal tab recipes
                populateHorizontalSection(containerRecents, sectionRecents, loadRecents());
                populateHorizontalSection(containerFavorites, sectionFavorites, loadFavorites());
                switchTab(activeTab);
            }
            updateIngredientsUI();
        });
    }

    private List<Recipe> translateRecipesToItalian(List<Recipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return recipes;
        return translateRecipesWithMyMemory(recipes);
    }

    private String translateTextMyMemory(String text) {
        if (text == null || text.trim().isEmpty()) return text;
        HttpURLConnection conn = null;
        try {
            String encodedText = java.net.URLEncoder.encode(text, "UTF-8");
            URL url = new URL("https://api.mymemory.translated.net/get?q=" + encodedText + "&langpair=en|it&de=fridggy.app@gmail.com");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                
                JSONObject res = new JSONObject(sb.toString());
                if (res.has("responseData")) {
                    JSONObject respData = res.getJSONObject("responseData");
                    if (respData.has("translatedText")) {
                        String translated = respData.getString("translatedText");
                        if (translated != null && !translated.trim().isEmpty()) {
                            return translated.trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("MyMemoryTranslation", "Error translating text: " + text + ", error: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return text;
    }

    private List<Recipe> translateRecipesWithMyMemory(List<Recipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return recipes;

        java.util.Set<String> stringsToTranslate = new java.util.HashSet<>();
        for (Recipe r : recipes) {
            if (r.getTitle() != null && !r.getTitle().trim().isEmpty()) {
                stringsToTranslate.add(r.getTitle().trim());
            }
            if (r.getIngredients() != null) {
                for (Ingredient ing : r.getIngredients()) {
                    if (ing.getName() != null && !ing.getName().trim().isEmpty()) {
                        stringsToTranslate.add(ing.getName().trim());
                    }
                }
            }
            if (r.getSteps() != null) {
                for (String step : r.getSteps()) {
                    if (step != null && !step.trim().isEmpty()) {
                        stringsToTranslate.add(step.trim());
                    }
                }
            }
        }

        java.util.concurrent.ConcurrentHashMap<String, String> translationMap = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(6);
        java.util.List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        for (final String text : stringsToTranslate) {
            futures.add(executor.submit(() -> {
                String translated = translateTextMyMemory(text);
                if (translated != null) {
                    translationMap.put(text, translated);
                }
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        List<Recipe> translatedList = new ArrayList<>();
        for (Recipe r : recipes) {
            String title = translationMap.get(r.getTitle().trim());
            if (title == null || title.equals(r.getTitle().trim())) {
                translatedList.add(translateRecipeMock(r));
                continue;
            }

            String description = r.getDescription();
            if (description.startsWith("A delicious ")) {
                description = description.replace("A delicious ", "Un delizioso piatto di tipo ")
                                         .replace(" dish.", ".");
            }
            description = translateTextMyMemory(description);

            String difficulty = r.getDifficulty();
            if (difficulty.equalsIgnoreCase("Easy")) difficulty = "Facile";
            else if (difficulty.equalsIgnoreCase("Medium")) difficulty = "Medio";
            else if (difficulty.equalsIgnoreCase("Hard")) difficulty = "Difficile";

            String tips = r.getTips();
            if (tips.equalsIgnoreCase("Serve hot and enjoy!")) tips = "Servire caldo e gustare!";

            List<Ingredient> ingredients = new ArrayList<>();
            if (r.getIngredients() != null) {
                for (Ingredient ing : r.getIngredients()) {
                    String name = translationMap.getOrDefault(ing.getName().trim(), ing.getName());
                    String amount = ing.getAmount();
                    if (amount != null) {
                        amount = amount.replaceAll("(?i)\\btbsp\\b", "cch.aio")
                                       .replaceAll("(?i)\\btsp\\b", "cch.ino")
                                       .replaceAll("(?i)\\bcups\\b", "tazze")
                                       .replaceAll("(?i)\\bcup\\b", "tazza")
                                       .replaceAll("(?i)\\boz\\b", "once")
                                       .replaceAll("(?i)\\blbs\\b", "libbre")
                                       .replaceAll("(?i)\\blb\\b", "libbra")
                                       .replaceAll("(?i)\\bpinches\\b", "pizzichi")
                                       .replaceAll("(?i)\\bpinch\\b", "pizzico")
                                       .replaceAll("(?i)\\bslices\\b", "fette")
                                       .replaceAll("(?i)\\bslice\\b", "fetta")
                                       .replaceAll("(?i)\\bcloves\\b", "spicchi")
                                       .replaceAll("(?i)\\bclove\\b", "spicchio")
                                       .replaceAll("(?i)\\bwhole\\b", "intero")
                                       .replaceAll("(?i)\\bcans\\b", "lattine")
                                       .replaceAll("(?i)\\bcan\\b", "lattina")
                                       .replaceAll("(?i)\\bbottle\\b", "bottiglia")
                                       .replaceAll("(?i)\\bchopped\\b", "tritato");
                    } else {
                        amount = "q.b.";
                    }
                    ingredients.add(new Ingredient(name, amount, getIngredientEmoji(name)));
                }
            }

            List<String> steps = new ArrayList<>();
            if (r.getSteps() != null) {
                for (String step : r.getSteps()) {
                    String translatedStep = translationMap.get(step.trim());
                    if (translatedStep == null || translatedStep.equals(step.trim())) {
                        translatedStep = translateStepMockSingle(step);
                    }
                    steps.add(translatedStep);
                }
            }

            translatedList.add(new Recipe(
                    r.getId(),
                    title,
                    description,
                    r.getTime(),
                    difficulty,
                    r.getRating(),
                    ingredients,
                    r.getProteins(),
                    r.getFats(),
                    r.getCarbs(),
                    steps,
                    tips,
                    r.getImageUrl()
            ));
        }

        return translatedList;
    }

    private Recipe translateRecipeMock(Recipe r) {
        String title = r.getTitle();
        String description = r.getDescription();
        String difficulty = r.getDifficulty();
        String tips = r.getTips();

        if (difficulty.equalsIgnoreCase("Easy")) difficulty = "Facile";
        else if (difficulty.equalsIgnoreCase("Medium")) difficulty = "Medio";
        else if (difficulty.equalsIgnoreCase("Hard")) difficulty = "Difficile";

        if (tips.equalsIgnoreCase("Serve hot and enjoy!")) tips = "Servire caldo e gustare!";

        if (description.startsWith("A delicious ")) {
            description = description.replace("A delicious ", "Un delizioso piatto di tipo ")
                                     .replace(" dish.", ".");
        }

        List<Ingredient> translatedIngs = new ArrayList<>();
        for (Ingredient ing : r.getIngredients()) {
            String name = translateIngredientNameMock(ing.getName());
            String amount = translateAmountMock(ing.getAmount());
            translatedIngs.add(new Ingredient(name, amount, getIngredientEmoji(name)));
        }

        title = translateTitleMock(title);

        return new Recipe(
                r.getId(),
                title,
                description,
                r.getTime(),
                difficulty,
                r.getRating(),
                translatedIngs,
                r.getProteins(),
                r.getFats(),
                r.getCarbs(),
                translateStepsMock(r.getSteps()),
                tips,
                r.getImageUrl()
        );
    }

    private String translateTitleMock(String title) {
        title = title.replaceAll("(?i)\\bchicken\\b", "Pollo")
                     .replaceAll("(?i)\\bbeef\\b", "Manzo")
                     .replaceAll("(?i)\\bsalad\\b", "Insalata")
                     .replaceAll("(?i)\\bsoup\\b", "Zuppa")
                     .replaceAll("(?i)\\bcake\\b", "Torta")
                     .replaceAll("(?i)\\bpie\\b", "Crostata")
                     .replaceAll("(?i)\\bbread\\b", "Pane")
                     .replaceAll("(?i)\\bpancakes\\b", "Frittelle")
                     .replaceAll("(?i)\\bcream\\b", "Crema")
                     .replaceAll("(?i)\\bsauce\\b", "Salsa")
                     .replaceAll("(?i)\\broasted\\b", "Arrosto")
                     .replaceAll("(?i)\\bbaked\\b", "Al forno")
                     .replaceAll("(?i)\\bsweet\\b", "Dolce")
                     .replaceAll("(?i)\\bspicy\\b", "Piccante")
                     .replaceAll("(?i)\\bfried\\b", "Fritto")
                     .replaceAll("(?i)\\bwith\\b", "con");
        return title;
    }

    private String translateAmountMock(String amount) {
        if (amount == null) return "q.b.";
        String lower = amount.toLowerCase().trim();
        if (lower.equals("to taste")) return "q.b.";
        
        amount = amount.replaceAll("(?i)\\btbsp\\b", "cch.aio")
                       .replaceAll("(?i)\\btsp\\b", "cch.ino")
                       .replaceAll("(?i)\\bcups\\b", "tazze")
                       .replaceAll("(?i)\\bcup\\b", "tazza")
                       .replaceAll("(?i)\\boz\\b", "once")
                       .replaceAll("(?i)\\blbs\\b", "libbre")
                       .replaceAll("(?i)\\blb\\b", "libbra")
                       .replaceAll("(?i)\\bpinches\\b", "pizzichi")
                       .replaceAll("(?i)\\bpinch\\b", "pizzico")
                       .replaceAll("(?i)\\bslices\\b", "fette")
                       .replaceAll("(?i)\\bslice\\b", "fetta")
                       .replaceAll("(?i)\\bcloves\\b", "spicchi")
                       .replaceAll("(?i)\\bclove\\b", "spicchio")
                       .replaceAll("(?i)\\bwhole\\b", "intero")
                       .replaceAll("(?i)\\bcans\\b", "lattine")
                       .replaceAll("(?i)\\bcan\\b", "lattina")
                       .replaceAll("(?i)\\bbottle\\b", "bottiglia")
                       .replaceAll("(?i)\\bchopped\\b", "tritato");
        return amount;
    }

    private String translateIngredientNameMock(String name) {
        String lower = name.toLowerCase().trim();
        switch (lower) {
            case "butter": return "burro";
            case "egg": case "eggs": return "uova";
            case "milk": return "latte";
            case "flour": return "farina";
            case "sugar": return "zucchero";
            case "salt": return "sale";
            case "pepper": return "pepe";
            case "water": return "acqua";
            case "chicken": return "pollo";
            case "beef": return "manzo";
            case "garlic": return "aglio";
            case "onion": return "cipolla";
            case "onions": return "cipolle";
            case "olive oil": return "olio d'oliva";
            case "vegetable oil": return "olio vegetale";
            case "tomato": case "tomatoes": return "pomodori";
            case "cheese": return "formaggio";
            case "rice": return "riso";
            case "bread": return "pane";
            case "potato": case "potatoes": return "patate";
            case "carrot": case "carrots": return "carote";
            case "lemon": return "limone";
            case "lemon juice": return "succo di limone";
            case "vanilla extract": return "estratto di vaniglia";
            case "yeast": return "lievito";
            case "honey": return "miele";
            case "cinnamon": return "cannella";
            case "parsley": return "prezzemolo";
            case "basil": return "basilico";
            case "cream": return "panna";
            case "pasta": return "pasta";
            case "pork": return "maiale";
            case "spinach": return "spinaci";
            case "mushrooms": case "mushroom": return "funghi";
            case "shrimp": case "shrimps": return "gamberetti";
            case "fish": return "pesce";
            case "mustard": return "senape";
            case "vinegar": return "aceto";
            case "ginger": return "zenzero";
            case "bacon": return "pancetta";
            case "ham": return "prosciutto";
            default: return name;
        }
    }

    private List<String> translateStepsMock(List<String> steps) {
        if (steps == null) return new ArrayList<>();
        List<String> translated = new ArrayList<>();
        for (String step : steps) {
            if (step == null) continue;
            translated.add(translateStepMockSingle(step));
        }
        return translated;
    }

    private String translateStepMockSingle(String step) {
        if (step == null) return "";
        String t = step;
        // Ovens and heating
        t = t.replaceAll("(?i)\\bpreheat (the )?oven to\\b", "Preriscaldare il forno a")
             .replaceAll("(?i)\\bheat (the )?oil\\b", "Scaldare l'olio")
             .replaceAll("(?i)\\bheat a large (skillet|frying pan)\\b", "Scaldare una padella grande")
             .replaceAll("(?i)\\bheat a large pot\\b", "Scaldare una pentola grande")
             .replaceAll("(?i)\\bbring to (a|the) boil\\b", "Portare a bollore")
             .replaceAll("(?i)\\bsimmer for\\b", "Sobbollire per")
             .replaceAll("(?i)\\breduce (the )?heat\\b", "Ridurre la fiamma")
             .replaceAll("(?i)\\bremove from (the )?heat\\b", "Togliere dal fuoco")
             .replaceAll("(?i)\\blet it cool\\b", "Lasciare raffreddare")
             .replaceAll("(?i)\\bcool completely\\b", "Raffreddare completamente");

        // Prep verbs
        t = t.replaceAll("(?i)\\bin a large bowl\\b", "In una ciotola capiente")
             .replaceAll("(?i)\\bin a medium bowl\\b", "In una ciotola media")
             .replaceAll("(?i)\\bin a small bowl\\b", "In una ciotola piccola")
             .replaceAll("(?i)\\bmix together\\b", "Mescolare insieme")
             .replaceAll("(?i)\\b(mix|stir) well\\b", "Mescolare bene")
             .replaceAll("(?i)\\bstir in\\b", "Incorporare")
             .replaceAll("(?i)\\bwhisk together\\b", "Sbattere insieme")
             .replaceAll("(?i)\\bwhisk the eggs\\b", "Sbattere le uova")
             .replaceAll("(?i)\\bwhisk well\\b", "Sbattere bene")
             .replaceAll("(?i)\\bfinely chopped\\b", "tritato finemente")
             .replaceAll("(?i)\\bchopped finely\\b", "tritato finemente")
             .replaceAll("(?i)\\bfinely chop\\b", "tritare finemente")
             .replaceAll("(?i)\\bchop the\\b", "tagliare il")
             .replaceAll("(?i)\\bslice the\\b", "affettare il")
             .replaceAll("(?i)\\bcut the\\b", "tagliare il")
             .replaceAll("(?i)\\bpeel the\\b", "sbucciare il")
             .replaceAll("(?i)\\bdrain and\\b", "scolare e")
             .replaceAll("(?i)\\bdrain the\\b", "scolare il")
             .replaceAll("(?i)\\bpour the mixture\\b", "versare il composto")
             .replaceAll("(?i)\\bpour into\\b", "versare in")
             .replaceAll("(?i)\\btransfer to\\b", "trasferire in")
             .replaceAll("(?i)\\bplace in\\b", "mettere in")
             .replaceAll("(?i)\\bplace on\\b", "mettere su")
             .replaceAll("(?i)\\bseason with\\b", "condire con")
             .replaceAll("(?i)\\bgarnish with\\b", "guarnire con")
             .replaceAll("(?i)\\bcover and\\b", "coprire e")
             .replaceAll("(?i)\\bstirring occasionally\\b", "mescolando di tanto in tanto");

        // Cooking & time
        t = t.replaceAll("(?i)\\bcook for\\b", "Cuocere per")
             .replaceAll("(?i)\\bbake for\\b", "Cuocere in forno per")
             .replaceAll("(?i)\\bcook until\\b", "Cuocere fino a quando")
             .replaceAll("(?i)\\buntil golden brown\\b", "Fino a doratura")
             .replaceAll("(?i)\\buntil golden\\b", "Fino a doratura")
             .replaceAll("(?i)\\buntil softened\\b", "fino a quando si ammorbidisce")
             .replaceAll("(?i)\\buntil soft\\b", "fino a quando si ammorbidisce")
             .replaceAll("(?i)\\bminutes\\b", "minuti")
             .replaceAll("(?i)\\bminute\\b", "minuto")
             .replaceAll("(?i)\\bhours\\b", "ore")
             .replaceAll("(?i)\\bhour\\b", "ora")
             .replaceAll("(?i)\\bdegrees\\b", "gradi");

        // Ingredients
        t = t.replaceAll("(?i)\\badd the chicken\\b", "Aggiungere il pollo")
             .replaceAll("(?i)\\badd chicken\\b", "Aggiungere pollo")
             .replaceAll("(?i)\\badd the beef\\b", "Aggiungere il manzo")
             .replaceAll("(?i)\\badd beef\\b", "Aggiungere manzo")
             .replaceAll("(?i)\\badd the garlic\\b", "Aggiungere l'aglio")
             .replaceAll("(?i)\\badd garlic\\b", "Aggiungere aglio")
             .replaceAll("(?i)\\badd the onions\\b", "Aggiungere le cipolle")
             .replaceAll("(?i)\\badd the onion\\b", "Aggiungere la cipolla")
             .replaceAll("(?i)\\badd onions\\b", "Aggiungere cipolle")
             .replaceAll("(?i)\\badd onion\\b", "Aggiungere cipolla")
             .replaceAll("(?i)\\badd the butter\\b", "Aggiungere il burro")
             .replaceAll("(?i)\\badd butter\\b", "Aggiungere burro")
             .replaceAll("(?i)\\badd the milk\\b", "Aggiungere il latte")
             .replaceAll("(?i)\\badd milk\\b", "Aggiungere latte")
             .replaceAll("(?i)\\badd the flour\\b", "Aggiungere la farina")
             .replaceAll("(?i)\\badd flour\\b", "Aggiungere farina")
             .replaceAll("(?i)\\badd the sugar\\b", "Aggiungere lo zucchero")
             .replaceAll("(?i)\\badd sugar\\b", "Aggiungere zucchero")
             .replaceAll("(?i)\\badd the eggs\\b", "Aggiungere le uova")
             .replaceAll("(?i)\\badd eggs\\b", "Aggiungere uova")
             .replaceAll("(?i)\\badd the salt\\b", "Aggiungere il sale")
             .replaceAll("(?i)\\badd salt\\b", "Aggiungere sale")
             .replaceAll("(?i)\\badd the pepper\\b", "Aggiungere il pepe")
             .replaceAll("(?i)\\badd pepper\\b", "Aggiungere pepe")
             .replaceAll("(?i)\\badd the water\\b", "Aggiungere l'acqua")
             .replaceAll("(?i)\\badd water\\b", "Aggiungere acqua")
             .replaceAll("(?i)\\badd the cheese\\b", "Aggiungere il formaggio")
             .replaceAll("(?i)\\badd cheese\\b", "Aggiungere formaggio")
             .replaceAll("(?i)\\badd the tomatoes\\b", "Aggiungere i pomodori")
             .replaceAll("(?i)\\badd tomatoes\\b", "Aggiungere pomodori")
             .replaceAll("(?i)\\badd the vegetables\\b", "Aggiungere le verdure")
             .replaceAll("(?i)\\badd vegetables\\b", "Aggiungere verdure")
             .replaceAll("(?i)\\badd the rice\\b", "Aggiungere il riso")
             .replaceAll("(?i)\\badd rice\\b", "Aggiungere riso")
             .replaceAll("(?i)\\badd the pasta\\b", "Aggiungere la pasta")
             .replaceAll("(?i)\\badd pasta\\b", "Aggiungere pasta")
             .replaceAll("(?i)\\badd the potatoes\\b", "Aggiungere le patate")
             .replaceAll("(?i)\\badd potatoes\\b", "Aggiungere patate")
             .replaceAll("(?i)\\badd the carrots\\b", "Aggiungere le carote")
             .replaceAll("(?i)\\badd carrots\\b", "Aggiungere carote")
             .replaceAll("(?i)\\badd the olive oil\\b", "Aggiungere l'olio d'oliva")
             .replaceAll("(?i)\\badd olive oil\\b", "Aggiungere olio d'oliva")
             .replaceAll("(?i)\\badd the vegetable oil\\b", "Aggiungere l'olio vegetale")
             .replaceAll("(?i)\\badd vegetable oil\\b", "Aggiungere olio vegetale");

        // Serving & enjoying
        t = t.replaceAll("(?i)\\bserve hot\\b", "Servire caldissimo")
             .replaceAll("(?i)\\bserve warm\\b", "Servire caldo")
             .replaceAll("(?i)\\bserve with\\b", "Servire con")
             .replaceAll("(?i)\\bserve immediately\\b", "Servire immediatamente")
             .replaceAll("(?i)\\benjoy!?\\b", "Buon appetito!");

        // General conjunctions/prepositions
        t = t.replaceAll("(?i)\\band then\\b", "e poi")
             .replaceAll("(?i)\\bthen\\b", "poi")
             .replaceAll("(?i)\\buntil the\\b", "fino a quando il")
             .replaceAll("(?i)\\buntil it\\b", "fino a quando non")
             .replaceAll("(?i)\\bwith the\\b", "con il")
             .replaceAll("(?i)\\bto the\\b", "al")
             .replaceAll("(?i)\\bin the\\b", "nel")
             .replaceAll("(?i)\\bon the\\b", "sul")
             .replaceAll("(?i)\\bfrom the\\b", "dal")
             .replaceAll("(?i)\\binto the\\b", "nel")
             .replaceAll("(?i)\\babout\\b", "circa")
             .replaceAll("(?i)\\bover medium heat\\b", "a fuoco medio")
             .replaceAll("(?i)\\bover high heat\\b", "a fuoco alto")
             .replaceAll("(?i)\\bover low heat\\b", "a fuoco basso");

        return t;
    }
}
