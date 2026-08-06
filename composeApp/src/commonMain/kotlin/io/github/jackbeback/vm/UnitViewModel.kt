@file:OptIn(ExperimentalUuidApi::class)

package io.github.jackbeback.vm

import androidx.lifecycle.*
import io.github.jackbeback.data.*
import io.github.jackbeback.units.*
import io.github.jackbeback.util.json
import kotlinx.coroutines.flow.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlin.collections.plus
import kotlin.uuid.*

@OptIn(ExperimentalUuidApi::class)
class UnitViewModel: ViewModel() {
    companion object{
        val instance = UnitViewModel()
    }

    private val _units = MutableStateFlow<List<UnitEntity>>(listOf(
        wizard
    ))
    val units: StateFlow<List<UnitEntity>> = _units.asStateFlow()

    val _selectedUnit = MutableStateFlow<String?>(null)
    val selectedUnit: StateFlow<UnitEntity?> = combine(_selectedUnit, _units) { selectedId, unitsList ->
        unitsList.firstOrNull { it.id == selectedId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )


    // Ein MutableStateFlow, der angibt, ob wir den Player explizit deselektiert haben
    private val _playerExplicitlyDeselected = MutableStateFlow(false)
    private val _enemyExplicitlyDeselected = MutableStateFlow(false)

    // Funktion zum Deselektieren des Players
    fun deselectPlayer() {
        _playerExplicitlyDeselected.update { true }  // Erhöht den Wert, um ein neues Event auszulösen
    }

    fun deselectEnemy() {
        _enemyExplicitlyDeselected.update { true }  // Erhöht den Wert, um ein neues Event auszulösen
    }

    fun initEnemies(){
        val grunts = createGrunts(3)
        _units.update {
            it.toMutableList().apply {
                addAll(grunts)
            }
        }
    }

    // StateFlow, der nur aktualisiert wird, wenn die ausgewählte Einheit ein Player ist
    val selectedPlayer: StateFlow<UnitEntity?> = combine(
        combine(_selectedUnit, _units) { selectedId, unitsList ->
            unitsList.firstOrNull { it.id == selectedId }
        },
        _playerExplicitlyDeselected
    ) { unitEntity, _ ->
        unitEntity
    }.scan<UnitEntity?, UnitEntity?>(null) { previousValue, newValue ->
        // Wenn der neue Wert ein Player ist, verwende ihn, sonst behalte den alten Wert bei
        if (newValue is Player) newValue else _units.value.firstOrNull { it.id == previousValue?.id }
    }.combine(_playerExplicitlyDeselected) { player, counter ->
        // Wenn ein Deselect-Event vorliegt und der Zähler geändert wurde, gib null zurück
        if (_playerExplicitlyDeselected.value) null else player
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )


    // StateFlow, der nur aktualisiert wird, wenn die ausgewählte Einheit ein Player ist
    val selectedEnemy: StateFlow<UnitEntity?> = combine(
        combine(_selectedUnit, _units) { selectedId, unitsList ->
            unitsList.firstOrNull { it.id == selectedId }
        },
        _enemyExplicitlyDeselected
    ) { unitEntity, _ ->
        unitEntity
    }.scan<UnitEntity?, UnitEntity?>(null) { previousValue, newValue ->
        // Wenn der neue Wert ein Enemy ist, verwende ihn, sonst behalte den alten Wert bei
        if (newValue is Enemy) newValue else _units.value.firstOrNull { it.id == previousValue?.id }
    }.combine(_enemyExplicitlyDeselected) { enemy, counter ->
        // Wenn ein Deselect-Event vorliegt und der Zähler geändert wurde, gib null zurück
        if (_enemyExplicitlyDeselected.value) null else enemy
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )



    fun updateUnit(new: UnitEntity){
        _units.update {
            it.map {
                if (it.id == new.id) new else it
            }
        }
    }

    fun select(unitId: String?){
        _playerExplicitlyDeselected.update { false }
        _enemyExplicitlyDeselected.update { false }
        _selectedUnit.value = unitId
    }

    fun enemyConditionTick() {
        _units.update {
            it.map { unitEntity ->
                when(unitEntity){
                    is Enemy -> {
                        var updated: Enemy = unitEntity
                        unitEntity.conditions.forEach { condition ->
                            updated = getConditionEffect(condition.key)(updated, condition.value) as Enemy
                        }
                        updated.copy(conditions = updated.conditions.map { condition -> condition.key to condition.value - 1 }.filter { it.second > 0 }.toMap())
                    }
                    else -> unitEntity
                }
            }
        }
    }

    fun load() {
        val loaded: List<UnitEntity>? =
            io.github.jackbeback.util.load()?.let { json.decodeFromString(ListSerializer(UnitEntity.serializer()), it) }
        if (loaded == null) return
        _units.update {
            it -> loaded
        }
    }

    fun save() {
        val json = json.encodeToString(units.value)
        io.github.jackbeback.util.save(json)
    }
}
