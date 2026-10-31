# 0.3.6: avoid duplicate matching and unrelated stock columns

Changes:

- MaterialAllocation now records candidate indices during its initial stock scan. Each distinct Ingredient object is evaluated once per stock entry; the later candidate-array construction reads those recorded matches instead of invoking Ingredient.test again. Expanded repeated units still retain separate demand.
- Recursive whole-recipe direct reservation builds its flow matrix only from available stock columns referenced by that subrecipe. Local indices are translated back to global inventory indices before updating reservations, used item types and leaf-unit counts.
- Repeated candidate rows reuse the same local mapping. Empty available rows and aggregate capacity below demand reject the optional direct shortcut early; recursive alternatives remain available.

This targets extra matching work and dense matrices for small subrecipes in large warehouses. For example, a subrecipe using three inventory variants now builds three columns even if the warehouse has thousands of unrelated variants. No measured timing, frame-rate or memory benchmark is claimed.

Recipe eligibility, enchanted-book/trim exclusions, NBT predicates, per-plan backtracking, actual smithing assembly validation, preview cooldown, nine-type limit and existing configuration defaults are unchanged. Candidate/state data is not cached across transfers.

Validation: static inspection of local/global index translation, stock reservations and candidate reuse; compilation/reobfuscation via assemble -x test. No tests, game launches or performance benchmarks were run, as requested.
