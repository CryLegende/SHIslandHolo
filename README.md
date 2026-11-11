<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHIslandHolo

*Conseils en hologramme sur l'île du joueur, choisis selon sa progression et cachés d'un clic.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Quand un joueur se trouve sur sa propre île, un hologramme FancyHolograms visible de lui seul y affiche le premier conseil de `tips.yml` qui lui correspond : niveau de l'île, nombre de membres, ou n'importe quelle comparaison de placeholders (défi en cours, votes...).

- **Clic gauche** : cache ce conseil, le suivant prend sa place.
- **Clic droit** : lance la commande du conseil (ouvrir les défis, les métiers, les améliorations...), ou le cache s'il n'en a pas.
- Les conseils cachés et déjà vus sont retenus par joueur (données du joueur, rien en base).
- Un conseil jamais vu est annoncé dans le chat, sauf le tout premier.
- Les hologrammes ne sont pas sauvegardés par FancyHolograms : ils sont recréés à chaque passage sur l'île et retirés quand le joueur la quitte.

La zone cliquable est une entité « interaction » envoyée en paquets au seul joueur concerné : elle n'existe pas côté serveur.

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`, `FancyHolograms`, `packetevents`
- Optionnelle : `PlaceholderAPI` (conditions et placeholders dans les lignes)

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/conseil` (alias `/conseils`) | `shislandholo.conseil` (tous) | Réaffiche tous les conseils cachés |
| `/conseil cacher` | `shislandholo.conseil` (tous) | Cache le conseil affiché (joueurs Bedrock, sans clic) |
| `/shislandholo reload` | `shislandholo.admin.reload` (op) | Recharge les trois fichiers ; un fichier invalide annule le rechargement et garde l'ancienne configuration |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shislandholo.conseil` | Utiliser `/conseil` | `true` |
| `shislandholo.admin` | Toutes les commandes d'administration (parent de la suivante) | `op` |
| `shislandholo.admin.reload` | Recharger la configuration, les conseils et les messages | `false` |

## Configuration

Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHIslandHolo/
├── <a href="src/main/resources/config.yml">config.yml</a>     réglages : position, apparence, zone cliquable, annonce
├── <a href="src/main/resources/tips.yml">tips.yml</a>       les conseils, leurs conditions et leurs commandes
└── <a href="src/main/resources/messages.yml">messages.yml</a>   messages du chat
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
