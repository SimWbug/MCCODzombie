# CleanZombies — mode Zombies pour CleanGang

Mode coopératif par manches (1 à 4 joueurs) inspiré du mode Zombies de *World at War* :
fenêtres barricadées, points, portes à débloquer, vagues de plus en plus dures.
Plugin **Paper 26.3**, aucun mod côté joueur, pack de ressources obligatoire.

> **Version 0.1.0 : étape 1 / 7 — prototype jouable.**
> Une seule carte jouable (Bunker 13). Les trois autres sont visibles dans le menu, marquées « Bientôt ».

---

## Ce qui marche dans cette étape

| Système | État |
|---|---|
| Menu des 4 cartes (`/zombies menu`) | ✅ Bunker 13 jouable, les 3 autres « Bientôt disponible » |
| Construction automatique de la carte | ✅ Plan modulaire (`maps/bunker.yml`) assemblé à chaque partie, variations déterministes par graine |
| Vérification de la carte | ✅ Points de départ + chemin enclos → joueurs pour chaque fenêtre. Partie refusée si la carte est cassée |
| Salon d'attente | ✅ Compte à rebours 15 s (5 s si plein), 1 à 4 joueurs |
| Manches | ✅ Nombre, vie et cadence des zombies configurables, pause entre manches, manche max optionnelle |
| Zombies | ✅ 3 variantes (standard, rapide dès manche 4, renforcé dès manche 6), marchent vers une fenêtre, arrachent les planches, puis chassent le joueur le plus proche. Zombie bloqué/hors carte → remplacé |
| Fenêtres | ✅ 6 planches, arrachées par le haut, réparées accroupi (+10 pts, plafond 50 pts/fenêtre/manche) |
| Portes | ✅ Débris achetables (750 et 1000 pts), débloquent les fenêtres de la salle |
| Points | ✅ Touche +10, élimination +50, tête +50, couteau +80, achat atomique (pas de double paiement) |
| Pistolet de départ | ✅ Tir instantané (hitscan), tirs à la tête, chargeur/réserve, rechargement avec **F**, traverse les planches |
| Couteau | ✅ Clic gauche sur un zombie |
| Chute / défaite | ✅ Coup fatal → spectateur, retour à la manche suivante (en attendant la réanimation), fin quand tout le monde est à terre |
| Écran de fin | ✅ Manche atteinte, durée, points, éliminations, têtes, chutes + boutons Rejouer / Choisir une carte |
| Sécurité inventaire | ✅ Inventaire, position, vie, effets sauvegardés **sur disque** avant d'entrer, rendus à la sortie, à la déconnexion, ou à la reconnexion après un crash |
| Pack de ressources | ✅ Envoyé à l'entrée dans une partie, retiré à la sortie. Refus = retour au hub avec explication |
| Diagnostic | ✅ `/zombies debug` : rapport + particules (apparitions, réparations, portes, départs) |

## Ce qui reste à faire (étapes suivantes)

- **Étape 2** : armes murales, boîte mystère, achat de munitions, HUD avec icônes.
- **Étape 3** : réanimation (à terre → compte à rebours → relevé par un coéquipier), reconnexion en cours de partie.
- **Étape 4** : perks (4 distributeurs), courant, pièges, téléporteurs, station d'amélioration.
- **Étape 5** : cartes Sanatorium Sainte-Lucie, Marais de Kurohama, Complexe Titan.
- **Étape 6** : modèles de zombies, sons d'ambiance, interface personnalisée, optimisation.

---

## Installation

### 1. Compiler (GitHub Actions)

1. Pousse ce dossier dans un dépôt GitHub.
2. L'onglet **Actions** lance « Build CleanZombies » à chaque push sur `main`.
3. Télécharge l'artefact **CleanZombies** : il contient
   - `CleanZombies-0.1.0.jar` → à mettre dans `plugins/` (via Crafty),
   - `CleanZombies-pack.zip` → le pack de ressources,
   - `CleanZombies-pack.sha1` → son empreinte.

Pour une **URL de pack publique et stable**, crée un tag : `git tag v0.1.0 && git push --tags`.
Le workflow publie alors une *Release* GitHub ; le lien du zip dans la release sert d'URL de pack.

> Compilation locale : Java 25 + Gradle 9.1 ou plus récent, puis `gradle build`.
> Si Gradle ne trouve pas `paper-api`, ajuste `paperApiVersion` dans `gradle.properties`
> (liste des versions sur repo.papermc.io).

### 2. Configurer le pack

Dans `plugins/CleanZombies/config.yml` :

```yaml
resource-pack:
  url: "https://github.com/<toi>/CleanZombies/releases/download/v0.1.0/CleanZombies-pack.zip"
  sha1: "<contenu de CleanZombies-pack.sha1>"
```

Puis `/zombies reload`. Sans URL, le mode marche quand même (avertissement en console), mais le pistolet
apparaît en armure de cheval et les sons personnalisés sont muets. Pour tester sans pack, mets aussi
`items.custom-models: false`.

