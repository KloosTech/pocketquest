package io.github.jackbeback.vm

import androidx.lifecycle.ViewModel
import io.github.jackbeback.data.ConditionType
import io.github.jackbeback.data.UnitEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LogViewModel: ViewModel() {
    val _log = MutableStateFlow<List<String>>(listOf())
    val log = _log.asStateFlow()

    companion object {
        val current = LogViewModel()
    }

    fun addLog(new: String){
        _log.update { it + new }
    }

    fun addAttack(target: UnitEntity, amount: Int){
        addLog("Attacked ${target.name} for $amount damage")
    }

    fun addHeal(target: UnitEntity, amount: Int){
        addLog("Healed ${target.name} for $amount health")
    }

    fun addCondition(target: UnitEntity, new: Map<ConditionType, Int>){
        //Add log only for the Conditions that changed
        val changed = new.filter { (k, v) -> target.conditions[k] != v }
        if(changed.isNotEmpty()){
            addLog("Applied ${changed.map { (k, v) -> "$v ${k.name}" }.joinToString(", ")} to ${target.name}")
        }
    }

    fun conditionDamage(unit: UnitEntity, condition: ConditionType, amount: Int) {
        addLog("${unit.name} took $amount Damage from ${condition.name}")
    }
}
