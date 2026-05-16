import { useState, useEffect, KeyboardEvent } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Search, X, Clock, Star, ArrowLeft, Heart, Loader2 } from 'lucide-react';
import { Recipe } from './types';
import { generateRecipes } from './services/geminiService';
import { getRecipesForTab } from './services/mealDbService';

export default function App() {
  const [view, setView] = useState<'hero' | 'home' | 'detail'>('hero');
  const [selectedRecipe, setSelectedRecipe] = useState<Recipe | null>(null);
  const [favorites, setFavorites] = useState<Recipe[]>(() => {
    const saved = localStorage.getItem('recipe_favorites');
    return saved ? JSON.parse(saved) : [];
  });

  useEffect(() => {
    localStorage.setItem('recipe_favorites', JSON.stringify(favorites));
  }, [favorites]);

  const handleSelectRecipe = (recipe: Recipe) => {
    setSelectedRecipe(recipe);
    setView('detail');
  };

  const toggleFavorite = (recipe: Recipe) => {
    setFavorites(prev => {
      const isFav = prev.some(r => r.id === recipe.id);
      if (isFav) {
        return prev.filter(r => r.id !== recipe.id);
      } else {
        return [...prev, recipe];
      }
    });
  };

  const isFavorite = (recipeId: string) => favorites.some(r => r.id === recipeId);

  return (
    <div className="min-h-screen bg-black/90 flex justify-center p-0 sm:p-6 lg:p-8">
      <div className="w-full max-w-[420px] bg-[#1F2027] min-h-screen sm:min-h-[800px] sm:h-auto sm:max-h-[850px] relative overflow-hidden sm:rounded-[2.5rem] sm:border-[6px] sm:border-gray-800 shadow-2xl flex flex-col font-sans text-white z-10">
        <AnimatePresence mode="wait">
          {view === 'hero' && (
            <Hero key="hero" onStart={() => setView('home')} />
          )}
          {view === 'home' && (
            <motion.div key="home" className="absolute inset-0 w-full h-full flex flex-col">
               <Home 
                onSelect={handleSelectRecipe} 
                favorites={favorites}
               />
            </motion.div>
          )}
          {view === 'detail' && selectedRecipe && (
            <motion.div key="detail" className="absolute inset-0 z-10 w-full h-full">
               <RecipeDetail 
                recipe={selectedRecipe} 
                onBack={() => setView('home')} 
                isFavorite={isFavorite(selectedRecipe.id)}
                onToggleFavorite={() => toggleFavorite(selectedRecipe)}
               />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Background Decor Elements matching screenshot */}
      <div className="fixed inset-0 pointer-events-none -z-0 opacity-40">
        <motion.img 
          animate={{ y: [0, 10, 0], rotate: [0, 5, 0] }}
          transition={{ duration: 6, repeat: Infinity }}
          src="https://images.unsplash.com/photo-1517093157656-b99917c6471c?auto=format&fit=crop&w=150&q=80" 
          className="absolute top-10 left-10 w-24 rounded-full blur-[1px]" 
        />
        <motion.img 
          animate={{ y: [0, -15, 0], rotate: [0, -8, 0] }}
          transition={{ duration: 5, repeat: Infinity }}
          src="https://images.unsplash.com/photo-1610832958506-aa56368176cf?auto=format&fit=crop&w=150&q=80" 
          className="absolute top-40 right-40 w-16" 
        />
        <motion.img 
          animate={{ scale: [1, 1.1, 1], x: [0, 10, 0] }}
          transition={{ duration: 7, repeat: Infinity }}
          src="https://images.unsplash.com/photo-1594736797933-d0501ba2fe65?auto=format&fit=crop&w=150&q=80" 
          className="absolute bottom-20 left-1/4 w-20 opacity-50" 
        />
        <motion.img 
          src="https://images.unsplash.com/photo-1559181567-c3190ca9959b?auto=format&fit=crop&w=150&q=80" 
          className="absolute top-1/2 -right-10 w-32 blur-[2px]" 
        />
      </div>
    </div>
  );
}

function Hero({ onStart }: { onStart: () => void }) {
  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="absolute inset-0 bg-[#1F2027] flex flex-col p-8 pb-12"
    >
      <div className="flex-1 relative flex items-center justify-center -mx-8 -mt-8 mb-12">
        <img 
          src="https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=800&q=80" 
          className="w-full h-full object-cover brightness-75" 
          alt="Healthy food" 
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#1F2027] via-transparent to-black/20"></div>
      </div>

      <div className="relative z-10">
        <h1 className="text-4xl font-bold mb-4 leading-[1.1] tracking-tight">
          Healthy food<br/>is <span className="text-[#FBB72C]">goooood</span>
        </h1>
        <p className="text-gray-400 text-lg mb-10 max-w-[280px]">
          More than 10,000 recipes for every day and taste
        </p>

        <button 
          onClick={onStart}
          className="w-full bg-[#FBB72C] text-[#1F2027] font-bold py-5 rounded-2xl text-lg shadow-2xl shadow-[#FBB72C]/20 active:scale-95 transition-transform"
        >
          Let's get started
        </button>
      </div>
    </motion.div>
  );
}

