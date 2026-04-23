"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getWindowCloseAction = getWindowCloseAction;
exports.getWindowMinimizeAction = getWindowMinimizeAction;
exports.getTrayMenuEntries = getTrayMenuEntries;
function getWindowCloseAction(input) {
    if (input.isTrayEnabled && !input.isQuitting) {
        return 'hide-to-tray';
    }
    return 'quit';
}
function getWindowMinimizeAction(input) {
    return 'keep-minimized';
}
function getTrayMenuEntries() {
    return [
        { label: '显示主窗口', action: 'show-window' },
        { label: '隐藏到托盘', action: 'hide-window' },
        { label: '', action: 'separator' },
        { label: '首页', action: 'navigate-home' },
        { label: '召唤师信息', action: 'navigate-summoner' },
        { label: '战绩查询', action: 'navigate-match-history' },
        { label: '', action: 'separator' },
        { label: '开发者工具', action: 'toggle-devtools' },
        { label: '退出', action: 'quit' }
    ];
}
