export interface Ingredient {
  name: string;
  amount: string;
  emoji: string;
}

export interface Nutrition {
  proteins: number;
  fats: number;
  carbs: number;
}

export interface Recipe {
  id: string;
  title: string;
  description: string;
  time: string; // e.g., "15 min"
  difficulty: 'Easy' | 'Medium' | 'Hard';
  rating: number; // e.g., 4.5
  ingredients: Ingredient[];
  nutrition: Nutrition;
  steps: string[];
  tips?: string;
  imageKeyword: string;
  imageUrl?: string;
}

export interface ApiResponse {
  recipes: Recipe[];
}
