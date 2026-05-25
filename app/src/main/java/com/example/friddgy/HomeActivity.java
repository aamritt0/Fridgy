package com.example.friddgy;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.bumptech.glide.Glide;
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

    // la chiave api gemini viene caricata dinamicamente dalle sharedpreferences

    private List<String> currentIngredients = new ArrayList<>();
    private List<Recipe> currentRecipes = new ArrayList<>();
    private List<Recipe> generatedRecipes = new ArrayList<>();
    private Map<String, List<Recipe>> mealDbCache = new HashMap<>();

    private LinearLayout containerRecents, containerFavorites;
    private View sectionRecents, sectionFavorites;
    private String activeTab = "Breakfast";
    private View catBreakfastView, catLunchView, catDinnerView, catDessertView, catSaladsView;

    private com.google.android.material.chip.ChipGroup linearIngredients;
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
    private String dialogSelectedAvatarPath = null;
    private ImageView dialogIvProfile = null;

    private final androidx.activity.result.ActivityResultLauncher<String> pickAvatarLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    String savedPath = saveCustomAvatar(uri);
                    if (savedPath != null) {
                        dialogSelectedAvatarPath = savedPath;
                        if (dialogIvProfile != null) {
                            Glide.with(this)
                                    .load(savedPath)
                                    .signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()))
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .error(android.R.drawable.ic_menu_gallery)
                                    .into(dialogIvProfile);
                            dialogIvProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            dialogIvProfile.setImageTintList(null); // rimuove il colore dell'icona fotocamera
                        }
                    }
                }
            }
    );

    private String saveCustomAvatar(android.net.Uri uri) {
        java.io.InputStream in = null;
        java.io.OutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            java.io.File file = new java.io.File(getFilesDir(), "profile_picture_custom.jpg");
            out = new java.io.FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    private android.os.Handler loadingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable loadingRunnable;
    private boolean isLoadingActive = false;
    private boolean isGeneratedViewActive = false;

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

        catBreakfastView = findViewById(R.id.cat_breakfast);
        catLunchView = findViewById(R.id.cat_lunch);
        catDinnerView = findViewById(R.id.cat_dinner);
        catDessertView = findViewById(R.id.cat_dessert);
        catSaladsView = findViewById(R.id.cat_salads);

        if (catBreakfastView != null) catBreakfastView.setOnClickListener(v -> switchTab("Breakfast"));
        if (catLunchView != null) catLunchView.setOnClickListener(v -> switchTab("Lunch"));
        if (catDinnerView != null) catDinnerView.setOnClickListener(v -> switchTab("Dinner"));
        if (catDessertView != null) catDessertView.setOnClickListener(v -> switchTab("Dessert"));
        if (catSaladsView != null) catSaladsView.setOnClickListener(v -> switchTab("Salads"));

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

        // inizializza la cache dal disco (la chiave v6 invalida le vecchie cache non tradotte)
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
        // se la schermata delle ricette generate è attiva (ad esempio quando l'utente torna indietro dal dettaglio),
        // non ricaricare i recenti/preferiti in modo da mantenere visibili i risultati generati.
        if (!isGeneratedViewActive) {
            populateHorizontalSection(containerRecents, sectionRecents, loadRecents());
            populateHorizontalSection(containerFavorites, sectionFavorites, loadFavorites());
        }
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
            com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) pillView;
            chip.setText(ingredient);

            chip.setOnCloseIconClickListener(v -> {
                currentIngredients.remove(ingredient);
                updateIngredientsUI();
            });

            linearIngredients.addView(pillView);
        }
    }

    private void generateRecipesFromGemini() {
        if (currentIngredients.isEmpty()) return;

        String apiKeyVal = ThemeManager.getGeminiApiKey(this);
        if (apiKeyVal == null || apiKeyVal.trim().isEmpty()) {
            apiKeyVal = BuildConfig.GEMINI_API_KEY;
        }
        final String apiKey = apiKeyVal;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            Toast.makeText(this, "Configura la tua API Key Gemini nelle impostazioni del profilo per sbloccare la generazione con l'IA.", Toast.LENGTH_LONG).show();
            return;
        }

        progressBar.setVisibility(View.GONE);
        btnGenerate.setEnabled(false);
        showLoadingState(false);

        new Thread(() -> {
            try {
                // costruisce manualmente la richiesta json con generationconfig e responseschema
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

                    // configurazione per l'output strutturato
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
                            responseJsonStr = makeGeminiCall(model, requestBody.toString(), apiKey);
                            break; // fatto!
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

                                // recupera l'immagine da mealdb
                                String imageUrl = fetchMealDbImage(imageKeyword, title);

                                currentRecipes.add(new Recipe(
                                        id, title, desc, time, diff, rating,
                                        ingredientsList, proteins, fats, carbs,
                                        stepsList, tips, imageUrl
                                ));
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
            
            // simula un layout staggered
            if (i % 2 != 0) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) cardView.getLayoutParams();
                params.topMargin = 120; // effetto stagger
                cardView.setLayoutParams(params);
            }

            ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);
            int accentColor = Color.parseColor(theme.accentColor);

            TextView tvTitle = cardView.findViewById(R.id.item_title);
            ImageView ivImage = cardView.findViewById(R.id.item_image);

            tvTitle.setText(recipe.getTitle());

            // imposta le 5 stelle di valutazione da codice
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
                        stars[j].setImageTintList(ColorStateList.valueOf(Color.parseColor("#4b4c53"))); // grigio scuro per quelle non selezionate
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

            // carica l'immagine
            loadImage(recipe.getImageUrl(), ivImage);

            // imposta il tempo di preparazione
            TextView tvTime = cardView.findViewById(R.id.item_time);
            if (tvTime != null && recipe.getTime() != null && !recipe.getTime().isEmpty()) {
                tvTime.setText(recipe.getTime());
            }

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
        // aggiorna l'evidenziazione delle icone delle categorie
        updateCategoryHighlight(categoryName);
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

    private void updateCategoryHighlight(String activeCategory) {
        ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);
        int accentColor = Color.parseColor(theme.accentColor);
        int inactiveColor = Color.parseColor("#2C2D35"); // colore di sfondo della scheda

        View[] catViews = {catBreakfastView, catLunchView, catDinnerView, catDessertView, catSaladsView};
        String[] catNames = {"Breakfast", "Lunch", "Dinner", "Dessert", "Salads"};

        for (int i = 0; i < catViews.length; i++) {
            if (catViews[i] == null) continue;
            // il primo elemento figlio del linearlayout è il framelayout (il cerchio)
            android.view.ViewGroup catContainer = (android.view.ViewGroup) catViews[i];
            if (catContainer.getChildCount() > 0) {
                android.view.View circle = catContainer.getChildAt(0);
                boolean isActive = catNames[i].equals(activeCategory);
                circle.setBackgroundTintList(ColorStateList.valueOf(isActive ? accentColor : inactiveColor));
                // aggiorna il colore del testo
                if (catContainer.getChildCount() > 1 && catContainer.getChildAt(1) instanceof TextView) {
                    TextView label = (TextView) catContainer.getChildAt(1);
                    label.setTextColor(isActive ? accentColor : Color.parseColor("#a0a0ab"));
                }
            }
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
            
            // imposta i parametri di layout personalizzati per gli elementi a scorrimento orizzontale
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

            // stelle di valutazione
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
                            stars[j].setImageTintList(ColorStateList.valueOf(Color.parseColor("#ffcc00"))); // oro/giallo
                        } else {
                            stars[j].setImageTintList(ColorStateList.valueOf(Color.parseColor("#4b4c53"))); // grigio scuro
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

            // imposta il tempo di preparazione
            TextView tvTimeH = cardView.findViewById(R.id.item_time);
            if (tvTimeH != null && recipe.getTime() != null && !recipe.getTime().isEmpty()) {
                tvTimeH.setText(recipe.getTime());
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
                        Thread.sleep(7000); // ritardo di sicurezza per evitare rate limit di gemini durante il pre-fetch in background
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
        // se abbiamo una cache in memoria valida per questo tab, la usiamo subito (senza caricare)
        List<Recipe> cached = mealDbCache.get(tabName);
        if (cached != null && !cached.isEmpty()) {
            currentRecipes.clear();
            currentRecipes.addAll(cached);
            updateRecipesUI();
            return;
        }

        // nessuna cache disponibile: mostriamo il caricamento, scarichiamo e traduciamo
        progressBar.setVisibility(View.GONE);
        showLoadingState(true);
        new Thread(() -> {
            try {
                List<Recipe> fetchedRecipes = fetchCategoryFromMealDb(categoryName);
                List<Recipe> translatedRecipes = translateRecipesToItalian(fetchedRecipes);
                if (translatedRecipes == null || translatedRecipes.isEmpty()) {
                    translatedRecipes = fetchedRecipes; // ripiego sulle ricette non tradotte se fallisce tutto
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
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> builder = Glide.with(this).load(urlStr);
        if (urlStr.startsWith("/") || urlStr.startsWith("file://") || urlStr.contains("profile_picture")) {
            builder = builder.signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()));
        }
        builder.placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(imageView);
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

    private String makeGeminiCall(String modelName, String requestBodyStr, String apiKey) throws Exception {
        int maxRetries = 3;
        long retryDelayMs = 3000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey);
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

        // aggiorna la foto del profilo
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
            String avatarUrl = ThemeManager.getAvatarUrl(this);
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                ivProfile.setImageDrawable(null);
            } else {
                loadImage(avatarUrl, ivProfile);
            }
        }

        // aggiorna il nome di saluto dell'utente
        TextView tvHelloUser = findViewById(R.id.tv_hello_user);
        if (tvHelloUser != null) {
            tvHelloUser.setText("Ciao, " + ThemeManager.getUserName(this) + " \uD83D\uDC4B");
        }

        // aggiorna il colore del testo del pulsante per aggiungere gli ingredienti
        TextView btnAddIngredient = findViewById(R.id.btn_add_ingredient);
        if (btnAddIngredient != null) {
            btnAddIngredient.setTextColor(accentColor);
        }

        // aggiorna la tinta di sfondo del pulsante per generare
        if (btnGenerate != null) {
            btnGenerate.setBackgroundTintList(ColorStateList.valueOf(accentColor));
        }

        // aggiorna la barra di progresso
        if (progressBar != null) {
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(accentColor));
        }

        // aggiorna la vista delle ricette solo quando non sta caricando
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

        EditText etApiKey = dialogView.findViewById(R.id.dialog_et_api_key);
        if (etApiKey != null) {
            etApiKey.setText(ThemeManager.getGeminiApiKey(this));
        }

        LinearLayout layoutThemes = dialogView.findViewById(R.id.dialog_layout_themes);

        // pre-recupera le scelte
        final String[] selectedTheme = {ThemeManager.getCurrentTheme(this).name};

        // popola le scelte dei temi
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

        // logica per l'avatar personalizzato nel dialog
        View imageContainer = dialogView.findViewById(R.id.dialog_profile_image_container);
        ImageView ivProfile = dialogView.findViewById(R.id.dialog_iv_profile);
        View btnPickImage = dialogView.findViewById(R.id.dialog_btn_pick_image);
        View badgeContainer = dialogView.findViewById(R.id.dialog_badge_container);

        ThemeManager.ThemePreset currentActiveTheme = ThemeManager.getCurrentTheme(this);
        int currentAccentColor = Color.parseColor(currentActiveTheme.accentColor);

        if (imageContainer != null) {
            imageContainer.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            imageContainer.setClipToOutline(true);

            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.OVAL);
            border.setColor(currentAccentColor);
            imageContainer.setBackground(border);
        }

        if (ivProfile != null) {
            ivProfile.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            ivProfile.setClipToOutline(true);
            ivProfile.setPadding(6, 6, 6, 6);

            dialogSelectedAvatarPath = ThemeManager.getAvatarUrl(this);
            if (dialogSelectedAvatarPath == null || dialogSelectedAvatarPath.isEmpty()) {
                ivProfile.setImageDrawable(null);
            } else {
                loadImage(dialogSelectedAvatarPath, ivProfile);
            }
            ivProfile.setImageTintList(null); // rimuove la tinta
        }

        if (badgeContainer != null) {
            badgeContainer.setBackgroundTintList(ColorStateList.valueOf(currentAccentColor));
        }

        dialogIvProfile = ivProfile;
        if (btnPickImage != null) {
            btnPickImage.setOnClickListener(v -> pickAvatarLauncher.launch("image/*"));
        }

        dialogView.findViewById(R.id.dialog_btn_cancel).setOnClickListener(v -> dialog.dismiss());
        
        Button btnApply = dialogView.findViewById(R.id.dialog_btn_apply);
        btnApply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(currentAccentColor));

        btnApply.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                ThemeManager.setUserName(HomeActivity.this, name);
            }
            ThemeManager.setCurrentTheme(HomeActivity.this, selectedTheme[0]);

            if (dialogSelectedAvatarPath != null) {
                if (dialogSelectedAvatarPath.contains("profile_picture_custom.jpg")) {
                    try {
                        java.io.File src = new java.io.File(dialogSelectedAvatarPath);
                        java.io.File dest = new java.io.File(getFilesDir(), "profile_picture.jpg");
                        if (src.exists()) {
                            java.io.FileInputStream in = new java.io.FileInputStream(src);
                            java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                            in.close();
                            out.close();
                            ThemeManager.setAvatarUrl(HomeActivity.this, dest.getAbsolutePath());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    ThemeManager.setAvatarUrl(HomeActivity.this, dialogSelectedAvatarPath);
                }
            }

            if (etApiKey != null) {
                String apiKey = etApiKey.getText().toString().trim();
                ThemeManager.setGeminiApiKey(HomeActivity.this, apiKey);
            }

            applyCustomizations();
            dialog.dismiss();
            
            Toast.makeText(HomeActivity.this, "Personalizzazioni applicate!", Toast.LENGTH_SHORT).show();
        });

        dialog.setOnDismissListener(d -> {
            dialogIvProfile = null;
            dialogSelectedAvatarPath = null;
        });

        dialog.show();
    }

    private void showLoadingState(boolean isInitialization) {
        // va chiamato sul thread principale, meglio non metterlo in runonuithread
        // altrimenti rischiamo che venga rimandato al ciclo successivo e sfarfalli la schermata
        if (layoutLoadingState == null || layoutHomeContent == null) return;

        isLoadingActive = true;

        // cancella animazioni o runnable ancora attivi
        if (loadingRunnable != null) {
            loadingHandler.removeCallbacks(loadingRunnable);
            loadingRunnable = null;
        }

        // nasconde solo il contenuto principale, lasciando visibili l'intestazione, la ricerca
        // e i pulsanti degli ingredienti così l'utente vede cosa sta facendo
        layoutHomeContent.setVisibility(View.GONE);
        layoutLoadingState.setVisibility(View.VISIBLE);

        // mantiene visibile la barra di ricerca e la testata durante il caricamento
        if (layoutHeaderBar != null) layoutHeaderBar.setVisibility(View.VISIBLE);
        if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.VISIBLE);
        // la barra degli ingredienti e il pulsante per generare sono controllati da updateingredientsui()
        // quindi non vanno nascosti qui

            TextView tvEmoji = layoutLoadingState.findViewById(R.id.tv_loading_emoji);
            TextView tvTitle = layoutLoadingState.findViewById(R.id.tv_loading_title);
            TextView tvSubtitle = layoutLoadingState.findViewById(R.id.tv_loading_subtitle);
            TextView tvTip = layoutLoadingState.findViewById(R.id.tv_loading_tip);
            View emojiContainer = layoutLoadingState.findViewById(R.id.layout_emoji_container);
            View dot1 = layoutLoadingState.findViewById(R.id.dot1);
            View dot2 = layoutLoadingState.findViewById(R.id.dot2);
            View dot3 = layoutLoadingState.findViewById(R.id.dot3);

            // imposta i testi iniziali in base allo stato
            if (isInitialization) {
                if (tvTitle != null) tvTitle.setText("Inizializzazione...");
                if (tvSubtitle != null) tvSubtitle.setText("Preparazione dei tuoi ingredienti freschi");
            } else {
                if (tvTitle != null) tvTitle.setText("Creando qualche idea...");
                if (tvSubtitle != null) tvSubtitle.setText("Consultando i nostri chef stellati");
            }

            // 1. animazione di rotazione per il cerchio dell'emoji
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

            // 2. effetto pulsante / scala per il testo dell'emoji
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

            // elenco dei consigli dello chef
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

            // elenco dei titoli di caricamento
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

            // elenco di emoji sul cibo
            String[] foodEmojis = {"🍳", "🥣", "🥘", "🥗", "🍲", "🥞", "🧁", "🍝", "🍕"};

            final int[] tipIndex = {0};
            final int[] titleIndex = {0};
            final int[] emojiIndex = {0};
            final int[] activeDot = {0};

            loadingRunnable = new Runnable() {
                @Override
                public void run() {
                    if (layoutLoadingState.getVisibility() != View.VISIBLE) return;

                    // aggiorna il consiglio dello chef con una semplice sfumatura
                    tipIndex[0] = (tipIndex[0] + 1) % chefTips.length;
                    if (tvTip != null) {
                        tvTip.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                            tvTip.setText(chefTips[tipIndex[0]]);
                            tvTip.animate().alpha(1f).setDuration(250).start();
                        }).start();
                    }

                    // aggiorna il titolo con una sfumatura
                    titleIndex[0] = (titleIndex[0] + 1) % loadingTitles.length;
                    if (tvTitle != null) {
                        tvTitle.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                            tvTitle.setText(loadingTitles[titleIndex[0]]);
                            tvTitle.animate().alpha(1f).setDuration(250).start();
                        }).start();
                    }

                    // cambia emoji ogni tanto
                    emojiIndex[0] = (emojiIndex[0] + 1) % foodEmojis.length;
                    if (tvEmoji != null) {
                        tvEmoji.setText(foodEmojis[emojiIndex[0]]);
                    }

                    // aggiorna i pallini di caricamento
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
        // dismissloadingstate viene sempre invocato da thread in background con runonuithread
        // quindi gira sul thread principale
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
        // Header and search bar are always visible (we keep them during loading too)
        if (layoutHeaderBar != null) layoutHeaderBar.setVisibility(View.VISIBLE);
        if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.VISIBLE);
        // ripristina dinamicamente la visibilità degli ingredienti
        updateIngredientsUI();
    }

    private void showGeneratedRecipesView(boolean show) {
        runOnUiThread(() -> {
            if (show) {
                isGeneratedViewActive = true;
                // tiene visibili l'intestazione e la barra di ricerca
                // nascondiamo categorie, recenti, preferiti e il contatore standard
                // così mostriamo solo la griglia delle ricette generate
                if (layoutHeaderBar != null) layoutHeaderBar.setVisibility(View.VISIBLE);
                if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.VISIBLE);
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
                isGeneratedViewActive = false;
                // ripristina le viste standard
                if (layoutHeaderBar != null) layoutHeaderBar.setVisibility(View.VISIBLE);
                if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.VISIBLE);
                if (layoutCategories != null) layoutCategories.setVisibility(View.VISIBLE);
                if (layoutGeneratedHeader != null) layoutGeneratedHeader.setVisibility(View.GONE);
                if (tvRecipeCount != null) tvRecipeCount.setVisibility(View.VISIBLE);

                // aggiorna le ricette dei recenti, preferiti e della categoria selezionata
                populateHorizontalSection(containerRecents, sectionRecents, loadRecents());
                populateHorizontalSection(containerFavorites, sectionFavorites, loadFavorites());
                switchTab(activeTab);
            }
            updateIngredientsUI();
        });
    }

    private List<Recipe> translateRecipesToItalian(List<Recipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return recipes;

        String apiKey = ThemeManager.getGeminiApiKey(this);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = BuildConfig.GEMINI_API_KEY;
        }

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                return translateRecipesWithGemini(recipes, apiKey);
            } catch (Exception e) {
                Log.e("GeminiTranslation", "Error translating with Gemini: " + e.getMessage());
            }
        }
        return translateRecipesWithMyMemory(recipes);
    }

    private List<Recipe> translateRecipesWithGemini(List<Recipe> recipes, String apiKey) throws Exception {
        JSONArray arr = new JSONArray();
        for (Recipe r : recipes) {
            JSONObject obj = new JSONObject();
            obj.put("id", r.getId());
            obj.put("title", r.getTitle());
            obj.put("description", r.getDescription());
            obj.put("time", r.getTime());
            obj.put("difficulty", r.getDifficulty());
            obj.put("rating", r.getRating());
            obj.put("proteins", r.getProteins());
            obj.put("fats", r.getFats());
            obj.put("carbs", r.getCarbs());
            obj.put("tips", r.getTips());
            obj.put("imageUrl", r.getImageUrl());

            JSONArray ingArr = new JSONArray();
            for (Ingredient ing : r.getIngredients()) {
                JSONObject ingObj = new JSONObject();
                ingObj.put("name", ing.getName());
                ingObj.put("amount", ing.getAmount());
                ingObj.put("emoji", ing.getEmoji());
                ingArr.put(ingObj);
            }
            obj.put("ingredients", ingArr);

            JSONArray stepArr = new JSONArray();
            for (String step : r.getSteps()) {
                stepArr.put(step);
            }
            obj.put("steps", stepArr);

            arr.put(obj);
        }

        JSONObject requestBody = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();

        String prompt = "Translate the following recipes from English to Italian. " +
                "Translate the 'title', 'description', 'tips' (if not empty), each entry in the 'steps' array, and the 'name' and 'amount' fields of each ingredient to Italian. " +
                "Keep all other fields exactly as they are ('id', 'time', 'rating', 'proteins', 'fats', 'carbs', 'imageUrl', and ingredient 'emoji'). " +
                "For the 'difficulty' field, translate 'Easy' to 'Facile', 'Medium' to 'Medio', and 'Hard' to 'Difficile'. " +
                "IMPORTANT: Return ONLY a valid JSON array containing the translated recipes. Do not wrap it in markdown (like ```json), just return the raw JSON text.\n\n" +
                arr.toString();

        part.put("text", prompt);
        parts.put(part);
        content.put("parts", parts);
        contents.put(content);
        requestBody.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("responseMimeType", "application/json");
        requestBody.put("generationConfig", generationConfig);

        String responseJsonStr = null;
        String[] models = {"gemini-2.5-flash", "gemini-1.5-flash"};
        for (String model : models) {
            try {
                responseJsonStr = makeGeminiCall(model, requestBody.toString(), apiKey);
                break;
            } catch (Exception e) {
                Log.w("GeminiTranslation", model + " failed: " + e.getMessage());
            }
        }

        if (responseJsonStr != null) {
            JSONObject responseJson = new JSONObject(responseJsonStr);
            JSONArray candidates = responseJson.getJSONArray("candidates");
            if (candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);
                JSONObject contentObj = candidate.getJSONObject("content");
                JSONArray partsVal = contentObj.getJSONArray("parts");
                if (partsVal.length() > 0) {
                    String textResponse = partsVal.getJSONObject(0).getString("text");
                    JSONArray translatedArr = new JSONArray(textResponse.trim());
                    List<Recipe> translatedList = new ArrayList<>();
                    for (int i = 0; i < translatedArr.length(); i++) {
                        JSONObject recipeObj = translatedArr.getJSONObject(i);
                        Recipe original = recipes.get(i);
                        
                        String id = recipeObj.optString("id", original.getId());
                        String title = recipeObj.optString("title", original.getTitle());
                        String description = recipeObj.optString("description", original.getDescription());
                        String time = recipeObj.optString("time", original.getTime());
                        String difficulty = recipeObj.optString("difficulty", original.getDifficulty());
                        double rating = recipeObj.optDouble("rating", original.getRating());
                        double proteins = recipeObj.optDouble("proteins", original.getProteins());
                        double fats = recipeObj.optDouble("fats", original.getFats());
                        double carbs = recipeObj.optDouble("carbs", original.getCarbs());
                        String tips = recipeObj.optString("tips", original.getTips());
                        String imageUrl = recipeObj.optString("imageUrl", original.getImageUrl());
                        
                        List<Ingredient> ingredients = new ArrayList<>();
                        JSONArray ingArr = recipeObj.optJSONArray("ingredients");
                        if (ingArr != null) {
                            for (int j = 0; j < ingArr.length(); j++) {
                                JSONObject ingObj = ingArr.getJSONObject(j);
                                String name = ingObj.optString("name", original.getIngredients().get(j).getName());
                                String amount = ingObj.optString("amount", original.getIngredients().get(j).getAmount());
                                String emoji = ingObj.optString("emoji", original.getIngredients().get(j).getEmoji());
                                ingredients.add(new Ingredient(name, amount, emoji));
                            }
                        } else {
                            ingredients = original.getIngredients();
                        }
                        
                        List<String> steps = new ArrayList<>();
                        JSONArray stepsArr = recipeObj.optJSONArray("steps");
                        if (stepsArr != null) {
                            for (int j = 0; j < stepsArr.length(); j++) {
                                steps.add(stepsArr.getString(j));
                            }
                        } else {
                            steps = original.getSteps();
                        }
                        
                        translatedList.add(new Recipe(id, title, description, time, difficulty, rating, ingredients, proteins, fats, carbs, steps, tips, imageUrl));
                    }
                    return translatedList;
                }
            }
        }
        throw new Exception("Empty response from Gemini during translation");
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
            if (title == null || title.isEmpty()) {
                title = r.getTitle();
            }

            String description = r.getDescription();
            if (description != null) {
                if (description.startsWith("A delicious ")) {
                    description = description.replace("A delicious ", "Un delizioso piatto di tipo ")
                                             .replace(" dish.", ".");
                }
                description = translateTextMyMemory(description);
            }

            String difficulty = r.getDifficulty();
            if (difficulty != null) {
                if (difficulty.equalsIgnoreCase("Easy")) difficulty = "Facile";
                else if (difficulty.equalsIgnoreCase("Medium")) difficulty = "Medio";
                else if (difficulty.equalsIgnoreCase("Hard")) difficulty = "Difficile";
            }

            String tips = r.getTips();
            if (tips != null && tips.equalsIgnoreCase("Serve hot and enjoy!")) {
                tips = "Servire caldo e gustare!";
            } else if (tips != null) {
                tips = translateTextMyMemory(tips);
            }

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
                    if (translatedStep == null) {
                        translatedStep = step;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loadingRunnable != null) {
            loadingHandler.removeCallbacks(loadingRunnable);
            loadingRunnable = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}
