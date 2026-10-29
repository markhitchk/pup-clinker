import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

// Regression: the browser's valid Supabase Auth bearer JWT must pass the
// same Exchange entrypoint that accepts Android's signed PupEye envelopes.
const source=readFileSync(new URL("../supabase/functions/pup-exchange/index.ts",import.meta.url),"utf8");
const pattern=source.match(/if\(!\/(\^Bearer[^\n]+?)\/\.test\(bearer\)\)/)?.[1];
const stripPattern=source.match(/bearer\.replace\(\/(\^Bearer[^\n]+?)\/,""\)/)?.[1];

test("Puppy Exchange accepts an ordinary browser Bearer session",()=>{
 assert.ok(pattern,"Exchange must validate a Bearer JWT");
 const bearer=new RegExp(pattern);
 assert.equal(bearer.test("Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature"),true);
 assert.equal(bearer.test("Bearer "),false);
 assert.equal(bearer.test("Basic eyJhbGciOiJIUzI1NiJ9"),false);
});

test("Puppy Exchange passes only the JWT to Supabase Auth",()=>{
 assert.ok(stripPattern,"Exchange must extract the bearer token");
 const strip=new RegExp(stripPattern);
 assert.equal("Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature".replace(strip,""),
  "eyJhbGciOiJIUzI1NiJ9.payload.signature");
});

test("Puppy Exchange accepts all generated alphanumeric Friend Code digits",()=>{
 const sourcePattern=source.match(/const codePattern=(\\/[^\\n]+\\/);/)?.[1];
 assert.ok(sourcePattern,"Exchange Friend Code validator must exist");
 const codePattern=new RegExp(sourcePattern.slice(1,-1));
 for(const code of ["PUP-A0B1-C234-D567","PUP-Z999-A000-1B2C","PUP-HARLEYTG-DEV"]){
  assert.equal(codePattern.test(code),true,code);
 }
 for(const code of ["PUP-AAA-BBBB-CCCC","PUP-AAAA-BBBB-CCCC-extra","PUP-AAAA_BBBB_CCCC"]){
  assert.equal(codePattern.test(code),false,code);
 }
});
