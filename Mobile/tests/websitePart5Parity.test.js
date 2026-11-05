import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';
import {websitePasswordRules,WEBSITE_JOIN_SOURCE_OPTIONS,WEBSITE_ACCOUNT_COPY} from '../src/production/websiteAccountContract.js';
import {WEBSITE_SETTINGS_SECTIONS,WEBSITE_SETTINGS_LABELS} from '../src/production/websiteSettingsContract.js';
import {passwordRules,JOIN_SOURCE_OPTIONS,registrationError,loginError} from '../src/shared/usePupAuthForm.js';
import {t,LANGUAGE_KEY} from '../src/production/websiteI18n.js';

const load=path=>readFileSync(new URL('../'+path,import.meta.url),'utf8');
test('registration and sign-in use website source-owned validation and referral choices',()=>{
 assert.equal(passwordRules,websitePasswordRules);
 assert.equal(JOIN_SOURCE_OPTIONS,WEBSITE_JOIN_SOURCE_OPTIONS);
 assert.deepEqual(WEBSITE_JOIN_SOURCE_OPTIONS.map(item=>item[0]),[
  'friend','discord','youtube','tiktok','instagram','reddit','google','play_store','other'
 ]);
 assert.equal(passwordRules('StrongPup1!').every(rule=>rule.passed),true);
 assert.equal(passwordRules('weak').every(rule=>rule.passed),false);
 assert.equal(registrationError({username:'Pup_User',password:'StrongPup1!',confirm:'StrongPup1!',
  terms:true,privacy:true,contactEmail:''}),'');
 assert.match(registrationError({username:'Pup_User',password:'StrongPup1!',confirm:'StrongPup1!',
  terms:true,privacy:false}),/Accept both/);
 assert.match(registrationError({username:'invalid name',password:'StrongPup1!',confirm:'StrongPup1!',
  terms:true,privacy:true}),/Username/);
 assert.equal(loginError('Pup_User','StrongPup1!'),'');
 assert.match(loginError('Pup_User',''),/username and password/);
});

test('native account screen uses website intro, benefits, tabs and password-manager hints',()=>{
 const source=load('src/production/BetaAccountEntry.jsx');
 assert.equal(WEBSITE_ACCOUNT_COPY.signinTitle,'Welcome back to your puppy world.');
 assert.equal(WEBSITE_ACCOUNT_COPY.registerTitle,'Your puppy journey starts here.');
 assert.equal(WEBSITE_ACCOUNT_COPY.benefits.length,3);
 assert.ok(WEBSITE_ACCOUNT_COPY.benefits.some(item=>item.includes('Verified account protection')));
 for(const key of ['eyebrow','intro','registerTitle','signinTitle','benefits'])
  assert.ok(source.includes('WEBSITE_ACCOUNT_COPY.'+key),key);
 assert.match(source,/accessibilityRole="tab" accessibilityState=\{\{selected:mode===item\}\}/);
 assert.match(source,/autoComplete="username"/);
 assert.match(source,/desktop=windowWidth>=960/);
 assert.match(source,/desktopContent/);
 assert.match(source,/desktopIntro/);
 assert.match(source,/autoComplete=\{mode==='signup'\?'new-password':'current-password'\}/);
 assert.match(source,/autoComplete="new-password"/);
 assert.match(source,/Read Terms of Use/);
 assert.match(source,/Read Privacy Policy/);
 assert.match(source,/Support contact email/);
 assert.match(source,/JOIN_SOURCE_OPTIONS.map/);
 assert.doesNotThrow(()=>transformSync(source,{babelrc:false,configFile:false,plugins:[jsx]}));
});

test('native PupEye authentication transport is not replaced by browser sessions',()=>{
 const source=load('src/production/BetaAccountEntry.jsx');
 const native=load('plugins/puppy-production/PuppyBetaAccountPackage.kt');
 for(const value of ['NativeModules.PuppyBetaAccount','api().createPupAccount(',
  'api().signInPupAccount(', 'api().completeDiscord(', 'supportsDatabaseLayout',
  'registrationError(', 'loginError('])
  assert.ok(source.includes(value),value);
 assert.doesNotMatch(source,/supabase\.auth\.signInWithPassword|client\.auth\.setSession|window\.localStorage|service_role/);
 assert.ok(native.includes('SHA256withECDSA'));
 assert.ok(native.includes('X-Pupeye-Session'));
});

test('Settings mirrors website headings, cloud status and bilingual local-only settings',()=>{
 const settings=load('src/components/SettingsScreen.jsx');
 const language=load('src/production/websiteLanguage.js');
 assert.deepEqual(WEBSITE_SETTINGS_SECTIONS.map(item=>item.id),
  ['account','profile','appearance','play','notifications','data','about']);
 assert.equal(WEBSITE_SETTINGS_LABELS.account,'Pup Account & cloud save');
 assert.equal(WEBSITE_SETTINGS_LABELS.play,'Play & performance');
 for(const key of ['account','profile','appearance','play','notifications'])
  assert.ok(settings.includes('WEBSITE_SETTINGS_LABELS.'+key),key);
 assert.ok(settings.includes('Refresh cloud progress'));
 assert.ok(settings.includes('onRefreshAccount?.()'));
 assert.ok(settings.includes('Language / Idioma'));
 assert.ok(settings.includes('useWebsiteLanguage()'));
 assert.ok(language.includes('AsyncStorage.getItem(LANGUAGE_KEY)'));
 assert.ok(language.includes('AsyncStorage.setItem(LANGUAGE_KEY,next)'));
 assert.ok(language.includes('const listeners=new Set()'));
 assert.ok(language.includes('userChanged=true'));
 assert.equal(LANGUAGE_KEY,'puppy-clicker:language:v1');
 assert.equal(t('es','rewards'),'Recompensas');
 assert.equal(t('en','rewards'),'Rewards');
 assert.doesNotMatch(language,/gameplayAction|cloud-save-write|service_role|NativeModules/);
 assert.doesNotThrow(()=>transformSync(settings,{babelrc:false,configFile:false,plugins:[jsx]}));
});

test('notification inbox and account onboarding stay website-owned native UI',()=>{
 const manifest=JSON.parse(load('src/production/websiteUiSources.json'));
 for(const pair of [
 ['src/native/src/components/NotificationInbox.jsx','src/components/NotificationInbox.jsx'],
 ['src/native/src/components/AccountDetailsScreen.jsx','src/components/AccountDetailsScreen.jsx'],
 ['src/native/src/production/AccountOnboarding.jsx','src/production/AccountOnboarding.jsx']
 ]){
  assert.ok(manifest.components.some(item=>item.source===pair[0]&&item.target===pair[1]),pair[0]);
 }
 const inbox=load('src/components/NotificationInbox.jsx');
 assert.ok(inbox.includes('visibleInboxItems'));
 assert.ok(inbox.includes("onNavigate?.('rewards')"));
 assert.ok(inbox.includes("serverBacked&&n.id.startsWith('afk:')"));
 assert.ok(inbox.includes("type:'afkOpen'"));
 assert.doesNotMatch(inbox,/<(?:div|article|section|button)\b/);
});
