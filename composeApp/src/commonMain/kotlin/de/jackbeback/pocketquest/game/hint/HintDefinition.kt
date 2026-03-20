package de.jackbeback.pocketquest.game.hint

data class HintDefinition(
    val id: String,
    val title: String,
    val body: String,
)

/** All in-game tutorial hints. Each is shown at most once (persisted in Room). */
val allHints: List<HintDefinition> = listOf(
    HintDefinition(
        id    = "hint_battle_intro",
        title = "Kampf-Grundlagen",
        body  = "Blaue Felder zeigen deine Bewegungsreichweite. Wähle eine Fähigkeit, um Ziele zu markieren. Tippe auf 'Zug beenden', wenn du fertig bist.",
    ),
    HintDefinition(
        id    = "hint_skills",
        title = "Fähigkeiten & Mana",
        body  = "Fähigkeiten kosten Mana. Du regenerierst jeden Zug automatisch 5 Mana. Ausgegrauete Fähigkeiten sind momentan zu teuer.",
    ),
    HintDefinition(
        id    = "hint_conditions",
        title = "Statuseffekte",
        body  = "🔥 Verbrennung, ☠ Gift und ❄ Kälte verursachen jeden Zug Schaden. 🛡 Block absorbiert eingehende Treffer. Mehrere Stapel verstärken den Effekt.",
    ),
    HintDefinition(
        id    = "hint_overworld",
        title = "Die Karte",
        body  = "Wähle deinen Weg durch die Karte. Erreichbare Knoten leuchten auf. Rastplätze erlauben es, HP zwischen den Kämpfen zu regenerieren.",
    ),
    HintDefinition(
        id    = "hint_rest",
        title = "Rastplatz",
        body  = "Hier kannst du rasten und einen Teil deiner HP regenerieren. Du kannst auch weitergehen ohne zu rasten — spare Kraft für später!",
    ),
    HintDefinition(
        id    = "hint_level_up",
        title = "Levelaufstieg!",
        body  = "Du hast ein Level erreicht und erhältst 2 Attributpunkte. Besuche den Charakterbildschirm, um STR, DEX, CON, INT, WIS oder CHA zu erhöhen.",
    ),
    HintDefinition(
        id    = "hint_relic",
        title = "Relikt gefunden!",
        body  = "Relikte verleihen dauerhafte Boni für den Rest dieses Durchlaufs — von mehr HP bis hin zu mehr Mana oder XP-Boni. Jedes Relikt kann nur einmal gehalten werden.",
    ),
    HintDefinition(
        id    = "hint_boss",
        title = "Bossgegner!",
        body  = "Ein mächtiger Feind erwartet dich. Bosse haben hohe HP, starke Angriffe und skalieren mit deinem Fortschritt. Geh gut vorbereitet in den Kampf!",
    ),
)