const DEFAULT_NEW_RECIPES = [
  {
    id: '1',
    title: 'Ramen noodle soup',
    description: 'Ramen is a Japanese noodle dish. It consists of Chinese alkaline wheat noodles served in a meat-based broth, often flavored with soy sauce or miso, and uses toppings such as sliced pork.',
    time: '15 min',
    difficulty: 'Medium' as const,
    rating: 4.8,
    ingredients: [
      { name: 'Pork', amount: '100g', emoji: '🥩' },
      { name: 'Noodle', amount: '200g', emoji: '🍜' },
      { name: 'Corn', amount: '50g', emoji: '🌽' },
      { name: 'Eggs', amount: '2', emoji: '🥚' },
      { name: 'Onion', amount: '1', emoji: '🧅' }
    ],
    nutrition: { proteins: 3.45, fats: 10.69, carbs: 22.72 },
    steps: [
      'Boil water and cook noodles according to package instructions.',
      'Prepare the broth using miso and soy sauce.',
      'Slice the pork and boil the eggs.',
      'Assemble the bowl with noodles, broth, and all toppings.'
    ],
    imageKeyword: 'ramen',
    imageUrl: 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=400&q=80'
  }
];

function Home({ onSelect, favorites }: { onSelect: (r: Recipe) => void; favorites: Recipe[] }) {
  const [ingredients, setIngredients] = useState<string[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [tabData, setTabData] = useState<Record<string, Recipe[]>>({ New: DEFAULT_NEW_RECIPES });
  const [activeTab, setActiveTab] = useState<'New' | 'Popular' | 'Breakfast' | 'Snacks' | 'Favorites'>('New');
  const [tabLoading, setTabLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (activeTab === 'New' || activeTab === 'Favorites' || tabData[activeTab]) return;

    setTabLoading(true);
    getRecipesForTab(activeTab).then(recipes => {
      setTabData(prev => ({ ...prev, [activeTab]: recipes }));
      setTabLoading(false);
    });
  }, [activeTab, tabData]);

  const addIngredient = () => {
    const trimmed = inputValue.trim().toLowerCase();
    if (trimmed && !ingredients.includes(trimmed)) {
      setIngredients([...ingredients, trimmed]);
      setInputValue('');
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      addIngredient();
    }
  };

  const removeIngredient = (ingredient: string) => {
    setIngredients(ingredients.filter(i => i !== ingredient));
  };

  const handleGenerate = async () => {
    if (ingredients.length === 0) return;
    setLoading(true);
    setError(null);
    try {
      const result = await generateRecipes(ingredients);
      setTabData(prev => ({ ...prev, New: result.recipes }));
      setActiveTab('New');
    } catch (err) {
      setError('Failed to fetch recipes. Please try again.');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const currentList = activeTab === 'Favorites' ? favorites : (tabData[activeTab] || []);

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex-1 overflow-y-auto p-6 scrollbar-hide pb-12"
    >
      {/* Header Profile */}
      <div className="flex items-center justify-between mb-8 mt-2">
        <div>
          <h1 className="text-xl font-semibold mb-1">Hello, Kristin 👋</h1>
          <p className="text-[#a0a0ab] text-sm">What you want to cook today?</p>
        </div>
        <div className="w-12 h-12 rounded-full overflow-hidden bg-gray-700">
          <img src="https://i.pravatar.cc/150?img=47" alt="Profile" className="w-full h-full object-cover" />
        </div>
      </div>

      {/* Search Input */}
      <div className="relative mb-6">
        <Search className="absolute left-5 top-1/2 -translate-y-1/2 text-gray-400" size={20} />
        <input
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Find your favorite recipe..."
          className="w-full bg-[#2C2D35] text-white rounded-[2rem] py-4 pl-[3.5rem] pr-[5rem] focus:outline-none placeholder-gray-500"
        />
        <button 
          onClick={addIngredient}
          className="absolute right-4 top-1/2 -translate-y-1/2 text-[#FBB72C] font-medium"
        >
          Add
        </button>
      </div>

      {/* Ingredient Pills */}
      {ingredients.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-8">
          <AnimatePresence>
            {ingredients.map((ingredient) => (
              <motion.span
                key={ingredient}
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.8 }}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-[#2C2D35] rounded-full text-sm font-medium text-gray-200"
              >
                {ingredient}
                <button 
                  onClick={() => removeIngredient(ingredient)}
                  className="text-gray-400 hover:text-white transition-colors cursor-pointer"
                >
                  <X size={14} />
                </button>
              </motion.span>
            ))}
          </AnimatePresence>
        </div>
      )}

      {/* Generate Action Area */}
      {ingredients.length > 0 && activeTab === 'New' && (
        <motion.button 
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          onClick={handleGenerate} 
          disabled={loading}
          className="w-full bg-[#FBB72C] text-[#1F2027] font-semibold py-4 rounded-2xl shadow-lg shadow-[#FBB72C]/10 active:scale-95 transition-transform disabled:opacity-50 mt-4 mb-4"
        >
          {loading ? 'Generating...' : 'Generate New Recipes'}
        </motion.button>
      )}

      {loading && (
        <div className="flex flex-col items-center justify-center py-12">
           <Loader2 className="animate-spin text-[#FBB72C] mb-4" size={40} />
           <p className="text-gray-400">Crafting recipes from your pantry...</p>
        </div>
      )}

      {error && !loading && (
        <div className="p-4 bg-red-500/10 border border-red-500/20 text-red-400 rounded-xl text-center text-sm mb-6">
          {error}
        </div>
      )}

      {/* Categories */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-4">
          <h3 className="font-bold text-lg">Category</h3>
          <button className="text-gray-500 text-sm font-medium">See All</button>
        </div>
        <div className="flex gap-4 overflow-x-auto scrollbar-hide pb-2 -mx-2 px-2 snap-x">
          {[
            { name: 'Breakfast', emoji: '🍳' },
            { name: 'Lunch', emoji: '🍱' },
            { name: 'Dinner', emoji: '🍛' },
            { name: 'Dessert', emoji: '🍰' },
            { name: 'Salads', emoji: '🥗' },
            { name: 'Drinks', emoji: '🍹' },
          ].map((cat) => (
            <motion.div 
              key={cat.name}
              whileTap={{ scale: 0.95 }}
              className="flex flex-col items-center gap-2 snap-center cursor-pointer"
            >
              <div className="w-14 h-14 rounded-2xl bg-[#2C2D35] flex items-center justify-center text-2xl shadow-lg border border-white/5">
                {cat.emoji}
              </div>
              <span className="text-xs font-medium text-gray-400">{cat.name}</span>
            </motion.div>
          ))}
        </div>
      </div>

      {/* Results */}
      {!loading && (
        <motion.div 
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="mt-2"
        >
          {/* Tabs */}
          <div className="flex gap-6 mb-8 text-sm overflow-x-auto whitespace-nowrap pb-1 scrollbar-hide font-medium">
            {['New', 'Popular', 'Breakfast', 'Snacks', 'Favorites'].map((tab) => (
              <div 
                key={tab}
                onClick={() => setActiveTab(tab as any)}
                className={`pb-1 cursor-pointer transition-colors ${activeTab === tab ? 'text-[#00855F] border-b-2 border-[#00855F]' : 'text-gray-500 hover:text-gray-300'}`}
              >
                {tab}
              </div>
            ))}
          </div>

          <h2 className="text-2xl font-bold mb-10 leading-tight">
            {tabLoading ? 'Loading...' : `${String(currentList.length).padStart(2, '0')} ${activeTab.toLowerCase()}\nrecipes`}
          </h2>

          <div className="grid grid-cols-2 gap-4 items-start pb-8">
            {!tabLoading && currentList.map((recipe, idx) => (
              <RecipeCard 
                key={recipe.id} 
                recipe={recipe} 
                onClick={() => onSelect(recipe)} 
                index={idx}
              />
            ))}
            {!tabLoading && activeTab === 'Favorites' && currentList.length === 0 && (
              <div className="col-span-2 text-center py-12 text-gray-500">
                No favorites yet. Add some!
              </div>
            )}
            {tabLoading && (
              <div className="col-span-2 flex justify-center py-12">
                <Loader2 className="animate-spin text-gray-500" size={30} />
              </div>
            )}
          </div>
        </motion.div>
      )}
    </motion.div>
  );
}

