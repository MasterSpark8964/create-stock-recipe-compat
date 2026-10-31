# Source audit

Target dependency: `com.simibubi.create:create-1.20.1:6.0.8-289:slim`.
Inspected the matching Maven sources Jar and the official `mc1.20.1-6.0.8` tag.

Sources Jar SHA-256: `9345e86746d68e01f56acee95cc4c26dc3ccfacabfe5b9c1913d71b09e9df87a`.

Primary sources:

- [Create official dependency guide](https://wiki.createmod.net/developers/depend-on-create/forge-1.20.1)
- [StockKeeperTransferHandler](https://github.com/Creators-of-Create/Create/blob/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/compat/jei/StockKeeperTransferHandler.java)
- [CraftableBigItemStack](https://github.com/Creators-of-Create/Create/blob/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/logistics/stockTicker/CraftableBigItemStack.java)
- [StockKeeperRequestScreen](https://github.com/Creators-of-Create/Create/blob/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen.java)
- JEI Maven API sources: `mezz.jei:jei-1.20.1-common-api:15.20.0.130:sources`.

Confirmed:

| Location | Finding | Consequence |
| --- | --- | --- |
| Public transfer method | Returns early for objects that do not implement `Recipe` | Intercept the public method, with its exact typed descriptor, not the private client method or compiler bridge |
| Private transfer method | JEI inputs are used to validate, but the created entry retains only the Minecraft recipe | Preserve JEI input snapshots in a subtype |
| `CraftableBigItemStack` | `getIngredients()` delegates to recipe; output count uses recipe result | Override both only in our subtype |
| `BigItemStack` constructor | Initial count is 1 | New compat entries start at zero before reserving their first batch |
| `requestCraftable()` | Subtracts current orders, rounds requests to output multiples, calls `maxCraftable`, mutates material orders | Keep this method as the order writer |
| `resolveIngredientAmounts()` | Takes shared candidates in recipe order, without augmenting reassignment | A preflight-only capacity matcher is insufficient; scoped allocation patch is required |
| `maxCraftable()` | Trims potential types, not just the eventual allocation | Enforce the limit on selected actual item types |
| `sendIt()` | Creates a simple order and adds crafting entries only for `CraftingRecipe` | Ordinary recipes and synthetic adapters stay material-only; no packet/server changes |

JEI public API verified: `getItemStacks`, `getDisplayedItemStack`, role-filtered slot views, `isEmpty`, `IRecipeTransferRegistration.getTransferHelper`, `createInternalError`, and `createUserErrorWithTooltip`. Production addon imports no JEI internal implementation packages.

The target modpack was read only. Its `createjeicompat-1.0.2` Mixin list contains category/display patches, not the stock transfer handler. This is static evidence, not a complete modpack coexistence test.
