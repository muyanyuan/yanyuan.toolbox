package com.yanyuan.toolbox.data

// =======================================================
//  功能页卡片：长按操作区样式
// =======================================================
/**
 * 卡片长按操作区的呈现方式。
 *
 * 「卡片内展开」方案已废弃（实现上一直被网格测量裁切，效果不好），
 * 现在固定使用「翻转成面板」。设置页也不再提供切换入口。
 */
enum class CardActionStyle(val id: String, val displayName: String) {
    OVERLAY("overlay", "翻转成面板");

    companion object {
        val current: CardActionStyle get() = OVERLAY
    }
}
