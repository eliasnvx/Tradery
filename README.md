# Tradery - Minecraft Economy System

<div align="center">

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.4+-orange?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

**Cross-platform economy system with player shops, virtual currency, and modern GUI**

**Plugin (Bukkit/Sponge) + Mod (Forge/NeoForge) hybrid architecture**

</div>

## 🎯 Features

- **Virtual Currency System** - Vault API integration for Bukkit, Sponge Economy Service
- **Player Shops** - Place shop blocks, set items, prices, and let players trade
- **Cross-Platform** - Works on Bukkit/Paper/Spigot and SpongeForge servers
- **Modern GUI** - Client-side mod provides enhanced trading interface
- **Multi-Currency Support** - Multiple currencies (planned)
- **Transaction History** - Full audit log of all trades

## 📋 Requirements

### Server-side (Plugin)
- **Minecraft**: 1.20.1+
- **Java**: 21+
- **Vault** (for Bukkit) or Sponge Economy Service
- **Platform**: Paper/Spigot or SpongeForge

### Client-side (Mod) - Optional
- **Forge/NeoForge**: 1.20.1+
- Enhances GUI and adds custom blocks

## 🏗️ Architecture
```
Tradery/
├── tradery-core/       # Shared logic, database, economy core
├── tradery-bukkit/     # Bukkit/Paper/Spigot plugin
├── tradery-sponge/     # Sponge plugin
├── tradery-mod/        # Forge/NeoForge mod (GUI, blocks)
└── tradery-common/     # Shared networking protocol
```

## 🚀 Quick Start

### For Server Admins

1. Download plugin from Releases
2. Place in `plugins/` directory
3. Install Vault (Bukkit) or use Sponge's economy
4. Restart server
5. Configure in `config.yml`

### For Players (Optional)

1. Download Tradery mod from Releases
2. Install Forge/NeoForge 1.20.1+
3. Place mod in `mods/` folder
4. Enjoy enhanced GUI!

## ⚙️ Basic Configuration
```yaml
economy:
  starting-balance: 1000.0
  currency-name: "Coins"

shops:
  max-per-player: 5
  transaction-fee: 0.02  # 2%

database:
  type: h2  # h2, mysql, postgresql
```

## 🎮 Commands

| Command | Description |
|---------|-------------|
| `/tradery` | Open main menu |
| `/tradery shop create` | Create new shop |
| `/tradery balance` | Check balance |
| `/tradery pay <player> <amount>` | Send money |

## 🔧 Development Status

**Current Phase:** Core Development (Alpha)

- [x] Project structure
- [x] Core economy logic
- [ ] Bukkit adapter + Vault
- [ ] Shop system
- [ ] GUI (mod)
- [ ] Sponge adapter
- [ ] Database layer

## 📝 License

MIT License - see [LICENSE](LICENSE)

## 🤝 Contributing

Contributions welcome! This is an early-stage project.

---

<div align="center">

Made for Minecraft servers that want better economy

</div>
