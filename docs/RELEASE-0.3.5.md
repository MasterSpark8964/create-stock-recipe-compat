# 0.3.5: skip decorative/enchantment recipe expansion

Recursive exclusions:

- SmithingTrimRecipe instances (including subclasses) and recipes using the SMITHING_TRIM serializer are skipped before querying their previews or exploring their inputs.
- Outputs that are EnchantedBookItem instances or contain StoredEnchantments are excluded from intermediate production. Actual custom smithing outputs are checked too, so an empty/misleading preview cannot reintroduce enchanted-book production.
- A known ingredient whose alternatives are all excluded books may consume existing stock, but missing books do not trigger further production search. Root book shortages are checked before building the complete recursive recipe index.
- Existing enchanted books remain valid inventory. Ordinary smithing upgrades and inherited equipment enchantments remain supported. Direct JEI material requests are unchanged; these exclusions apply to recursive production.

Preview optimization:

- A time/work/decision-limit failure from a recursive JEI preview is reused for the same menu and source for one second. This prevents immediate repeated expensive retries.
- Only limit failures are throttled. Successful plans and material-shortage results are not cached. A committed transfer bypasses this preview cooldown and recalculates against current stock.
- Weak references avoid retaining the menu or source object solely for this cooldown. A recent limit tooltip can remain for up to one second after stock/config changes. Switching the request clears the single-entry cache.

Scope and limits:

These are explicit interface/item/NBT-based exclusions, not guesses based on recipe names. Arbitrary custom decorative recipe implementations that expose neither recognized trim interfaces/serializers nor the recognized book output may still be evaluated within the existing bounds. Mod callbacks are cooperative, and no zero-lag or measured frame-rate claim is made. Missing excluded materials are reported as raw-material shortage; genuine search/deadline limits remain separate.

Validation: static review and assemble -x test compilation/reobfuscation only. No tests, performance benchmarks or game launches were run. No original modpack files were modified.
