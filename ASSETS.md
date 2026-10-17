# Inventaire des ressources du pack

Toutes les ressources ci-dessous ont été créées pour CleanZombies (dessinées au pixel ou synthétisées
par programme). Aucune n'est extraite d'un jeu existant. Licence : propriété de CleanGang, libre
d'utilisation et de modification pour le serveur.

| Fichier | Type | Origine |
|---|---|---|
| `assets/cgzombies/textures/item/weapon_palette.png` | Texture palette 32×32 | Originale (générée) |
| `assets/cgzombies/models/item/{pistol,garand,mp40,bowie,raygun}.json` | Modèles 3D | Originaux (`tools/generate_models.py`) |
| `assets/cgzombies/textures/item/powerup_*.png` | Icônes des bonus | Pixel art original (généré) |
| `assets/cgzombies/items/*.json` | Définitions d'objets | Originales |
| `assets/cgzombies/sounds/weapon/{garand_fire,garand_ping,mp40_fire,raygun_fire,raygun_impact}.ogg` | Sons | Synthèse |
| `assets/cgzombies/sounds/box/*.ogg`, `sounds/powerup/*.ogg` | Sons | Synthèse |
| `assets/cgzombies/sounds/weapon/pistol_fire.ogg` | Son | Synthèse (bruit filtré + grave) |
| `assets/cgzombies/sounds/weapon/reload.ogg` | Son | Synthèse (clics métalliques) |
| `assets/cgzombies/sounds/weapon/empty.ogg` | Son | Synthèse |
| `assets/cgzombies/sounds/ui/hitmarker.ogg` | Son | Synthèse |
| `assets/cgzombies/sounds/round/start.ogg` | Son | Synthèse (accord grave) |
| `assets/cgzombies/sounds/round/end.ogg` | Son | Synthèse |

## Clés de son (sounds.json)

`cgzombies:weapon.pistol.fire`, `cgzombies:weapon.reload`, `cgzombies:weapon.empty`,
`cgzombies:ui.hitmarker`, `cgzombies:round.start`, `cgzombies:round.end`.

Les autres sons de `config.yml` utilisent des sons vanilla et peuvent être remplacés par des clés du pack.

## Format

`pack.mcmeta` déclare `min_format: 84` (Minecraft 26.1) et un `max_format` large pour rester
compatible avec les versions 26.x suivantes.
