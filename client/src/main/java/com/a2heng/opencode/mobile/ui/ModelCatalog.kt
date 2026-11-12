package com.a2heng.opencode.mobile.ui

import com.a2heng.opencode.mobile.data.ModelRef
import com.a2heng.opencode.mobile.data.arr
import com.a2heng.opencode.mobile.data.dbl
import com.a2heng.opencode.mobile.data.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 模型目录：按来源分组排序，和 opencode 的来源一致。
 *   0 免费（opencode 里 cost 为 0 的）
 *   1 opencode go（go 订阅，providerID = opencode-go）
 *   2 opencode console（opencode 里收费的）
 *   3 其它 provider
 */
data class ModelEntry(
    val ref: ModelRef,
    val name: String,
    val providerID: String,
    val group: Int,
    /** 思考强度（reasoning effort）可选值；为空表示这个模型没有档位。 */
    val variants: List<String> = emptyList(),
)

fun modelGroupTitle(group: Int): String = when (group) {
    0 -> "免费"
    1 -> "opencode go"
    2 -> "opencode console（付费）"
    else -> "其它"
}

/** 模型展示前缀：free / go / console / provider */
fun sourcePrefix(group: Int?, providerID: String): String = when (group) {
    0 -> "free"
    1 -> "go"
    2 -> "console"
    else -> providerID.ifBlank { "model" }
}

fun modelDisplay(model: ModelRef?, groups: Map<String, Int>): String =
    model?.let {
        val group = groups["${it.providerID}/${it.id}"]
        "${sourcePrefix(group, it.providerID)} · ${it.label}"
    } ?: ""

/** 思考强度的展示名；没有选档位（variant == null）就是 Default。 */
fun variantLabel(variant: String?): String =
    if (variant.isNullOrBlank() || variant == "default") "Default"
    else variant.replaceFirstChar { it.uppercaseChar() }

fun modelEntries(models: List<JsonObject>): List<ModelEntry> =
    models.mapNotNull { item ->
        val id = item.str("id") ?: return@mapNotNull null
        val provider = item.str("providerID") ?: ""
        val cost = (item["cost"] as? JsonArray)?.firstOrNull() as? JsonObject
        val free = (cost?.dbl("input") ?: 0.0) == 0.0 && (cost?.dbl("output") ?: 0.0) == 0.0
        val group = when {
            provider == "opencode-go" -> 1
            provider == "opencode" && free -> 0
            provider == "opencode" -> 2
            else -> 3
        }
        ModelEntry(
            ref = ModelRef(id, provider),
            name = item.str("name") ?: id,
            providerID = provider,
            group = group,
            variants = item.arr("variants").mapNotNull { (it as? JsonObject)?.str("id") },
        )
    }.sortedWith(compareBy<ModelEntry> { it.group }.thenBy { it.name.lowercase() })
