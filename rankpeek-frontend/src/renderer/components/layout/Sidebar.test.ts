import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const testDir = dirname(fileURLToPath(import.meta.url))
const rendererRoot = resolve(testDir, '../..')

function readRendererFile(path: string) {
  return readFileSync(resolve(rendererRoot, path), 'utf8')
}

test('sidebar uses current-color svg branding and line icons', () => {
  const source = readRendererFile('components/layout/Sidebar.vue')

  assert.match(source, /const menuItems: Array<\{ path: string; icon: string; labelKey: MessageKey \}>/)
  assert.match(source, /import homeIcon from '@\/assets\/icons\/nav-home\.svg'/)
  assert.match(source, /import gamingIcon from '@\/assets\/icons\/nav-gamepad\.svg'/)
  assert.match(source, /import summonerIcon from '@\/assets\/icons\/nav-user-search\.svg'/)
  assert.match(source, /import matchRecordIcon from '@\/assets\/icons\/nav-record-bars\.svg'/)
  assert.match(source, /import settingsGearIcon from '@\/assets\/icons\/nav-gear-five\.svg'/)
  assert.match(source, /\{ path: '\/match-history', icon: matchRecordIcon, labelKey: 'nav.matchHistory' \}/)
  assert.match(source, /\{ path: '\/settings', icon: settingsGearIcon, labelKey: 'nav.settings' \}/)
  assert.match(source, /class="sidebar-brand"/)
  assert.match(source, /class="sidebar-logo"/)
  assert.match(source, /stroke="currentColor"/)
  assert.match(source, /\.sidebar-logo\s*{[\s\S]*width:\s*36px;[\s\S]*height:\s*36px;/)
  assert.match(source, /\.nav-icon-svg\s*{[\s\S]*background:\s*currentColor;[\s\S]*mask:\s*var\(--nav-icon-url\) center \/ contain no-repeat;/)
  assert.doesNotMatch(source, /🏠|🎮|👤|📊|🏷️|📝|🔧/)
})

test('match-history nav label is renamed to my matches', () => {
  const zhCN = readRendererFile('i18n/locales/zh-CN.ts')
  const enUS = readRendererFile('i18n/locales/en-US.ts')

  assert.match(zhCN, /'nav.matchHistory': '我的战绩'/)
  assert.doesNotMatch(zhCN, /'nav.matchHistory': '召唤师信息'/)
  assert.match(enUS, /'nav.matchHistory': 'My Matches'/)
  assert.doesNotMatch(enUS, /'nav.matchHistory': 'Summoner Info'/)
})

test('new requested sidebar icons are current-color linear svg files', () => {
  const iconRoot = resolve(rendererRoot, 'assets/icons')
  const requestedIcons = ['nav-record-bars.svg', 'nav-gear-five.svg']

  for (const iconName of requestedIcons) {
    const iconPath = resolve(iconRoot, iconName)
    assert.equal(existsSync(iconPath), true, `${iconName} should exist`)

    const svg = readFileSync(iconPath, 'utf8')
    assert.match(svg, /fill="none"/)
    assert.match(svg, /stroke="currentColor"/)
    assert.match(svg, /stroke-linecap="round"/)
    assert.match(svg, /stroke-linejoin="round"/)
  }

  const gear = readFileSync(resolve(iconRoot, 'nav-gear-five.svg'), 'utf8')
  assert.match(gear, /data-icon-name="five-tooth-gear"/)
})