function RecipeCard({ recipe, onClick, index }: { recipe: Recipe; onClick: () => void; index: number; key?: string }) {
  const imageSrc = recipe.imageUrl || `https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=400&q=80&sig=${recipe.id}`;
  
  return (
    <motion.div 
      initial={{ opacity: 0, scale: 0.9, y: 30 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      transition={{ delay: index * 0.1, type: "spring", stiffness: 200, damping: 20 }}
      onClick={onClick}
      className={`bg-[#2C2D35] rounded-3xl p-3 pt-0 flex flex-col items-center cursor-pointer transition-transform hover:scale-105 active:scale-95 shadow-xl ${index % 2 !== 0 ? 'mt-12' : ''}`}
    >
      <div className="w-28 h-28 rounded-full shadow-2xl overflow-hidden -mt-10 mb-3 bg-gray-800 border-4 border-[#1F2027] shrink-0 relative">
        <img src={imageSrc} loading="lazy" className="w-full h-full object-cover" alt={recipe.title} />
        <div className="absolute inset-0 rounded-full ring-1 ring-white/10 pointer-events-none"></div>
      </div>
      <h3 className="font-semibold text-sm text-center leading-snug mb-2 min-h-[40px] flex items-center justify-center px-1">
        {recipe.title}
      </h3>
      <div className="flex gap-1 text-[#FBB72C] mb-3">
        {[...Array(5)].map((_, i) => (
          <Star key={i} size={12} fill={i < Math.round(recipe.rating) ? 'currentColor' : 'transparent'} color={i < Math.round(recipe.rating) ? 'transparent' : '#4b4c53'} />
        ))}
      </div>
      <div className="text-[#a0a0ab] text-xs flex items-center gap-1.5 font-medium pb-4">
        <Clock size={13} /> {recipe.time}
      </div>
    </motion.div>
  );
}

function RecipeDetail({ recipe, onBack, isFavorite, onToggleFavorite }: { recipe: Recipe; onBack: () => void; isFavorite: boolean; onToggleFavorite: () => void }) {
  const imageSrc = recipe.imageUrl || `https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=800&q=80&sig=${recipe.id}`;

  return (
    <motion.div 
      initial={{ x: '100%' }}
      animate={{ x: 0 }}
      exit={{ x: '100%' }}
      transition={{ type: 'spring', damping: 25, stiffness: 200 }}
      className="absolute inset-0 bg-[#1F2027] z-10 flex flex-col overflow-y-auto scrollbar-hide pb-12"
    >
      {/* Top Header Region */}
      <div className="relative h-[45%] min-h-[320px] shrink-0 rounded-b-[3rem] overflow-hidden shadow-2xl bg-gray-800">
        <img src={imageSrc} className="w-full h-full object-cover" alt={recipe.title} />
        {/* Dark Gradient Overlay for text readability */}
        <div className="absolute inset-0 bg-gradient-to-t from-[#1F2027] via-transparent to-black/50"></div>
        
        {/* Nav actions */}
        <div className="absolute top-6 left-6 right-6 flex justify-between items-center z-10">
          <button 
            onClick={onBack} 
            className="w-10 h-10 flex items-center justify-center rounded-full text-white cursor-pointer"
          >
            <ArrowLeft size={24} />
          </button>
          <button 
            onClick={onToggleFavorite}
            className={`w-10 h-10 flex items-center justify-center rounded-full transition cursor-pointer ${isFavorite ? 'text-red-500' : 'text-white hover:text-red-400'}`}
          >
            <Heart size={24} fill={isFavorite ? 'currentColor' : 'none'} />
          </button>
        </div>

        {/* Floating time badge */}
        <div className="absolute bottom-6 left-8 z-10">
          <div className="bg-[#00855F] text-white px-4 py-2 rounded-[1rem] flex items-center gap-2 text-sm font-semibold shadow-xl border border-white/10">
            <Clock size={16} /> {recipe.time}
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="px-8 pt-8 flex-1">
        <div className="flex justify-between items-start mb-4">
          <h1 className="text-3xl font-bold tracking-tight leading-tight flex-1">{recipe.title}</h1>
          <div className="flex items-center gap-1 bg-[#FBB72C]/10 text-[#FBB72C] px-2 py-1 rounded-lg text-sm font-bold ml-4">
            <Star size={14} fill="currentColor" />
            {recipe.rating}
          </div>
        </div>
        
        <div className="flex gap-4 mb-6">
          <div className="flex items-center gap-2 text-xs font-semibold text-gray-400 bg-[#2C2D35] px-3 py-1.5 rounded-full border border-white/5">
             <span className="w-2 h-2 rounded-full bg-blue-500"></span>
             {recipe.difficulty}
          </div>
          <div className="flex items-center gap-2 text-xs font-semibold text-gray-400 bg-[#2C2D35] px-3 py-1.5 rounded-full border border-white/5">
             <Clock size={12} />
             {recipe.time}
          </div>
        </div>

        <p className="text-[#a0a0ab] text-sm leading-relaxed mb-8">{recipe.description}</p>

        {/* Nutritional Facts List */}
        <div className="space-y-4 mb-10">
          <NutritionRow label="Proteins" value={`${recipe.nutrition.proteins} g`} />
          <NutritionRow label="Fats" value={`${recipe.nutrition.fats} g`} />
          <NutritionRow label="Carbohydrates" value={`${recipe.nutrition.carbs} g`} />
        </div>

        {/* Ingredients Array */}
        <h3 className="text-xl font-bold mb-5">Ingredients</h3>
        <div className="flex gap-4 overflow-x-auto pb-4 scrollbar-hide -mx-4 px-4 snap-x">
          {recipe.ingredients.map((ing, i) => (
            <div key={i} className="flex flex-col items-center shrink-0 w-[4.5rem] snap-center">
              <div className="w-[4.5rem] h-[4.5rem] bg-white rounded-full text-3xl flex items-center justify-center shadow-lg mb-3 border-[3px] border-transparent relative overflow-hidden">
                 {/* Provide visual hint with emojis */}
                 <span>{ing.emoji}</span>
              </div>
              <span className="text-xs text-[#e1e1e6] text-center font-medium line-clamp-1 w-full">{ing.name}</span>
            </div>
          ))}
        </div>
        
        {/* Step by step Instructions */}
        <h3 className="text-xl font-bold mb-5 mt-6">Instructions</h3>
        <ol className="space-y-6">
          {recipe.steps.map((step, i) => (
            <li key={i} className="flex gap-4 items-start">
              <div className="w-6 h-6 rounded-full bg-[#35363D] flex items-center justify-center shrink-0 text-xs font-bold text-[#FBB72C] mt-0.5 shadow-md">
                {i+1}
              </div>
              <p className="text-[#a0a0ab] text-sm leading-relaxed flex-1">{step}</p>
            </li>
          ))}
        </ol>

        {recipe.tips && (
           <div className="mt-8 bg-[#FBB72C]/10 rounded-2xl p-5 border border-[#FBB72C]/20">
              <span className="font-bold text-[#FBB72C] text-sm block mb-1">Chef's Tip</span>
              <p className="text-[#a0a0ab] text-sm leading-relaxed">
                 {recipe.tips}
              </p>
           </div>
        )}
      </div>
    </motion.div>
  );
}

function NutritionRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-end text-[15px]">
      <span className="text-gray-300 font-medium whitespace-nowrap">{label}</span>
      <div className="flex-1 border-b border-dotted border-gray-600/60 mx-3 mb-1"></div>
      <span className="text-[#FBB72C] font-semibold whitespace-nowrap">{value}</span>
    </div>
  );
}

