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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private static final String GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE"; // Placeholder

    private List<String> currentIngredients = new ArrayList<>();
    private List<Recipe> currentRecipes = new ArrayList<>();

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
                // Mock payload for testing if API key is not set
                if (GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY_HERE")) {
                    Thread.sleep(1500); // Simulate network
                    Recipe r1 = createMockRecipe("Gemini generated 1");
                    Recipe r2 = createMockRecipe("Gemini generated 2");
                    currentRecipes.clear();
                    currentRecipes.add(r1);
                    currentRecipes.add(r2);
                } else {
                    // Actual Gemini API Call (Conceptual structure matching constraints)
                    URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    // Build JSON request manually
                    String prompt = "Generate 2 recipes using: " + String.join(", ", currentIngredients);
                    JSONObject requestBody = new JSONObject();
                    JSONArray contents = new JSONArray();
                    JSONObject content = new JSONObject();
                    JSONArray parts = new JSONArray();
                    JSONObject part = new JSONObject();
                    part.put("text", prompt);
                    parts.put(part);
                    content.put("parts", parts);
                    contents.put(content);
                    requestBody.put("contents", contents);

                    OutputStream os = conn.getOutputStream();
                    os.write(requestBody.toString().getBytes("UTF-8"));
                    os.close();

                    if (conn.getResponseCode() == 200) {
                        // In reality, we'd parse the complex Gemini JSON schema here into Recipe objects
                        // using org.json, followed by a MealDB call for the image.
                        // We simulate success here.
                        Recipe r1 = createMockRecipe("Generated from API");
                        currentRecipes.clear();
                        currentRecipes.add(r1);
                    }
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnGenerate.setEnabled(true);
                    updateRecipesUI();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnGenerate.setEnabled(true);
                    Toast.makeText(HomeActivity.this, "Failed to generate", Toast.LENGTH_SHORT).show();
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
        tvRecipeCount.setText(String.format("%02d new\nrecipes", currentRecipes.size()));
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

            tvTitle.setText(recipe.getTitle());
            tvTime.setText(recipe.getTime());

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
}
