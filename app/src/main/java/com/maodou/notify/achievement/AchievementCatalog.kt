package com.maodou.notify.achievement

import com.maodou.notify.data.AchievementCategoryEntity
import com.maodou.notify.data.AchievementDefEntity

/**
 * 内置成就配置。仅在安装后 seed 一次（INSERT OR IGNORE），
 * 外部可通过 AchievementRepository.installDefinitions() 覆盖/追加配置，
 * 因此新增成就不需要发版改表，也不影响已有用户进度。
 */
object AchievementCatalog {

    fun categories(): List<AchievementCategoryEntity> = listOf(
        AchievementCategoryEntity("start", "初出茅庐", 0),
        AchievementCategoryEntity("guard", "清净守护", 1),
        AchievementCategoryEntity("learn", "本地学习", 2),
        AchievementCategoryEntity("master", "进阶之路", 3)
    )

    fun definitions(): List<AchievementDefEntity> = listOf(
        def(
            id = "first_record", category = "start", name = "第一条记录", sort = 0,
            desc = "成功把第一条通知存进本地历史。",
            condition = ConditionSpec(ConditionType.ANY_NOTIFICATION),
            progress = ProgressSpec(ProgressMode.ONCE, target = 1),
            reward = RewardSpec(RewardType.TITLE, "title_first", "初来乍到")
        ),
        def(
            id = "daily_7", category = "start", name = "七日之约", sort = 1,
            desc = "在 7 个不同自然日打开毛豆通知。",
            condition = ConditionSpec(ConditionType.APP_OPENED),
            progress = ProgressSpec(ProgressMode.INCREMENT, target = 7),
            reward = RewardSpec(RewardType.NONE)
        ),
        def(
            id = "filtered_10", category = "guard", name = "小试牛刀", sort = 0,
            desc = "累计过滤 10 条骚扰通知。",
            condition = ConditionSpec(ConditionType.FILTERED, result = "*"),
            progress = ProgressSpec(ProgressMode.INCREMENT, target = 10),
            reward = RewardSpec(RewardType.TITLE, "title_cleaner", "清净新手")
        ),
        def(
            id = "filtered_100", category = "guard", name = "清净达人", sort = 1,
            desc = "累计过滤 100 条骚扰通知。",
            condition = ConditionSpec(ConditionType.FILTERED, result = "*"),
            progress = ProgressSpec(ProgressMode.INCREMENT, target = 100),
            reward = RewardSpec(RewardType.TITLE, "title_master", "清净达人")
        ),
        def(
            id = "code_5", category = "guard", name = "验证码守护", sort = 2,
            desc = "保存 5 条验证码 / 取件码类通知。",
            condition = ConditionSpec(ConditionType.CATEGORY, category = "code"),
            progress = ProgressSpec(ProgressMode.INCREMENT, target = 5),
            reward = RewardSpec(RewardType.NONE)
        ),
        def(
            id = "learn_10", category = "learn", name = "见习训练师", sort = 0,
            desc = "标注 10 条样本，帮助本地模型更懂你。",
            condition = ConditionSpec(ConditionType.LEARNED),
            progress = ProgressSpec(ProgressMode.ABSOLUTE, ProgressSource.LEARNED, target = 10),
            reward = RewardSpec(RewardType.TITLE, "title_trainer", "见习训练师")
        ),
        def(
            id = "learn_50", category = "learn", name = "资深训练师", sort = 1,
            desc = "累计标注 50 条样本。",
            condition = ConditionSpec(ConditionType.LEARNED),
            progress = ProgressSpec(ProgressMode.ABSOLUTE, ProgressSource.LEARNED, target = 50),
            reward = RewardSpec(RewardType.NONE)
        ),
        def(
            id = "rules_3", category = "master", name = "规则建筑师", sort = 0,
            desc = "创建 3 条自定义过滤规则。",
            condition = ConditionSpec(ConditionType.RULE_CREATED),
            progress = ProgressSpec(ProgressMode.ABSOLUTE, ProgressSource.RULES, target = 3),
            reward = RewardSpec(RewardType.TITLE, "title_architect", "规则建筑师")
        ),
        def(
            id = "pro_supporter", category = "master", name = "同行者", sort = 1,
            desc = "激活一次 Pro，支持项目持续开发。",
            condition = ConditionSpec(ConditionType.PRO_ACTIVATED),
            progress = ProgressSpec(ProgressMode.ABSOLUTE, ProgressSource.PRO, target = 1),
            reward = RewardSpec(RewardType.NONE)
        ),
        def(
            id = "unlock_3", category = "master", name = "成就收藏家", sort = 2,
            desc = "累计解锁 3 个成就。",
            condition = ConditionSpec(ConditionType.ACHIEVEMENT_UNLOCKED),
            progress = ProgressSpec(ProgressMode.INCREMENT, target = 3),
            reward = RewardSpec(RewardType.TITLE, "title_collector", "成就收藏家")
        )
    )

    private fun def(
        id: String, category: String, name: String, desc: String, sort: Int,
        condition: ConditionSpec, progress: ProgressSpec, reward: RewardSpec
    ) = AchievementDefEntity(
        id = id,
        categoryId = category,
        name = name,
        description = desc,
        iconKey = id,
        conditionJson = condition.toJson(),
        progressJson = progress.toJson(),
        rewardJson = reward.toJson(),
        sortOrder = sort,
        enabled = true
    )
}
