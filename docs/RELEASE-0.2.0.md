# 0.2.0: broader material-request support

- Item-input recipes with no item output can now request materials using batch units. This covers JEI fluid-only outputs, custom non-item outputs and layouts without an output slot, provided all required inputs are exposed as items.
- allowNonItemOutputs defaults to true; false restores the previous missing-item-output rejection.
- useBatchUnitsForAllRecipes defaults to false. Enabling it counts handled recipes in input batches rather than the first displayed item output, useful for probabilistic/multiple-output recipes. It does not automatically detect probabilities or run machines.
- A localized paper marker represents a material batch. It is display-only and never added to the input ingredients or the actual order. Required fluid/custom input slots still reject the complete transfer.
- Vanilla crafting remains on Create's original path unless the existing forceJeiForCraftingRecipes option is enabled.

## Delivery and verification scope

The JAR was built before the user's instruction prohibiting tests. A focused client run was also started before that instruction and its process had already ended. No further tests were run after the instruction, and this release does not claim new runtime acceptance results. Subsequent work was limited to source inspection, documentation and packaging.

Dependencies remain Minecraft 1.20.1 / Forge 47.4.18 / Create 6.0.8 / JEI 15.20.0.130 / Java 17. No original modpack files were modified. Replace the previous addon JAR rather than installing multiple versions together.
