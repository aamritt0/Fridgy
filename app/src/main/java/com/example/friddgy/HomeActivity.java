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

public class HomeActivity extends AppCompatActivity {

    // Use the API key from BuildConfig if available, otherwise fallback
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY_HERE")
            ? "YOUR_GEMINI_API_KEY_HERE"
            : BuildConfig.GEMINI_API_KEY;

    private List<String> currentIngredients = new ArrayList<>();
    private List<Recipe> currentRecipes = new ArrayList<>();
    private List<Recipe> generatedRecipes = new ArrayList<>();
    private Map<String, List<Recipe>> mealDbCache = new HashMap<>();

    private TextView tabNew, tabPopular, tabBreakfast, tabSnacks, tabFavorites;
    private String activeTab = "New";

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
            loadImage("https://i.pravatar.cc/150?img=47", ivProfile);
        }

        tabNew = findViewById(R.id.tab_new);
        tabPopular = findViewById(R.id.tab_popular);
        tabBreakfast = findViewById(R.id.tab_breakfast);
        tabSnacks = findViewById(R.id.tab_snacks);
        tabFavorites = findViewById(R.id.tab_favorites);

        if (tabNew != null) tabNew.setOnClickListener(v -> switchTab("New"));
        if (tabPopular != null) tabPopular.setOnClickListener(v -> switchTab("Popular"));
        if (tabBreakfast != null) tabBreakfast.setOnClickListener(v -> switchTab("Breakfast"));
        if (tabSnacks != null) tabSnacks.setOnClickListener(v -> switchTab("Snacks"));
        if (tabFavorites != null) tabFavorites.setOnClickListener(v -> switchTab("Favorites"));

        updateTabsUI();

        EditText etSearch = findViewById(R.id.et_search);
        TextView btnAddIngredient = findViewById(R.id.btn_add_ingredient);

        btnAddIngredient.setOnClickListener(v -> {
            String ingredient = etSearch.getText().toString().trim();
            if (!ingredient.isEmpty() && !currentIngredients.contains(ingredient.toLowerCase())) {
                currentIngredients.add(ingredient.toLowerCase());
                etSearch.setText("");
                updateIngredientsUI();
            }
        });

        btnGenerate.setOnClickListener(v -> generateRecipesFromGemini());

        loadDefaultRecipes();
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
        currentRecipes.clear();
        currentRecipes.add(createMockRecipe("Ramen noodle soup"));
        currentRecipes.add(createMockRecipe("Quesadilla"));
        currentRecipes.add(createMockRecipe("Pilaf with seafood"));
        currentRecipes.add(createMockRecipe("Tom Yam"));
        generatedRecipes.clear();
        generatedRecipes.addAll(currentRecipes);
        updateRecipesUI();
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
                "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=400&q=80"
        );
    }

    private void updateRecipesUI() {
        String label = activeTab.toLowerCase();
        tvRecipeCount.setText(String.format("%02d %s\nrecipes", currentRecipes.size(), label));
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

            TextView tvTitle = cardView.findViewById(R.id.item_title);
            TextView tvTime = cardView.findViewById(R.id.item_time);
            ImageView ivImage = cardView.findViewById(R.id.item_image);
            TextView tvRating = cardView.findViewById(R.id.item_rating);

            tvTitle.setText(recipe.getTitle());
            tvTime.setText(recipe.getTime());
            if (tvRating != null) {
                tvRating.setText(String.valueOf(recipe.getRating()));
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

            cardView.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, DetailActivity.class);
                intent.putExtra("RECIPE", recipe);
                startActivity(intent);
            });

            currentRow.addView(cardView);
        }
    }

    private void switchTab(String tabName) {
        activeTab = tabName;
        updateTabsUI();
        if (tabName.equals("New")) {
            currentRecipes.clear();
            currentRecipes.addAll(generatedRecipes);
            updateRecipesUI();
        } else if (tabName.equals("Favorites")) {
            loadFavorites();
        } else if (tabName.equals("Popular")) {
            loadMealDbRecipes("Seafood", "Popular");
        } else if (tabName.equals("Breakfast")) {
            loadMealDbRecipes("Breakfast", "Breakfast");
        } else if (tabName.equals("Snacks")) {
            loadMealDbRecipes("Starter", "Snacks");
        }
    }

    private void updateTabsUI() {
        TextView[] tabs = {tabNew, tabPopular, tabBreakfast, tabSnacks, tabFavorites};
        String[] tabNames = {"New", "Popular", "Breakfast", "Snacks", "Favorites"};

        for (int i = 0; i < tabs.length; i++) {
            TextView tab = tabs[i];
            String name = tabNames[i];
            if (tab == null) continue;
            if (activeTab.equals(name)) {
                tab.setTextColor(getResources().getColor(R.color.secondary_color, getTheme()));
                tab.setTypeface(null, Typeface.BOLD);
            } else {
                tab.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
                tab.setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    private void loadFavorites() {
        currentRecipes.clear();
        SharedPreferences prefs = getSharedPreferences("favorites", Context.MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String value = entry.getValue().toString();
            if (value.startsWith("{")) {
                Recipe recipe = Recipe.fromJson(value);
                if (recipe != null) {
                    currentRecipes.add(recipe);
                }
            } else {
                currentRecipes.add(createMockRecipe(value));
            }
        }
        updateRecipesUI();
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
                        int count = Math.min(mealList.size(), 4);

                        for (int i = 0; i < count; i++) {
                            JSONObject m = mealList.get(i);
                            String idMeal = m.getString("idMeal");

                            URL detailsUrl = new URL("https://www.themealdb.com/api/json/v1/1/lookup.php?i=" + idMeal);
                            HttpURLConnection detailsConn = (HttpURLConnection) detailsUrl.openConnection();
                            detailsConn.setRequestMethod("GET");
                            if (detailsConn.getResponseCode() == 200) {
                                BufferedReader dBr = new BufferedReader(new InputStreamReader(detailsConn.getInputStream(), "UTF-8"));
                                StringBuilder dSb = new StringBuilder();
                                while ((line = dBr.readLine()) != null) {
                                    dSb.append(line);
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
                                                ingredients.add(new Ingredient(name, amount, "🥘"));
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

                                    fetchedRecipes.add(new Recipe(
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
                                    ));
                                }
                            }
                        }
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
        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                runOnUiThread(() -> imageView.setImageBitmap(bitmap));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
}
