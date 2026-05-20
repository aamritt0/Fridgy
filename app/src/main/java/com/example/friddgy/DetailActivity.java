package com.example.friddgy;

import android.content.res.ColorStateList;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import org.json.JSONException;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class DetailActivity extends AppCompatActivity {

    private Recipe recipe;
    private boolean isFavorite = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recipe = (Recipe) getIntent().getSerializableExtra("RECIPE");
        if (recipe == null) {
            finish();
            return;
        }
        addToRecents(recipe);

        prefs = getSharedPreferences("favorites", Context.MODE_PRIVATE);
        isFavorite = prefs.contains(recipe.getId());

        ImageView btnBack = findViewById(R.id.btn_back);
        ThemeManager.applyTouchScaleAnimation(btnBack, () -> finish());

        ImageView btnFavorite = findViewById(R.id.btn_favorite);
        updateFavoriteIcon(btnFavorite);

        ThemeManager.applyTouchScaleAnimation(btnFavorite, () -> {
            isFavorite = !isFavorite;
            updateFavoriteIcon(btnFavorite);
            if (isFavorite) {
                prefs.edit().putString(recipe.getId(), recipe.toJson()).apply();
            } else {
                prefs.edit().remove(recipe.getId()).apply();
            }
        });

        populateUI();
    }

    private void updateFavoriteIcon(ImageView btnFavorite) {
        if (isFavorite) {
            btnFavorite.setImageResource(R.drawable.ic_heart_filled);
            btnFavorite.clearColorFilter();
        } else {
            btnFavorite.setImageResource(R.drawable.ic_heart_outline);
            btnFavorite.setColorFilter(Color.WHITE);
        }
    }

    private void populateUI() {
        TextView tvTimeBadge = findViewById(R.id.tv_time_badge);
        TextView tvTitle = findViewById(R.id.tv_title);
        TextView tvRating = findViewById(R.id.tv_rating);
        TextView tvDifficulty = findViewById(R.id.tv_difficulty);
        TextView tvTime = findViewById(R.id.tv_time);
        TextView tvDescription = findViewById(R.id.tv_description);
        
        TextView tvProteins = findViewById(R.id.tv_proteins);
        TextView tvFats = findViewById(R.id.tv_fats);
        TextView tvCarbs = findViewById(R.id.tv_carbs);

        tvTimeBadge.setText(recipe.getTime());
        tvTitle.setText(recipe.getTitle());
        tvRating.setText(String.valueOf(recipe.getRating()));
        tvDifficulty.setText(recipe.getDifficulty());
        tvTime.setText(recipe.getTime());
        tvDescription.setText(recipe.getDescription());

        ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);
        int accentColor = Color.parseColor(theme.accentColor);
        int secondaryColor = Color.parseColor(theme.secondaryColor);

        // Dynamically style badge backgrounds
        if (tvTimeBadge.getParent() instanceof View) {
            ((View) tvTimeBadge.getParent()).setBackgroundTintList(ColorStateList.valueOf(secondaryColor));
        }
        if (tvRating.getParent() instanceof View) {
            ((View) tvRating.getParent()).setBackgroundTintList(ColorStateList.valueOf(secondaryColor));
        }

        tvRating.setTextColor(accentColor);
        tvProteins.setTextColor(accentColor);
        tvFats.setTextColor(accentColor);
        tvCarbs.setTextColor(accentColor);

        tvProteins.setText(recipe.getProteins() + " g");
        tvFats.setText(recipe.getFats() + " g");
        tvCarbs.setText(recipe.getCarbs() + " g");

        // Load image
        ImageView ivDetail = findViewById(R.id.detail_image);
        loadImage(recipe.getImageUrl(), ivDetail);

        // Ingredients
        LinearLayout containerIngredients = findViewById(R.id.container_detail_ingredients);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Ingredient ingredient : recipe.getIngredients()) {
            View itemView = inflater.inflate(R.layout.item_detail_ingredient, containerIngredients, false);
            TextView tvEmoji = itemView.findViewById(R.id.tv_emoji);
            TextView tvName = itemView.findViewById(R.id.tv_name);
            tvEmoji.setText(ingredient.getEmoji());
            tvName.setText(ingredient.getName());
            containerIngredients.addView(itemView);
        }

        // Instructions
        LinearLayout containerInstructions = findViewById(R.id.container_instructions);
        for (int i = 0; i < recipe.getSteps().size(); i++) {
            View itemView = inflater.inflate(R.layout.item_instruction_step, containerInstructions, false);
            TextView tvStepNumber = itemView.findViewById(R.id.tv_step_number);
            TextView tvStepText = itemView.findViewById(R.id.tv_step_text);
            tvStepNumber.setText(String.valueOf(i + 1));
            tvStepText.setText(recipe.getSteps().get(i));

            // Dynamic Step styling
            tvStepNumber.setTextColor(accentColor);
            tvStepNumber.setBackgroundTintList(ColorStateList.valueOf(secondaryColor));

            containerInstructions.addView(itemView);
        }

        // Tips
        if (recipe.getTips() != null && !recipe.getTips().isEmpty()) {
            View containerTip = findViewById(R.id.container_tip);
            containerTip.setVisibility(View.VISIBLE);
            TextView tvTip = findViewById(R.id.tv_tip);
            tvTip.setText(recipe.getTips());

            if (containerTip instanceof LinearLayout) {
                LinearLayout llTip = (LinearLayout) containerTip;
                if (llTip.getChildCount() > 0 && llTip.getChildAt(0) instanceof TextView) {
                    ((TextView) llTip.getChildAt(0)).setTextColor(accentColor);
                }
            }
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

    private void addToRecents(Recipe recipe) {
        SharedPreferences recentsPrefs = getSharedPreferences("recents", Context.MODE_PRIVATE);
        String recentsStr = recentsPrefs.getString("list", "[]");
        try {
            JSONArray arr = new JSONArray(recentsStr);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                if (obj.getString("id").equals(recipe.getId())) {
                    arr.remove(i);
                    break;
                }
            }
            JSONArray newArr = new JSONArray();
            newArr.put(new org.json.JSONObject(recipe.toJson()));
            for (int i = 0; i < arr.length(); i++) {
                newArr.put(arr.getJSONObject(i));
            }
            if (newArr.length() > 10) {
                JSONArray limitedArr = new JSONArray();
                for (int i = 0; i < 10; i++) {
                    limitedArr.put(newArr.getJSONObject(i));
                }
                newArr = limitedArr;
            }
            recentsPrefs.edit().putString("list", newArr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
