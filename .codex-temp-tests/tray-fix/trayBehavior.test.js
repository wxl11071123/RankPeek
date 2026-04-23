"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const node_test_1 = __importDefault(require("node:test"));
const strict_1 = __importDefault(require("node:assert/strict"));
const trayBehavior_1 = require("./trayBehavior");
(0, node_test_1.default)('close hides to tray when tray mode is available and app is not quitting', () => {
    strict_1.default.equal((0, trayBehavior_1.getWindowCloseAction)({
        isTrayEnabled: true,
        isQuitting: false
    }), 'hide-to-tray');
});
(0, node_test_1.default)('close exits when app is already quitting', () => {
    strict_1.default.equal((0, trayBehavior_1.getWindowCloseAction)({
        isTrayEnabled: true,
        isQuitting: true
    }), 'quit');
});
(0, node_test_1.default)('minimize stays minimized even when tray mode is available', () => {
    strict_1.default.equal((0, trayBehavior_1.getWindowMinimizeAction)({
        isTrayEnabled: true,
        isQuitting: false
    }), 'keep-minimized');
});
(0, node_test_1.default)('minimize stays minimized when tray mode is disabled', () => {
    strict_1.default.equal((0, trayBehavior_1.getWindowMinimizeAction)({
        isTrayEnabled: false,
        isQuitting: false
    }), 'keep-minimized');
});
(0, node_test_1.default)('tray menu exposes core navigation and utility actions', () => {
    const entries = (0, trayBehavior_1.getTrayMenuEntries)();
    strict_1.default.deepEqual(entries.map((entry) => entry.action), [
        'show-window',
        'hide-window',
        'separator',
        'navigate-home',
        'navigate-summoner',
        'navigate-match-history',
        'separator',
        'toggle-devtools',
        'quit'
    ]);
});
