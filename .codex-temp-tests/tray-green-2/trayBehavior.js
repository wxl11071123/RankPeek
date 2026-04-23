"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getWindowCloseAction = getWindowCloseAction;
exports.getWindowMinimizeAction = getWindowMinimizeAction;
function getWindowCloseAction(input) {
    if (input.isTrayEnabled && !input.isQuitting) {
        return 'hide-to-tray';
    }
    return 'quit';
}
function getWindowMinimizeAction(input) {
    if (input.isTrayEnabled && !input.isQuitting) {
        return 'hide-to-tray';
    }
    return 'keep-minimized';
}
