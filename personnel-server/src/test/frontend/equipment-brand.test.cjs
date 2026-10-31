'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');

function configs() {
  const source = readFileSync(resolve(__dirname, '../../main/resources/static/app.js'), 'utf8');
  const start = source.indexOf('var equipmentCfg = {');
  const end = source.indexOf('var materialCfg = {', start);
  assert.ok(start >= 0 && end > start);
  const dictionaries = { equipment_brand: ['其他', '测试品牌'] };
  const context = { dictLabels: type => dictionaries[type] || [] };
  vm.createContext(context);
  vm.runInContext(source.slice(start, end), context);
  return { context, dictionaries };
}

test('large and small equipment expose identification code and brand directly before model', () => {
  const { context, dictionaries } = configs();
  for (const key of ['equipmentLargeCfg', 'equipmentSmallCfg']) {
    const cfg = context[key];
    const index = cfg.form.findIndex(field => field.key === 'model');
    const brand = cfg.form[index - 1];
    assert.equal(brand.key, 'brand');
    assert.equal(brand.label, '品牌');
    assert.equal(brand.type, 'select');
    assert.ok(!brand.required);
    assert.deepEqual(brand.options(), ['其他', '测试品牌']);
    dictionaries.equipment_brand = ['新品牌'];
    assert.deepEqual(brand.options(), ['新品牌']);
    dictionaries.equipment_brand = ['其他', '测试品牌'];
    const column = cfg.columns.findIndex(field => field.key === 'model');
    assert.equal(cfg.columns[column - 1].key, 'brand');
    assert.equal(cfg.form.filter(field => field.key === 'identCode').length, 1);
    assert.equal(cfg.columns.filter(field => field.key === 'identCode').length, 1);
  }
});

test('photo extension preserves variant columns and adds one photo column', () => {
  const { context } = configs();
  const source = readFileSync(resolve(__dirname, '../../main/resources/static/ext-features.js'), 'utf8');
  const start = source.indexOf('var PHOTO_HOOKED = [];');
  const end = source.indexOf('/* 人员照片提示文案', start);
  assert.ok(start >= 0 && end > start);
  context.entityPhotoBlockFactory = () => function () {};
  vm.runInContext(source.slice(start, end), context);
  vm.runInContext(source.slice(start, end), context);
  for (const key of ['equipmentLargeCfg', 'equipmentSmallCfg']) {
    const cfg = context[key];
    const model = cfg.columns.findIndex(field => field.key === 'model');
    assert.equal(cfg.columns[model - 1].key, 'brand');
    assert.equal(cfg.columns.filter(field => field.key === 'identCode').length, 1);
    assert.notEqual(cfg.columns, context.equipmentCfg.columns);
  }
  for (const key of ['equipmentCfg', 'equipmentGunCfg', 'equipmentLargeCfg', 'equipmentSmallCfg']) {
    const cfg = context[key];
    assert.equal(cfg.columns.filter(field => field.key === '__photo').length, 1);
    assert.equal(typeof cfg.formTop, 'function');
    assert.equal(typeof cfg.afterTable, 'function');
  }
  assert.ok(context.equipmentCfg.columns.some(field => field.key === 'identCode'));
  assert.ok(!context.equipmentGunCfg.columns.some(field => field.key === 'brand'));
  assert.ok(!context.equipmentGunCfg.columns.some(field => field.key === 'identCode'));
});

test('brand additions do not mutate general equipment or gun forms', () => {
  const { context } = configs();
  assert.ok(!context.equipmentCfg.form.some(field => field.key === 'brand'));
  assert.ok(context.equipmentCfg.form.some(field => field.key === 'identCode'));
  assert.ok(!context.equipmentGunCfg.form.some(field => field.key === 'brand'));
  assert.ok(!context.equipmentGunCfg.form.some(field => field.key === 'identCode'));
  assert.ok(context.equipmentGunCfg.form.find(field => field.key === 'code').required);
  for (const key of ['equipmentLargeCfg', 'equipmentSmallCfg']) {
    assert.equal(context[key].form.filter(field => field.key === 'brand').length, 1);
  }
});
