import { Recipe, Ingredient } from '../types';

const cache = new Map<string, Recipe[]>();

export async function getRecipesForTab(tab: string): Promise<Recipe[]> {
  if (cache.has(tab)) {
    return cache.get(tab)!;
  }

  let category = '';
  if (tab === 'Breakfast') category = 'Breakfast';
  else if (tab === 'Snacks') category = 'Starter';
  else category = 'Seafood'; // For Popular

  try {
    const res = await fetch(`https://www.themealdb.com/api/json/v1/1/filter.php?c=${category}`);
    const data = await res.json();
    if (!data.meals) return [];

    // Select random 4 meals to keep it quick
    const selectedMeals = data.meals.sort(() => 0.5 - Math.random()).slice(0, 4);
    
    const recipes: Recipe[] = [];
    for (const m of selectedMeals) {
      const detailsRes = await fetch(`https://www.themealdb.com/api/json/v1/1/lookup.php?i=${m.idMeal}`);
      const detailsData = await detailsRes.json();
      const details = detailsData.meals[0];

      const ingredients: Ingredient[] = [];
      for (let i = 1; i <= 20; i++) {
        const name = details[`strIngredient${i}`];
        const amount = details[`strMeasure${i}`];
        if (name && name.trim()) {
           ingredients.push({
              name,
              amount: amount || 'to taste',
              emoji: '🥘'
           });
        }
      }

      recipes.push({
        id: details.idMeal,
        title: details.strMeal,
        description: `A delicious ${details.strArea || ''} ${details.strCategory || ''} dish.`,
        time: ['15 min', '25 min', '45 min', '1 hr'][Math.floor(Math.random() * 4)],
        difficulty: ['Easy', 'Medium', 'Hard'][Math.floor(Math.random() * 3)] as 'Easy',
        rating: Number((Math.random() * (5 - 4.2) + 4.2).toFixed(1)),
        ingredients,
        nutrition: {
          proteins: Math.floor(Math.random() * 30) + 10,
          fats: Math.floor(Math.random() * 20) + 5,
          carbs: Math.floor(Math.random() * 50) + 15,
        },
        steps: details.strInstructions.split('\r\n').filter((s: string) => s.trim().length > 0),
        imageKeyword: details.strMeal,
        imageUrl: details.strMealThumb
      });
    }

    cache.set(tab, recipes);
    return recipes;
  } catch (error) {
    console.error(`Error fetching ${tab} recipes:`, error);
    return [];
  }
}
