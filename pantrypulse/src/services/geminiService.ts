import { GoogleGenAI, Type } from "@google/genai";
import { ApiResponse } from "../types";

const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

const FOOD_IMAGE_IDS = [
  '1546069901-ba9599a7e63c', '1504674900247-0877df9cc836', '1476224203421-9ac3993511d1',
  '1482049016688-2d3e1b311543', '1473093226795-af9932fe5856', '1493770348161-369560ae357d',
  '1540189549336-e6e99c3679fe', '1567621132-12143467bf4d', '1467003909585-2f8a72700288',
  '1565299624946-b28f40a0ae38', '1555939594-58d7cb561ad1', '1512621776951-a57141f2eefd',
  '1504754524776-8f4f37790ca0', '1498837167922-ddd27525d352'
];

function getFallbackImage(title: string) {
  let hash = 0;
  for (let i = 0; i < title.length; i++) hash = title.charCodeAt(i) + ((hash << 5) - hash);
  return `https://images.unsplash.com/photo-${FOOD_IMAGE_IDS[Math.abs(hash) % FOOD_IMAGE_IDS.length]}?auto=format&fit=crop&w=800&q=80`;
}

export async function generateRecipes(ingredients: string[]): Promise<ApiResponse> {
  const prompt = `I have these ingredients: ${ingredients.join(", ")}. 
  Suggest 3 creative and delicious recipes that primarily use these ingredients. 
  You can assume basic pantry staples like oil, salt, pepper, and water are available.
  For each recipe, provide:
  - id: a unique short string
  - title: name of the dish
  - description: a short, appetizing summary
  - time: total cooking time (e.g., "15 min")
  - difficulty: "Easy", "Medium", or "Hard"
  - rating: A number between 4.0 and 5.0 representing the general user rating
  - ingredients: an array of objects representing required ingredients. For each, give a "name", an "amount", and an "emoji" that best represents it
  - nutrition: an estimated nutritional breakdown per serving with number values for "proteins", "fats", and "carbs" (in grams)
  - steps: numbered step-by-step instructions
  - tips: an optional professional chef tip
  - imageKeyword: a short, 1 or 2 word keyword to search for an image of this dish`;

  try {
    const response = await ai.models.generateContent({
      model: "gemini-3-flash-preview",
      contents: prompt,
      config: {
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            recipes: {
              type: Type.ARRAY,
              items: {
                type: Type.OBJECT,
                properties: {
                  id: { type: Type.STRING },
                  title: { type: Type.STRING },
                  description: { type: Type.STRING },
                  time: { type: Type.STRING },
                  difficulty: { type: Type.STRING, enum: ["Easy", "Medium", "Hard"] },
                  rating: { type: Type.NUMBER },
                  ingredients: {
                    type: Type.ARRAY,
                    items: {
                      type: Type.OBJECT,
                      properties: {
                        name: { type: Type.STRING },
                        amount: { type: Type.STRING },
                        emoji: { type: Type.STRING }
                      },
                      required: ["name", "amount", "emoji"]
                    }
                  },
                  nutrition: {
                    type: Type.OBJECT,
                    properties: {
                      proteins: { type: Type.NUMBER },
                      fats: { type: Type.NUMBER },
                      carbs: { type: Type.NUMBER }
                    },
                    required: ["proteins", "fats", "carbs"]
                  },
                  steps: {
                    type: Type.ARRAY,
                    items: { type: Type.STRING }
                  },
                  tips: { type: Type.STRING },
                  imageKeyword: { type: Type.STRING }
                },
                required: ["id", "title", "description", "time", "difficulty", "rating", "ingredients", "nutrition", "steps", "imageKeyword"]
              }
            }
          },
          required: ["recipes"]
        }
      }
    });

    const text = response.text;
    if (!text) throw new Error("No response from AI");
    
    const parsed = JSON.parse(text);
    const recipes = parsed.recipes;
    
    for (const r of recipes) {
      try {
        const res = await fetch(`https://www.themealdb.com/api/json/v1/1/search.php?s=${r.imageKeyword}`);
        const data = await res.json();
        if (data.meals && data.meals.length > 0) {
          r.imageUrl = data.meals[0].strMealThumb;
        } else {
          r.imageUrl = getFallbackImage(r.title);
        }
      } catch (e) {
        r.imageUrl = getFallbackImage(r.title);
      }
    }
    
    return { recipes };
  } catch (error) {
    console.error("Gemini API Error:", error);
    throw error;
  }
}
