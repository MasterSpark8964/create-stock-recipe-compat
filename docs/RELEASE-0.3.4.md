# 0.3.4: vanilla-workstation-only recursive expansion

Recursive lookup now queries only minecraft:crafting, minecraft:smithing, minecraft:smelting, minecraft:blasting, minecraft:smoking and minecraft:stonecutting recipe types. Item namespaces and recipe IDs are unrestricted: mod/data-pack recipes registered for these workstations remain eligible under the supported recipe interfaces.

Custom machine recipe types, including Create sequenced assembly, crushing, milling and mechanical crafting, are ignored during recursive lookup. A custom recipe inheriting CraftingRecipe is no longer eligible merely because of its Java superclass. Ignored machine recipes do not consume intermediate recipe-count or per-recipe search budgets. If the remaining workstation lookup finds no supplied plan, the result is raw-material shortage; the machine product must be stocked directly. This does not disable direct JEI material requests for an explicitly selected machine recipe.

Additionally, exact native trim recipes are excluded for known ingredient item sets containing no trimmable armor. Known compound ingredients are recognized recursively. This avoids exploring unrelated armor/template chains for ordinary machine-only raw materials. Custom smithing implementations keep the concrete matches/assemble path; where their behavior is unknown, actual search limits still report complexity rather than an unproven shortage.

Complexity/deadline messages remain reserved for limits actually reached within eligible planning. Existing stock, quantity, NBT and nine-type protections are unchanged.

Validation: static inspection and assemble -x test compilation/reobfuscation only. No tests or game launches were performed.
