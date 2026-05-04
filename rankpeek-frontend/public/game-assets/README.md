# RankPeek Game Assets

This directory is copied into the renderer bundle and served as `./game-assets/...`.

`manifest.json` maps League asset IDs to local files:

```json
{
  "version": "15.24.1",
  "locale": "zh_CN",
  "items": { "1001": "items/1001.png" },
  "summonerSpells": { "4": "summoner-spells/4.png" },
  "perks": { "8005": "perks/8005.png" },
  "augments": {},
  "champions": { "103": "champions/103.png" },
  "profileIcons": { "29": "profile-icons/29.png" }
}
```

Keep this folder selective. Do not commit a full dragontail archive or large binary packs.
