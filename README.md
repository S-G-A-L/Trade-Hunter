# TradeHunter

TradeHunter is a client-side mod for Minecraft that automatically cycles villager trades to search for specific enchanted books that meet user-defined conditions (enchantment, minimum level, and maximum price).

The mod works together with the Trade Cycling mod to repeatedly refresh villager trades until a desired enchantment appears.

---

## Features

* Automatic villager trade cycling
* Detection of enchanted book trades
* Filtering by:

  * Enchantment ID
  * Minimum enchantment level
  * Maximum emerald cost
* In-game command system to manage targets
* Chat feedback showing detected trades
* Config file saved automatically

---

## Requirements

The mod requires the following:

* Minecraft 1.21.4
* Fabric Loader
* Fabric API
* Trade Cycling

Dependencies:

* https://modrinth.com/mod/fabric-api
* https://github.com/henkelmax/trade-cycling

---

## Installation

1. Install Fabric Loader.
2. Install Fabric API.
3. Install Trade Cycling.
4. Download the latest `tradehunter-x.x.jar`.
5. Place the file in:

```
.minecraft/mods
```

6. Launch Minecraft using the Fabric profile.

---

## Usage

Open a villager trading screen and press:

```
H
```

This toggles the TradeHunter search.

If the desired trade is found, the search stops automatically.

---

## Commands

All commands start with:

```
/th
```

### Add target enchantment

```
/th add <enchantment> <level> <max_price>
```

Example:

```
/th add mending 1 30
/th add efficiency 5 64
```

This means:

* Mending level ≥ 1 and price ≤ 30
* Efficiency level ≥ 5 and price ≤ 64

---

### List targets

```
/th list
```

Displays all active search targets.

---

### Clear targets

```
/th clear
```

Removes all saved targets.

---

## Config

Targets are saved automatically in:

```
config/tradehunter_targets.json
```

Example:

```
[
  {
    "id": "minecraft:mending",
    "level": 1,
    "maxPrice": 30
  }
]
```

---

## How it Works

TradeHunter reads villager trades when the trading screen is open.
If the result item is an enchanted book, the mod checks:

* enchantment id
* enchantment level
* emerald price

If none of the configured targets match, the mod triggers the Trade Cycling button to refresh trades.

---

## Keybind

Default key:

```
H
```

Used to start or stop the search.

---

## License

MIT License

---

## Author

SGAL