### 3. Brancher sur le hub

Le menu des cartes s'ouvre avec `/zombies menu` (ou `/zm`). Ton menu CleanGangMenu ou ton PNJ de hub
peut simplement exécuter cette commande pour le joueur. La commande de retour au hub se règle avec
`hub-command` (par défaut `hub`).

L'arène est construite dans un monde vide dédié, `cg_zombies`, créé automatiquement. Les joueurs n'y
entrent que par le plugin ; un joueur non admin qui s'y reconnecte hors partie est renvoyé au hub.

---

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/zombies` ou `/zombies menu` | `cleanzombies.play` | Menu des cartes |
| `/zombies join [carte]` | `cleanzombies.play` | Rejoindre / ouvrir une partie |
| `/zombies leave` | `cleanzombies.play` | Quitter (affaires rendues) |
| `/zombies start [carte]` | `cleanzombies.admin` | Ouvre une partie, ou force le départ du salon |
| `/zombies stop` | `cleanzombies.admin` | Arrêt immédiat, joueurs restaurés |
| `/zombies reset [carte]` | `cleanzombies.admin` | Reconstruit et vérifie l'arène (hors partie) |
| `/zombies map <carte>` | `cleanzombies.admin` | Carte par défaut |
| `/zombies status` | `cleanzombies.admin` | État de la partie |
| `/zombies debug` | `cleanzombies.admin` | Diagnostic (rapport + particules) |
| `/zombies reload` | `cleanzombies.admin` | Recharge les configs ; refusé pendant une partie ; en cas d'erreur, l'ancienne config reste active |

## Contrôles en jeu

- **Clic droit** : tirer · **F** : recharger · **Clic gauche** : couteau
- **S'accroupir** près d'une fenêtre abîmée : la réparer
- **Clic droit** sur des débris : ouvrir la porte (le prix s'affiche en visant)

---

## Fichiers de configuration

- `config.yml` : manches, zombies, points, joueur, barricades, pack, sons. Chaque valeur est commentée.
- `weapons.yml` : armes (dégâts, cadence, chargeur, réserve, rechargement, dispersion, sons, modèle).
- `maps/*.yml` : plans des cartes. Une salle = une boîte, deux salles voisines partagent un mur ;
  fenêtres, portes, points de départ et décor sont listés dans le même fichier.

Toutes les erreurs de configuration sont listées avec le fichier et le chemin fautif
(console au démarrage, chat sur `/zombies reload`).

### Courbe par défaut (solo)

| Manche | Zombies | Vie d'un standard | Tirs de pistolet (corps / tête) |
|---|---|---|---|
| 1 | 6 | 8 | 2 / 1 |
| 3 | 10 | 16 | 4 / 2 |
| 5 | 16 | 24 | 6 / 3 |
| 9 | 30 | 40 | 10 / 5 |
| 12 | 43 | 53 | 14 / 7 |

En coop, le nombre de zombies est multiplié par 1,5 / 2 / 2,5 (2, 3, 4 joueurs).
Le pistolet seul devient vite insuffisant : c'est voulu, les armes murales arrivent à l'étape 2.

---

## Tester l'étape 1

1. `/zombies debug` puis `/zombies reset bunker` : le rapport doit afficher « ✔ Chemins zombies → joueurs valides ».
2. `/zombies menu` → Bunker 13. Accepter le pack. Compte à rebours, puis manche 1.
3. Vérifier : les zombies sortent de 3 fenêtres seulement (salle de départ), arrachent les planches, entrent.
4. Tirer : +10 par touche, +50 par élimination (+50 à la tête). F recharge.
5. S'accroupir à une fenêtre abîmée : les planches reviennent, points plafonnés à 50 par fenêtre et par manche.
6. Viser les débris de la salle radio : prix affiché, achat à 750, les fenêtres de la salle s'activent.
7. Se laisser tuer : passage en spectateur. En solo → écran de fin. Vérifier que l'inventaire et la position d'origine reviennent.
8. Pendant une partie, couper le serveur. Au redémarrage, se reconnecter : l'inventaire doit être restauré.
9. À 2 joueurs : un joueur tombe, l'autre survit à la manche → le premier revient au début de la suivante.
10. Refuser le pack : retour au hub avec le message d'explication.

## Limites connues

- Une seule partie à la fois (une arène).
- Rejoindre une partie déjà lancée n'est pas possible (prévu à l'étape 3).
- Les zombies utilisent le modèle vanilla avec une tenue teintée par variante ; les modèles dédiés arrivent à l'étape 6.
- Le code a été vérifié contre les sources de l'API Paper 26.3 mais n'a pas encore tourné sur un vrai serveur :
  le premier test en jeu peut révéler des réglages à affiner (vitesse des zombies, portée de réparation…).

## Ressources

Toutes les ressources du pack (texture du pistolet, sons) sont **originales**, créées pour ce projet.
Aucun fichier, nom de carte ou logo de Call of Duty n'est utilisé. Voir `ASSETS.md`.
