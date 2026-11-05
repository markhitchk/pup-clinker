import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame,UPGRADES} from '../src/engine/game.js';
import {MAX_UPGRADES_PER_ITEM} from '../src/engine/balancedProgression.js';
import {localEpochDay} from '../src/engine/monthlyRewards.js';
import {webGoalState,hasCompletedUpgradeShop} from '../src/production/websiteGoalState.js';
import {t,normalizeLanguage} from '../src/production/websiteI18n.js';

const read=path=>readFileSync(new URL('../'+path,import.meta.url),'utf8');
test('canonical website Rewards markup stays a native generated UI source',()=>{
 const body=read('src/components/WebsiteRewardsScreens.jsx');
 const app=read('App.js');
 for(const component of ['DailyGoalCard','RewardsGroup','RewardsScreen']){
  assert.ok(body.includes('function '+component+'('),component);
  assert.ok(!app.includes('function '+component+'('),'duplicate Android '+component);
 }
 for(const text of ['Today’s goals','Daily Puppy Gift','Dog Park Adventure','Puppy Casino','Puppy Gacha','Prestige','ACHIEVEMENTS','Claim Reward'])
  assert.ok(body.includes(text),'missing website reward section '+text);
 for(const text of ["onOpen?.(destination)","act({type:'claimDailyGoal'","accessibilityState={{expanded:open}}","disabled={unavailable}"])
  assert.ok(body.includes(text),'missing website Rewards behavior '+text);
 assert.doesNotMatch(body,/<(?:div|article|section|button)\b/);
 assert.ok(app.includes('createWebsiteRewardsScreens'));
 assert.ok(app.includes('webServerRewardsDate:nativeRewardDate'));
 assert.ok(app.includes('webServerEpochDay:nativeRewardEpoch'));
});
test('website daily goal eligibility includes actionable navigation and maxed shop',()=>{
 const date=new Date(2026,10,4,14,0);
 const g=freshGame();
 g.dailyDay=localEpochDay(date);
 g.dailyTapStart=g.totalTaps;
 g.dailyShopStart=g.totalShopPurchases;
 const tap={id:'tap_time',metric:'taps',target:50};
 const pending=webGoalState(g,tap,date);
 assert.equal(pending.complete,false);
 assert.equal(pending.destination,'play');
 assert.equal(pending.continueLabel,'Go to Play');
 const shop={id:'shop_visit',metric:'shop',target:1};
 assert.equal(webGoalState(g,shop,date).continueLabel,'Go to Shop');
 assert.equal(hasCompletedUpgradeShop(g),false);
 assert.equal(UPGRADES.length,10);
 for(const u of UPGRADES)g.upgrades[u.id]=MAX_UPGRADES_PER_ITEM;
 const maxed=webGoalState(g,shop,date);
 assert.equal(hasCompletedUpgradeShop(g),true);
 assert.equal(maxed.maxedShop,true);
 assert.equal(maxed.complete,true);
 assert.equal(maxed.progress>=1,true);
});
test('Rewards use the unchanged website bilingual string catalogue',()=>{
 assert.equal(t('en','rewards'),'Rewards');
 assert.equal(t('es','rewards'),'Recompensas');
 assert.equal(t('es','todaysGoals'),'Metas de hoy');
 assert.equal(t('en','overview'),'Overview');
 assert.equal(normalizeLanguage('es-MX'),'es');
});
