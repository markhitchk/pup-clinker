import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {classifyBetaSaveFailure} from '../src/state/saveFeedback.js';

const read=path=>readFileSync(new URL(path,import.meta.url),'utf8');

test('full inventory and maxed upgrades do not appear as cloud-save failures',()=>{
 const upgrade=classifyBetaSaveFailure({
  code:'BETA_BETA_GAMEPLAY_REJECTED',message:'Gameplay write rejected: UPGRADE_MAXED'
 });
 assert.equal(upgrade.state,'NO_CHANGE');
 assert.equal(upgrade.errorCode,null);
 assert.match(upgrade.notice,/maximum/);
 const tickets=classifyBetaSaveFailure({code:'BETA_GAMEPLAY_REJECTED',message:'TICKET_CAP'});
 assert.equal(tickets.state,'NO_CHANGE');
 const money=classifyBetaSaveFailure({code:'BETA_GAMEPLAY_REJECTED',message:'UPGRADE_NOT_AFFORDABLE'});
 assert.equal(money.state,'NO_CHANGE');
});

test('unknown/ambiguous cloud receipts are not retried automatically',()=>{
 const lost=classifyBetaSaveFailure({code:'BETA_TRANSPORT_FAILURE'});
 assert.equal(lost.state,'VERIFYING_SAVE');
 assert.equal(lost.errorCode,'SAVE_RECEIPT_UNCONFIRMED');
 const bad=classifyBetaSaveFailure({code:'BETA_GAMEPLAY_REJECTED',message:'INVALID_SAVE_STATE'});
 assert.equal(bad.state,'REJECTED');
});

test('saving UI and status are exclusive to Settings, with Account Details subpage',()=>{
 const app=read('../App.js');
 const settings=read('../src/components/SettingsScreen.jsx');
 const details=read('../src/components/AccountDetailsScreen.jsx');
 const hook=read('../src/state/useGame.js');
 const sheet=read('../src/production/BetaAccountEntry.jsx');
 assert.doesNotMatch(app,/Pup Account save status|Saving progress…|Progress saved to Pup Account|Save needs attention/);
 assert.match(app,/secondary==='account-details'/);
 assert.match(app,/AccountDetailsScreen/);
 assert.match(settings,/View Account Details/);
 assert.match(settings,/syncStatus\?\.state==='NO_CHANGE'/);
 assert.match(settings,/syncStatus\?\.notice/);
 assert.match(settings,/Check Cloud Backup/);
 assert.match(details,/Friend Code/);
 assert.match(details,/Account username/);
 assert.match(details,/Share Friend Code/);
 assert.match(details,/Back to Settings/);
 assert.doesNotMatch(details,/accountId|cloudRevision|sessionToken|deviceKey/);
 assert.match(hook,/classifyBetaSaveFailure/);
 assert.doesNotMatch(hook,/ToastAndroid\.show\('Saved, but|Save confirmation lost/);
 assert.match(sheet, /!onboarding&&!requiredAtStartup/);
});
