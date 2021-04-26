package org.openrewrite.maven.ui;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.openrewrite.config.Environment;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.maven.AbstractRewriteMojo;

import java.util.*;

/**
 * Phonebook approach.
 */
@Mojo(name = "plexus", threadSafe = true)
public class PlexusPrompter extends AbstractRewriteMojo {

    @Component
    private Prompter prompter;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Environment env = environment();
        Collection<RecipeDescriptor> recipeDescriptors = env.listRecipeDescriptors();
        SortedMap<String, List<RecipeDescriptor>> groupedRecipes = getAsGroupedRecipes(recipeDescriptors);
        RecipeDescriptor selected = prompt(groupedRecipes);
        getLog().info(printRecipe(0, selected));
    }

    private static SortedMap<String, List<RecipeDescriptor>> getAsGroupedRecipes(Collection<RecipeDescriptor> recipeDescriptors) {
        SortedMap<String, List<RecipeDescriptor>> groupedRecipes = new TreeMap<>();
        for (RecipeDescriptor recipe : recipeDescriptors) {
            String recipeCategory = getRecipePath(recipe);
            List<RecipeDescriptor> categoryRecipes = groupedRecipes.computeIfAbsent(recipeCategory, k -> new ArrayList<>());
            categoryRecipes.add(recipe);
        }
        // insert missing parent categories
        Map<String, List<RecipeDescriptor>> missingCategories = new HashMap<>();
        for (String category : groupedRecipes.keySet()) {
            String p = category;
            while (!p.isEmpty()) {
                if (!groupedRecipes.containsKey(p)) {
                    missingCategories.put(p, Collections.emptyList());
                }
                if (p.contains("/")) {
                    p = p.substring(0, p.lastIndexOf("/"));
                } else {
                    p = "";
                }
            }
        }
        groupedRecipes.putAll(missingCategories);
        return groupedRecipes;
    }

    private static String getRecipeCategory(RecipeDescriptor recipe) {
        String recipePath = getRecipePath(recipe);
        return recipePath.substring(0, recipePath.lastIndexOf("/"));
    }

    private static String getRecipePath(RecipeDescriptor recipe) {
        String name = recipe.getName();
        if (name.startsWith("org.openrewrite")) {
            return name.substring(16).replaceAll("\\.", "/").toLowerCase();
        } else {
            throw new RuntimeException("Recipe package unrecognized: " + name);
        }
    }

    private String selectCategory(Set<String> rs) throws MojoExecutionException {
        SortedMap<String, String> answerSet = new TreeMap<>(); // integer 'id' to string key pairing
        String selection;

        do {
            StringBuilder query = new StringBuilder("Available categories:\n");
            int counter = 0;
            for (String category : rs) {
                counter++;
                String answer = String.valueOf(counter);
                answerSet.put(answer, category);
                query.append("[" + answer + "]: " + category + "\n");
            }
            String prompted;
            query.append("Choose a number");
            do {
                prompted = prompt(query.toString());
                while (!isNumber(prompted)) {
                    query.append("\nYour input selection must be a number, try again (hint: enter to return to initial list)");
                    prompted = prompt(query.toString());
                }
                if (answerSet.get(prompted) == null) {
                    query.append(String.format("\nYour selection [%s] is not an option in the list, try again (hint: enter to return to initial list)", prompted));
                }
            } while (answerSet.get(prompted) == null);
            selection = answerSet.get(prompted);
        } while (selection == null);

        return selection;

    }

    private RecipeDescriptor selectRecipe(Collection<RecipeDescriptor> recipeDescriptors) throws MojoExecutionException {
        SortedMap<String, RecipeDescriptor> answerSet = new TreeMap<>();
        RecipeDescriptor selection = null;

        do {
            StringBuilder query = new StringBuilder("Available options:\n");
            int counter = 0;

            for (RecipeDescriptor recipeDescriptor : recipeDescriptors) {
                counter++;
                String answer = String.valueOf(counter);
                answerSet.put(answer, recipeDescriptor);
                query.append("[" + answer + "]: " + recipeDescriptor.getDisplayName() + "\n");
            }

            String prompted;
            query.append("Choose a number");
            do {
                prompted = prompt(query.toString());
                while (!isNumber(prompted)) {
                    query.append("\nYour input selection must be a number, try again (hint: enter to return to initial list)");
                    prompted = prompt(query.toString());
                }
                if (answerSet.get(prompted) == null) {
                    query.append(String.format("\nYour selection [%s] is not an option in the list, try again (hint: enter to return to initial list)", prompted));
                }
            } while (answerSet.get(prompted) == null);
            selection = answerSet.get(prompted);
        } while (selection == null);

        return selection;
    }

    private RecipeDescriptor prompt(SortedMap<String, List<RecipeDescriptor>> groupedRecipes) throws MojoExecutionException {
        // todo
        String category = selectCategory(groupedRecipes.keySet());
        RecipeDescriptor rd = selectRecipe(groupedRecipes.get(category));
        return rd;
    }

    private String prompt(String message) throws MojoExecutionException {
        String prompted;
        try {
            prompted = prompter.prompt(message);
        } catch (PrompterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        return prompted;
    }

    private boolean isNumber(String message) {
        try {
            Integer.parseInt(message);
        } catch (NumberFormatException e) {
            getLog().debug(e);
            return false;
        }
        return true;
    }

}
