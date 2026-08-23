package com.dzhokhov.currencyrates.ui

/** Что разрешено свайпу по карточке. */
enum class SwipeGrant {
    /** Idle: карточка идёт за пальцем в пределах [−REVEAL, +RIGHT_MAX]. */
    FULL,

    /** Revealed(другая): предыдущая возвращена, новая идёт только влево (SwitchReveal). */
    LEFT_ONLY,

    /** Editing / Revealed(эта же) / Dragging: действие вентиля выполнено, карточка в этом жесте не двигается. */
    NONE,
}

/** Пороги жестов в dp — одно место; приёмка наблюдаемая, не числовая. */
object GestureDefaults {
    /** Ширина области корзины. */
    const val REVEAL_DP = 72f

    /** Порог фиксации сдвига — половина области корзины. */
    const val REVEAL_THRESHOLD_DP = REVEAL_DP / 2

    /** Максимальный уход карточки вправо. */
    const val RIGHT_MAX_DP = 56f

    /** Порог обновления источника свайпом вправо. */
    const val RIGHT_THRESHOLD_DP = 40f

    /** Зона автопрокрутки у краёв списка при перетаскивании и шаг за кадр. */
    const val EDGE_DP = 56f
    const val SCROLL_STEP_DP = 6f
}
